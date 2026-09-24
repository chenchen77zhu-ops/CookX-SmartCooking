"""Reproducible subset extraction. Download source archive separately; never scrape estimates."""
import csv,io,json,zipfile,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
archive=ROOT/'tmp/nutrition-source/FoodData_Central_sr_legacy_food_csv_2018-04.zip'
z=zipfile.ZipFile(archive)
def rows(name):return list(csv.DictReader(io.StringIO(z.read(next(n for n in z.namelist() if n.endswith('/'+name+'.csv'))).decode('utf8'))))
foods={r['fdc_id']:r for r in rows('food')};nutrients=rows('food_nutrient');portions=rows('food_portion')
# Nutrition examples, not a promise that a user's physical ingredient has this composition.
ids={'番茄':'170457','鸡蛋':'171287','土豆':'170026','干辣椒':'168570','醋':'172237','食用油':'171411','盐':'173468','猪肉':'167818','青椒':'170427','生抽':'174277','牛腩':'168607','胡萝卜':'170393','鸡胸肉':'171077','胡椒粉':'170931','豆腐':'172476','西兰花':'170379','大蒜':'169230','洋葱':'170000','白菜':'169979','茄子':'169228','黄瓜':'168409','香菇':'169242','青菜':'170390','冬瓜':'170069','排骨':'167853','鸡翅':'172390','芹菜':'169988','圆白菜':'169975','粉丝':'174258','韭菜':'169994','白萝卜':'168451','生姜':'169231','牛肉':'168607','白糖':'169655','大米':'169756','燕麦':'169705','面条':'169731','牛奶':'171265'}
portion_map={'番茄':('个','medium whole (2-2/5" dia)'),'鸡蛋':('个','large'),'土豆':('个','Potato medium (2-1/4" to 3-1/4" dia)'),'干辣椒':('个','pepper'),'青椒':('个','medium (approx 2-3/4" long, 2-1/2" dia)'),'胡萝卜':('根','medium'),'大蒜':('瓣','clove'),'洋葱':('个','medium (2-1/2" dia)'),'茄子':('根','eggplant, unpeeled (approx 1-1/4 lb)'),'黄瓜':('根','cucumber (8-1/4")'),'香菇':('个','piece whole'),'鸡翅':('个','piece')}
# Match the official description exactly (tomato string verified below).
portion_map['番茄']=('个','medium whole (2-3/5" dia)')
result={}
for name,id in ids.items():
 data={n['nutrient_id']:float(n['amount']) for n in nutrients if n['fdc_id']==id and n['amount']!=''}
 item={'fdc_id':int(id),'description':foods[id]['description'],'source_url':f'https://fdc.nal.usda.gov/food-details/{id}/nutrients','per_100g':{k:data.get(v) for k,v in {'kcal':'1008','protein_g':'1003','fat_g':'1004','carb_g':'1005'}.items()},'grams_per_unit':{'克':1},'portion_evidence':[]}
 if name in portion_map:
  unit,modifier=portion_map[name];r=next(r for r in portions if r['fdc_id']==id and r['modifier']==modifier)
  item['grams_per_unit'][unit]=float(r['gram_weight'])/float(r['amount']);item['portion_evidence'].append({'unit':unit,**r})
 if name in ('醋','食用油','生抽'):
  r=next(r for r in portions if r['fdc_id']==id and r['modifier']=='tbsp');item['grams_per_unit']['毫升']=float(r['gram_weight'])/15
  item['portion_evidence'].append({'unit':'毫升','assumption':'规划估算采用 1 tbsp = 15 mL；不用于库存自动换算',**r})
 result[name]=item
payload={'schema_version':1,'source':'USDA FoodData Central SR Legacy April 2018','archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'archive_url':'https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip','retrieved_at':'2026-09-24','note':'食材对应通用样品，熟制损耗与品牌差异未校正；份量换算仅是用户确认后的营养估算，不改变库存单位。豆瓣酱与泡发木耳未找到可直接对应记录，保持缺失。','foods':result}
(ROOT/'app/data/planning/nutrition.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf8')
recipes=json.loads((ROOT/'app/data/recipes/recipes.json').read_text(encoding='utf8'))
metadata={r['id']:{'servings':2,'roles':['main','side'],'meals':['lunch','dinner'],'version':'planning-v1','servings_basis':'工程规划模板：原菜谱按 2 人份估算，需用户确认；并非来源原有实测份量'} for r in recipes}
(ROOT/'app/data/planning/metadata.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding='utf8')
print('Extracted',len(result),'food records and',len(metadata),'recipe metadata entries')
