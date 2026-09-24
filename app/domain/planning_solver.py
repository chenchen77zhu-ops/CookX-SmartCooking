"""Independent integer CP-SAT meal planning; never rewrites single-meal scoring."""
import math,time
from datetime import datetime,timedelta,timezone
from ortools.sat.python import cp_model
from app.domain.planning_catalog import catalog,nutrition,key,NUTRIENTS
from app.services.recommendation_service import score_recipe
Q=1000
N=1000
P=10000
DIFFICULTY={'简单':1,'中等':2,'困难':3}

def solve(config,inventory,prices,reference=None):
    from app.services.freshness_service import calculate_freshfusion
    current=reference or datetime.now(timezone.utc);started=time.monotonic()
    recipes=catalog();price_map={key(p['name'],p['unit']):p['price'] for p in prices}
    meals=sorted(config['meals'],key=lambda s:(s['day'],{'breakfast':0,'lunch':1,'dinner':2}[s['meal']]))
    portions=config['people']*config['portion_factor'];nutrient_bounds=config['nutrition'];budget=config.get('budget')
    rows={};exclusions=[]
    for recipe in recipes:
        score=score_recipe(recipe,inventory,{'avoid_ingredients':config['avoid_ingredients'],'dietary_restrictions':config['dietary_restrictions']},now=current)
        if score is None:exclusions.append({'recipe_id':recipe['id'],'reason':'不符合忌口或饮食限制'});continue
        if DIFFICULTY.get(recipe['difficulty'],99)>config['max_difficulty']:continue
        factor=portions/recipe['_planning']['servings'];n=nutrition(recipe)
        demands={}
        for i in recipe['ingredients']:
            k=key(i['name'],i['unit']);demands[k]=demands.get(k,0)+round(i['amount']*factor*Q)
        missing_prices=[k for k in demands if k not in price_map]
        if budget is not None and missing_prices:exclusions.append({'recipe_id':recipe['id'],'reason':'预算硬约束缺价格','missing':missing_prices});continue
        if nutrient_bounds and n['values'] is None:exclusions.append({'recipe_id':recipe['id'],'reason':'营养硬约束缺数据','missing':n['missing']});continue
        rows[recipe['id']]={'recipe':recipe,'score':score,'demands':demands,'nutrition':{k:v*factor for k,v in n['values'].items()} if n['values'] else None,'nutrition_evidence':n,'missing_prices':missing_prices,'factor':factor}
    model=cp_model.CpModel();variables={};meal_variables={};missing=[]
    locks={ (r['day'],r['meal'],r['role']):r['recipe_id'] for r in config['locks']}
    for slot in meals:
        sid=f"{slot['day']}:{slot['meal']}";meal_variables[sid]=[]
        for role in ['main','staple']+(['side'] if slot['side'] else []):
            choices=[];locked=locks.get((slot['day'],slot['meal'],role))
            for id,row in rows.items():
                metadata=row['recipe']['_planning']
                if role not in metadata['roles'] or slot['meal'] not in metadata['meals'] or (locked and locked!=id):continue
                v=model.new_bool_var(sid+':'+role+':'+id);variables[(sid,role,id)]=v;choices.append(v);meal_variables[sid].append((id,v))
            if not choices:
                possible={r['id'] for r in recipes if role in r['_planning']['roles'] and slot['meal'] in r['_planning']['meals'] and (not locked or r['id']==locked)}
                lacks_data=bool(possible & {e['recipe_id'] for e in exclusions if e.get('missing')})
                missing.append({'slot':sid,'role':role,'locked':locked,'data_missing':lacks_data,'reason':'缺少可验证数据，请补充数据或主动调整约束' if lacks_data else '当前角色、锁定菜、忌口或难度约束冲突，请主动调整'})
            else:model.add(sum(choices)==1)
        model.add(sum(rows[id]['recipe']['cooking_time']*v for id,v in meal_variables[sid])<=config['max_minutes'])
        for id in rows:model.add(sum(v for rid,v in meal_variables[sid] if rid==id)<=1)
    if missing:return {'solver_status':'NEEDS_DATA' if all(m['data_missing'] for m in missing) else 'INFEASIBLE','issues':missing,'exclusions':exclusions,'meals':[]}
    for id,row in rows.items():
        if 'staple' not in row['recipe']['_planning']['roles']:model.add(sum(v for (sid,role,rid),v in variables.items() if rid==id)<=config['max_repeat'])
    all_keys=sorted({k for row in rows.values() for k in row['demands']})
    total_demand={k:sum(row['demands'].get(k,0)*v for (sid,role,id),v in variables.items() for row in [rows[id]]) for k in all_keys}
    price_integer={k:round(value*P) for k,value in price_map.items()}
    if budget is not None:model.add(sum(total_demand[k]*price_integer[k] for k in all_keys)<=math.floor(budget*Q*P))
    for nutrient,bounds in nutrient_bounds.items():
        # Ceil lower-bound coefficients/floor upper-bound coefficients would allow violations.
        lower=sum(math.floor(rows[id]['nutrition'][nutrient]*N)*v for (sid,role,id),v in variables.items())
        upper=sum(math.ceil(rows[id]['nutrition'][nutrient]*N)*v for (sid,role,id),v in variables.items())
        if bounds.get('min') is not None:model.add(lower>=math.ceil(bounds['min']*N))
        if bounds.get('max') is not None:model.add(upper<=math.floor(bounds['max']*N))
    batches=[];allocations=[];allocation_by_key={k:[] for k in all_keys};urgent_terms=[];use_terms=[]
    for b in inventory:
        quantity=b.get('quantity');unit=b.get('unit') or '库存计数';k=key(b['name'],unit)
        if k not in all_keys or not isinstance(quantity,(float,int)) or isinstance(quantity,bool) or quantity<=0:continue
        capacity=math.floor(quantity*Q);batch_vars=[]
        if capacity<=0:continue
        for slot in meals:
            sid=f"{slot['day']}:{slot['meal']}";hour={'breakfast':8,'lunch':12,'dinner':18}[slot['meal']]
            date=datetime.fromisoformat(config['start_date']).replace(tzinfo=timezone(timedelta(hours=config['utc_offset_hours'])))+timedelta(days=slot['day'],hours=hour)
            assessment=calculate_freshfusion({k:v for k,v in b.items() if k!='visual_freshness'},reference_time=max(current,date))
            if assessment['freshness_level'] in ('unknown','high_risk','expired'):continue
            v=model.new_int_var(0,capacity,'use:'+str(b['id'])+':'+sid);allocations.append((b,sid,k,v));allocation_by_key[k].append(v);batch_vars.append(v)
            normalized=max(1,round(100000/capacity));use_terms.append(v*normalized)
            if assessment.get('expiring_soon'):urgent_terms.append(v*normalized)
        if batch_vars:model.add(sum(batch_vars)<=capacity);batches.append(b)
    for slot in meals:
        sid=f"{slot['day']}:{slot['meal']}"
        for k in all_keys:
            use=sum(v for b,ms,bk,v in allocations if ms==sid and bk==k)
            demand=sum(rows[id]['demands'].get(k,0)*v for id,v in meal_variables[sid]);model.add(use<=demand)
    objectives=[]
    if config['inventory_priority'] and urgent_terms:objectives.append(('临期库存利用',sum(urgent_terms),'max'))
    objectives.append(('现有单餐推荐分',sum(round(rows[id]['score']['total_score']*100)*v for (sid,role,id),v in variables.items()),'max'))
    cost_known=all(k in price_integer for k in all_keys)
    if cost_known:objectives.append(('补购估算成本',sum((total_demand[k]-sum(allocation_by_key[k]))*price_integer[k] for k in all_keys),'min'))
    if use_terms:objectives.append(('同单位可用库存利用',sum(use_terms),'max'))
    stages=[];snapshot=None;best_status='UNKNOWN';solver=cp_model.CpSolver();solver.parameters.num_search_workers=1;solver.parameters.random_seed=config['seed']
    for name,objective,direction in objectives:
        remaining=config['timeout_seconds']-(time.monotonic()-started)
        if remaining<=0:break
        solver.parameters.max_time_in_seconds=remaining
        model.maximize(objective) if direction=='max' else model.minimize(objective)
        status=solver.solve(model);label=solver.status_name(status);stages.append({'name':name,'status':label,'objective':solver.objective_value if status in (cp_model.OPTIMAL,cp_model.FEASIBLE) else None})
        if status not in (cp_model.OPTIMAL,cp_model.FEASIBLE):
            if snapshot is None:return {'solver_status':'INFEASIBLE' if status==cp_model.INFEASIBLE else 'TIMEOUT' if status==cp_model.UNKNOWN else 'MODEL_INVALID','issues':['约束无可行组合' if status==cp_model.INFEASIBLE else '求解未得到可行方案，约束未自动放宽'],'meals':[],'stages':stages,'exclusions':exclusions}
            break
        snapshot=({index:solver.value(v) for index,v in variables.items()},[(b,sid,k,solver.value(v)) for b,sid,k,v in allocations]);best_status='OPTIMAL' if status==cp_model.OPTIMAL else 'FEASIBLE'
        if status!=cp_model.OPTIMAL:break
        model.add(objective==round(solver.objective_value))
    if snapshot is None:return {'solver_status':'TIMEOUT','issues':['求解时间已耗尽，尚无可行菜单'],'meals':[],'exclusions':exclusions,'stages':stages}
    selected,usage=snapshot;output=[];total={};totals={k:0. for k in NUTRIENTS};all_nutrition=True
    for slot in meals:
        sid=f"{slot['day']}:{slot['meal']}";dishes=[]
        for (ms,role,id),value in selected.items():
            if ms!=sid or not value:continue
            row=rows[id];dishes.append({'role':role,'recipe_id':id,'name':row['recipe']['name'],'recipe':row['recipe'],'factor':row['factor'],'single_meal_score':row['score'],'nutrition':row['nutrition'],'nutrition_evidence':row['nutrition_evidence']})
            for k,quantity in row['demands'].items():total[k]=total.get(k,0)+quantity
            if row['nutrition']:
                for k,v in row['nutrition'].items():totals[k]+=v
            else:all_nutrition=False
        output.append({**slot,'dishes':dishes,'minutes':sum(d['recipe']['cooking_time'] for d in dishes)})
    used={};alloc=[]
    for b,sid,k,amount in usage:
        if amount:used[k]=used.get(k,0)+amount;alloc.append({'item_id':b['id'],'expected_version':b.get('version'),'expected_quantity':b['quantity'],'slot':sid,'key':k,'quantity':amount/Q})
    requirements=[{'name':k.split('|')[0],'unit':k.split('|')[1],'quantity':max(0,(qty-used.get(k,0))/Q)} for k,qty in total.items() if qty-used.get(k,0)>0]
    missing_price_keys=[k for k in total if k not in price_map]
    cost=None if missing_price_keys else sum(qty/Q*price_map[k] for k,qty in total.items())
    buy=None if missing_price_keys else sum((qty-used.get(k,0))/Q*price_map[k] for k,qty in total.items())
    result={'solver_status':best_status if len(stages)==len(objectives) and all(s['status']=='OPTIMAL' for s in stages) else 'FEASIBLE','meals':output,'shopping_requirements':requirements,'inventory_allocations':alloc,'total_estimated_cost':round(cost,4) if cost is not None else None,'shopping_estimated_cost':round(buy,4) if buy is not None else None,'nutrition':totals if all_nutrition else None,'missing_prices':missing_price_keys,'stages':stages,'exclusions':exclusions,'elapsed_seconds':round(time.monotonic()-started,4),'note':'营养与预算仅对应勾选餐次及所填人数/份量；预算为全部食材估算成本，补购另扣同单位可用库存。未自动换算库存计数、个、根与克。营养为经确认的原料估算，不含品牌差异及熟制损耗。'}
    audit(config,result,inventory)
    return result

def audit(config,result,inventory):
    """Recalculate hard constraints from returned dishes, independent of CP variable values."""
    from collections import Counter
    counts=Counter();lock={(r['day'],r['meal'],r['role']):r['recipe_id'] for r in config['locks']}
    assert len(result['meals'])==len(config['meals'])
    for meal in result['meals']:
        assert {d['role'] for d in meal['dishes']}==({'main','staple','side'} if meal['side'] else {'main','staple'})
        assert meal['minutes']<=config['max_minutes']
        for d in meal['dishes']:
            assert DIFFICULTY[d['recipe']['difficulty']]<=config['max_difficulty']
            assert not lock.get((meal['day'],meal['meal'],d['role'])) or lock[(meal['day'],meal['meal'],d['role'])]==d['recipe_id']
            if d['role']!='staple':counts[d['recipe_id']]+=1
    assert all(v<=config['max_repeat'] for v in counts.values())
    if config.get('budget') is not None:assert result['total_estimated_cost'] is not None and result['total_estimated_cost']<=config['budget']+0.0001
    for k,b in config['nutrition'].items():
        value=result['nutrition'][k]
        assert b.get('min') is None or value>=b['min']-0.000001
        assert b.get('max') is None or value<=b['max']+0.000001
    quantities={str(b['id']):b['quantity'] for b in inventory};used=Counter()
    for allocation in result['inventory_allocations']:used[str(allocation['item_id'])]+=allocation['quantity']
    assert all(v<=quantities[k]+0.000001 for k,v in used.items())
