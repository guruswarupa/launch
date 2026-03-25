package com.guruswarupa.launch.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppMetadata(
    val packageName: String,
    val activityName: String,
    val label: String,
    val lastUpdated: Long
)
