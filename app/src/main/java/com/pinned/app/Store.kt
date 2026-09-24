package com.pinned.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Pin(val id: Int, val title: String, val note: String, val created: Long)

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

    fun nextId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = p.getInt("next", 1)
        p.edit().putInt("next", id + 1).apply()
        return id
    }
}
