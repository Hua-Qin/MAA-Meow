package com.aliothmoon.maameow.data.datasource.update

import com.aliothmoon.maameow.data.datasource.AppDownloader
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.model.update.UpdateCheckResult
import com.aliothmoon.maameow.data.model.update.UpdateInfo
import com.aliothmoon.maameow.domain.service.ValidationService
import com.aliothmoon.maameow.domain.service.update.checker.AppVersionChecker
import timber.log.Timber

class ValidationAppVersionChecker(
    private val validationService: ValidationService
) : AppVersionChecker {

    companion object {
        private const val TAG = "ValidationAppVersionChecker"
    }

    override suspend fun check(
        current: String,
        channel: UpdateChannel,
    ): UpdateCheckResult {
        return try {
            val config = validationService.getCachedConfig() ?: validationService.refreshConfig()
            
            if (config == null) {
                Timber.w("$TAG: Failed to get app config")
                return UpdateCheckResult.UpToDate(current)
            }

            val remoteVersion = config.version ?: return UpdateCheckResult.UpToDate(current)
            
            if (AppDownloader.compareVersions(current, remoteVersion) >= 0) {
                UpdateCheckResult.UpToDate(current)
            } else {
                UpdateCheckResult.Available(
                    UpdateInfo(
                        version = remoteVersion,
                        releaseNote = config.appUpdateShow ?: config.versionInfo
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Version check failed")
            UpdateCheckResult.UpToDate(current)
        }
    }

    fun getUpdateUrl(): String? {
        return validationService.getCachedConfig()?.appUpdateUrl
    }

    fun isForceUpdate(): Boolean {
        return validationService.getCachedConfig()?.isForceUpdate() == true
    }
}