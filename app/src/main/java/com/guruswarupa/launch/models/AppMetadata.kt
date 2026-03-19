package com.guruswarupa.launch.models

import java.io.Serializable





data class AppMetadata(
    val packageName: String,
    val activityName: String,
    val label: String,
    val lastUpdated: Long
) : Serializable
