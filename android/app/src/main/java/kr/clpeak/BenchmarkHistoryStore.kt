package kr.clpeak

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded on-device benchmark history. */
data class BenchmarkRun(
    val startedAtMs: Long,
    val completedAtMs: Long,
    val exitCode: Int,
    val entries: List<ResultEntry>
) {
    val backends: List<String> get() = entries.map { it.backend }.distinct()
    val devices: List<String> get() = entries.map { it.device }.distinct()
}

class BenchmarkHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<BenchmarkRun> {
        val root = runCatching { JSONArray(prefs.getString(KEY_RUNS, "[]")) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until root.length()) {
                runCatching { runFromJson(root.getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }
    }

    fun save(run: BenchmarkRun) {
        val encoded = prefs.getString(KEY_RUNS, "[]") ?: "[]"
        val existing = runCatching { JSONArray(encoded) }
        if (existing.isFailure) {
            // Preserve the raw value for recovery instead of silently discarding
            // it when a future run is recorded.
            prefs.edit()
                .putString(KEY_CORRUPT_RUNS, encoded)
                .putString(KEY_RUNS, JSONArray().put(runToJson(run)).toString())
                .apply()
            return
        }
        val runs = (listOf(run) + load()).take(MAX_RUNS)
        val json = JSONArray()
        runs.forEach { json.put(runToJson(it)) }
        prefs.edit().putString(KEY_RUNS, json.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY_RUNS).remove(KEY_CORRUPT_RUNS).apply()

    private fun runToJson(run: BenchmarkRun) = JSONObject().apply {
        put("startedAtMs", run.startedAtMs)
        put("completedAtMs", run.completedAtMs)
        put("exitCode", run.exitCode)
        put("entries", JSONArray().also { array -> run.entries.forEach { array.put(entryToJson(it)) } })
    }

    private fun runFromJson(json: JSONObject) = BenchmarkRun(
        startedAtMs = json.optLong("startedAtMs"),
        completedAtMs = json.optLong("completedAtMs"),
        exitCode = json.optInt("exitCode"),
        entries = json.optJSONArray("entries")?.let { array ->
            buildList { for (index in 0 until array.length()) add(entryFromJson(array.getJSONObject(index))) }
        } ?: emptyList()
    )

    private fun entryToJson(entry: ResultEntry) = JSONObject().apply {
        put("backend", entry.backend); put("platform", entry.platform); put("device", entry.device)
        put("driver", entry.driver); put("category", entry.category); put("test", entry.test)
        put("display", entry.display); put("metric", entry.metric); put("unit", entry.unit)
        put("value", entry.value.toDouble()); put("status", entry.status); put("reason", entry.reason)
    }

    private fun entryFromJson(json: JSONObject) = ResultEntry(
        backend = json.optString("backend"), platform = json.optString("platform"),
        device = json.optString("device"), driver = json.optString("driver"),
        category = json.optString("category"), test = json.optString("test"),
        display = json.optString("display"), metric = json.optString("metric"),
        unit = json.optString("unit"), value = json.optDouble("value").toFloat(),
        status = json.optString("status", "ok"), reason = json.optString("reason")
    )

    private companion object {
        const val PREFS_NAME = "benchmark_history"
        const val KEY_RUNS = "runs"
        const val KEY_CORRUPT_RUNS = "corrupt_runs"
        const val MAX_RUNS = 20
    }
}
