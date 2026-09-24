"""Fixed, synthetic evaluation of constraints and explicit failure states; no user feedback."""
import json,sys
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from app.domain.planning_solver import solve,audit
from app.domain.planning_catalog import catalog,key
from app.services.recommendation_service import score_recipe
reference=datetime(2026,9,24,tzinfo=timezone.utc)
base={'start_date':'2026-09-25','meals':[{'day':i,'meal':m,'side':False} for i in range(7) for m in ['lunch','dinner']],'people':1,'portion_factor':1,'budget':None,'max_minutes':120,'max_difficulty':2,'max_repeat':2,'avoid_ingredients':[],'dietary_restrictions':[],'nutrition':{},'locks':[],'family_id':None,'inventory_priority':True,'confirm_estimates':True,'timeout_seconds':5,'seed':77,'utc_offset_hours':8}
prices=[{'name':k.split('|')[0],'unit':k.split('|')[1],'price':.1} for k in sorted({key(i['name'],i['unit']) for r in catalog() for i in r['ingredients']})]
cases=[('weekly',base,prices),('budget_missing',{**base,'budget':100},[]),('time_infeasible',{**base,'max_minutes':1},prices),('locked_allergen',{**base,'avoid_ingredients':['鸡蛋'],'locks':[{'day':0,'meal':'lunch','role':'main','recipe_id':'recipe_001'}]},prices),('timeout',{**base,'timeout_seconds':.0000001},prices),('nutrition_bounds',{**base,'nutrition':{'protein_g':{'min':100,'max':800}},'budget':10000},prices)]
report={'provenance':'synthetic configuration and price fixtures; not real purchases or human preferences','reference_time':reference.isoformat(),'solver':'OR-Tools 9.15.6755, one worker, seed 77','configs':[],'results':[]}
for name,cfg,ps in cases:
 r=solve(cfg,[],ps,reference);report['configs'].append({'name':name,'config':cfg,'prices':ps})
 if r['meals']:audit(cfg,r,[])
 report['results'].append({'case':name,'status':r['solver_status'],'meals':len(r['meals']),'selected':[[d['recipe_id'] for d in m['dishes']] for m in r['meals']],'constraints_rechecked':bool(r['meals']),'stages':r.get('stages',[]),'elapsed_seconds':r.get('elapsed_seconds')})
# Independent naive baseline picks best single-meal score repeatedly, without week constraints.
standard=[r for r in catalog() if 'lunch' in r['_planning']['meals'] and 'main' in r['_planning']['roles']]
best=max(standard,key=lambda r:score_recipe(r,[],now=reference)['total_score'])
report['baseline']={'method':'repeat highest original single-meal score','recipe_id':best['id'],'repeat_count':14,'max_repeat':2,'weekly_repeat_constraint_passed':False,'note':'Demonstrates why an independent week constraint layer is needed; not a comparison of real eating outcomes.'}
again=solve(base,[],prices,reference)
report['deterministic_selection']=report['results'][0]['selected']==[[d['recipe_id'] for d in m['dishes']] for m in again['meals']]
(ROOT/'docs/planning-experiment-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'cases':[(r['case'],r['status']) for r in report['results']],'deterministic':report['deterministic_selection']},ensure_ascii=False))
