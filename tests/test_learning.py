from datetime import datetime,timedelta,timezone
from test_sqlite_auth import sqlite_app,signup
from test_households import post,key
from app.domain.learning_model import train,prediction

def synthetic_rows():
    start=datetime(2026,1,1,tzinfo=timezone.utc)
    return [{'id':str(i),'created_at':(start+timedelta(days=i)).isoformat(),'liked':i%2==0,'features':['清淡' if i%2==0 else '麻辣'],'original_score':50,'provenance':'synthetic_fixture'} for i in range(20)]

def test_learning_chronological_gate_and_no_test_feature_leakage():
    rows=synthetic_rows();rows[-1]['features'].append('只在测试出现')
    model=train(rows);assert model['accepted'];assert model['metrics']['train_end']<model['metrics']['test_start']
    assert '只在测试出现' not in model['evaluation_vocabulary']
    assert set(model['evaluation_train_ids']).isdisjoint(model['evaluation_test_ids'])
    assert model['metrics']['personalized_pairwise_auc']>model['metrics']['original_pairwise_auc']
    assert prediction(model,{'tags':['清淡']})[0]>prediction(model,{'tags':['麻辣']})[0]
    assert not train(rows[:5])['accepted']
    assert not train([{**r,'liked':True} for r in rows])['accepted']
    assert not train([{**r,'original_score':100 if r['liked'] else 0} for r in rows])['accepted']

def test_learning_opt_in_private_feedback_reset_replay_and_fallback(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'learning-one');_,other=signup(c,'learning-two')
    path='/api/v3/learning/feedback/recipe_001';body={'idempotency_key':key(),'liked':True,'expected_version':0}
    assert c.put(path,headers=h,json=body).status_code==409
    assert c.put('/api/v3/learning/settings',headers=h,json={'idempotency_key':key(),'enabled':True,'expected_version':0}).status_code==200
    first=c.put(path,headers=h,json=body);assert first.status_code==200,first.text
    assert c.put(path,headers=h,json=body).json()['replayed']
    assert c.get('/api/v3/learning',headers=h).json()['feedback'][0]['liked']
    assert c.get('/api/v3/learning',headers=other).json()['feedback']==[]
    trained=post(c,h,'/learning/train').json()['model'];assert not trained['accepted']
    from app.domain.learning import rerank
    sample=[{'recipe_id':'recipe_001','rank':1,'total_score':80}]
    assert rerank(u['id'],sample)[0]==sample
    post(c,h,'/learning/reset',action='clear_feedback');assert c.get('/api/v3/learning',headers=h).json()['feedback']==[]
    assert c.put(path,headers=h,json=body).json()['cleared']
    assert c.get('/api/v3/learning',headers=h).json()['feedback']==[]
    c.put('/api/v3/learning/settings',headers=h,json={'idempotency_key':key(),'enabled':False,'expected_version':1})
    assert rerank(u['id'],sample)[1]['enabled'] is False

def test_learning_only_reorders_input_candidates_keeps_original_scores(sqlite_app):
    c,_=sqlite_app;u,h=signup(c,'learning-accepted')
    from app.domain.common import put
    from app.domain.learning import rerank
    from app.storage import store
    rows=synthetic_rows();model=train(rows)
    with store.transaction():
        put('learning_settings',u['id'],u['id'],{'enabled':True})
        put('learning_model',u['id'],u['id'],model)
        put('feedback','last-row',u['id'],rows[-1])
    candidates=[{'recipe_id':'recipe_006','rank':1,'total_score':90,'component_scores':{'F':.5}},{'recipe_id':'recipe_005','rank':2,'total_score':70,'component_scores':{'F':.2}}]
    result,status=rerank(u['id'],candidates);assert status['applied']
    assert {r['recipe_id'] for r in result}=={r['recipe_id'] for r in candidates}
    for r in result:
        original=next(c for c in candidates if c['recipe_id']==r['recipe_id']);assert r['total_score']==original['total_score'] and r['component_scores']==original['component_scores']
    with store.transaction():
        row=__import__('app.domain.common',fromlist=['read']).read('learning_model',u['id']);put('learning_model',u['id'],u['id'],{**row,'weights':[float(0)]},row['version'])
    assert rerank(u['id'],candidates)[0]==candidates
