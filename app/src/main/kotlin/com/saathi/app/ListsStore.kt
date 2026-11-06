package com.saathi.app

import android.content.Context
import com.saathi.app.core.ListItem
import org.json.JSONArray
import org.json.JSONObject

data class StoredItem(val id: Long, val list: String, val item: ListItem, val done: Boolean)

/** Shopping and to-do lists, stored only on the phone and never sent to the AI. */
class ListsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("saathi_lists", Context.MODE_PRIVATE)

    fun all(list: String): List<StoredItem> = load().filter { it.list == list }

    fun open(list: String): List<ListItem> = all(list).filter { !it.done }.map { it.item }

    /** Adds items, or re-opens one already there. Returns the items as stored. */
    fun add(list: String, items: List<ListItem>): List<ListItem> {
        val current = load().toMutableList()
        for (item in items) {
            val index = current.indexOfFirst { it.list == list && same(it.item, item) }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(item = old.item.copy(qty = item.qty.ifBlank { old.item.qty }), done = false)
            } else {
                current += StoredItem(nextId(current), list, item, false)
            }
        }
        save(current)
        return items
    }

    /** Ticks off the given items. Returns the ones it actually found. */
    fun tick(list: String, items: List<ListItem>): List<ListItem> {
        val current = load().toMutableList()
        val found = ArrayList<ListItem>()
        for (item in items) {
            val index = current.indexOfFirst { it.list == list && !it.done && same(it.item, item) }
            if (index >= 0) {
                current[index] = current[index].copy(done = true)
                found += current[index].item
            }
        }
        save(current)
        return found
    }

    fun has(list: String, item: ListItem): Boolean = open(list).any { same(it, item) }

    fun clear(list: String) = save(load().filter { it.list != list })

    fun setDone(id: Long, done: Boolean) = save(load().map { if (it.id == id) it.copy(done = done) else it })

    fun delete(id: Long) = save(load().filter { it.id != id })

    private fun same(a: ListItem, b: ListItem): Boolean {
        val romanA = normal(a.name)
        val romanB = normal(b.name)
        return romanA == romanB || a.nameHi.trim() == b.nameHi.trim() ||
            (romanA.length >= 4 && romanB.length >= 4 && (romanA in romanB || romanB in romanA))
    }

    private fun normal(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun nextId(items: List<StoredItem>) = (items.maxOfOrNull { it.id } ?: 0L) + 1

    private fun load(): List<StoredItem> {
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            StoredItem(
                id = json.getLong("id"),
                list = json.getString("list"),
                item = ListItem(json.getString("name"), json.getString("nameHi"), json.optString("qty")),
                done = json.optBoolean("done"),
            )
        }
    }

    private fun save(items: List<StoredItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("list", it.list)
                    .put("name", it.item.name)
                    .put("nameHi", it.item.nameHi)
                    .put("qty", it.item.qty)
                    .put("done", it.done)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
