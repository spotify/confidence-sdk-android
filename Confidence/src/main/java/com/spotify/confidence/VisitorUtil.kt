package com.spotify.confidence

import android.content.Context
import java.util.UUID

internal const val SHARED_PREFS_NAME = "confidence-visitor"
internal const val VISITOR_ID_SHARED_PREFS_KEY = "visitorId"
internal const val DEFAULT_VALUE = "unable-to-read"

internal object VisitorUtil {
    fun getId(context: Context): String {
        return with(context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)) {
            if (contains(VISITOR_ID_SHARED_PREFS_KEY)) {
                getString(VISITOR_ID_SHARED_PREFS_KEY, DEFAULT_VALUE) ?: DEFAULT_VALUE
            } else {
                val visitorId = UUID.randomUUID().toString()
                edit().putString(VISITOR_ID_SHARED_PREFS_KEY, visitorId).apply()
                visitorId
            }
        }
    }

    fun resetId(context: Context): String {
        return with(context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)) {
            val newVisitorId = UUID.randomUUID().toString()
            edit().putString(VISITOR_ID_SHARED_PREFS_KEY, newVisitorId).apply()
            newVisitorId
        }
    }
}