package com.aliothmoon.maameow.data.api.model.validation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidationBaseResponse<T>(
    @SerialName("code") val code: Int,
    @SerialName("msg") val msg: T? = null,
    @SerialName("time") val time: Long = 0,
    @SerialName("check") val check: String? = null
) {
    fun isSuccess(): Boolean = code == 200
}