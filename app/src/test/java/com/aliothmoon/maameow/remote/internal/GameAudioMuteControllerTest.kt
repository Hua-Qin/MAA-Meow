package com.aliothmoon.maameow.remote.internal

import android.app.AppOpsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameAudioMuteControllerTest {

    @Test
    fun untrackedRestorePreservesAlreadyAudibleMode() {
        assertNull(
            GameAudioMuteController.targetModeForUntrackedRestore(AppOpsManager.MODE_DEFAULT)
        )
        assertNull(
            GameAudioMuteController.targetModeForUntrackedRestore(AppOpsManager.MODE_ALLOWED)
        )
    }

    @Test
    fun untrackedRestoreAllowsAudioWhenMuteRecordWasLost() {
        assertEquals(
            AppOpsManager.MODE_ALLOWED,
            GameAudioMuteController.targetModeForUntrackedRestore(AppOpsManager.MODE_IGNORED),
        )
    }
}
