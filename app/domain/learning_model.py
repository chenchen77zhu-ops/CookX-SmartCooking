"""Small deterministic regularized logistic regression; chronological holdout gate."""
import numpy as np
from app.services.inventory_transactions import digest
VERSION='tag-logistic-v1'
CONFIG={'regularization':.08,'learning_rate':.25,'iterations':800,'train_fraction':.75,'minimum_rows':12}
def tokens(recipe):return sorted({str(t).strip() for t in recipe.get('tags',[]) if isinstance(t,str) and t.strip()}|({'方法:'+recipe['method']} if isinstance(recipe.get('method'),str) and recipe['method'] else set()))
def matrix(rows,vocabulary):return np.asarray([[1.]+[float(tag in row['features']) for tag in vocabulary] for row in rows],dtype=float)
def sigmoid(values):return 1/(1+np.exp(-np.clip(values,-40,40)))
def fit(x,y):
    w=np.zeros(x.shape[1],dtype=float)
    for _ in range(CONFIG['iterations']):
        penalty=CONFIG['regularization']*w;penalty[0]=0
        w-=CONFIG['learning_rate']*((x.T@(sigmoid(x@w)-y))/len(y)+penalty)
    return w

def auc(y,scores):
    p=[float(s) for v,s in zip(y,scores) if v==1];n=[float(s) for v,s in zip(y,scores) if v==0]
    if not p or not n:return None
    return sum((a>b)+.5*(a==b) for a in p for b in n)/(len(p)*len(n))
def logloss(y,scores):
    p=np.clip(scores,1e-8,1-1e-8);return float(np.mean(-y*np.log(p)-(1-y)*np.log(1-p)))
def train(rows):
    rows=sorted(rows,key=lambda r:(r['created_at'],r['id']))
    base={'algorithm':VERSION,'config':CONFIG,'row_count':len(rows),'accepted':False,'provenance':sorted({r.get('provenance','explicit_user') for r in rows})}
    if len(rows)<CONFIG['minimum_rows']:return {**base,'reason':'至少需要 12 道菜的明确反馈，并同时包含喜欢与不喜欢；当前继续使用原排序'}
    cut=int(len(rows)*CONFIG['train_fraction'])
    # Never split a timestamp group across train/test.
    while cut>0 and cut<len(rows) and rows[cut-1]['created_at']==rows[cut]['created_at']:cut-=1
    before,after=rows[:cut],rows[cut:]
    if len(before)<6 or len(after)<3 or len({r['liked'] for r in before})<2 or len({r['liked'] for r in after})<2:return {**base,'reason':'时间划分后的训练/测试正负反馈不足，继续使用原排序'}
    vocabulary=sorted({t for row in before for t in row['features']})
    x=matrix(before,vocabulary);y=np.array([int(r['liked']) for r in before]);w=fit(x,y)
    test_y=np.array([int(r['liked']) for r in after]);pred=sigmoid(matrix(after,vocabulary)@w)
    original=auc(test_y,[r['original_score'] for r in after]);personal=auc(test_y,pred);loss=logloss(test_y,pred);null_loss=logloss(test_y,np.full(len(after),float(y.mean())))
    accepted=personal>original+1e-6 and loss<=null_loss
    metrics={'train_count':len(before),'test_count':len(after),'train_end':before[-1]['created_at'],'test_start':after[0]['created_at'],'original_pairwise_auc':original,'personalized_pairwise_auc':personal,'heldout_log_loss':loss,'constant_preference_log_loss':null_loss}
    result={**base,'accepted':bool(accepted),'metrics':metrics,'reason':'按时间留出的反馈上排序对照通过，启用标签偏好重排' if accepted else '按时间留出的反馈上未优于原排序，继续使用原排序','evaluation_vocabulary':vocabulary,'evaluation_train_ids':[r['id'] for r in before],'evaluation_test_ids':[r['id'] for r in after]}
    if accepted:
        # Gate uses only the prefix-fitted model. Deployment refits known feedback after evaluation.
        deployed_vocabulary=sorted({t for row in rows for t in row['features']})
        deployed=fit(matrix(rows,deployed_vocabulary),np.array([int(r['liked']) for r in rows]))
        result.update(vocabulary=deployed_vocabulary,weights=deployed.tolist(),model_version=VERSION+'-'+digest(rows)[:12],trained_through=rows[-1]['created_at'])
    return result

def prediction(model,recipe):
    features=tokens(recipe);weights=np.asarray(model['weights']);x=np.asarray([1.]+[float(t in features) for t in model['vocabulary']])
    if len(weights)!=len(x) or not np.isfinite(weights).all():raise ValueError('invalid model coefficients')
    score=float(sigmoid(x@weights));reasons=sorted([(t,float(w)) for t,w in zip(model['vocabulary'],weights[1:]) if t in features],key=lambda p:-abs(p[1]))[:3]
    return score,[{'tag':t,'direction':'偏好' if w>0 else '较少偏好','weight':round(w,4)} for t,w in reasons]
