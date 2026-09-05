package com.googleedge.shixian

import org.json.JSONArray
import org.json.JSONObject

data class Ingredient(val name: String, val amount: String = "适量", val category: String = "其他")

data class Preferences(
    val city: String = "",
    val budget: String = "不限",
    val taste: String = "清淡",
    val avoid: String = "",
    val allergies: String = "",
    val people: Int = 1,
    val goal: String = "均衡饮食",
    val minutes: Int = 30
)

data class RecipeIngredient(val name: String, val amount: String, val status: String)
data class RecipeStep(val title: String, val detail: String, val minutes: Int, val check: String)
data class Recipe(
    val id: String,
    val name: String,
    val reason: String,
    val weather: String,
    val season: String,
    val totalMinutes: Int,
    val difficulty: String,
    val calories: String,
    val estimatedCost: String,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val safety: List<String>,
    val substitutions: List<String>
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("reason", reason); put("weather", weather)
        put("season", season); put("total_minutes", totalMinutes); put("difficulty", difficulty)
        put("calories", calories); put("estimated_cost", estimatedCost)
        put("ingredients", JSONArray().also { a -> ingredients.forEach { a.put(JSONObject().put("name",it.name).put("amount",it.amount).put("status",it.status)) } })
        put("steps", JSONArray().also { a -> steps.forEach { a.put(JSONObject().put("title",it.title).put("detail",it.detail).put("minutes",it.minutes).put("check",it.check)) } })
        put("safety", JSONArray(safety)); put("substitutions", JSONArray(substitutions))
    }

    companion object {
        fun fromJson(o: JSONObject): Recipe = Recipe(
            o.optString("id", o.optString("name") + System.currentTimeMillis()),
            o.optString("name", "今日菜谱"), o.optString("reason"), o.optString("weather"),
            o.optString("season"), o.optInt("total_minutes", 30), o.optString("difficulty", "家常"),
            o.optString("calories", "未估算"), o.optString("estimated_cost", "未估算"),
            o.optJSONArray("ingredients").objects().map { RecipeIngredient(it.optString("name"), it.optString("amount","适量"), it.optString("status","已有")) },
            o.optJSONArray("steps").objects().map { RecipeStep(it.optString("title"),it.optString("detail"),it.optInt("minutes"),it.optString("check")) },
            o.optJSONArray("safety").strings(), o.optJSONArray("substitutions").strings()
        )
    }
}

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

