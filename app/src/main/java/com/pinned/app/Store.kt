package com.pinned.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Pin(val id: Int, val title: String, val note: String, val created: Long)

data class Done(val pin: Pin, val doneAt: Long)

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
            Pin(o.getInt("id"), o.getString("title"), o.optString("note"), o.getLong("created"))
        }
    }

    fun save(ctx: Context, pins: List<Pin>) {
        val arr = JSONArray()
        pins.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("note", it.note)
                    .put("created", it.created)
            )
        }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
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

    fun nextId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = p.getInt("next", 1)
        p.edit().putInt("next", id + 1).apply()
        return id
    }
}
