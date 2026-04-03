package com.guruswarupa.launch.utils

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.guruswarupa.launch.managers.WebAppManager

object AppDisplayHelper {
    fun getLabel(appInfo: ResolveInfo, packageManager: PackageManager): String {
        val packageName = appInfo.activityInfo.packageName
        if (WebAppManager.isWebAppPackage(packageName)) {
            return appInfo.activityInfo.name?.takeIf { it.isNotBlank() } ?: packageName
        }

        return try {
            appInfo.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: packageName
        } catch (_: Exception) {
            packageName
        }
    }
}
