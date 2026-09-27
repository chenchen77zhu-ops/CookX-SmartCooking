package com.smartcooking.app.data

import com.smartcooking.app.core.toFiniteOrNull
import com.smartcooking.app.core.Time
import com.smartcooking.app.core.asDouble
import com.smartcooking.app.core.asText
import com.smartcooking.app.core.clean
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.num
import com.smartcooking.app.core.str
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A personal inventory batch as returned by `/api/inventory`; the raw object is kept for round trips. */
data class InventoryItem(val raw: JsonObject) {
    val id: String get() = raw.str("id").orEmpty()
    val name: String get() = raw.str("name").orEmpty()
    val quantity: Double get() = raw.num("quantity") ?: 0.0
    val unit: String? get() = raw.str("unit") ?: raw.str("quantity_unit")
    val storageType: String? get() = raw.str("storage_type") ?: raw.str("storage_method") ?: raw.str("storage")
    val shelfLife: Double? get() = raw.num("shelf_life")
    val addTime: String? get() = raw.str("add_time")
    val purchaseTime: String? get() = raw.str("purchase_time") ?: raw.str("purchase_date")
    val expiryDate: String? get() = raw.str("expiry_date")
    val revision: String? get() = raw.str("_revision")
    val imageUrl: String? get() = raw.str("image_url") ?: raw.str("image") ?: raw.str("thumbnail")
    val info: FoodInfo get() = FoodCatalog.info(name)
    val category: FoodCategory get() = FoodCatalog.category(name, raw.str("category"))

    /** Quantity text: legacy inventory counts are not grams, so the unit is always shown explicitly. */
    val measureText: String
        get() {
            val q = quantity.clean()
            val unitText = unit ?: "库存计数"
            val base = "$q $unitText"
            fun measure(v: Any?, suffix: String): String {
                val text = (v as? kotlinx.serialization.json.JsonElement).asText()
                if (text != null && Regex("[a-zA-Z\\u4e00-\\u9fa5]").containsMatchIn(text)) return text
                val n = (v as? kotlinx.serialization.json.JsonElement).asDouble() ?: return ""
                return if (n < 0) "" else "${n.clean(1)}$suffix"
            }
            raw["weight_g"]?.takeIf { it !is JsonNull }?.let { return "$base · ${measure(it, "g")}" }
            raw["grams"]?.takeIf { it !is JsonNull }?.let { return "$base · ${measure(it, "g")}" }
            raw["volume_ml"]?.takeIf { it !is JsonNull }?.let { return "$base · ${measure(it, "ml")}" }
            raw["ml"]?.takeIf { it !is JsonNull }?.let { return "$base · ${measure(it, "ml")}" }
            return base
        }
}

/** Editable form values; dates are local "yyyy-MM-ddTHH:mm" strings, blank means unknown. */
@kotlinx.serialization.Serializable
data class InventoryForm(
    val name: String = "",
    val quantity: String = "1",
    val storageType: String = "",
    val shelfLife: String = "",
    val purchaseTime: String = "",
    val addTime: String = "",
    val expiryDate: String = "",
) {
    companion object {
        fun from(item: InventoryItem, displayName: String = item.info.cn) = InventoryForm(
            name = displayName,
            quantity = item.quantity.clean(),
            storageType = item.storageType.orEmpty(),
            shelfLife = item.shelfLife?.clean(3).orEmpty(),
            purchaseTime = Time.localInput(item.purchaseTime),
            addTime = Time.localInput(item.addTime),
            expiryDate = Time.localInput(item.expiryDate),
        )

        /** Recognition candidates arrive with backend date fields that need local editing values. */
        fun fromRecognition(raw: JsonObject) = InventoryForm(
            name = raw.str("name").orEmpty(),
            quantity = raw.num("quantity")?.clean() ?: "1",
            storageType = raw.str("storage_type") ?: raw.str("storage_method").orEmpty(),
            shelfLife = raw.num("shelf_life")?.clean(3).orEmpty(),
            purchaseTime = Time.localInput(raw.str("purchase_time") ?: raw.str("purchase_date")),
            addTime = "",
            expiryDate = Time.localInput(raw.str("expiry_date")),
        )
    }
}

object InventoryRules {
    val storageOptions = listOf("常温", "冷藏", "冷冻", "阴凉干燥")

    private val aliases = mapOf(
        "tomato" to "西红柿", "番茄" to "西红柿", "西红柿" to "西红柿", "小番茄" to "西红柿", "圣女果" to "西红柿",
        "beef" to "牛肉", "牛肉" to "牛肉", "milk" to "牛奶", "牛奶" to "牛奶", "tofu" to "豆腐", "豆腐" to "豆腐",
        "potato" to "土豆", "土豆" to "土豆", "carrot" to "胡萝卜", "胡萝卜" to "胡萝卜", "chicken" to "鸡肉", "鸡肉" to "鸡肉",
        "egg" to "鸡蛋", "鸡蛋" to "鸡蛋", "onion" to "洋葱", "洋葱" to "洋葱", "garlic" to "大蒜", "大蒜" to "大蒜",
        "ginger" to "生姜", "姜" to "生姜", "生姜" to "生姜", "broccoli" to "西兰花", "西兰花" to "西兰花",
        "kimchi" to "泡菜", "韩式泡菜" to "泡菜", "泡菜" to "泡菜", "chili" to "红辣椒", "红辣椒" to "红辣椒", "青辣椒" to "青辣椒",
    )

    /** Mirrors the backend name aliases for read-back verification only. */
    fun canonicalName(name: String?): String {
        val v = name?.trim()?.lowercase().orEmpty()
        return aliases[v] ?: v
    }

    /**
     * Builds the request payload. For edits only changed fields are sent and blank means "clear".
     * Throws [IllegalArgumentException] with a user-facing message on invalid input.
     */
    fun serialize(form: InventoryForm, original: InventoryItem? = null, now: Long = System.currentTimeMillis()): JsonObject {
        val name = form.name.trim()
        if (name.isEmpty()) throw IllegalArgumentException("请填写名称")
        val quantity = form.quantity.trim().toFiniteOrNull()
        if (quantity == null || quantity <= 0 || quantity != Math.floor(quantity) || quantity > 9_007_199_254_740_991.0) {
            throw IllegalArgumentException("数量必须是正整数")
        }
        val result = linkedMapOf<String, Any?>("name" to name, "quantity" to quantity.toLong())
        if (form.storageType.isNotBlank()) result["storage_type"] = form.storageType
        if (form.shelfLife.isNotBlank()) {
            val days = form.shelfLife.trim().toFiniteOrNull()
            if (days == null || !days.isFinite() || days <= 0) throw IllegalArgumentException("保质期必须大于 0")
            result["shelf_life"] = days
        }
        if (original != null) {
            if (form.storageType.isBlank() && original.storageType != null) result["storage_type"] = null
            if (form.shelfLife.isBlank() && original.shelfLife != null) result["shelf_life"] = null
            val originalDates = mapOf("purchase_time" to original.purchaseTime, "add_time" to original.addTime, "expiry_date" to original.expiryDate)
            val formDates = mapOf("purchase_time" to form.purchaseTime, "add_time" to form.addTime, "expiry_date" to form.expiryDate)
            for ((key, value) in formDates) {
                if (value == Time.localInput(originalDates[key])) continue
                result[key] = if (value.isBlank()) null else Time.isoFromLocal(value)
            }
            val purchase = Time.parseMillis((if ("purchase_time" in result) result["purchase_time"] else original.purchaseTime) as String?)
            val added = Time.parseMillis((if ("add_time" in result) result["add_time"] else original.addTime) as String?)
            val expiry = Time.parseMillis((if ("expiry_date" in result) result["expiry_date"] else original.expiryDate) as String?)
            if (result["purchase_time"] != null && purchase != null && purchase > now) throw IllegalArgumentException("购买时间不能晚于当前时间")
            if (expiry != null && ((purchase != null && expiry <= purchase) || (added != null && expiry <= added))) {
                throw IllegalArgumentException("到期时间必须晚于购买及入库时间")
            }
            val changed = result.filter { (key, value) ->
                when {
                    value == null -> when (key) {
                        "storage_type" -> original.storageType != null
                        "shelf_life" -> original.shelfLife != null
                        else -> originalDates[key] != null
                    }
                    key == "name" -> canonicalName(value as String) != canonicalName(original.name)
                    key == "quantity" -> original.quantity != (value as Long).toDouble()
                    key == "shelf_life" -> original.shelfLife != value
                    key == "storage_type" -> original.storageType != value
                    else -> originalDates[key] != value
                }
            }
            return jsonOf(*changed.map { it.key to it.value }.toTypedArray())
        }
        val purchase = Time.isoFromLocal(form.purchaseTime)
        val added = Time.isoFromLocal(form.addTime)
        val expiry = Time.isoFromLocal(form.expiryDate)
        if (purchase != null && Time.parseMillis(purchase)!! > now) throw IllegalArgumentException("购买时间不能晚于当前时间")
        if (expiry != null) {
            val e = Time.parseMillis(expiry)!!
            if ((purchase != null && e <= Time.parseMillis(purchase)!!) || e <= (added?.let { Time.parseMillis(it) } ?: now)) {
                throw IllegalArgumentException("到期时间必须晚于购买及入库时间；历史记录请填写入库时间")
            }
        }
        purchase?.let { result["purchase_time"] = it }
        added?.let { result["add_time"] = it }
        expiry?.let { result["expiry_date"] = it }
        return jsonOf(*result.map { it.key to it.value }.toTypedArray())
    }

    private fun sameField(key: String, actual: kotlinx.serialization.json.JsonElement?, expected: kotlinx.serialization.json.JsonElement?): Boolean {
        if (key == "name") return canonicalName(actual.asText()) == canonicalName(expected.asText())
        if (expected == null || expected is JsonNull) return actual == null || actual is JsonNull
        if (key in listOf("purchase_time", "expiry_date", "add_time")) return Time.parseMillis(actual.asText()) == Time.parseMillis(expected.asText())
        val a = actual.asDouble(); val e = expected.asDouble()
        if (a != null && e != null) return a == e
        return actual.asText() == expected.asText()
    }

    /** Confirms a write by re-reading inventory, like the web `verifyMutation`. */
    fun verifyEdit(id: String, payload: JsonObject, after: List<InventoryItem>): Boolean {
        val item = after.firstOrNull { it.id == id } ?: return false
        return payload.all { (key, value) -> sameField(key, item.raw[key], value) }
    }

    fun verifyAdd(payload: List<JsonObject>, before: List<InventoryItem>, after: List<InventoryItem>): Boolean {
        data class Group(val fields: Map<String, kotlinx.serialization.json.JsonElement>, var quantity: Double)
        val groups = linkedMapOf<String, Group>()
        for (item in payload) {
            val fields = item.toMutableMap().apply { remove("quantity"); put("name", JsonPrimitive(canonicalName(item.str("name")))) }
            val key = fields.toSortedMap().toString()
            groups.getOrPut(key) { Group(fields, 0.0) }.quantity += item.num("quantity") ?: 0.0
        }
        return groups.values.all { group ->
            fun count(rows: List<InventoryItem>) = rows.filter { row -> group.fields.all { (k, v) -> sameField(k, row.raw[k], v) } }.sumOf { it.quantity }
            count(after) - count(before) == group.quantity
        }
    }
}
