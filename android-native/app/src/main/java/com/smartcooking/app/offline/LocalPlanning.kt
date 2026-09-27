package com.smartcooking.app.offline
import com.smartcooking.app.core.*
import kotlinx.serialization.json.*

internal fun LocalEngine.planning(m:String,s:List<String>,b:JsonObject,u:String):JsonObject {
    val prices=rows("prices").firstOrNull{it.str("id")==u}?:jsonOf("id" to u,"version" to 0,"items" to emptyList<String>())
    when(s[1]) {
        "catalog" -> return jsonOf("recipes" to recipes,"prices" to prices)
        "prices" -> {version(prices,b);if(b.objects("items").any{(it.num("price")?:-1.0)<0})fail(422,"单价不能为负数");remove("prices",u);add("prices",updated(prices,b));return ok()}
        "preview" -> {
            b.str("family_id")?.let{member(it,u)}
            val slots=b.objects("meals");if(slots.isEmpty())fail(422,"请选择餐次")
            val issues=mutableListOf<String>();val scope=b.str("family_id")?:u;val unsafe=stock(scope).filter{fresh(it).bool("expired")||"storage_unsuitable" in fresh(it).strings("risk_flags")}.mapNotNull{it.str("name")};val avoided=b.strings("avoid_ingredients")+unsafe;val diet=b.strings("dietary_restrictions")
            val factor=(b.num("people")?:1.0)*(b.num("portion_factor")?:1.0)/2
            val uses=mutableMapOf<String,Int>();val meals=mutableListOf<JsonObject>();var total=0.0;var knownCost=true
            if(b.obj("nutrition")?.isNotEmpty()==true)issues.add("营养数据不足，无法核验所选营养硬约束")
            for(slot in slots) {
                val dishes=mutableListOf<JsonObject>()
                for(role in if(slot.bool("side"))listOf("main","staple","side")else listOf("main","staple")) {
                    val lock=b.objects("locks").firstOrNull{it.str("day")==slot.str("day")&&it.str("meal")==slot.str("meal")&&it.str("role")==role}?.str("recipe_id")
                    val candidates=recipes.filter { r -> val meta=r.obj("_planning");meta?.strings("roles")?.contains(role)==true&&meta.strings("meals").contains(slot.str("meal"))&&
                        (lock==null||r.str("id")==lock)&&r.objects("ingredients").none{it.str("name") in avoided}&&
                        (r.num("cooking_time")?:Double.MAX_VALUE)<=(b.num("max_minutes")?:120.0)&&
                        (if(r.str("difficulty")=="简单")1 else 2)<=(b.num("max_difficulty")?:2.0)&&
                        (uses[r.str("id")]?:0)<(b.num("max_repeat")?:3.0)&&
                        diet.all{d-> when(d){"vegetarian"->r.strings("tags").any{it in listOf("素食","纯素","主食")};"vegan"->r.strings("tags").contains("纯素");else->r.strings("tags").contains(d)} }
                    }.sortedBy{uses[it.str("id")]?:0}
                    val r=candidates.firstOrNull()
                    if(r==null){issues.add("第 ${slot.str("day")} 天 ${slot.str("meal")} 的 $role 未找到满足条件的菜谱");continue}
                    uses[r.str("id")!!]=(uses[r.str("id")]?:0)+1
                    r.objects("ingredients").forEach{i->val price=prices.objects("items").firstOrNull{it.str("name")==i.str("name")&&it.str("unit")==i.str("unit")}?.num("price");if(price==null)knownCost=false else total+=price*(i.num("amount")?:0.0)*factor}
                    dishes.add(jsonOf("role" to role,"recipe_id" to r.str("id"),"name" to r.str("name"),"recipe" to r,"factor" to factor))
                }
                meals.add(slot.with("dishes" to dishes,"minutes" to dishes.maxOfOrNull{it.obj("recipe")?.num("cooking_time")?:0.0}))
            }
            if(b.num("budget")!=null){if(!knownCost)issues.add("请补充全部食材的同单位单价后验证预算")else if(total>b.num("budget")!!)issues.add("当前组合超出预算，请调整或换菜")}
            val result=jsonOf("solver_status" to if(issues.isEmpty())"FEASIBLE" else "UNKNOWN","meals" to if(issues.isEmpty())meals else emptyList<JsonObject>(),"issues" to issues,"total_estimated_cost" to if(knownCost)total else null,"shopping_estimated_cost" to null,"note" to "根据可用菜谱组合餐次；未知单位不自动换算，未找到组合时可调整条件重试。")
            val preview=jsonOf("id" to id(),"owner" to u,"version" to 1,"config" to b.without("idempotency_key"),"result" to result,"inventory_version" to stockVersion(u),"price_version" to prices.num("version"))
            add("previews",preview);return ok("preview" to preview)
        }
        "menus" -> {
            if(m=="POST"){val p=find("previews",b.str("preview_id").orEmpty());own(p,u);version(p,b);if(p.obj("result")!!.objects("meals").isEmpty())fail(422,"此组合尚不能保存");val menu=p.with("id" to id(),"name" to b.str("name"),"created_at" to now());add("menus",menu);return ok("menu" to menu)}
            if(s.size==2)return items(rows("menus").filter{it.str("owner")==u})
            val menu=find("menus",s[2]);own(menu,u);menu.obj("config")?.str("family_id")?.let{member(it,u)}
            return jsonOf("menu" to menu,"inventory_changed" to (menu.num("inventory_version")!=stockVersion(u)),"price_changed" to (menu.num("price_version")!=prices.num("version")),"inventory" to stock(u).map{jsonOf("item" to it,"freshness" to fresh(it))},"revalidation_issues" to emptyList<String>(),"note" to "执行前请再次核对数量、单位和储存状态")
        }
    };fail(404,"操作不存在")
}
