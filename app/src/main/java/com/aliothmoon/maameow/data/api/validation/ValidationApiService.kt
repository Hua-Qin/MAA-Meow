package com.aliothmoon.maameow.data.api.validation

import android.content.Context
import android.provider.Settings
import com.aliothmoon.maameow.constant.MaaApi
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.api.model.validation.AppConfig
import com.aliothmoon.maameow.data.api.model.validation.KamiLoginResponse
import com.aliothmoon.maameow.data.api.model.validation.NoticeResponse
import com.aliothmoon.maameow.data.api.model.validation.ValidationBaseResponse
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale

class ValidationApiService(
    private val httpClient: HttpClientHelper,
    private val context: Context
) {
    companion object {
        private const val TAG = "ValidationApiService"
    }

    private val json = JsonUtils.common

    private fun getDeviceCode(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device"
        } catch (e: Exception) {
            Timber.w(e, "Failed to get device ID")
            "unknown_device"
        }
    }

    private fun md5(str: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(str.toByteArray())
        return bytes.joinToString("") { "%02x".format(it).uppercase(Locale.ROOT) }
    }

    private fun generateSign(params: Map<String, String>): String {
        val sortedParams = params.entries.sortedBy { it.key }
        val paramStr = sortedParams.joinToString("&") { "${it.key}=${it.value}" }
        return md5("$paramStr&${MaaApi.VALIDATION_APP_KEY}")
    }

    private fun rc4Encrypt(data: String, key: String): String {
        val result = ByteArray(data.length)
        val s = IntArray(256)
        val k = IntArray(256)
        
        for (i in 0..255) {
            s[i] = i
            k[i] = key[i % key.length].code
        }
        
        var j = 0
        for (i in 0..255) {
            j = (j + s[i] + k[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }
        
        var i = 0
        j = 0
        for (m in data.indices) {
            i = (i + 1) % 256
            j = (j + s[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            val t = (s[i] + s[j]) % 256
            result[m] = (data[m].code xor s[t]).toByte()
        }
        
        return result.toHexString()
    }

    private fun rc4Decrypt(hexData: String, key: String): String {
        val data = hexData.hexStringToByteArray()
        val result = CharArray(data.size)
        val s = IntArray(256)
        val k = IntArray(256)
        
        for (i in 0..255) {
            s[i] = i
            k[i] = key[i % key.length].code
        }
        
        var j = 0
        for (i in 0..255) {
            j = (j + s[i] + k[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }
        
        var i = 0
        j = 0
        for (m in data.indices) {
            i = (i + 1) % 256
            j = (j + s[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            val t = (s[i] + s[j]) % 256
            result[m] = (data[m].toInt() xor s[t]).toChar()
        }
        
        return String(result)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it).uppercase(Locale.ROOT) }
    }

    private fun String.hexStringToByteArray(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    suspend fun getAppConfig(markcode: String? = null): Result<AppConfig> {
        return runCatching {
            val params = mutableMapOf<String, String>(
                "api" to "ini",
                "app" to MaaApi.VALIDATION_APP_ID
            )
            markcode?.let { params["markcode"] = it }
            
            val response = httpClient.get(MaaApi.VALIDATION_BASE_URL, query = params)
            response.use { resp ->
                val body = resp.body.string()
                Timber.d("$TAG: getAppConfig response: $body")
                val result = json.decodeFromString<ValidationBaseResponse<AppConfig>>(body)
                if (result.isSuccess()) {
                    result.msg ?: throw Exception("Config data is null")
                } else {
                    throw Exception("API error: ${result.code}")
                }
            }
        }.onFailure {
            Timber.e(it, "$TAG: getAppConfig failed")
        }
    }

    suspend fun getNotice(): Result<String> {
        return runCatching {
            val params = mapOf<String, String>(
                "api" to "notice",
                "app" to MaaApi.VALIDATION_APP_ID
            )
            
            val response = httpClient.get(MaaApi.VALIDATION_BASE_URL, query = params)
            response.use { resp ->
                val body = resp.body.string()
                Timber.d("$TAG: getNotice response: $body")
                val result = json.decodeFromString<ValidationBaseResponse<NoticeResponse>>(body)
                if (result.isSuccess()) {
                    result.msg?.getNotice() ?: ""
                } else {
                    throw Exception("API error: ${result.code}")
                }
            }
        }.onFailure {
            Timber.e(it, "$TAG: getNotice failed")
        }
    }

    suspend fun loginWithKami(kami: String): Result<KamiLoginResponse> {
        return runCatching {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val deviceCode = getDeviceCode()
            
            val params = mutableMapOf<String, String>(
                "api" to "kmlogon",
                "app" to MaaApi.VALIDATION_APP_ID,
                "kami" to kami,
                "markcode" to deviceCode,
                "t" to timestamp
            )
            
            val sign = generateSign(params)
            params["sign"] = sign
            
            Timber.d("$TAG: loginWithKami params: $params")
            
            val response = httpClient.get(MaaApi.VALIDATION_BASE_URL, query = params)
            response.use { resp ->
                val body = resp.body.string()
                Timber.d("$TAG: loginWithKami response: $body")
                val result = json.decodeFromString<ValidationBaseResponse<KamiLoginResponse>>(body)
                if (result.isSuccess()) {
                    result.msg ?: throw Exception("Login data is null")
                } else {
                    throw Exception("API error: ${result.code}")
                }
            }
        }.onFailure {
            Timber.e(it, "$TAG: loginWithKami failed")
        }
    }

    suspend fun getFileUrl(id: String? = null): Result<List<FileInfo>> {
        return runCatching {
            val params = mutableMapOf<String, String>(
                "api" to "getfile",
                "app" to MaaApi.VALIDATION_APP_ID
            )
            id?.let { params["id"] = it }
            
            val response = httpClient.get(MaaApi.VALIDATION_BASE_URL, query = params)
            response.use { resp ->
                val body = resp.body.string()
                Timber.d("$TAG: getFileUrl response: $body")
                val result = json.decodeFromString<ValidationBaseResponse<List<FileInfo>>>(body)
                if (result.isSuccess()) {
                    result.msg ?: emptyList()
                } else {
                    throw Exception("API error: ${result.code}")
                }
            }
        }.onFailure {
            Timber.e(it, "$TAG: getFileUrl failed")
        }
    }

    data class FileInfo(
        val file_url: String? = null,
        val date: String? = null,
        val state: String? = null
    ) {
        fun isEnabled(): Boolean = state == "y"
    }
}