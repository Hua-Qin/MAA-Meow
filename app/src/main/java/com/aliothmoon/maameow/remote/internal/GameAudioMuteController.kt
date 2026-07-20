package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import com.aliothmoon.maameow.third.Ln


object GameAudioMuteController {
    private const val TAG = "GameAudioMute"

    private data class MuteRecord(
        val uid: Int,
        val originalMode: Int,
        val playerFallback: Boolean,
    )

    // 所有访问均在 @Synchronized 入口内，无需并发容器
    private val muted = mutableMapOf<String, MuteRecord>()

    @Synchronized
    fun setMuted(packageName: String, muted: Boolean): Boolean {
        return if (muted) mute(packageName) else unmute(packageName)
    }

    @Synchronized
    fun restoreAll() {
        muted.keys.toList().forEach { unmute(it) }
    }

    private fun mute(pkg: String): Boolean {
        muted[pkg]?.let { existing ->
            Ln.i(
                "$TAG: $pkg already muted, preserving originalMode=${existing.originalMode}"
            )
            return true
        }

        val uid = RemoteUtils.getAppUid(pkg)
        if (uid < 0) {
            Ln.w("$TAG: mute $pkg failed - cannot resolve uid")
            return false
        }
        // 记录原始 mode，恢复时还原原值而非硬编码 allow
        val original = AppOpsHelper.checkPlayAudioMode(pkg, uid)
            .takeIf { it >= 0 } ?: AppOpsManager.MODE_ALLOWED

        if (AppOpsHelper.setPlayAudioMode(pkg, uid, AppOpsManager.MODE_IGNORED)) {
            muted[pkg] = MuteRecord(uid, original, playerFallback = false)
            Ln.i("$TAG: muted $pkg via appops (uid=$uid, originalMode=$original)")
            return true
        }

        Ln.w("$TAG: appops mute failed for $pkg, falling back to player volume")
        if (PlayerVolumeFallback.engage(uid)) {
            muted[pkg] = MuteRecord(uid, original, playerFallback = true)
            return true
        }
        // 两条路径都失败：尽力把 appops 还原为原始值，避免半生效状态残留
        AppOpsHelper.setPlayAudioMode(pkg, uid, original)
        return false
    }

    private fun unmute(pkg: String): Boolean {
        val record = muted[pkg]
        val uid = record?.uid ?: RemoteUtils.getAppUid(pkg)
        if (uid < 0) {
            Ln.w("$TAG: unmute $pkg failed - cannot resolve uid")
            return false
        }

        if (record?.playerFallback == true) {
            PlayerVolumeFallback.disengage(uid)
            // 尽力清理可能半生效的 appops（写成功但校验读取失败的极端情况）
            AppOpsHelper.setPlayAudioMode(pkg, uid, record.originalMode)
            muted.remove(pkg)
            return true
        }
        // AppOps 跨远端进程存活：记录丢失时只修正 MaaMeow 会设置的 IGNORED，
        // 已恢复成 DEFAULT 等其他 mode 时保持原值，保证重复恢复幂等。
        val target = record?.originalMode ?: targetModeForUntrackedRestore(
            AppOpsHelper.checkPlayAudioMode(pkg, uid)
        ) ?: run {
            Ln.i("$TAG: $pkg is already audible; preserving current appops mode")
            return true
        }
        val ok = AppOpsHelper.setPlayAudioMode(pkg, uid, target)
        if (ok) {
            // 校验成功才移除记录；失败时保留 originalMode，重试仍能还原原值
            muted.remove(pkg)
            Ln.i("$TAG: unmuted $pkg (restored mode=$target)")
        } else {
            Ln.w("$TAG: unmute $pkg failed to verify (target=$target)")
        }
        return ok
    }

    internal fun targetModeForUntrackedRestore(currentMode: Int): Int? =
        if (currentMode >= 0 && currentMode != AppOpsManager.MODE_IGNORED) {
            null
        } else {
            AppOpsManager.MODE_ALLOWED
        }
}
