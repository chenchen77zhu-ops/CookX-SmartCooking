package com.smartcooking.app.data

import androidx.annotation.DrawableRes
import com.smartcooking.app.R

enum class FoodCategory(val id: String, val label: String) {
    ALL("all", "全部"),
    VEGETABLE("vegetable", "蔬菜"),
    MEAT("meat", "肉类"),
    FRUIT("fruit", "水果"),
    DAIRY("dairy", "蛋奶"),
    STAPLE("staple", "主食"),
    CONDIMENT("condiment", "调料"),
    OTHER("other", "其他");

    companion object {
        fun of(id: String): FoodCategory = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

data class FoodInfo(val key: String, val cn: String, val category: FoodCategory, @DrawableRes val image: Int?)

/** Display names, categories and bundled photos for common ingredients (English or Chinese input). */
object FoodCatalog {
    private val images = mapOf(
        "beef" to R.drawable.ing_beef, "tomato" to R.drawable.ing_tomato, "potato" to R.drawable.ing_potato,
        "carrot" to R.drawable.ing_carrot, "onion" to R.drawable.ing_onion, "egg" to R.drawable.ing_egg,
        "chicken" to R.drawable.ing_chicken, "chili" to R.drawable.ing_chili, "green_pepper" to R.drawable.ing_green_chili,
        "green_chili" to R.drawable.ing_green_chili, "garlic" to R.drawable.ing_garlic, "kimchi" to R.drawable.ing_kimchi,
        "leek" to R.drawable.ing_leek, "chive" to R.drawable.ing_leek, "lettuce" to R.drawable.ing_lettuce,
        "broccoli" to R.drawable.ing_broccoli, "rice" to R.drawable.ing_rice, "milk" to R.drawable.ing_milk,
        "cabbage" to R.drawable.ing_cabbage, "spinach" to R.drawable.ing_spinach, "tofu" to R.drawable.ing_tofu,
        "cilantro" to R.drawable.ing_cilantro, "ginger" to R.drawable.ing_ginger, "doubanjiang" to R.drawable.ing_doubanjiang,
    )

    private fun c(cn: String, category: FoodCategory) = cn to category
    private val config: Map<String, Pair<String, FoodCategory>> = mapOf(
        "beef" to c("牛肉", FoodCategory.MEAT), "pork" to c("猪肉", FoodCategory.MEAT), "chicken" to c("鸡肉", FoodCategory.MEAT),
        "duck" to c("鸭肉", FoodCategory.MEAT), "mutton" to c("羊肉", FoodCategory.MEAT), "ribs" to c("排骨", FoodCategory.MEAT),
        "ham" to c("火腿", FoodCategory.MEAT), "sausage" to c("香肠", FoodCategory.MEAT), "beef_tendon" to c("牛筋", FoodCategory.MEAT),
        "pig_liver" to c("猪肝", FoodCategory.MEAT),
        "cabbage" to c("白菜", FoodCategory.VEGETABLE), "cilantro" to c("香菜", FoodCategory.VEGETABLE), "carrot" to c("胡萝卜", FoodCategory.VEGETABLE),
        "chili" to c("辣椒", FoodCategory.VEGETABLE), "garlic" to c("大蒜", FoodCategory.VEGETABLE), "leek" to c("韭菜", FoodCategory.VEGETABLE),
        "onion" to c("洋葱", FoodCategory.VEGETABLE), "potato" to c("土豆", FoodCategory.VEGETABLE), "tomato" to c("西红柿", FoodCategory.VEGETABLE),
        "cucumber" to c("黄瓜", FoodCategory.VEGETABLE), "spinach" to c("菠菜", FoodCategory.VEGETABLE), "lettuce" to c("生菜", FoodCategory.VEGETABLE),
        "eggplant" to c("茄子", FoodCategory.VEGETABLE), "pumpkin" to c("南瓜", FoodCategory.VEGETABLE), "mushroom" to c("蘑菇", FoodCategory.VEGETABLE),
        "ginger" to c("生姜", FoodCategory.VEGETABLE), "green_chili" to c("青辣椒", FoodCategory.VEGETABLE), "broccoli" to c("西兰花", FoodCategory.VEGETABLE),
        "green_pepper" to c("青椒", FoodCategory.VEGETABLE), "chive" to c("韭菜", FoodCategory.VEGETABLE), "cauliflower" to c("花菜", FoodCategory.VEGETABLE),
        "celery" to c("芹菜", FoodCategory.VEGETABLE), "radish" to c("白萝卜", FoodCategory.VEGETABLE), "pea" to c("豌豆", FoodCategory.VEGETABLE),
        "corn" to c("玉米", FoodCategory.VEGETABLE), "asparagus" to c("芦笋", FoodCategory.VEGETABLE),
        "apple" to c("苹果", FoodCategory.FRUIT), "banana" to c("香蕉", FoodCategory.FRUIT), "orange" to c("橙子", FoodCategory.FRUIT),
        "grape" to c("葡萄", FoodCategory.FRUIT), "watermelon" to c("西瓜", FoodCategory.FRUIT), "strawberry" to c("草莓", FoodCategory.FRUIT),
        "mango" to c("芒果", FoodCategory.FRUIT), "pineapple" to c("菠萝", FoodCategory.FRUIT), "peach" to c("桃子", FoodCategory.FRUIT),
        "pear" to c("梨子", FoodCategory.FRUIT), "lemon" to c("柠檬", FoodCategory.FRUIT), "cherry" to c("樱桃", FoodCategory.FRUIT),
        "kiwi" to c("猕猴桃", FoodCategory.FRUIT), "blueberry" to c("蓝莓", FoodCategory.FRUIT), "coconut" to c("椰子", FoodCategory.FRUIT),
        "durian" to c("榴莲", FoodCategory.FRUIT), "avocado" to c("牛油果", FoodCategory.FRUIT),
        "egg" to c("鸡蛋", FoodCategory.DAIRY), "milk" to c("牛奶", FoodCategory.DAIRY), "yogurt" to c("酸奶", FoodCategory.DAIRY),
        "cheese" to c("奶酪", FoodCategory.DAIRY), "tofu" to c("豆腐", FoodCategory.OTHER), "rice" to c("大米", FoodCategory.STAPLE),
        "noodle" to c("面条", FoodCategory.STAPLE), "flour" to c("面粉", FoodCategory.STAPLE), "mantou" to c("馒头", FoodCategory.STAPLE),
        "salt" to c("盐", FoodCategory.CONDIMENT), "sugar" to c("糖", FoodCategory.CONDIMENT), "soy_sauce" to c("酱油", FoodCategory.CONDIMENT),
        "vinegar" to c("醋", FoodCategory.CONDIMENT), "cooking_wine" to c("料酒", FoodCategory.CONDIMENT), "cooking_oil" to c("食用油", FoodCategory.CONDIMENT),
        "chili_sauce" to c("辣椒酱", FoodCategory.CONDIMENT), "kimchi" to c("泡菜", FoodCategory.OTHER), "doubanjiang" to c("豆瓣酱", FoodCategory.CONDIMENT),
    )

    private val cnToEn: Map<String, String> = mapOf(
        "牛肉" to "beef", "牛排" to "beef", "猪肉" to "pork", "鸡肉" to "chicken", "鸡胸肉" to "chicken", "鸭肉" to "duck",
        "羊肉" to "mutton", "排骨" to "ribs", "火腿" to "ham", "香肠" to "sausage", "牛筋" to "beef_tendon", "猪肝" to "pig_liver",
        "白菜" to "cabbage", "大白菜" to "cabbage", "香菜" to "cilantro", "胡萝卜" to "carrot", "辣椒" to "chili", "红辣椒" to "chili",
        "青辣椒" to "green_chili", "青椒" to "green_chili", "大蒜" to "garlic", "蒜" to "garlic", "蒜头" to "garlic", "韭菜" to "leek",
        "大葱" to "leek", "洋葱" to "onion", "红洋葱" to "onion", "紫洋葱" to "onion", "土豆" to "potato", "马铃薯" to "potato",
        "番茄" to "tomato", "西红柿" to "tomato", "小番茄" to "tomato", "圣女果" to "tomato", "黄瓜" to "cucumber", "菠菜" to "spinach",
        "生菜" to "lettuce", "茄子" to "eggplant", "南瓜" to "pumpkin", "蘑菇" to "mushroom", "生姜" to "ginger", "姜" to "ginger",
        "西兰花" to "broccoli", "花菜" to "cauliflower", "芹菜" to "celery", "白萝卜" to "radish", "豌豆" to "pea", "玉米" to "corn",
        "芦笋" to "asparagus", "苹果" to "apple", "香蕉" to "banana", "橙子" to "orange", "葡萄" to "grape", "西瓜" to "watermelon",
        "草莓" to "strawberry", "芒果" to "mango", "菠萝" to "pineapple", "桃子" to "peach", "梨子" to "pear", "柠檬" to "lemon",
        "樱桃" to "cherry", "猕猴桃" to "kiwi", "蓝莓" to "blueberry", "椰子" to "coconut", "榴莲" to "durian", "牛油果" to "avocado",
        "鸡蛋" to "egg", "蛋" to "egg", "鸭蛋" to "egg", "牛奶" to "milk", "纯牛奶" to "milk", "酸奶" to "yogurt", "奶酪" to "cheese",
        "豆腐" to "tofu", "大米" to "rice", "米" to "rice", "面条" to "noodle", "挂面" to "noodle", "面粉" to "flour", "馒头" to "mantou",
        "盐" to "salt", "食盐" to "salt", "糖" to "sugar", "白糖" to "sugar", "酱油" to "soy_sauce", "醋" to "vinegar", "料酒" to "cooking_wine",
        "食用油" to "cooking_oil", "辣椒酱" to "chili_sauce", "泡菜" to "kimchi", "韩国泡菜" to "kimchi", "辣白菜" to "kimchi",
        "韩式泡菜" to "kimchi", "豆瓣酱" to "doubanjiang",
    )

    private val categoryAliases = mapOf(
        "vegetable" to FoodCategory.VEGETABLE, "vegetables" to FoodCategory.VEGETABLE, "蔬菜" to FoodCategory.VEGETABLE,
        "meat" to FoodCategory.MEAT, "meats" to FoodCategory.MEAT, "肉类" to FoodCategory.MEAT, "肉" to FoodCategory.MEAT,
        "fruit" to FoodCategory.FRUIT, "fruits" to FoodCategory.FRUIT, "水果" to FoodCategory.FRUIT,
        "dairy" to FoodCategory.DAIRY, "egg" to FoodCategory.DAIRY, "蛋奶" to FoodCategory.DAIRY, "蛋类" to FoodCategory.DAIRY, "奶类" to FoodCategory.DAIRY,
        "staple" to FoodCategory.STAPLE, "grain" to FoodCategory.STAPLE, "主食" to FoodCategory.STAPLE, "谷物" to FoodCategory.STAPLE,
        "condiment" to FoodCategory.CONDIMENT, "seasoning" to FoodCategory.CONDIMENT, "调料" to FoodCategory.CONDIMENT, "调味品" to FoodCategory.CONDIMENT,
        "other" to FoodCategory.OTHER, "其他" to FoodCategory.OTHER,
    )

    fun key(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (Regex("^[a-zA-Z_]+$").matches(trimmed)) return trimmed.lowercase()
        return cnToEn[trimmed] ?: trimmed.lowercase()
    }

    fun info(name: String?): FoodInfo {
        if (name.isNullOrBlank()) return FoodInfo("", "未知", FoodCategory.OTHER, null)
        val k = key(name)
        val found = config[k]
        return FoodInfo(k, found?.first ?: name.trim(), found?.second ?: FoodCategory.OTHER, images[k])
    }

    fun category(name: String?, backendCategory: String?): FoodCategory =
        categoryAliases[backendCategory?.trim()?.lowercase().orEmpty()] ?: info(name).category
}
