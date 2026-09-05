package com.googleedge.shixian

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object RecommendationManager {
    private const val WORK_NAME = "shixian-recommendation"
    private const val KEY_INPUT = "custom_input"
    private const val KEY_ANSWERS = "answers"
    private const val KEY_TASK_ID = "task_id"

    fun enqueue(context: Context, custom: String, answers: String = "") {
        val store = AppStore(context)
        val taskId = store.task.id.takeIf { answers.isNotBlank() && it.isNotBlank() } ?: UUID.randomUUID().toString()
        val old = store.task
        store.task = TaskSnapshot(id = taskId, status = TaskStatus.RUNNING, phase = "正在排队", progress = 0, etaSeconds = 120, startedAt = old.startedAt.takeIf { answers.isNotBlank() && it > 0 } ?: System.currentTimeMillis(), input = custom, answers = answers, workLog = if (answers.isBlank()) emptyList() else old.workLog, questions = emptyList())
        val data = Data.Builder().putString(KEY_INPUT, custom).putString(KEY_ANSWERS, answers).putString(KEY_TASK_ID, taskId).build()
        val request = OneTimeWorkRequestBuilder<RecommendationWorker>().setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
}

class RecommendationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = AppStore(applicationContext)
        val custom = inputData.getString("custom_input").orEmpty()
        val answers = inputData.getString("answers").orEmpty()
        val id = inputData.getString("task_id") ?: UUID.randomUUID().toString()
        val previous = store.task
        val started = previous.startedAt.takeIf { answers.isNotBlank() && it > 0 } ?: System.currentTimeMillis()
        fun update(phase: String, progress: Int, eta: Int, log: String? = null) {
            val current = store.task
            val logs = (current.workLog + listOfNotNull(log)).distinct().takeLast(12)
            store.task = current.copy(id = id, status = TaskStatus.RUNNING, phase = phase, progress = progress.coerceIn(0,100), etaSeconds = eta.coerceAtLeast(0), startedAt = started, input = custom, answers = answers, workLog = logs, error = "")
            if (log != null) store.appendLog("后台任务", log)
        }
        update("正在启动后台任务", 4, 120, "已创建可在切换页面后继续的任务")
        return try {
            if (answers.isBlank()) {
                update("AI 正在判断是否需要确认信息", 18, 105, "已读取你的饮食偏好和冰箱内容")
                val questions = withContext(Dispatchers.IO) { OpenAiService(store).clarify(custom) }
                if (questions.isNotEmpty()) {
                    store.task = store.task.copy(status = TaskStatus.NEEDS_INPUT, phase = "需要你补充一点信息", progress = 24, etaSeconds = 0, questions = questions, workLog = (store.task.workLog + "AI 发现有信息会影响推荐，先向你确认").distinct())
                    return Result.success()
                }
            }
            val recipes = withContext(Dispatchers.IO) {
                OpenAiService(store).recommend(custom, answers) { phase, progress, eta -> update(phase, progress, eta, phase) }
            }
            if (recipes.isEmpty()) throw IllegalStateException("AI 没有返回可执行菜谱，请换个需求再试")
            store.task = store.task.copy(status = TaskStatus.SUCCESS, phase = "推荐完成", progress = 100, etaSeconds = 0, recipes = recipes, questions = emptyList(), workLog = (store.task.workLog + "已完成食材匹配、天气核实和步骤校验").distinct())
            store.appendLog("后台任务", "菜谱推荐完成", "生成 ${recipes.size} 道菜")
            Result.success()
        } catch (e: Exception) {
            store.task = store.task.copy(status = TaskStatus.FAILED, phase = "任务失败", progress = 0, etaSeconds = 0, error = e.message ?: "请求失败", workLog = (store.task.workLog + "任务未完成：${e.message ?: "未知错误"}").distinct())
            store.appendLog("后台任务", "菜谱任务失败", e.message ?: "未知错误")
            Result.failure()
        }
    }
}
