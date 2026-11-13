package com.example.moneymanager.utils

/**
 * The one rule for what a category picker shows: the user's list for that type (from
 * CategoryRepository, never a hard-coded fallback), plus the row's current category when
 * it is not in the list, so opening a picker never silently re-categorises a transaction.
 */
object CategoryChoices {
    fun forPicker(userList: List<String>, current: String? = null): List<String> {
        val seen = HashSet<String>()
        val list = userList.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        val cur = current?.trim().orEmpty()
        return if (cur.isEmpty() || cur.lowercase() in seen) list else list + cur
    }

    fun indexOf(list: List<String>, name: String?): Int =
        if (name == null) -1 else list.indexOfFirst { it.equals(name.trim(), ignoreCase = true) }
}
