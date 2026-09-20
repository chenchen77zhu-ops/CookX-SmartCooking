"""Reproducible offline training; no private CSVs and no claimed hardware accuracy."""
import argparse, copy, hashlib, json, os, platform, subprocess, time
from pathlib import Path
import numpy as np
import torch
from torch import nn
from sklearn.metrics import f1_score
from simulate import simulate, PHASES
from models import ThermalModel

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ASSETS = ROOT / "frontend/public/temperature"
REPORT = ROOT / "docs/temperature"
torch.set_num_threads(4)

def prepare(runs, context=True, quality=True):
    proc = subprocess.run(["node",str(HERE/"prepare.mjs")], input=json.dumps({"runs":runs,"context":context,"qualityGate":quality}),
                          capture_output=True,text=True,encoding="utf-8",check=True)
    packed=json.loads(proc.stdout)
    x,y,f,q,rule,meta=[],[],[],[],[],[]
    for run,batch in zip(runs,packed):
        for row in batch["records"]:
            i=row["index"]
            x.append(row["x"]); y.append(run["truth"][i]["phase"]); q.append(float(run["truth"][i]["quality"]))
            # Forecast only across horizons with no user action: current-condition continuation.
            future=[run["truth"][i+h]["temperature"]/300 for h in [10,30,60]]
            mask=[not any(i<e["index"]<=i+h for e in run["events"]) for h in [10,30,60]]
            f.append(future + mask)
            rule.append(row)
            meta.append((run["id"], i))
    return {"x":np.asarray(x,dtype=np.float32).reshape(-1,120,8),"y":np.asarray(y),"f":np.asarray(f,dtype=np.float32),
            "q":np.asarray(q,dtype=np.float32),"rule":rule,"meta":meta}

def train(kind, data, val, epochs, seed):
    torch.manual_seed(seed)
    model=ThermalModel(kind)
    opt=torch.optim.AdamW(model.parameters(),lr=.001,weight_decay=.01)
    x=torch.from_numpy(data["x"]); y=torch.from_numpy(data["y"]); f=torch.from_numpy(data["f"]); q=torch.from_numpy(data["q"])
    counts=np.bincount(data["y"],minlength=6)
    weights=torch.tensor((len(y)/(6*np.maximum(counts,1)))**.5,dtype=torch.float32)
    best,best_loss=None,float("inf")
    rng=np.random.default_rng(seed)
    history=[]
    quantiles=x.new_tensor([.1,.5,.9])
    for epoch in range(epochs):
        model.train(); losses=[]
        for ix in np.array_split(rng.permutation(len(x)),max(1,int(np.ceil(len(x)/128)))):
            logits,forecast,quality=model(x[ix])
            error=f[ix,:3,None]-forecast
            pin=torch.maximum(quantiles*error,(quantiles-1)*error)
            mask=f[ix,3:,None]
            loss=nn.functional.cross_entropy(logits,y[ix],weight=weights) + 5*(pin*mask).sum()/mask.sum().clamp_min(1)/3 + .3*nn.functional.binary_cross_entropy_with_logits(quality[:,0],q[ix])
            opt.zero_grad();loss.backward();nn.utils.clip_grad_norm_(model.parameters(),1);opt.step();losses.append(loss.item())
        pred=infer(model,val["x"])
        score=float(nn.functional.cross_entropy(torch.from_numpy(pred[0]),torch.from_numpy(val["y"])))
        history.append({"epoch":epoch+1,"train_loss":float(np.mean(losses)),"validation_cross_entropy":score})
        if score<best_loss: best_loss=score;best=copy.deepcopy(model.state_dict())
        print(kind,epoch+1,round(float(np.mean(losses)),4),flush=True)
    model.load_state_dict(best);model.eval()
    return model,history

def infer(model,x):
    outputs=[[],[],[]]
    model.eval()
    with torch.no_grad():
        for batch in np.array_split(x,max(1,int(np.ceil(len(x)/256)))):
            for out,val in zip(outputs,model(torch.from_numpy(batch))): out.append(val.numpy())
    return [np.concatenate(o) for o in outputs]

def metrics(prediction,data,forecast=None):
    actual=data["y"]; accepted=prediction!=5
    result={"macro_f1":float(f1_score(actual,prediction,labels=list(range(6)),average="macro",zero_division=0)),
            "abstention_fraction":float(np.mean(~accepted)),
            "accepted_error_rate":float(np.mean(prediction[accepted]!=actual[accepted])) if accepted.any() else None}
    if forecast is not None:
        valid=data["f"][:,3:].astype(bool) & data["q"][:,None].astype(bool)
        result["forecast_mae_C"]=[float(np.mean(np.abs(forecast[valid[:,j],j,1]-data["f"][valid[:,j],j])*300)) if valid[:,j].any() else None for j in range(3)]
        result["interval_80_coverage"]=[float(np.mean((forecast[valid[:,j],j,0]<=data["f"][valid[:,j],j]) & (forecast[valid[:,j],j,2]>=data["f"][valid[:,j],j]))) if valid[:,j].any() else None for j in range(3)]
    # Transition matching at recorded (4-second) evaluation cadence.
    delays=[];false_events=0;missed=0
    ids=[m[0] for m in data["meta"]]
    for run_id in dict.fromkeys(ids):
        indexes=[i for i,k in enumerate(ids) if k==run_id]
        truth_events=[i for a,i in zip(indexes,indexes[1:]) if actual[i]!=actual[a] and actual[i]!=5]
        prediction_events=[i for a,i in zip(indexes,indexes[1:]) if prediction[i]!=prediction[a] and prediction[i]!=5]
        matched=set()
        for ti in truth_events:
            candidates=[pi for pi in prediction_events if pi not in matched and 0<=(data["meta"][pi][1]-data["meta"][ti][1])*.5<=20 and prediction[pi]==actual[ti]]
            if candidates:
                pi=candidates[0];matched.add(pi);delays.append((data["meta"][pi][1]-data["meta"][ti][1])*.5)
            else: missed+=1
        false_events+=len(set(prediction_events)-matched)
    result.update(event_false_positives=false_events,event_misses=missed,mean_event_delay_s=float(np.mean(delays)) if delays else None)
    return result

def main():
    ap=argparse.ArgumentParser();ap.add_argument("--epochs",type=int,default=10);ap.add_argument("--train-runs",type=int,default=100)
    args=ap.parse_args();ASSETS.mkdir(parents=True,exist_ok=True);REPORT.mkdir(parents=True,exist_ok=True)
    runs={split:[simulate(seed,split) for seed in range(start,start+n)] for split,start,n in
          [("train",1000,args.train_runs),("validation",2000,24),("test",3000,30),("ood",4000,30)]}
    (HERE/"runs.generated.json").write_text(json.dumps(runs),encoding="utf-8")
    datasets={k:prepare(v) for k,v in runs.items()}
    report={"scope":"physics_simulation_only","hardware_validation":False,"seed":42,"epochs":args.epochs,
            "runtime":{"platform":platform.platform(),"processor":platform.processor(),"torch":torch.__version__,"threads":4},
            "splits":{k:[r["id"] for r in v] for k,v in runs.items()},"models":{}}
    for split in ["test","ood"]:
        d=datasets[split]
        # Legacy UI cannot identify semantic phases; mapping defined explicitly as trend proxy.
        t=d["x"][:,-1,0]*300
        fixed=np.where(t<170,0,np.where(t<=185,3,5))
        report.setdefault("baselines",{})[split]={"fixed_threshold_proxy":metrics(fixed,d),
            "dynamic_rules":metrics(np.array([r["rulePhase"] for r in d["rule"]]),d)}
    full=None
    for variant,kind in [("transformer","transformer"),("tcn","tcn"),("without_context","transformer"),("without_motion_training","transformer")]:
        train_data,val_data=datasets["train"],datasets["validation"]
        eval_data={k:datasets[k] for k in ["test","ood"]}
        if variant=="without_context":
            def strip(d):
                clone={**d,"x":d["x"].copy()};clone["x"][:,:,5:8]=0;return clone
            train_data=strip(train_data);val_data=strip(val_data);eval_data={k:strip(v) for k,v in eval_data.items()}
        if variant=="without_motion_training":
            train_data=prepare([simulate(r["seed"],"train",motion=False) for r in runs["train"]])
        model,history=train(kind,train_data,val_data,args.epochs,42)
        entry={"parameters":sum(p.numel() for p in model.parameters()),"history":history,"evaluation":{}}
        for split,d in eval_data.items():
            logits,forecasts,quality=infer(model,d["x"]);probs=torch.softmax(torch.from_numpy(logits),dim=1).numpy()
            predicted=probs.argmax(1)
            # A quality gate may veto the model, but model may not override rule risk decisions.
            allow=np.array([r["quality"]=="usable" for r in d["rule"]]) & (probs.max(1)>=.55) & (quality[:,0]>=0)
            predicted[~allow]=5
            entry["evaluation"][split]=metrics(predicted,d,forecasts)
            if variant=="transformer":
                entry.setdefault("raw_model",{})[split]=metrics(probs.argmax(1),d,forecasts)
        report["models"][variant]=entry
        if variant=="transformer": full=model
    # Quality-gate ablation: same model, features rebuilt without segment quality filtering.
    for split in ["test","ood"]:
        d=prepare(runs[split],quality=False);logits,forecasts,_=infer(full,d["x"])
        report.setdefault("without_quality_gate",{})[split]=metrics(logits.argmax(1),d,forecasts)
    model_path=ASSETS/"thermal-transformer.onnx"
    dummy=torch.zeros(1,120,8)
    torch.onnx.export(full,dummy,str(model_path),input_names=["history"],output_names=["phase_logits","forecast","quality_logit"],
                      opset_version=17,dynamo=False)
    import onnxruntime as ort
    session=ort.InferenceSession(str(model_path),providers=["CPUExecutionProvider"])
    sample=datasets["test"]["x"][:1]
    expected=infer(full,sample)
    actual=session.run(None,{"history":sample})
    report["onnx_max_abs_error"]=max(float(np.max(np.abs(a-b))) for a,b in zip(expected,actual))
    times=[]
    for _ in range(10): session.run(None,{"history":sample})
    for _ in range(100):
        start=time.perf_counter();session.run(None,{"history":sample});times.append((time.perf_counter()-start)*1000)
    report["desktop_onnx_p95_ms"]=float(np.percentile(times,95));report["model_bytes"]=model_path.stat().st_size
    report["promotion"]={"default":"rules","reason":"No target-device cooking validation; synthetic benchmark cannot establish real-world reliability."}
    manifest={"schemaVersion":1,"featureVersion":1,"modelVersion":"thermal-transformer-v1","file":model_path.name,
              "sha256":hashlib.sha256(model_path.read_bytes()).hexdigest(),"bytes":model_path.stat().st_size,"inputShape":[1,120,8],
              "phases":PHASES,"horizonsSeconds":[5,15,30],"defaultMode":"rules","validationDomain":"physics_simulation_only","experimental":True}
    (ASSETS/"manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    (REPORT/"experiment-results.json").write_text(json.dumps(report,indent=2,allow_nan=False),encoding="utf-8")
    demo=runs["test"][1]
    replay={"schemaVersion":1,"source":"physics_simulation","label":"热锅、投料与移动测温仿真","context":demo["context"],
            "samples":demo["samples"],"events":[e for e in demo["events"] if e["confirmed"]]}
    (ASSETS/"replay.json").write_text(json.dumps(replay,ensure_ascii=False),encoding="utf-8")
    (HERE/"parity-fixture.json").write_text(json.dumps({"input":sample.tolist(),"outputs":[o.tolist() for o in actual]}),encoding="utf-8")
    print("REPORT",report["model_bytes"],report["desktop_onnx_p95_ms"],flush=True)
if __name__=="__main__":main()
