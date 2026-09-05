package com.googleedge.shixian

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

class OpenAiService(private val store: AppStore) {
    fun models(): List<String> {
        store.appendLog("AI 调用", "获取模型列表", "GET /models")
        val root = request("GET", "/models")
        return root.optJSONArray("data").objects().map { it.optString("id") }.filter { it.isNotBlank() }.sorted()
    }

    fun inspect(bitmap: Bitmap): List<Ingredient> {
        store.appendLog("AI 调用", "开始识别食材照片")
        val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        val data = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        val prompt = "识别照片中可以烹饪的食材。不要猜测被遮挡的物品；数量不确定写适量。仅返回JSON：{\"ingredients\":[{\"name\":\"番茄\",\"amount\":\"2个\",\"category\":\"蔬菜\"}]}"
        val content = JSONArray().put(JSONObject().put("type","input_text").put("text",prompt)).put(JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,$data"))
        val body = JSONObject().put("model", requiredModel()).put("input", JSONArray().put(JSONObject().put("role","user").put("content",content)))
        val json = extractJson(request("POST", "/responses", body).outputText())
        return JSONObject(json).optJSONArray("ingredients").objects().map { Ingredient(it.optString("name"),it.optString("amount","适量"),it.optString("category","其他")) }.filter { it.name.isNotBlank() }
    }

    fun clarify(custom: String): List<ClarificationQuestion> {
        store.appendLog("AI 调用", "询问是否需要补充信息")
        val p = store.preferences
        val inventory = store.ingredients.joinToString("、") { "${it.name}(${it.amount})" }.ifBlank { "未提供" }
        val prompt = """
            你是一个耐心的家庭做饭助手。请判断为了给出安全、确实能做的菜谱，是否还缺少关键信息。
            已知城市：${p.city.ifBlank { "未填写" }}；冰箱：$inventory；人数：${p.people}；口味：${p.taste}；过敏：${p.allergies.ifBlank { "未填写" }}；忌口：${p.avoid.ifBlank { "未填写" }}；本次需求：${custom.ifBlank { "未填写" }}。
            只有在信息缺失会显著改变菜谱或影响安全时才提问，最多提2个问题。城市缺失时可以询问城市；过敏和忌口含糊时优先确认。不要询问已经提供的信息。只返回JSON：{"questions":[{"id":"city","question":"你所在的城市是？","options":["北京","上海","广州","其他"]}]}。没有必要提问时返回{"questions":[]}。
        """.trimIndent()
        val body = JSONObject().put("model", requiredModel()).put("input", prompt)
        val response = request("POST", "/responses", body)
        return JSONObject(extractJson(response.outputText())).optJSONArray("questions").objects().map(ClarificationQuestion::fromJson).take(2)
    }

    fun recommend(custom: String, answers: String = "", onProgress: (String, Int, Int) -> Unit = { _, _, _ -> }): List<Recipe> {
        store.appendLog("AI 调用", "开始生成菜谱", "联网天气与时令核实已启用")
        onProgress("正在整理你的冰箱和饮食条件", 12, 90)
        val p = store.preferences
        val inventory = store.ingredients.joinToString("、") { "${it.name}(${it.amount})" }.ifBlank { "未提供" }
        val today = LocalDate.now().toString()
        val prompt = """
            你是严谨的中国家庭厨师。日期：$today，城市：${p.city.ifBlank { "用户未填写" }}。
            冰箱：$inventory。
            条件：${p.people}人，预算${p.budget}，口味${p.taste}，目标${p.goal}，最多${p.minutes}分钟；忌口${p.avoid.ifBlank { "无" }}；过敏${p.allergies.ifBlank { "无" }}；本次需求${custom.ifBlank { "无额外要求" }}；用户补充回答：${answers.ifBlank { "无" }}。
            请联网核实该城市今天的天气，并结合当地当前时令推荐2到3道真正能完成的菜。核心主料必须在冰箱中；允许缺少油盐酱醋等常备调料，并标为“缺少”。绝不能包含过敏或忌口食材。给出可量化用量、连续完整步骤、熟透判断、食品安全提示。天气查不到就明确写未知。
            只输出JSON，结构：{"recipes":[{"id":"短标识","name":"菜名","reason":"推荐原因","weather":"核实的天气摘要","season":"时令依据","total_minutes":30,"difficulty":"简单","calories":"约500千卡/人","estimated_cost":"约20元","ingredients":[{"name":"食材","amount":"数量","status":"已有|缺少|可选"}],"steps":[{"title":"步骤标题","detail":"包含火力温度和操作细节","minutes":5,"check":"完成判断"}],"safety":["提示"],"substitutions":["替换方案"]}]}
        """.trimIndent()
        val base = JSONObject().put("model", requiredModel()).put("input", prompt)
        onProgress("正在联网核实天气和当季食材", 36, 65)
        val first = JSONObject(base.toString()).put("tools", JSONArray().put(JSONObject().put("type","web_search")))
        val response = runCatching { request("POST", "/responses", first) }.getOrElse { request("POST", "/responses", base) }
        onProgress("AI 正在设计可执行的步骤", 72, 35)
        val root = JSONObject(extractJson(response.outputText()))
        val result = root.optJSONArray("recipes").objects().map(Recipe::fromJson)
        onProgress("已完成食材和步骤校验", 100, 0)
        return result
    }

    private fun requiredModel() = store.model.ifBlank { throw IllegalStateException("请先在设置中获取并选择模型") }
    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        if (store.apiKey.isBlank()) throw IllegalStateException("请先填写 API Key")
        val started = System.currentTimeMillis()
        val endpoint = store.baseUrl.trimEnd('/') + path
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            throw IllegalStateException("为了保护 API Key，Base URL 必须使用 HTTPS")
        }
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = method; conn.connectTimeout = 25_000; conn.readTimeout = 120_000
        conn.setRequestProperty("Authorization", "Bearer ${store.apiKey}"); conn.setRequestProperty("Content-Type", "application/json")
        if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = conn.responseCode; val text = (if(code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull().takeUnless { it.isNullOrBlank() } ?: "接口请求失败（HTTP $code）"
            store.appendLog("AI 错误", message, "$method $path · HTTP $code · ${(System.currentTimeMillis() - started) / 1000}s")
            throw IllegalStateException(message)
        }
        store.appendLog("AI 响应", "请求完成", "$method $path · HTTP $code · ${(System.currentTimeMillis() - started) / 1000}s")
        return JSONObject(text)
    }
    private fun JSONObject.outputText(): String {
        optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val parts = mutableListOf<String>()
        optJSONArray("output").objects().forEach { item -> item.optJSONArray("content").objects().forEach { c -> c.optString("text").takeIf { it.isNotBlank() }?.let(parts::add) } }
        return parts.joinToString("\n").ifBlank { throw IllegalStateException("模型没有返回可读内容") }
    }
    private fun extractJson(raw: String): String {
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = clean.indexOf('{'); val end = clean.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("模型返回的菜谱格式无法识别")
        return clean.substring(start, end + 1)
    }
}
