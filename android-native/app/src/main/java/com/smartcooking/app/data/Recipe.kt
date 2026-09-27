package com.smartcooking.app.data

import com.smartcooking.app.core.AppJson
import com.smartcooking.app.core.asArray
import com.smartcooking.app.core.asDouble
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.asText
import com.smartcooking.app.core.num
import com.smartcooking.app.core.obj
import com.smartcooking.app.core.objects
import com.smartcooking.app.core.str
import com.smartcooking.app.core.strings
import com.smartcooking.app.core.with
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class RecipeStep(val raw: JsonObject, val index: Int) {
    val id: String get() = raw.str("id") ?: "step-${index + 1}"
    val text: String get() = raw.str("text") ?: raw.str("content").orEmpty()
    val title: String get() = raw.str("title")?.takeIf { it.isNotBlank() } ?: "步骤 ${index + 1}"
    val hasTitle: Boolean get() = !raw.str("title").isNullOrBlank()
    val durationSeconds: Double? get() = raw.num("time_estimate") ?: raw.num("duration")
    val heat: String? get() = raw.str("heat")
    val temperatureText: String? get() = raw["temperature"]?.let { if (it is JsonArray) it.joinToString("–") { v -> v.asText().orEmpty() } + " ℃" else it.asText() }
    val tip: String? get() = raw.str("tip") ?: raw.str("note")
}

data class Ingredient(val item: String, val amount: String?)

/** A validated recipe; the normalized JSON is kept so unknown fields (scope, copy id …) survive. */
data class Recipe(val json: JsonObject) {
    val dishName: String get() = json.str("dish_name").orEmpty()
    val steps: List<RecipeStep> get() = json.arr("steps").mapIndexedNotNull { i, e -> e.asObject()?.let { RecipeStep(it, i) } }
    val ingredients: List<Ingredient> get() = json.objects("ingredients_list").mapNotNull { o -> o.str("item")?.let { Ingredient(it, o.str("amount")) } }
    val usedIngredients: List<String> get() = json.strings("used_ingredients")
    val missing: List<String> get() = json.strings("missing")
    val method: String? get() = json.str("method")
    val tags: List<String> get() = json.strings("tags")
    val imageUrl: String? get() = json.str("image_url") ?: json.str("image") ?: json.str("thumbnail")
    val reminder: String? get() = json.str("tip") ?: json.str("note") ?: json.str("cooking_tip")
    val nutrition: JsonObject? get() = json.obj("nutrition")
    val familyScope: String? get() = json.obj("inventory_scope")?.str("family_id")
    val totalSeconds: Double get() = steps.sumOf { s -> s.durationSeconds?.takeIf { it > 0 } ?: 0.0 }

    val nutritionSummary: String
        get() {
            val n = nutrition ?: return ""
            return listOfNotNull(
                n.str("calories")?.let { "能量 $it" }, n.str("protein")?.let { "蛋白 $it" },
                n.str("fat")?.let { "脂肪 $it" }, n.str("carbs")?.let { "碳水 $it" },
            ).joinToString(" · ")
        }

    fun withStepText(index: Int, text: String): Recipe {
        val steps = json.arr("steps").mapIndexed { i, e -> if (i == index) (e.asObject() ?: JsonObject(emptyMap())).with("text" to text) else e }
        return Recipe(json.with("steps" to JsonArray(steps)))
    }

    companion object {
        /**
         * Port of `normalizeRecipe`: accepts an object or a JSON string (optionally fenced),
         * validates steps and durations, and throws a user-facing message on bad input.
         */
        fun normalize(input: JsonElement?): Recipe {
            var element = input
            if (element is JsonPrimitive && element.isString) {
                val text = element.content.trim().replace(Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$"), "$1")
                element = try { AppJson.parseToJsonElement(text) } catch (e: Exception) { throw IllegalArgumentException("菜谱 JSON 无法解析，请重新生成") }
            }
            val recipe = element.asObject() ?: throw IllegalArgumentException("菜谱结果为空或结构异常")
            val name = recipe.str("dish_name")?.trim()
            if (recipe["dish_name"] !is JsonPrimitive || name.isNullOrEmpty()) throw IllegalArgumentException("菜谱缺少菜名")
            val rawSteps = recipe["steps"].asArray()
            if (rawSteps == null || rawSteps.isEmpty()) throw IllegalArgumentException("菜谱没有可执行步骤")
            val steps = buildJsonArray {
                rawSteps.forEachIndexed { index, raw ->
                    val step = when {
                        raw is JsonPrimitive && raw.isString -> buildJsonObject { put("text", raw) }
                        raw is JsonObject -> raw
                        else -> throw IllegalArgumentException("第 ${index + 1} 步结构异常")
                    }
                    val text = (step["text"] ?: step["content"])
                    if (text !is JsonPrimitive || !text.isString || text.content.isBlank()) throw IllegalArgumentException("第 ${index + 1} 步内容为空")
                    val durationEl = step["time_estimate"] ?: step["duration"]
                    val duration: Double? = when {
                        durationEl == null || durationEl is JsonNull -> null
                        durationEl is JsonPrimitive && durationEl.isString && durationEl.content.isBlank() -> null
                        durationEl is JsonPrimitive && (durationEl.content == "true" || durationEl.content == "false") && !durationEl.isString ->
                            throw IllegalArgumentException("第 ${index + 1} 步时长无效（秒）")
                        else -> durationEl.asDouble()?.takeIf { it in 0.0..86400.0 } ?: throw IllegalArgumentException("第 ${index + 1} 步时长无效（秒）")
                    }
                    add(step.with("id" to "step-${index + 1}", "text" to text.content.trim(), "time_estimate" to duration))
                }
            }
            val normalized = recipe.with(
                "schemaVersion" to 1,
                "dish_name" to name,
                "steps" to steps,
                "ingredients_list" to JsonArray(recipe["ingredients_list"].asArray()?.filter { it is JsonObject && it.str("item") != null }.orEmpty()),
                "used_ingredients" to JsonArray(recipe["used_ingredients"].asArray()?.filter { it.asText()?.isNotBlank() == true && (it as JsonPrimitive).isString }.orEmpty()),
                "missing" to JsonArray(recipe["missing"].asArray()?.filter { (it as? JsonPrimitive)?.isString == true }.orEmpty()),
            )
            return Recipe(normalized)
        }

        fun tryNormalize(input: JsonElement?): Recipe? = runCatching { normalize(input) }.getOrNull()
    }
}

private fun JsonObject.arr(key: String): JsonArray = this[key].asArray() ?: JsonArray(emptyList())

/** One message in the AI kitchen conversation. */
data class ChatMessage(val role: String, val content: String, val recipe: Recipe? = null)

/** Port of `safeHistory`: invalid recipes become a note on the message rather than a crash. */
fun safeHistory(rows: JsonElement?): List<ChatMessage> = rows.asArray().orEmpty().mapNotNull { row ->
    val o = row.asObject() ?: return@mapNotNull null
    val role = o.str("role")
    if (role != "user" && role != "assistant") return@mapNotNull null
    var content = (o["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    var recipe: Recipe? = null
    val raw = o["recipe"]
    if (raw != null && raw !is JsonNull) {
        try { recipe = Recipe.normalize(raw) } catch (e: IllegalArgumentException) { content += "\n" + e.message }
    }
    ChatMessage(role, content, recipe)
}
