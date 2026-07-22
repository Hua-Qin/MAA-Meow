package com.aliothmoon.maameow.data.api.model.validation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KamiLoginResponse(
    @SerialName("kami") val kami: String? = null,
    @SerialName("vip") val vip: String? = null
) {
    fun getExpireTime(): Long = vip?.toLongOrNull() ?: 0
    fun isExpired(): Boolean = getExpireTime() <= System.currentTimeMillis() / 1000
}