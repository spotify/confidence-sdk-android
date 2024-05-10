package com.spotify.confidence.openfeature

import android.content.SharedPreferences

internal const val SHARED_PREFS_NAME = "confidence-visitor"
internal const val VISITOR_ID_SHARED_PREFS_KEY = "visitor_id"

internal class InMemorySharedPreferences : SharedPreferences {
    private var visitorId: String = ""
    override fun getAll(): MutableMap<String, *> {
        TODO("Not yet implemented")
    }

    override fun getString(key: String?, default: String?): String? =
        if (key == VISITOR_ID_SHARED_PREFS_KEY) {
            visitorId
        } else {
            default
        }

    override fun getStringSet(p0: String?, p1: MutableSet<String>?): MutableSet<String>? {
        TODO("Not yet implemented")
    }

    override fun getInt(p0: String?, p1: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getLong(p0: String?, p1: Long): Long {
        TODO("Not yet implemented")
    }

    override fun getFloat(p0: String?, p1: Float): Float {
        TODO("Not yet implemented")
    }

    override fun getBoolean(p0: String?, p1: Boolean): Boolean {
        TODO("Not yet implemented")
    }

    override fun contains(key: String?) = if (key == VISITOR_ID_SHARED_PREFS_KEY) {
        visitorId.isNotEmpty()
    } else {
        false
    }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private var visitorId: String = ""
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key == VISITOR_ID_SHARED_PREFS_KEY) {
                visitorId = value ?: ""
            }
            return this
        }

        override fun putStringSet(p0: String?, p1: MutableSet<String>?): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun putInt(p0: String?, p1: Int): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun putLong(p0: String?, p1: Long): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun putFloat(p0: String?, p1: Float): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun putBoolean(p0: String?, p1: Boolean): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun remove(p0: String?): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun clear(): SharedPreferences.Editor {
            TODO("Not yet implemented")
        }

        override fun commit(): Boolean {
            TODO("Not yet implemented")
        }

        override fun apply() {
            this@InMemorySharedPreferences.visitorId = this.visitorId
        }
    }

    override fun registerOnSharedPreferenceChangeListener(p0: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("Not yet implemented")
    }

    override fun unregisterOnSharedPreferenceChangeListener(p0: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("Not yet implemented")
    }
}