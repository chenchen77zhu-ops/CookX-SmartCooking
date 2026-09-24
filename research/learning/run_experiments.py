"""Artificial chronological feedback for pipeline tests, never production training data."""
import sys,json
from pathlib import Path
from datetime import datetime,timedelta,timezone
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from app.domain.learning_model import train,CONFIG
start=datetime(2026,1,1,tzinfo=timezone.utc)
rows=[{'id':f'artificial-{i}','created_at':(start+timedelta(days=i)).isoformat(),'liked':i%2==0,'features':['清淡' if i%2==0 else '麻辣'],'original_score':50,'provenance':'synthetic_fixture'} for i in range(20)]
cases={'separable':rows,'cold_start':rows[:5],'positive_only':[{**r,'liked':True} for r in rows],'already_perfect_original':[{**r,'original_score':100 if r['liked'] else 0} for r in rows],'same_timestamp':[{**r,'created_at':start.isoformat()} for r in rows]}
report={'provenance':'Artificial alternating labels/tags to test training and fallback only. No real longitudinal users.','config':CONFIG,'datasets':cases,'results':{name:train(values) for name,values in cases.items()},'reproducible':train(rows)==train(rows),'limitations':'Synthetic separability is deliberately easy; passing this test does not establish actual user benefit or generalization.'}
(ROOT/'docs/learning-experiment-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print({name:r['accepted'] for name,r in report['results'].items()})
