package com.omp.chat.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class Project(
    val id: String = "",
    val path: String,
    val name: String,
    val sessionCount: Int = 0,
    val lastActive: Long = 0L,
    val exists: Boolean = true,
    val hidden: Boolean = false,
    val createdAt: Long = 0L,
    val auto: Boolean = false
)

data class SessionSummary(
    val id: String,
    val file: String,
    val cwd: String,
    val title: String,
    val model: String,
    val messageCount: Int,
    val toolCount: Int,
    val updatedAt: Long,
    val archived: Boolean,
    val projectId: String = ""
)

data class Usage(
    val input: Int,
    val output: Int,
    val total: Int,
    val cost: Double
)

data class ToolCallInfo(
    val id: String,
    val name: String,
    val args: String = "",
    val state: String = "queued",
    val result: String = "",
    val isError: Boolean = false,
    val intent: String = ""
)

data class ChatMessage(
    val id: String,
    val parentId: String = "",
    val role: String,
    val text: String = "",
    val thinking: String = "",
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val toolCallId: String = "",
    val toolName: String = "",
    val isError: Boolean = false,
    val timestamp: Long = 0L,
    val model: String = "",
    val status: String = "done",
    val images: Int = 0,
    val streaming: Boolean = false,
    val usage: Usage? = null,
    val error: String = ""
) {
    val isUser: Boolean get() = role == "user"
    val isAssistant: Boolean get() = role == "assistant"
    val isTool: Boolean get() = role == "tool"
    val isTerminal: Boolean get() = role == "terminal"
    val isSystem: Boolean get() = role == "system"
}

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val reasoning: Boolean,
    val input: List<String>,
    val contextWindow: Int?,
    val maxTokens: Int?,
    val api: String = "",
    val costTotal: Double? = null,
    val thinking: String = ""
) {
    val key: String get() = "$provider/$id"
    val supportsImages: Boolean get() = input.contains("image")
}

data class TodoTask(
    val content: String,
    val status: String,
    val blocker: String = ""
) {
    val done: Boolean get() = status == "completed"
    val abandoned: Boolean get() = status == "abandoned"
}

data class TodoPhase(
    val name: String,
    val tasks: List<TodoTask>
)

data class ChatState(
    val sessionId: String = "",
    val sessionFile: String = "",
    val sessionName: String = "",
    val model: ModelInfo? = null,
    val isStreaming: Boolean = false,
    val isCompacting: Boolean = false,
    val messageCount: Int = 0,
    val queuedMessageCount: Int = 0,
    val isSettled: Boolean = false,
    val todoPhases: List<TodoPhase> = emptyList(),
    val tokensPerSecond: Double? = null,
    val contextTokens: Int = 0,
    val contextWindow: Int = 0,
    val contextPercent: Double = 0.0,
    val thinkingLevel: String = ""
)

data class UiRequest(
    val id: String,
    val method: String,
    val title: String = "",
    val message: String = "",
    val options: List<String> = emptyList(),
    val questions: List<AskQuestion> = emptyList(),
    val placeholder: String = "",
    val prefill: String = "",
    val cwd: String = ""
) {
    val isApproval: Boolean
        get() = method == "select" || method == "confirm"
}

data class AskOption(val label: String, val description: String = "")

data class AskQuestion(
    val id: String,
    val question: String,
    val header: String = "",
    val options: List<AskOption> = emptyList(),
    val multi: Boolean = false
)

data class TerminalEntry(
    val id: Long,
    val command: String,
    val output: String = "",
    val running: Boolean = true,
    val exitCode: Int? = null,
    val isError: Boolean = false
)

data class Notice(val id: Long, val text: String, val level: String = "info")

class BridgeException(message: String, val status: Int = 0) : Exception(message)

data class Event(val type: String, val json: JsonObject) {
    val cwd: String get() = json.str("cwd")

    companion object {
        fun of(type: String): Event = Event(type, JsonObject())
    }
}

// ---------------------------------------------------------------------------
// JSON helpers — tolerant so a newer OMP never crashes the client.
// ---------------------------------------------------------------------------

fun JsonObject.str(key: String): String {
    val value = get(key) ?: return ""
    return if (value.isJsonNull) "" else if (value.isJsonPrimitive) value.asString else ""
}

fun JsonObject.int(key: String): Int {
    val value = get(key) ?: return 0
    return if (value.isJsonPrimitive) value.asInt else 0
}

fun JsonObject.long(key: String): Long {
    val value = get(key) ?: return 0L
    return if (value.isJsonPrimitive) value.asLong else 0L
}

fun JsonObject.dbl(key: String): Double {
    val value = get(key) ?: return 0.0
    return if (value.isJsonPrimitive) value.asDouble else 0.0
}

fun JsonObject.bool(key: String): Boolean = bool(key, false)

fun JsonObject.bool(key: String, default: Boolean): Boolean {
    val value = get(key) ?: return default
    return if (value.isJsonPrimitive) value.asBoolean else default
}

fun JsonObject.obj(key: String): JsonObject? {
    val value = get(key) ?: return null
    return if (value.isJsonObject) value.asJsonObject else null
}

fun JsonObject.arr(key: String): JsonArray {
    val value = get(key) ?: return JsonArray()
    return if (value.isJsonArray) value.asJsonArray else JsonArray()
}

fun JsonElement?.asObjectOrNull(): JsonObject? = if (this != null && isJsonObject) asJsonObject else null

object Parsers {

    fun project(json: JsonObject): Project = Project(
        id = json.str("id"),
        path = json.str("path"),
        name = json.str("name").ifBlank { json.str("path").substringAfterLast('\\').substringAfterLast('/') },
        sessionCount = json.int("sessionCount"),
        lastActive = json.long("lastActive"),
        exists = json.get("exists")?.let { it.isJsonPrimitive && it.asBoolean } ?: true,
        hidden = json.bool("hidden"),
        createdAt = json.long("createdAt"),
        auto = json.bool("auto")
    )

    fun session(json: JsonObject): SessionSummary = SessionSummary(
        id = json.str("id"),
        file = json.str("file"),
        cwd = json.str("cwd"),
        title = json.str("title").ifBlank { "Ny samtale" },
        model = json.str("model"),
        messageCount = json.int("messageCount"),
        toolCount = json.int("toolCount"),
        updatedAt = json.long("updatedAt"),
        archived = json.bool("archived"),
        projectId = json.str("projectId").ifBlank { "" }
    )
    fun trashItem(json: JsonObject): TrashItem = TrashItem(
        trashId = json.str("trashId"),
        file = json.str("file"),
        originalFile = json.str("originalFile"),
        originalDir = json.str("originalDir"),
        sessionId = json.str("sessionId"),
        cwd = json.str("cwd"),
        title = json.str("title").ifBlank { "Ny samtale" },
        archived = json.bool("archived"),
        deletedAt = json.long("deletedAt"),
        messageCount = json.int("messageCount"),
        size = json.long("size")
    )
    fun model(json: JsonObject): ModelInfo {
        val input = json.arr("input").mapNotNull { element ->
            if (element.isJsonPrimitive) element.asString else null
        }
        return ModelInfo(
            id = json.str("id"),
            name = json.str("name").ifBlank { json.str("id") },
            provider = json.str("provider"),
            reasoning = json.bool("reasoning"),
            input = input,
            contextWindow = if (json.get("contextWindow")?.isJsonPrimitive == true) json.int("contextWindow") else null,
            maxTokens = if (json.get("maxTokens")?.isJsonPrimitive == true) json.int("maxTokens") else null,
            api = json.str("api"),
            costTotal = json.obj("cost")?.dbl("total"),
            thinking = json.str("thinking")
        )
    }

    fun usage(json: JsonObject?): Usage? {
        if (json == null) return null
        val cost = json.obj("cost")?.dbl("total") ?: 0.0
        return Usage(
            input = json.int("input"),
            output = json.int("output"),
            total = json.int("totalTokens").let { if (it != 0) it else json.int("input") + json.int("output") },
            cost = cost
        )
    }

    fun toolCall(json: JsonObject): ToolCallInfo = ToolCallInfo(
        id = json.str("id"),
        name = json.str("name"),
        args = json.get("arguments")?.toString() ?: "",
        state = json.str("state").ifBlank { "queued" },
        result = json.str("result"),
        isError = json.bool("isError")
    )

    fun message(json: JsonObject): ChatMessage {
        var tools = json.arr("toolCalls").mapNotNull { element ->
            element.asObjectOrNull()?.let { toolCall(it) }
        }
        var text = json.str("text")
        if (json.str("role") == "assistant" && tools.isEmpty()) {
            // Some local models print the tool call as plain JSON when the runtime
            // cannot parse it. Show it as a tool card instead of raw JSON text.
            val inline = inlineToolCall(text)
            if (inline != null) {
                tools = listOf(inline)
                text = ""
            }
        }
        return ChatMessage(
            id = json.str("id"),
            parentId = json.str("parentId"),
            role = json.str("role"),
            text = text,
            thinking = json.str("thinking"),
            toolCalls = tools,
            toolCallId = json.str("toolCallId"),
            toolName = json.str("toolName"),
            isError = json.bool("isError"),
            timestamp = json.long("timestamp"),
            model = json.str("model"),
            status = json.str("status").ifBlank { "done" },
            images = json.int("images"),
            usage = usage(json.obj("usage")),
            error = json.str("error")
        )
    }

    fun todoTask(json: JsonObject): TodoTask = TodoTask(
        content = json.str("content"),
        status = json.str("status").ifBlank { "pending" },
        blocker = json.str("blocker")
    )

    fun todoPhase(json: JsonObject): TodoPhase = TodoPhase(
        name = json.str("name"),
        tasks = json.arr("tasks").mapNotNull { element ->
            element.asObjectOrNull()?.let { todoTask(it) }
        }
    )

    /**
     * Local models sometimes print a tool call as JSON instead of emitting it via
     * the runtime. Recognise that exact envelope so the UI can show a tool card
     * rather than a wall of raw JSON.
     */
    private fun inlineToolCall(text: String): ToolCallInfo? {
        val trimmed = stripCodeFence(text.trim())
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val obj = try {
            JsonParser.parseString(trimmed).asJsonObject
        } catch (error: Exception) {
            return null
        }
        val name = obj.str("name")
        val args = obj.get("arguments") ?: obj.get("input") ?: return null
        if (name.isBlank()) return null
        return ToolCallInfo(
            id = "printed-$name-${trimmed.hashCode()}",
            name = name,
            args = args.toString(),
            state = "unparsed"
        )
    }

    /**
     * Models that print tool calls usually wrap them in a ```json fence, so the
     * fenced form has to be unwrapped before the JSON envelope can be checked.
     */
    private fun stripCodeFence(text: String): String {
        if (!text.startsWith("```")) return text
        val body = text.removePrefix("```")
        val withoutLang = body.substringAfter('\n', body)
        val inner = if (withoutLang.endsWith("```")) withoutLang.dropLast(3) else withoutLang
        return inner.trim()
    }

    fun state(json: JsonObject?): ChatState {
        if (json == null) return ChatState()
        val model = json.obj("model")?.let { model(it) }
        val phases = json.arr("todoPhases").mapNotNull { element ->
            element.asObjectOrNull()?.let { todoPhase(it) }
        }
        val context = json.obj("contextUsage")
        return ChatState(
            sessionId = json.str("sessionId"),
            sessionFile = json.str("sessionFile"),
            sessionName = json.str("sessionName"),
            model = model,
            isStreaming = json.bool("isStreaming"),
            isCompacting = json.bool("isCompacting"),
            messageCount = json.int("messageCount"),
            queuedMessageCount = json.int("queuedMessageCount"),
            isSettled = json.bool("isSettled"),
            todoPhases = phases,
            tokensPerSecond = if (json.get("tokensPerSecond")?.isJsonPrimitive == true) json.dbl("tokensPerSecond") else null,
            contextTokens = context?.int("tokens") ?: 0,
            contextWindow = context?.int("contextWindow") ?: 0,
            contextPercent = context?.dbl("percent") ?: 0.0,
            thinkingLevel = json.str("thinkingLevel")
        )
    }

    fun uiRequest(json: JsonObject): UiRequest {
        val questions = json.arr("questions").mapNotNull { element ->
            val question = element.asObjectOrNull() ?: return@mapNotNull null
            AskQuestion(
                id = question.str("id"),
                question = question.str("question"),
                header = question.str("header"),
                multi = question.bool("multi"),
                options = question.arr("options").mapNotNull { option ->
                    val obj = option.asObjectOrNull() ?: return@mapNotNull null
                    AskOption(obj.str("label"), obj.str("description"))
                }
            )
        }
        return UiRequest(
            id = json.str("id"),
            method = json.str("method"),
            title = json.str("title"),
            message = json.str("message"),
            options = json.arr("options").mapNotNull { if (it.isJsonPrimitive) it.asString else null },
            questions = questions,
            placeholder = json.str("placeholder"),
            prefill = json.str("prefill"),
            cwd = json.str("cwd")
        )
    }
}

// ---------------------------------------------------------------------------
// Tale-motorer (STT/TTS) fra /api/voice/engines
// ---------------------------------------------------------------------------

/** En tale-motor returnert av /api/voice/engines. */
data class VoiceEntry(val id: String, val name: String)

data class VoiceEngine(
    val id: String,
    val name: String,
    val lang: String,
    val type: String,
    val hardware: String,
    val description: String,
    val downloadMB: Int,
    val installed: Boolean,
    val verified: Boolean,
    val bytes: Long,
    val voices: List<VoiceEntry> = emptyList(),
    val voice: String = ""
)

/** Samlet respons fra /api/voice/engines. */
data class VoiceEngineList(
    val sttEngine: String,
    val ttsEngine: String,
    val ttsVoice: String,
    val sttEngines: List<VoiceEngine>,
    val ttsEngines: List<VoiceEngine>,
    val installed: Boolean,
    val python: Boolean,
    val profile: String,
    val settings: Map<String, Any?>
)


/** PC resource usage snapshot. */
data class PcResources(
    val cpuPercent: Int = 0,
    val cpuCores: Int = 0,
    val ramPercent: Int = 0,
    val ramUsedBytes: Long = 0,
    val ramTotalBytes: Long = 0,
    val gpuPercent: Int = 0,
    val gpuName: String = "",
    val gpuMemoryUsedBytes: Long = 0,
    val gpuMemoryTotalBytes: Long = 0,
    val timestamp: Long = 0
)