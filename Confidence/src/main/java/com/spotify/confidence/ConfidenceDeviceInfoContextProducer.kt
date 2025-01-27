package com.spotify.confidence

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

class ConfidenceDeviceInfoContextProducer(
    applicationContext: Context,
    withVersionInfo: Boolean = false,
    withBundleId: Boolean = false,
    withDeviceInfo: Boolean = false,
    withLocale: Boolean = false,
) : ContextProducer {
    private val contextFlow = MutableStateFlow<Map<String, ConfidenceValue>>(mapOf())
    private val packageInfo: PackageInfo? = try {
        @Suppress("DEPRECATION")
        applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(DebugLogger.TAG, "Failed to get package info", e)
        null
    }

    init {
        val context = mutableMapOf<String, ConfidenceValue>()
        if (withVersionInfo) {
            val currentVersion = ConfidenceValue.String(packageInfo?.versionName ?: "")
            val currentBuild = ConfidenceValue.String(packageInfo?.getVersionCodeAsString() ?: "")
            val addedContext = mapOf(
                APP_VERSION_CONTEXT_KEY to currentVersion,
                APP_BUILD_CONTEXT_KEY to currentBuild
            )
            context += addedContext
        }
        if (withBundleId) {
            val bundleId = ConfidenceValue.String(applicationContext.packageName)
            context += mapOf(BUNDLE_ID_CONTEXT_KEY to bundleId)
        }
        if (withDeviceInfo) {
            val deviceInfo = mapOf(
                SYSTEM_NAME_CONTEXT_KEY to ConfidenceValue.String("Android"),
                DEVICE_BRAND_CONTEXT_KEY to ConfidenceValue.String(Build.BRAND),
                DEVICE_MODEL_CONTEXT_KEY to ConfidenceValue.String(Build.MODEL),
                SYSTEM_VERSION_CONTEXT_KEY to ConfidenceValue.Double(Build.VERSION.SDK_INT.toDouble())
            )
            context += deviceInfo
        }
        if (withLocale) {
            val preferredLang = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val locales = applicationContext.resources.configuration.locales
                (0 until locales.size()).map { locales.get(it).toString() }
            } else {
                listOf(Locale.getDefault().toString())
            }
            val localeIdentifier = Locale.getDefault().toString()
            val localeInfo = mapOf(
                LOCAL_IDENTIFIER_CONTEXT_KEY to ConfidenceValue.String(localeIdentifier),
                PREFERRED_LANGUAGES_CONTEXT_KEY to ConfidenceValue.List(preferredLang.map(ConfidenceValue::String))
            )
            context += localeInfo
        }
        contextFlow.value = context
    }

    override fun contextChanges(): Flow<Map<String, ConfidenceValue>> = contextFlow
    override fun stop() {}

    companion object {
        const val APP_VERSION_CONTEXT_KEY = "app_version"
        const val APP_BUILD_CONTEXT_KEY = "app_build"
        const val BUNDLE_ID_CONTEXT_KEY = "bundle_id"
        const val SYSTEM_NAME_CONTEXT_KEY = "system_name"
        const val DEVICE_BRAND_CONTEXT_KEY = "device_brand"
        const val DEVICE_MODEL_CONTEXT_KEY = "device_model"
        const val SYSTEM_VERSION_CONTEXT_KEY = "system_version"
        const val LOCAL_IDENTIFIER_CONTEXT_KEY = "locale_identifier"
        const val PREFERRED_LANGUAGES_CONTEXT_KEY = "preferred_languages"
    }
}

private fun PackageInfo.getVersionCodeAsString(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.longVersionCode.toString()
    } else {
        @Suppress("DEPRECATION")
        this.versionCode.toString()
    }