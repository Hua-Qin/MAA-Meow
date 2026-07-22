package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.domain.service.ValidationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class ValidationViewModel(
    private val validationService: ValidationService
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _kami = MutableStateFlow("")
    val kami: StateFlow<String> = _kami.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>("")
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    fun setKami(kami: String) {
        _kami.value = kami
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun login() {
        val kamiCode = _kami.value.trim()
        if (kamiCode.isEmpty()) {
            _errorMessage.value = "请输入卡密"
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val success = validationService.login(kamiCode)
                if (success) {
                    Timber.i("Validation login successful")
                    _loginSuccess.value = true
                } else {
                    _errorMessage.value = "卡密验证失败，请检查卡密是否正确"
                }
            } catch (e: Exception) {
                Timber.e(e, "Validation login exception")
                _errorMessage.value = "网络错误，请稍后重试"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetLoginSuccess() {
        _loginSuccess.value = false
    }

    fun getRemainingDays(): Long {
        return validationService.getRemainingDays()
    }

    fun getValidationState(): ValidationService.ValidationState {
        return validationService.validationState.value
    }

    fun getAppConfig() = validationService.appConfig.value

    fun refreshNotice() {
        viewModelScope.launch {
            validationService.refreshNotice()
        }
    }
}