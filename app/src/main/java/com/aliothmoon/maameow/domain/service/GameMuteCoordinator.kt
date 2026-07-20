package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.DesiredGameAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class GameMuteCoordinator internal constructor(
    private val appSettingsManager: AppSettingsManager,
    private val gameAudioAdapter: GameAudioAdapter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private data class ManagedMute(
        val packageName: String,
        val desired: DesiredGameAudio,
    )

    private val mutex = Mutex()

    /** 由持久化标记派生的静音状态，仅供 UI 观察（可能滞后写入瞬间，逻辑判断勿用） */
    val isMuted: StateFlow<Boolean> = appSettingsManager.mutedGamePackage
        .map { it.isNotEmpty() }
        .stateIn(
            scope, SharingStarted.Eagerly,
            appSettingsManager.mutedGamePackage.value.isNotEmpty()
        )

    fun start() {
        scope.launch {
            gameAudioAdapter.connected.collect { connected ->
                if (connected) {
                    reconcileOnConnected()
                }
            }
        }
    }

    /** 静音。远端失败时自行还原原始 mode（见 GameAudioMuteController），此处仅回滚标记 */
    suspend fun mute(clientType: String?): Boolean = mutex.withLock { muteLocked(clientType) }

    suspend fun toggle(clientType: String?): Boolean = mutex.withLock {
        val managed = currentManagedMute()
        if (managed != null) restoreAudibleLocked(managed) else muteLocked(clientType)
    }

    /** 在关闭远端前先持久化恢复意图，避免 IPC 中断后重连时误判为需要静音。 */
    suspend fun prepareForGameSessionClose() = mutex.withLock {
        val managed = currentManagedMute() ?: return@withLock
        appSettingsManager.setManagedGameAudio(
            managed.packageName,
            DesiredGameAudio.AUDIBLE,
        )
    }

    /** 完成关闭后的状态收敛；恢复失败时保留 AUDIBLE 目标等待重连。 */
    suspend fun onGameSessionClosed(): Boolean = mutex.withLock {
        val managed = currentManagedMute() ?: return@withLock true
        restoreAudibleLocked(managed)
    }

    private suspend fun muteLocked(clientType: String?): Boolean {
        val pkg = clientType?.let { Packages[it] } ?: return false
        val previous = currentManagedMute()
        appSettingsManager.setManagedGameAudio(pkg, DesiredGameAudio.MUTED)
        val ok = gameAudioAdapter.setMuted(pkg, muted = true)
        if (!ok) {
            if (previous == null) {
                appSettingsManager.clearManagedGameAudio()
            } else {
                appSettingsManager.setManagedGameAudio(previous.packageName, previous.desired)
            }
            Timber.w("Mute %s failed", pkg)
        }
        return ok
    }

    private suspend fun restoreAudibleLocked(managed: ManagedMute): Boolean {
        appSettingsManager.setManagedGameAudio(
            managed.packageName,
            DesiredGameAudio.AUDIBLE,
        )
        val ok = gameAudioAdapter.setMuted(managed.packageName, muted = false)
        if (ok) {
            appSettingsManager.clearManagedGameAudio()
        } else {
            Timber.w(
                "Restore audio for %s failed, AUDIBLE intent kept for reconnect",
                managed.packageName,
            )
        }
        return ok
    }

    private suspend fun reconcileOnConnected() = mutex.withLock {
        val managed = currentManagedMute() ?: return@withLock
        when (managed.desired) {
            DesiredGameAudio.MUTED -> {
                Timber.i(
                    "Service connected with persisted mute intent, re-muting %s",
                    managed.packageName,
                )
                if (!gameAudioAdapter.setMuted(managed.packageName, muted = true)) {
                    Timber.w("Reconcile mute for %s failed", managed.packageName)
                }
            }

            DesiredGameAudio.AUDIBLE -> {
                Timber.i(
                    "Service connected with pending audio restore, restoring %s",
                    managed.packageName,
                )
                if (gameAudioAdapter.setMuted(managed.packageName, muted = false)) {
                    appSettingsManager.clearManagedGameAudio()
                } else {
                    Timber.w("Reconcile audio restore for %s failed", managed.packageName)
                }
            }
        }
    }

    /** 权威读取：直读 DataStore，绕开派生 StateFlow 的传播延迟 */
    private suspend fun currentManagedMute(): ManagedMute? {
        val settings = appSettingsManager.settings.first()
        val packageName = settings.mutedGamePackage
        if (packageName.isEmpty()) return null
        return ManagedMute(
            packageName = packageName,
            desired = DesiredGameAudio.fromStoredValue(settings.desiredGameAudio),
        )
    }
}
