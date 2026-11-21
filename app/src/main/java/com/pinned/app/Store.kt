package com.pinned.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Pin(
    val id: Int,
    val title: String,
    val note: String,
    val created: Long,
    val remindAt: Long? = null,
)

data class Done(val pin: Pin, val doneAt: Long)

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
    Amoled("AMOLED black"),
}

/** Midnight at the start of today, shifted by [daysAgo] days. */
fun startOfDay(daysAgo: Int = 0): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_YEAR, -daysAgo)
}.timeInMillis

/** Pins are kept as a small JSON array in SharedPreferences. */
object Store {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pins", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Pin> {
        val raw = prefs(ctx).getString("items", null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val remindAt = if (o.has("remindAt") && !o.isNull("remindAt")) o.getLong("remindAt") else null
            Pin(o.getInt("id"), o.getString("title"), o.optString("note"), o.getLong("created"), remindAt)
        }
    }

    fun save(ctx: Context, pins: List<Pin>) {
        val arr = JSONArray()
        pins.forEach {
            val obj = JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("note", it.note)
                .put("created", it.created)
            if (it.remindAt != null) obj.put("remindAt", it.remindAt)
            arr.put(obj)
        }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }

    fun updateReminder(ctx: Context, pinId: Int, remindAt: Long?): Pin? {
        val pins = load(ctx).toMutableList()
        val idx = pins.indexOfFirst { it.id == pinId }
        if (idx < 0) return null
        val updated = pins[idx].copy(remindAt = remindAt)
        pins[idx] = updated
        save(ctx, pins)
        return updated
    }

    fun markDone(ctx: Context, pinId: Int): Pin? {
        val pins = load(ctx).toMutableList()
        val idx = pins.indexOfFirst { it.id == pinId }
        if (idx < 0) return null
        val removed = pins.removeAt(idx).copy(remindAt = null)
        save(ctx, pins)
        val history = loadDone(ctx).toMutableList()
        history.add(0, Done(removed, System.currentTimeMillis()))
        saveDone(ctx, history)
        return removed
    }

    /** Completed pins from today and yesterday; anything older is dropped. */
    fun loadDone(ctx: Context): List<Done> {
        val raw = prefs(ctx).getString("done", null) ?: return emptyList()
        val arr = JSONArray(raw)
        val cutoff = startOfDay(1)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Done(
                Pin(o.getInt("id"), o.getString("title"), o.optString("note"), o.getLong("created")),
                o.getLong("doneAt"),
            )
        }.filter { it.doneAt >= cutoff }
    }

    fun saveDone(ctx: Context, done: List<Done>) {
        val arr = JSONArray()
        done.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.pin.id)
                    .put("title", it.pin.title)
                    .put("note", it.pin.note)
                    .put("created", it.pin.created)
                    .put("doneAt", it.doneAt)
            )
        }
        prefs(ctx).edit().putString("done", arr.toString()).apply()
    }

    fun theme(ctx: Context): ThemeMode =
        prefs(ctx).getString("theme", null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.System

    fun setTheme(ctx: Context, mode: ThemeMode) =
        prefs(ctx).edit().putString("theme", mode.name).apply()

    fun nextId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = p.getInt("next", 1)
        p.edit().putInt("next", id + 1).apply()
        return id
    }
}
