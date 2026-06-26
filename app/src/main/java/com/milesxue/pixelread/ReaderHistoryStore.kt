package com.milesxue.pixelread

import android.content.SharedPreferences

fun SharedPreferences.loadRecentBooks(): List<RecentBookEntry> =
    decodeRecentBooks(getString(RECENT_BOOKS_PREF_KEY, null))

fun SharedPreferences.saveRecentBooks(entries: List<RecentBookEntry>) {
    edit()
        .putString(RECENT_BOOKS_PREF_KEY, encodeRecentBooks(entries))
        .apply()
}

fun SharedPreferences.upsertRecentBook(entry: RecentBookEntry) {
    saveRecentBooks(mergeRecentBook(loadRecentBooks(), entry))
}
