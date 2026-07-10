package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import com.aliothmoon.maameow.third.Ln


object AppOpsHelper {
    private const val TAG = "AppOpsHelper"

    private const val OP_PLAY_AUDIO = 28

    /** 读取当前生效 mode；读取失败返回 -1 */
    fun checkPlayAudioMode(packageName: String, uid: Int): Int = runCatching {
        RemoteUtils.appOpsService.checkOperation(OP_PLAY_AUDIO, uid, packageName)
    }.getOrElse {
        Ln.w("$TAG: checkOperation failed for $packageName: ${it.message}")
        -1
    }

    /** 设置 PLAY_AUDIO mode 并校验实际生效 */
    fun setPlayAudioMode(packageName: String, uid: Int, mode: Int): Boolean {
        val viaBinder = runCatching {
            RemoteUtils.appOpsService.setMode(OP_PLAY_AUDIO, uid, packageName, mode)
        }.onFailure {
            Ln.w("$TAG: setMode via binder failed for $packageName: ${it.message}")
        }.isSuccess

        if (!viaBinder) {
            val arg = if (mode == AppOpsManager.MODE_ALLOWED) "allow" else "ignore"
            RemoteUtils.shellExec("appops set $packageName PLAY_AUDIO $arg")
        }
        return checkPlayAudioMode(packageName, uid) == mode
    }
}
