package com.spotify.confidence

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Helper class to produce device information context for the Confidence context.
 *
 * @param applicationContext the application context.
 * @param withAppInfo whether to include app information in the context.
 * @param withDeviceInfo whether to include device information in the context.
 * @param withOsInfo whether to include OS information in the context.
 * @param withLocale whether to include locale information in the context.
 *
 * The values appended to the Context come primarily from the Android Build class and the application context.
 *
 * AppInfo contains:
 * - version: the version name of the app.
 * - build: the version code of the app.
 * - namespace: the package name of the app.
 *
 * DeviceInfo contains:
 * - manufacturer: the manufacturer of the device.
 * - brand: the brand of the device.
 * - model: the model of the device.
 * - type: the type of the device.
 *
 * OsInfo contains:
 * - name: the name of the OS.
 * - version: the version of the OS.
 *
 * Locale contains:
 * - locale: the locale of the device.
 * - preferred_languages: the preferred languages of the device.
 *
 * The context is only updated when the producer is initialized and then static.
 *
 */
class ConfidenceDeviceInfoContextProducer(
    applicationContext: Context,
    withAppInfo: Boolean = false,
    withDeviceInfo: Boolean = false,
    withOsInfo: Boolean = false,
    withLocale: Boolean = false
) : Producer {
    private val staticContext: ConfidenceFieldsType
    private val packageInfo: PackageInfo? = try {
        @Suppress("DEPRECATION")
        applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(DebugLogger.TAG, "Failed to get package info", e)
        null
    }

    init {
        val context = mutableMapOf<String, ConfidenceValue>()
        if (withAppInfo) {
            val currentVersion = ConfidenceValue.String(packageInfo?.versionName ?: "")
            val currentBuild = ConfidenceValue.String(packageInfo?.getVersionCodeAsString() ?: "")
            val bundleId = ConfidenceValue.String(applicationContext.packageName)
            context["app"] = ConfidenceValue.Struct(
                mapOf(
                    APP_VERSION_CONTEXT_KEY to currentVersion,
                    APP_BUILD_CONTEXT_KEY to currentBuild,
                    APP_NAMESPACE_CONTEXT_KEY to bundleId
                )
            )
        }

        if (withDeviceInfo) {
            context["device"] = ConfidenceValue.Struct(
                mapOf(
                    DEVICE_MANUFACTURER_CONTEXT_KEY to ConfidenceValue.String(Build.MANUFACTURER),
                    DEVICE_BRAND_CONTEXT_KEY to ConfidenceValue.String(Build.BRAND),
                    DEVICE_MODEL_CONTEXT_KEY to ConfidenceValue.String(Build.MODEL),
                    DEVICE_TYPE_CONTEXT_KEY to ConfidenceValue.String("android")
                )
            )
        }

        if (withOsInfo) {
            context["os"] = ConfidenceValue.Struct(
                mapOf(
                    OS_NAME_CONTEXT_KEY to ConfidenceValue.String("android"),
                    OS_VERSION_CONTEXT_KEY to ConfidenceValue.Double(Build.VERSION.SDK_INT.toDouble())
                )
            )
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
                LOCALE_CONTEXT_KEY to ConfidenceValue.String(localeIdentifier),
                PREFERRED_LANGUAGES_CONTEXT_KEY to ConfidenceValue.List(preferredLang.map(ConfidenceValue::String))
            )
            // these are on the top level
            context += localeInfo
        }
        staticContext = context
    }

    override fun updates(): Flow<Update> = flowOf(Update.ContextUpdate(staticContext))

    override fun stop() {}

    companion object {
        const val APP_VERSION_CONTEXT_KEY = "version"
        const val APP_BUILD_CONTEXT_KEY = "build"
        const val APP_NAMESPACE_CONTEXT_KEY = "namespace"
        const val DEVICE_MANUFACTURER_CONTEXT_KEY = "manufacturer"
        const val DEVICE_BRAND_CONTEXT_KEY = "brand"
        const val DEVICE_MODEL_CONTEXT_KEY = "model"
        const val DEVICE_TYPE_CONTEXT_KEY = "type"
        const val OS_NAME_CONTEXT_KEY = "name"
        const val OS_VERSION_CONTEXT_KEY = "version"
        const val LOCALE_CONTEXT_KEY = "locale"
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