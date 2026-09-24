"""Planning metadata stays separate from the owner's recommendation recipe database."""
import json
from pathlib import Path
from app.services.recommendation_service import load_recipes,normalize_ingredient
ROOT=Path(__file__).resolve().parents[1]/'data'/'planning'
NUTRIENTS=('kcal','protein_g','fat_g','carb_g')
def ingredient(name,amount,unit='克'):return {'name':name,'amount':amount,'unit':unit,'required':True}
def template(id,name,ingredients,steps,minutes,role,method='煮'):
    return {'id':id,'name':name,'ingredients':ingredients,'optional_ingredients':[],'steps':steps,'cooking_time':minutes,'difficulty':'简单','method':method,'tags':['清淡','不辣'],'nutrition':None,'estimated_cost':None,'_planning':{'servings':1,'roles':[role],'meals':['breakfast','lunch','dinner'] if role=='staple' else ['breakfast'],'version':'planning-v1','servings_basis':'工程模板一人份，按所列原料称量估算；不是营养处方'}}
def catalog():
    metadata=json.loads((ROOT/'metadata.json').read_text(encoding='utf8'))
    rows=[{**r,'_planning':metadata[r['id']]} for r in load_recipes()]
    rows.extend([
      template('plan-rice','米饭',[ingredient('大米',75)],['称量大米并淘洗。','按米种和设备要求加水煮熟。'],30,'staple'),
      template('plan-noodles','清水面条',[ingredient('面条',75)],['称量面条。','按包装要求煮熟后沥水。'],15,'staple'),
      template('plan-oats','燕麦粥',[ingredient('燕麦',50)],['称量燕麦。','按产品要求加水煮熟，按需要调整稀稠。'],15,'staple'),
      template('plan-potato','蒸土豆',[ingredient('土豆',200)],['称量可食部分，洗净切块。','蒸至内部熟透，核查后食用。'],25,'staple','蒸'),
      template('plan-breakfast-egg','早餐煮鸡蛋',[ingredient('鸡蛋',1,'个')],['检查鸡蛋并清洗外壳。','加水煮至蛋白蛋黄凝固，核查后食用。'],12,'main'),
      template('plan-breakfast-tofu','早餐豆腐汤',[ingredient('豆腐',150),ingredient('盐',1)],['豆腐切块，加水煮开。','继续煮熟，少量调味。'],15,'main'),
      template('plan-breakfast-tomato','早餐番茄蛋汤',[ingredient('番茄',1,'个'),ingredient('鸡蛋',1,'个'),ingredient('盐',1)],['番茄切块煮软。','淋入蛋液并煮熟，少量调味。'],15,'main')
    ])
    for row in rows:
        if row['id'].startswith('plan-'):
            names={i['name'] for i in row['ingredients']}
            row['tags']+=['素食']
            if not names & {'鸡蛋','牛奶','面条'}:row['tags']+=['纯素']
    return rows

def nutrition(recipe):
    source=json.loads((ROOT/'nutrition.json').read_text(encoding='utf8'));foods=source['foods'];values={k:0. for k in NUTRIENTS};missing=[];evidence=[]
    for i in recipe['ingredients']:
        food=foods.get(i['name']);unit=i['unit'];grams=food and food['grams_per_unit'].get(unit)
        if grams is None or any(food['per_100g'][k] is None for k in NUTRIENTS):missing.append(i['name']+' / '+unit);continue
        for k in NUTRIENTS:values[k]+=float(i['amount'])*grams/100*food['per_100g'][k]
        evidence.append({'ingredient':i['name'],'amount':i['amount'],'unit':unit,'grams_per_unit':grams,'fdc_id':food['fdc_id'],'description':food['description'],'source_url':food['source_url'],'portion_evidence':food['portion_evidence']})
    return {'values':None if missing else {k:round(v,6) for k,v in values.items()},'missing':missing,'evidence':evidence,'source_version':source['source'],'source_sha256':source['archive_sha256'],'note':source['note']}

def key(name,unit):return normalize_ingredient(name)+'|'+unit
