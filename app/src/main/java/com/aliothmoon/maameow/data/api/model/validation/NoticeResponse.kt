package com.aliothmoon.maameow.data.api.model.validation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoticeResponse(
    @SerialName("app_gg") val appGg: String? = null
) {
    fun getNotice(): String = appGg.orEmpty()
}