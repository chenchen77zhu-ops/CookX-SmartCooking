package com.smartcooking.app.temperature

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.smartcooking.app.core.AppJson
import com.smartcooking.app.core.KeyValueStore
import com.smartcooking.app.core.asArray
import com.smartcooking.app.core.asDouble
import com.smartcooking.app.core.asObject
import com.smartcooking.app.core.asText
import com.smartcooking.app.core.jsonOf
import com.smartcooking.app.core.long
import com.smartcooking.app.core.num
import com.smartcooking.app.core.obj
import com.smartcooking.app.core.objects
import com.smartcooking.app.core.str
import com.smartcooking.app.device.TemperatureSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.nio.FloatBuffer
import java.security.MessageDigest

/** The experimental Transformer (physics-simulation trained), verified by SHA-256 before use. */
class ThermalModel(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    var version: String? = null
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        val manifest = AppJson.parseToJsonElement(context.assets.open("temperature/manifest.json").bufferedReader().readText()).asObject()!!
        require(manifest.num("featureVersion") == 1.0) { "模型特征版本不匹配" }
        val bytes = context.assets.open("temperature/${manifest.str("file")}").use { it.readBytes() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(sha == manifest.str("sha256")) { "模型校验失败" }
        val e = OrtEnvironment.getEnvironment()
        env = e
        session = e.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(1) })
        version = manifest.str("modelVersion")
    }

    data class Output(val logits: FloatArray, val forecast: FloatArray, val qualityLogit: Float, val latencyMs: Double)

    suspend fun run(features: FloatArray): Output = withContext(Dispatchers.Default) {
        val s = session ?: throw IllegalStateException("模型未加载")
        val start = System.nanoTime()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, WINDOW.toLong(), CHANNELS.toLong())).use { input ->
            s.run(mapOf("history" to input)).use { result ->
                fun flat(name: String): FloatArray = (result.get(name).get() as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also { b.get(it) } }
                Output(flat("phase_logits"), flat("forecast"), flat("quality_logit").first(), (System.nanoTime() - start) / 1e6)
            }
        }
    }

    fun close() { session?.close(); session = null }
}

/**
 * App-scoped temperature intelligence: rules engine always on, the experimental model only when
 * the user enables it. Port of `useTemperatureIntelligence`.
 */
class TemperatureIntelligence(private val appContext: Context, private val store: KeyValueStore, private val scope: CoroutineScope) {
    private val engine = TemperatureEngine()
    private val model = ThermalModel(appContext)
    private val _assessment = MutableStateFlow(engine.snapshot)
    val assessment: StateFlow<Assessment> = _assessment.asStateFlow()
    private val _history = MutableStateFlow<List<TemperatureSample>>(emptyList())
    val history: StateFlow<List<TemperatureSample>> = _history.asStateFlow()
    private val _prediction = MutableStateFlow<Prediction?>(null)
    val prediction: StateFlow<Prediction?> = _prediction.asStateFlow()
    val modelState = MutableStateFlow("规则判断")
    val experimental = MutableStateFlow(false)
    val replaying = MutableStateFlow(false)
    val storageMessage = MutableStateFlow("")

    private var storageKey = "cookx-temperature-session-guest"
    private var modelReady = false
    private var busy = false
    private var lastInference = Long.MIN_VALUE / 2
    private var lastSave = 0L
    private var replayJob: Job? = null
    private var replayClock = 0L
    private var context = CookingContext()
    private var anchor: Triple<String?, Long, Long>? = null
    private var session = newSession("device", System.currentTimeMillis())

    private data class Log(
        val source: String, val startedAt: Long, val timelineStartedAt: Long,
        val samples: ArrayDeque<JsonObject> = ArrayDeque(), val assessments: ArrayDeque<JsonObject> = ArrayDeque(),
        val predictions: ArrayDeque<JsonObject> = ArrayDeque(), val events: ArrayDeque<JsonObject> = ArrayDeque(), val contexts: ArrayDeque<JsonObject> = ArrayDeque(),
    )

    private fun newSession(source: String, at: Long) = Log(source, System.currentTimeMillis(), at).also {
        it.contexts.add(jsonOf("at" to at, "context" to contextJson(context)))
    }

    private fun contextJson(c: CookingContext) = jsonOf("recipeId" to c.recipeId, "stepId" to c.stepId, "method" to c.method, "targetRange" to c.targetRange?.let { listOf(it.first, it.second) })

    init {
        scope.launch {
            while (isActive) {
                delay(1000)
                if (!replaying.value) {
                    _assessment.value = engine.expire(System.currentTimeMillis())
                    val p = _prediction.value
                    if (_assessment.value.quality == "invalid" || (p != null && System.currentTimeMillis() - p.at > 2500)) _prediction.value = null
                }
            }
        }
    }

    fun bindUser(user: String?) {
        persist()
        storageKey = "cookx-temperature-session-${user ?: "guest"}"
        session = newSession("device", System.currentTimeMillis())
        _history.value = emptyList()
        invalidate("等待数据")
    }

    private fun sessionJson(): JsonObject = jsonOf(
        "schemaVersion" to 1, "source" to session.source, "startedAt" to session.startedAt,
        "timeBase" to if (session.source == "simulation") "relative_ms" else "unix_ms", "timelineStartedAt" to session.timelineStartedAt,
        "modelVersion" to model.version, "samples" to JsonArray(session.samples.toList()), "assessments" to JsonArray(session.assessments.toList()),
        "predictions" to JsonArray(session.predictions.toList()), "events" to JsonArray(session.events.toList()), "contexts" to JsonArray(session.contexts.toList()),
    )

    private fun persist() {
        if (session.samples.isEmpty() && session.events.isEmpty()) return
        if (store.putSync(storageKey, sessionJson().toString())) storageMessage.value = "" else storageMessage.value = "本地存储不可用，请导出本次记录"
    }

    fun setExperimental(enabled: Boolean) {
        experimental.value = enabled
        _prediction.value = null
        if (enabled && !modelReady) {
            modelState.value = "模型加载中"
            scope.launch {
                try {
                    withTimeout(15_000) { model.load() }
                    modelReady = true
                    modelState.value = "实验模型就绪（仿真训练）"
                } catch (e: Exception) {
                    modelReady = false; experimental.value = false
                    modelState.value = if (e is kotlinx.coroutines.TimeoutCancellationException) "模型加载超时，规则判断" else "模型不可用，规则判断"
                }
            }
        } else if (!enabled) modelState.value = "规则判断"
    }

    fun invalidate(reason: String = "等待数据") {
        _prediction.value = null; anchor = null; lastInference = Long.MIN_VALUE / 2
        _assessment.value = engine.reset(reason)
    }

    fun setContext(next: CookingContext) {
        if (next == context) return
        context = next
        engine.setContext(next)
        _prediction.value = null
        session.contexts.add(jsonOf("at" to clock(), "context" to contextJson(next))); trim(session.contexts, 200)
        _assessment.value = engine.snapshot
    }

    private fun clock() = if (replaying.value) replayClock else System.currentTimeMillis()

    fun confirm(type: String) {
        if (type !in EVENT_TYPES) return
        val at = clock()
        engine.confirm(type, at)
        _prediction.value = null
        session.events.add(jsonOf("type" to type, "at" to at)); trim(session.events, 200)
        _assessment.value = engine.snapshot
    }

    private fun trim(q: ArrayDeque<JsonObject>, max: Int) { while (q.size > max) q.removeFirst() }

    fun receive(input: TemperatureSample, source: String = "device") {
        if (source == "device" && replaying.value) return
        if (session.source != source) {
            persist(); session = newSession(source, input.updatedAt); _history.value = emptyList(); engine.setContext(context); invalidate("数据来源已切换")
        }
        var sample = input.copy(source = source, valid = input.valid && input.temperature?.isFinite() == true)
        if (source == "device" && input.deviceTimeMs != null) {
            val a = anchor
            val next = if (a == null || input.bootId != a.first || input.discontinuity) Triple(input.bootId, input.deviceTimeMs, input.receivedAt) else a
            anchor = next
            sample = sample.copy(updatedAt = next.third + input.deviceTimeMs - next.second)
        }
        val oldEpoch = engine.epoch
        val result = engine.push(sample)
        _assessment.value = result
        if (engine.epoch != oldEpoch || result.quality != "usable") _prediction.value = null
        _history.value = (_history.value + sample).takeLast(120)
        session.samples.add(jsonOf("updatedAt" to sample.updatedAt, "temperature" to sample.temperature, "ambientTemperature" to sample.ambientTemperature, "valid" to sample.valid, "discontinuity" to sample.discontinuity, "source" to source))
        session.assessments.add(jsonOf("at" to sample.updatedAt, "quality" to result.quality, "phase" to result.phase, "risk" to result.risk, "temperature" to result.temperature, "slope" to result.slope))
        trim(session.samples, 2400); trim(session.assessments, 2400)
        if (System.currentTimeMillis() - lastSave > 10_000) { persist(); lastSave = System.currentTimeMillis() }
        if (experimental.value && modelReady && !busy && result.quality == "usable" && sample.updatedAt - lastInference >= 1000) {
            lastInference = sample.updatedAt; busy = true
            val epoch = engine.epoch
            val features = buildFeatures(engine.window(), engine.currentContext)
            val at = sample.updatedAt
            scope.launch {
                try {
                    val out = withTimeout(5000) { model.run(features) }
                    if (!experimental.value) return@launch
                    val accepted = acceptPrediction(out.logits, out.forecast, out.qualityLogit, epoch, engine.epoch, _assessment.value.quality, at, clock())
                    _prediction.value = accepted
                    if (accepted != null) {
                        session.predictions.add(jsonOf("at" to at, "epoch" to epoch, "latencyMs" to out.latencyMs, "phase" to accepted.phase, "probability" to accepted.probability))
                        trim(session.predictions, 2400)
                    }
                    modelState.value = "实验模型（仿真训练） · ${"%.0f".format(out.latencyMs)} ms"
                } catch (e: Exception) {
                    modelReady = false; experimental.value = false; _prediction.value = null
                    modelState.value = if (e is kotlinx.coroutines.TimeoutCancellationException) "推理超时，规则判断" else "模型不可用，规则判断"
                } finally { busy = false }
            }
        }
    }

    /** Replays the bundled physics simulation at one simulated sample per 0.5 s; never labelled as hardware data. */
    fun startReplay() {
        stopReplay()
        replayJob = scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { AppJson.parseToJsonElement(appContext.assets.open("temperature/replay.json").bufferedReader().readText()).asObject()!! }
                val samples = data.objects("samples")
                val events = data.objects("events")
                if (data.str("source") != "physics_simulation" || samples.isEmpty() || samples.any { it.num("updatedAt") == null }) throw IllegalStateException("回放来源不正确")
                persist()
                replayClock = samples.first().long("updatedAt")!!
                val ctx = data.obj("context")
                val replayContext = CookingContext(ctx?.str("recipeId").orEmpty(), ctx?.str("stepId").orEmpty(), ctx?.str("method") ?: "unknown",
                    ctx?.get("targetRange").asArray()?.let { r -> r[0].asDouble()!! to r[1].asDouble()!! })
                session = newSession("simulation", replayClock)
                replaying.value = true
                engine.setContext(replayContext); invalidate("仿真回放，等待连续数据")
                for ((index, s) in samples.withIndex()) {
                    replayClock = s.long("updatedAt")!!
                    events.filter { it.num("index")?.toInt() == index }.forEach { e -> e.str("type")?.let(::confirm) }
                    receive(TemperatureSample(s.num("temperature"), s.num("ambientTemperature"), s["valid"].asText() != "false", replayClock, replayClock,
                        discontinuity = s["discontinuity"].asText() == "true"), "simulation")
                    delay(500)
                }
                stopReplay()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                stopReplay(); storageMessage.value = "回放加载失败，请重试"
            }
        }
    }

    fun stopReplay() {
        val was = replaying.value
        replayJob?.cancel(); replayJob = null
        if (was) { persist(); session = newSession("device", System.currentTimeMillis()) }
        replaying.value = false
        _history.value = emptyList()
        engine.setContext(context)
        if (was) invalidate("回放已结束，等待真实设备数据")
        replayClock = 0
    }

    /** JSON for export: the current session, or the last one saved on this device. */
    fun exportJson(saved: Boolean): String? {
        if (!saved) return sessionJson().toString()
        return store.get(storageKey).also { if (it == null) storageMessage.value = "没有已保存的记录" }
    }
}
