package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.RemoteServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber


class GameMuteCoordinator(
    private val appSettingsManager: AppSettingsManager,
    private val compositionService: MaaCompositionService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** 由持久化标记派生的静音状态，仅供 UI 观察（可能滞后写入瞬间，逻辑判断勿用） */
    val isMuted: StateFlow<Boolean> = appSettingsManager.mutedGamePackage
        .map { it.isNotEmpty() }
        .stateIn(
            scope, SharingStarted.Eagerly,
            appSettingsManager.mutedGamePackage.value.isNotEmpty()
        )

    fun start() {
        // 远端服务每次连接后对账：恢复残留静音或重发静音。
        // 不 drop 初始值：若 start() 时服务已连接，该次连接同样需要对账
        scope.launch {
            RemoteServiceManager.state.collect { state ->
                if (state is RemoteServiceManager.ServiceState.Connected) {
                    reconcileOnConnected()
                }
            }
        }
        // 任务会话结束（自然结束或手动停止回到 IDLE/ERROR）时解除静音。
        // StateFlow 不会连发相等值，drop(1) 跳过初始重放后，收到 IDLE/ERROR 即意味着状态迁移；
        // unmute 对空标记幂等，无需前置判断
        scope.launch {
            compositionService.state
                .drop(1)
                .filter { it == MaaExecutionState.IDLE || it == MaaExecutionState.ERROR }
                .collect { unmute() }
        }
    }

    /** 静音。远端失败时自行还原原始 mode（见 GameAudioMuteController），此处仅回滚标记 */
    suspend fun mute(clientType: String?): Boolean = mutex.withLock { muteLocked(clientType) }

    /** 解除静音并清空标记。远端不可达视为失败，标记保留待下次连接对账恢复 */
    suspend fun unmute(): Boolean = mutex.withLock { unmuteLocked(currentMutedPackage()) }

    suspend fun toggle(clientType: String?): Boolean = mutex.withLock {
        val pkg = currentMutedPackage()
        if (pkg.isNotEmpty()) unmuteLocked(pkg) else muteLocked(clientType)
    }

    private suspend fun muteLocked(clientType: String?): Boolean {
        val pkg = clientType?.let { Packages[it] } ?: return false
        appSettingsManager.setMutedGamePackage(pkg) // write-ahead：先持久化再动系统状态
        val ok = requestRemote(pkg, mute = true)
        if (!ok) {
            appSettingsManager.setMutedGamePackage("")
            Timber.w("Mute %s failed", pkg)
        }
        return ok
    }

    private suspend fun unmuteLocked(pkg: String): Boolean {
        if (pkg.isEmpty()) return true
        val ok = requestRemote(pkg, mute = false)
        if (ok) {
            appSettingsManager.setMutedGamePackage("")
        } else {
            Timber.w("Unmute %s failed, keeping flag for recovery on next connect", pkg)
        }
        return ok
    }

    private suspend fun reconcileOnConnected() = mutex.withLock {
        val pkg = currentMutedPackage()
        if (pkg.isEmpty()) return@withLock
        val state = compositionService.state.value
        val executing = state == MaaExecutionState.STARTING || state == MaaExecutionState.RUNNING
        if (executing) {
            // 标记即静音意图（无论来自启动设置还是手动切换）：
            // 会话仍在进行说明是远端重连，重发静音保证远端进程重启后状态一致
            Timber.i("Service reconnected while executing, re-muting %s", pkg)
            requestRemote(pkg, mute = true)
        } else {
            // 脏路径恢复：上次会话未能正常解除静音（进程被杀 / 设备重启）
            Timber.i("Service connected with stale mute flag, restoring %s", pkg)
            if (requestRemote(pkg, mute = false)) {
                appSettingsManager.setMutedGamePackage("")
            }
        }
    }

    /** 权威读取：直读 DataStore，绕开派生 StateFlow 的传播延迟 */
    private suspend fun currentMutedPackage(): String =
        appSettingsManager.settings.first().mutedGamePackage

    private suspend fun requestRemote(pkg: String, mute: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                RemoteServiceManager.getInstanceOrNull()
                    ?.setPlayAudioOpAllowed(pkg, !mute) == true
            }.onFailure {
                Timber.w(it, "setPlayAudioOpAllowed(%s, mute=%s) IPC failed", pkg, mute)
            }.getOrDefault(false)
        }
}
