package com.omp.chat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TaskState { RUNNING, DONE, FAILED, CANCELLED, UNKNOWN }

data class TaskToolCall(
    val name: String,
    val args: String = "",
    val result: String = "",
    val isError: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = 0
)

data class TaskInfo(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "",
    val sessionTitle: String = "",
    val pcId: String = "",
    val modelName: String = "",
    val cwd: String = "",
    val state: TaskState = TaskState.RUNNING,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = 0,
    val lastActivity: String = "",
    val tools: List<TaskToolCall> = emptyList(),
    val filesChanged: List<String> = emptyList(),
    val error: String = "",
    val activityLog: List<String> = emptyList(),
    val pendingApproval: UiRequest? = null
)

sealed class TaskEvent {
    data class Started(val sessionId: String, val title: String, val modelName: String, val cwd: String) : TaskEvent()
    data class Activity(val sessionId: String, val text: String) : TaskEvent()
    data class ToolStarted(val sessionId: String, val name: String, val args: String) : TaskEvent()
    data class ToolEnded(val sessionId: String, val name: String, val result: String, val isError: Boolean) : TaskEvent()
    data class FileChanged(val sessionId: String, val path: String) : TaskEvent()
    data class Completed(val sessionId: String) : TaskEvent()
    data class Failed(val sessionId: String, val error: String) : TaskEvent()
    data class Cancelled(val sessionId: String) : TaskEvent()
    data class ConnectionLost(val sessionId: String = "") : TaskEvent()
    data class ConnectionRestored(val sessionId: String = "") : TaskEvent()
    data class ApprovalRequest(val sessionId: String, val request: UiRequest) : TaskEvent()
    data class ApprovalResolved(val sessionId: String) : TaskEvent()
}

class TaskStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _tasks = mutableMapOf<String, MutableList<TaskInfo>>() // pcId -> tasks
    private val _listeners = mutableListOf<(String, List<TaskInfo>) -> Unit>()

    fun tasksFor(pcId: String): List<TaskInfo> = _tasks[pcId]?.toList() ?: load(pcId)

    fun tasksForAll(pcIds: List<String>): List<TaskInfo> {
        val result = mutableListOf<TaskInfo>()
        for (pcId in pcIds.ifEmpty { listOf("default") }) {
            result += tasksFor(pcId)
        }
        return result.sortedByDescending { it.startedAt }
    }

    fun addListener(l: (String, List<TaskInfo>) -> Unit) { _listeners.add(l) }
    fun removeListener(l: (String, List<TaskInfo>) -> Unit) { _listeners.remove(l) }

    private fun notify(pcId: String) {
        val list = _tasks[pcId]?.toList() ?: emptyList()
        _listeners.forEach { it(pcId, list) }
    }

    fun handle(pcId: String, event: TaskEvent) {
        if (pcId.isBlank()) return
        val list = _tasks.getOrPut(pcId) { load(pcId).toMutableList() }
        when (event) {
            is TaskEvent.Started -> {
                // avoid duplicate running task for same session
                val existing = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING }
                if (existing == null) {
                    list.add(0, TaskInfo(
                        sessionId = event.sessionId,
                        sessionTitle = event.title.ifBlank { "Sesjon" },
                        pcId = pcId,
                        modelName = event.modelName,
                        cwd = event.cwd,
                        state = TaskState.RUNNING,
                        activityLog = listOf("Startet")
                    ))
                    trim(list)
                }
            }
            is TaskEvent.Activity -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                list[idx] = t.copy(
                    lastActivity = event.text,
                    activityLog = (t.activityLog + event.text).takeLast(50)
                )
            }
            is TaskEvent.ToolStarted -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                val tool = TaskToolCall(name = event.name, args = event.args)
                list[idx] = t.copy(
                    tools = t.tools + tool,
                    lastActivity = "Verktøy: ${event.name}",
                    activityLog = (t.activityLog + "▶ ${event.name}").takeLast(50)
                )
            }
            is TaskEvent.ToolEnded -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                val tools = t.tools.map { tc ->
                    if (tc.name == event.name && tc.endedAt == 0L) {
                        tc.copy(result = event.result.take(200), isError = event.isError, endedAt = System.currentTimeMillis())
                    } else tc
                }
                list[idx] = t.copy(
                    tools = tools,
                    lastActivity = if (event.isError) "Feil: ${event.name}" else "Ferdig: ${event.name}",
                    activityLog = (t.activityLog + (if (event.isError) "✗ ${event.name}" else "✓ ${event.name}")).takeLast(50)
                )
            }
            is TaskEvent.FileChanged -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                if (event.path !in t.filesChanged) {
                    list[idx] = t.copy(filesChanged = t.filesChanged + event.path)
                }
            }
            is TaskEvent.Completed -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                list[idx] = t.copy(
                    state = TaskState.DONE,
                    endedAt = System.currentTimeMillis(),
                    lastActivity = "Fullført",
                    activityLog = (t.activityLog + "✓ Fullført").takeLast(50)
                )
            }
            is TaskEvent.Failed -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId } ?: return
                val idx = list.indexOf(t)
                list[idx] = t.copy(
                    state = TaskState.FAILED,
                    endedAt = System.currentTimeMillis(),
                    error = event.error,
                    lastActivity = "Feilet",
                    activityLog = (t.activityLog + "✗ ${event.error}").takeLast(50)
                )
            }
            is TaskEvent.Cancelled -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId && it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                list[idx] = t.copy(
                    state = TaskState.CANCELLED,
                    endedAt = System.currentTimeMillis(),
                    lastActivity = "Avbrutt"
                )
            }
            is TaskEvent.ConnectionLost -> {
                for (i in list.indices) {
                    val t = list[i]
                    if (t.state == TaskState.RUNNING) {
                        list[i] = t.copy(state = TaskState.UNKNOWN, lastActivity = "Mistet tilkobling")
                    }
                }
            }
            is TaskEvent.ConnectionRestored -> {
                // behold ukjent; ingen automatisk FAILED-markering
            }
            is TaskEvent.ApprovalRequest -> {
                val t = list.firstOrNull { it.sessionId == event.sessionId } ?: list.firstOrNull { it.state == TaskState.RUNNING } ?: return
                val idx = list.indexOf(t)
                list[idx] = t.copy(pendingApproval = event.request, lastActivity = "Venter på godkjenning")
            }
            is TaskEvent.ApprovalResolved -> {
                for (i in list.indices) {
                    val t = list[i]
                    if (t.sessionId == event.sessionId) {
                        list[i] = t.copy(pendingApproval = null)
                    }
                }
            }
        }
        save(pcId, list)
        notify(pcId)
    }

    private fun trim(list: MutableList<TaskInfo>) {
        if (list.size > MAX_TASKS) list.subList(MAX_TASKS, list.size).clear()
    }

    private fun load(pcId: String): MutableList<TaskInfo> {
        val raw = prefs.getString(key(pcId), "[]") ?: "[]"
        val list = mutableListOf<TaskInfo>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val tools = mutableListOf<TaskToolCall>()
                val tarr = o.optJSONArray("tools")
                if (tarr != null) for (j in 0 until tarr.length()) {
                    val to = tarr.optJSONObject(j) ?: continue
                    tools.add(TaskToolCall(
                        name = to.optString("name"),
                        args = to.optString("args"),
                        result = to.optString("result"),
                        isError = to.optBoolean("isError"),
                        startedAt = to.optLong("startedAt"),
                        endedAt = to.optLong("endedAt")
                    ))
                }
                val files = mutableListOf<String>()
                val farr = o.optJSONArray("files")
                if (farr != null) for (j in 0 until farr.length()) files.add(farr.optString(j))
                val logs = mutableListOf<String>()
                val larr = o.optJSONArray("logs")
                if (larr != null) for (j in 0 until larr.length()) logs.add(larr.optString(j))
                list.add(TaskInfo(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    sessionId = o.optString("sessionId"),
                    sessionTitle = o.optString("sessionTitle"),
                    pcId = pcId,
                    modelName = o.optString("modelName"),
                    cwd = o.optString("cwd"),
                    state = TaskState.valueOf(o.optString("state", "DONE")),
                    startedAt = o.optLong("startedAt"),
                    endedAt = o.optLong("endedAt"),
                    lastActivity = o.optString("lastActivity"),
                    tools = tools,
                    filesChanged = files,
                    error = o.optString("error"),
                    activityLog = logs
                ))
            }
        }
        return list
    }

    private fun save(pcId: String, list: List<TaskInfo>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("sessionId", t.sessionId)
            o.put("sessionTitle", t.sessionTitle)
            o.put("modelName", t.modelName)
            o.put("cwd", t.cwd)
            o.put("state", t.state.name)
            o.put("startedAt", t.startedAt)
            o.put("endedAt", t.endedAt)
            o.put("lastActivity", t.lastActivity)
            o.put("error", t.error)
            // pendingApproval not persisted (transient)
            val tarr = JSONArray()
            for (tc in t.tools) {
                tarr.put(JSONObject().apply {
                    put("name", tc.name); put("args", tc.args); put("result", tc.result)
                    put("isError", tc.isError); put("startedAt", tc.startedAt); put("endedAt", tc.endedAt)
                })
            }
            o.put("tools", tarr)
            val farr = JSONArray(); t.filesChanged.forEach { farr.put(it) }; o.put("files", farr)
            val larr = JSONArray(); t.activityLog.forEach { larr.put(it) }; o.put("logs", larr)
            arr.put(o)
        }
        prefs.edit().putString(key(pcId), arr.toString()).apply()
    }

    fun cancel(pcId: String, sessionId: String) {
        handle(pcId, TaskEvent.Cancelled(sessionId))
    }
    fun clear(pcId: String) {
        _tasks.remove(pcId)
        prefs.edit().remove(key(pcId)).apply()
    }

    private fun key(pcId: String) = "tasks_$pcId"

    companion object {
        private const val PREFS = "omp_tasks"
        private const val MAX_TASKS = 50
    }
}
