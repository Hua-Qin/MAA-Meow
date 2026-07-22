package com.aliothmoon.maameow.domain.service

import android.content.Context
import com.aliothmoon.maameow.data.api.model.validation.AppConfig
import com.aliothmoon.maameow.data.api.model.validation.KamiLoginResponse
import com.aliothmoon.maameow.data.api.validation.ValidationApiService
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File

class ValidationService(
    private val apiService: ValidationApiService,
    private val appSettingsManager: AppSettingsManager,
    private val context: Context
) {
    companion object {
        private const val TAG = "ValidationService"
        private const val CACHE_FILE_NAME = "validation_cache.json"
        private const val LOGIN_FILE_NAME = "login_info.json"
    }

    private val mutex = Mutex()

    private val _validationState = MutableStateFlow<ValidationState>(ValidationState.Idle)
    val validationState: StateFlow<ValidationState> = _validationState.asStateFlow()

    private val _appConfig = MutableStateFlow<AppConfig?>(null)
    val appConfig: StateFlow<AppConfig?> = _appConfig.asStateFlow()

    private val _currentNotice = MutableStateFlow<String>("")
    val currentNotice: StateFlow<String> = _currentNotice.asStateFlow()

    private val _loginInfo = MutableStateFlow<LoginInfo?>(null)
    val loginInfo: StateFlow<LoginInfo?> = _loginInfo.asStateFlow()

    enum class ValidationState {
        Idle,
        Checking,
        NeedLogin,
        LoggedIn,
        Expired,
        ForceUpdate,
        NetworkError,
        ApiError
    }

    data class LoginInfo(
        val kami: String,
        val expireTime: Long,
        val loginTime: Long
    ) {
        fun isExpired(): Boolean = expireTime <= System.currentTimeMillis() / 1000
        fun getRemainingDays(): Long {
            val remainingSeconds = expireTime - (System.currentTimeMillis() / 1000)
            return remainingSeconds / (24 * 60 * 60)
        }
    }

    init {
        loadCachedLoginInfo()
    }

    private fun getCacheDir(): File {
        return File(context.cacheDir, "validation").apply { mkdirs() }
    }

    private fun saveLoginInfo(info: LoginInfo) {
        try {
            val file = File(getCacheDir(), LOGIN_FILE_NAME)
            file.writeText("${info.kami}\n${info.expireTime}\n${info.loginTime}")
            _loginInfo.value = info
            Timber.d("$TAG: Login info saved")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to save login info")
        }
    }

    private fun loadCachedLoginInfo() {
        try {
            val file = File(getCacheDir(), LOGIN_FILE_NAME)
            if (file.exists()) {
                val lines = file.readLines()
                if (lines.size >= 3) {
                    val info = LoginInfo(
                        kami = lines[0],
                        expireTime = lines[1].toLongOrNull() ?: 0,
                        loginTime = lines[2].toLongOrNull() ?: 0
                    )
                    _loginInfo.value = info
                    if (!info.isExpired()) {
                        Timber.d("$TAG: Loaded cached login info, expire time: ${info.expireTime}")
                    } else {
                        Timber.d("$TAG: Cached login info expired")
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to load cached login info")
        }
    }

    private fun clearLoginInfo() {
        try {
            val file = File(getCacheDir(), LOGIN_FILE_NAME)
            file.delete()
            _loginInfo.value = null
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to clear login info")
        }
    }

    suspend fun validateOnStartup(): ValidationState {
        return mutex.withLock {
            _validationState.value = ValidationState.Checking

            val cachedConfig = loadCachedConfig()
            
            val configResult = apiService.getAppConfig()
            if (configResult.isFailure) {
                Timber.e(configResult.exceptionOrNull(), "$TAG: Failed to get app config")
                if (cachedConfig != null) {
                    _appConfig.value = cachedConfig
                    return validateWithConfig(cachedConfig)
                }
                _validationState.value = ValidationState.NetworkError
                return ValidationState.NetworkError
            }

            val config = configResult.getOrThrow()
            _appConfig.value = config
            saveConfigCache(config)

            return validateWithConfig(config)
        }
    }

    private suspend fun validateWithConfig(config: AppConfig): ValidationState {
        if (config.isForceUpdate()) {
            _validationState.value = ValidationState.ForceUpdate
            return ValidationState.ForceUpdate
        }

        if (!config.isPaidApp()) {
            _validationState.value = ValidationState.LoggedIn
            return ValidationState.LoggedIn
        }

        val cachedLogin = _loginInfo.value
        if (cachedLogin != null && !cachedLogin.isExpired()) {
            _validationState.value = ValidationState.LoggedIn
            return ValidationState.LoggedIn
        }

        if (cachedLogin != null && cachedLogin.isExpired()) {
            clearLoginInfo()
            _validationState.value = ValidationState.Expired
            return ValidationState.Expired
        }

        if (config.isKamiMode()) {
            _validationState.value = ValidationState.NeedLogin
            return ValidationState.NeedLogin
        }

        return ValidationState.LoggedIn
    }

    suspend fun login(kami: String): Boolean {
        return mutex.withLock {
            val result = apiService.loginWithKami(kami)
            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "$TAG: Login failed")
                return false
            }

            val response = result.getOrThrow()
            val expireTime = response.getExpireTime()
            
            if (expireTime == 0L) {
                Timber.w("$TAG: Invalid expire time")
                return false
            }

            val loginInfo = LoginInfo(
                kami = kami,
                expireTime = expireTime,
                loginTime = System.currentTimeMillis() / 1000
            )
            
            saveLoginInfo(loginInfo)
            _validationState.value = ValidationState.LoggedIn
            
            Timber.i("$TAG: Login successful, expire at: $expireTime")
            return true
        }
    }

    suspend fun logout() {
        mutex.withLock {
            clearLoginInfo()
            _validationState.value = ValidationState.NeedLogin
            Timber.i("$TAG: User logged out")
        }
    }

    suspend fun refreshNotice(): String {
        val result = apiService.getNotice()
        if (result.isSuccess) {
            val notice = result.getOrThrow()
            _currentNotice.value = notice
            return notice
        }
        Timber.e(result.exceptionOrNull(), "$TAG: Failed to refresh notice")
        return _currentNotice.value
    }

    suspend fun refreshConfig(): AppConfig? {
        val result = apiService.getAppConfig()
        if (result.isSuccess) {
            val config = result.getOrThrow()
            _appConfig.value = config
            saveConfigCache(config)
            return config
        }
        Timber.e(result.exceptionOrNull(), "$TAG: Failed to refresh config")
        return _appConfig.value
    }

    fun getCachedConfig(): AppConfig? = _appConfig.value

    fun getCurrentNotice(): String = _currentNotice.value

    private fun saveConfigCache(config: AppConfig) {
        try {
            val file = File(getCacheDir(), CACHE_FILE_NAME)
            file.writeText(config.toString())
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to save config cache")
        }
    }

    private fun loadCachedConfig(): AppConfig? {
        return try {
            val file = File(getCacheDir(), CACHE_FILE_NAME)
            if (file.exists()) {
                val content = file.readText()
                parseConfig(content)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to load config cache")
            null
        }
    }

    private fun parseConfig(content: String): AppConfig? {
        return try {
            val parts = content.split(", ")
            val map = mutableMapOf<String, String>()
            parts.forEach { part ->
                val keyValue = part.split("=")
                if (keyValue.size == 2) {
                    map[keyValue[0].trim()] = keyValue[1].trim().removeSurrounding("\"")
                }
            }
            AppConfig(
                version = map["version"],
                versionInfo = map["versionInfo"],
                appUpdateShow = map["appUpdateShow"],
                appUpdateUrl = map["appUpdateUrl"],
                appUpdatePwd = map["appUpdatePwd"],
                trialMode = map["trialMode"],
                trialKami = map["trialKami"],
                appName = map["appName"],
                appQq = map["appQq"],
                appGroupchat = map["appGroupchat"],
                appImg = map["appImg"],
                appBackground = map["appBackground"],
                appUpdateMust = map["appUpdateMust"],
                appSwitch = map["appSwitch"],
                template = map["template"],
                diyTemplate = map["diyTemplate"],
                apiTotal = map["apiTotal"]
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return _loginInfo.value?.isExpired() == false
    }

    fun getRemainingDays(): Long {
        return _loginInfo.value?.getRemainingDays() ?: 0
    }

    fun getCurrentKami(): String {
        return _loginInfo.value?.kami ?: ""
    }
}