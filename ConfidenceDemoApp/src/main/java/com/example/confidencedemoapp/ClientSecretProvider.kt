package com.example.confidencedemoapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object ClientSecretProvider {
    fun clientSecret(context: Context): String {
        return try {
            val appInfo: ApplicationInfo = context.packageManager.getApplicationInfo(
                context.packageName, 
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("CLIENT_SECRET") ?: "CLIENT_SECRET"
        } catch (e: PackageManager.NameNotFoundException) {
            "CLIENT_SECRET"
        }
    }
}