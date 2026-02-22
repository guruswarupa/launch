package com.guruswarupa.launch.models

import java.io.Serializable

/**
 * Data class for app metadata caching.
 * Contains package name, activity name, label, and last updated timestamp.
 */
data class AppMetadata(
    val packageName: String,
    val activityName: String,
    val label: String,
    val lastUpdated: Long
) : Serializable
