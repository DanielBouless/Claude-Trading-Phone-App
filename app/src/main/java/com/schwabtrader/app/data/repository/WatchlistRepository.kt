package com.schwabtrader.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class WatchlistItem(
    val symbol: String,
    val companyName: String,
    val assetType: String = "EQUITY"
)

@Singleton
class WatchlistRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("watchlist_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "watchlist_items"

    fun getWatchlist(): List<WatchlistItem> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<WatchlistItem>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addToWatchlist(item: WatchlistItem) {
        val current = getWatchlist().toMutableList()
        if (current.none { it.symbol == item.symbol }) {
            current.add(item)
            save(current)
        }
    }

    fun removeFromWatchlist(symbol: String) {
        save(getWatchlist().filter { it.symbol != symbol })
    }

    fun isInWatchlist(symbol: String): Boolean = getWatchlist().any { it.symbol == symbol }

    private fun save(items: List<WatchlistItem>) {
        prefs.edit().putString(key, gson.toJson(items)).apply()
    }
}
