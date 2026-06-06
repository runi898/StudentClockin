package com.familycheckin.child

import android.content.Context

class ChildTaskOrderStore(
    context: Context,
    private val childKey: String
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun load(currentTaskIds: List<String>): List<String> {
        val stored = preferences.getString(orderKey(), null)
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        return merge(stored, currentTaskIds)
    }

    fun save(taskIds: List<String>) {
        preferences.edit()
            .putString(orderKey(), taskIds.joinToString(","))
            .apply()
    }

    private fun merge(stored: List<String>, currentTaskIds: List<String>): List<String> {
        val validStored = stored.filter { it in currentTaskIds }
        val missing = currentTaskIds.filterNot { it in validStored }
        return validStored + missing
    }

    private fun orderKey(): String = "order_${childKey.trim().ifBlank { "default" }}"

    private companion object {
        const val PREFS_NAME = "child_task_order_prefs"
    }
}
