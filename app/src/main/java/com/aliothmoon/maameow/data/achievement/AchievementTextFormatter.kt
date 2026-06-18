package com.aliothmoon.maameow.data.achievement

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

fun Context.achievementText(id: String, field: String): String {
    val key = "${id}_$field"
    val resId = resIdCache.getOrPut(key) {
        resources.getIdentifier("achievement_$key", "string", packageName)
    }
    return if (resId == 0) "" else getString(resId)
}

private val resIdCache = ConcurrentHashMap<String, Int>()
