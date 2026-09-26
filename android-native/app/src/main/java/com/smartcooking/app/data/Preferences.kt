package com.smartcooking.app.data

import com.smartcooking.app.core.BusinessApi
import com.smartcooking.app.core.EmptyObject
import com.smartcooking.app.core.long
import com.smartcooking.app.core.obj
import kotlinx.serialization.json.JsonObject

/** Account preferences (`/api/v3/preferences`): taste values plus saved recommendation criteria. */
data class AccountPreferences(val version: Long, val values: JsonObject, val recommendation: JsonObject)

class PreferencesRepository(private val business: BusinessApi) {
    suspend fun load(): AccountPreferences {
        val p = business.get("/preferences").obj("preferences") ?: EmptyObject
        return AccountPreferences(p.long("version") ?: 0, p.obj("values") ?: EmptyObject, p.obj("recommendation") ?: EmptyObject)
    }
}

object PreferenceOptions {
    val tastes = listOf("清淡", "咸香", "酸甜", "鲜香")
    val spices = listOf("不辣", "微辣", "中辣", "较辣")
    val durations = listOf("under_30" to "30 分钟内", "30_to_60" to "30–60 分钟", "any" to "不限时长")
    val nutritionFields = listOf(
        "max_calories_kcal" to "每份热量上限（kcal）",
        "min_protein_g" to "每份蛋白质下限（g）",
        "max_fat_g" to "每份脂肪上限（g）",
        "max_carbohydrates_g" to "每份碳水上限（g）",
    )
    val difficulties = listOf("" to "不限制", "easy" to "简单", "medium" to "中等", "hard" to "较难")
}
