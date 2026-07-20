package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.AppSettings
import com.aliothmoon.maameow.domain.models.DesiredGameAudio
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameMuteCoordinatorTest {

    @Test
    fun gameSessionClosedVerifiesAudioRestoreAndClearsManagedMute() = runBlocking {
        val fixture = fixture(
            AppSettings(
                mutedGamePackage = GAME_PACKAGE,
                desiredGameAudio = DesiredGameAudio.MUTED.name,
            )
        )

        fixture.coordinator.prepareForGameSessionClose()

        assertEquals(DesiredGameAudio.AUDIBLE.name, fixture.settings.value.desiredGameAudio)
        assertTrue(fixture.coordinator.onGameSessionClosed())

        assertEquals(listOf(AudioRequest(GAME_PACKAGE, muted = false)), fixture.audio.requests)
        assertEquals("", fixture.settings.value.mutedGamePackage)
        assertFalse(fixture.coordinator.isMuted.value)
        fixture.close()
    }

    @Test
    fun gameSessionCloseFailureIsRetriedAsAudibleOnReconnect() = runBlocking {
        val fixture = fixture(
            AppSettings(
                mutedGamePackage = GAME_PACKAGE,
                desiredGameAudio = DesiredGameAudio.MUTED.name,
            )
        )
        fixture.audio.result = false

        fixture.coordinator.prepareForGameSessionClose()
        assertFalse(fixture.coordinator.onGameSessionClosed())
        assertEquals(GAME_PACKAGE, fixture.settings.value.mutedGamePackage)
        assertEquals(DesiredGameAudio.AUDIBLE.name, fixture.settings.value.desiredGameAudio)

        fixture.audio.result = true
        fixture.coordinator.start()
        fixture.audio.connected.value = true
        yield()

        assertEquals(AudioRequest(GAME_PACKAGE, muted = false), fixture.audio.requests.last())
        assertEquals("", fixture.settings.value.mutedGamePackage)
        fixture.close()
    }

    @Test
    fun reconnectReappliesLegacyPersistedMuteIntent() = runBlocking {
        val fixture = fixture(
            AppSettings(
                mutedGamePackage = GAME_PACKAGE,
            )
        )

        fixture.coordinator.start()
        fixture.audio.connected.value = true
        yield()

        assertEquals(listOf(AudioRequest(GAME_PACKAGE, muted = true)), fixture.audio.requests)
        assertEquals(GAME_PACKAGE, fixture.settings.value.mutedGamePackage)
        fixture.close()
    }

    private fun fixture(initialSettings: AppSettings): Fixture {
        val settings = MutableStateFlow(initialSettings)
        val mutedGamePackage = MutableStateFlow(initialSettings.mutedGamePackage)
        val manager = mockk<AppSettingsManager>()
        every { manager.settings } returns settings
        every { manager.mutedGamePackage } returns mutedGamePackage
        coEvery { manager.setManagedGameAudio(any(), any()) } coAnswers {
            settings.value = settings.value.copy(
                mutedGamePackage = firstArg(),
                desiredGameAudio = secondArg<DesiredGameAudio>().name,
            )
            mutedGamePackage.value = settings.value.mutedGamePackage
        }
        coEvery { manager.clearManagedGameAudio() } coAnswers {
            settings.value = settings.value.copy(
                mutedGamePackage = "",
                desiredGameAudio = DesiredGameAudio.MUTED.name,
            )
            mutedGamePackage.value = ""
        }
        val audio = FakeGameAudioAdapter()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = GameMuteCoordinator(manager, audio, scope)
        return Fixture(settings, audio, coordinator, scope)
    }

    private data class Fixture(
        val settings: MutableStateFlow<AppSettings>,
        val audio: FakeGameAudioAdapter,
        val coordinator: GameMuteCoordinator,
        val scope: CoroutineScope,
    ) {
        fun close() = scope.cancel()
    }

    private class FakeGameAudioAdapter : GameAudioAdapter {
        override val connected = MutableStateFlow(false)
        val requests = mutableListOf<AudioRequest>()
        var result = true

        override suspend fun setMuted(packageName: String, muted: Boolean): Boolean {
            requests += AudioRequest(packageName, muted)
            return result
        }
    }

    private data class AudioRequest(
        val packageName: String,
        val muted: Boolean,
    )

    private companion object {
        const val GAME_PACKAGE = "com.hypergryph.arknights"
    }
}
