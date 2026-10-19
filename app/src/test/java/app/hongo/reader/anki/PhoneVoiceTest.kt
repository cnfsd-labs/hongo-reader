package app.hongo.reader.anki

import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTextToSpeech

/** Robolectric's engine answers nothing by itself: an engine that hangs, unless told otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PhoneVoiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.JAPAN)
    }

    private fun TestScope.voice() = PhoneVoice(
        ApplicationProvider.getApplicationContext(),
        scope = backgroundScope,
        main = StandardTestDispatcher(testScheduler),
        connectTimeoutMs = CONNECT_MS,
        speakTimeoutMs = SPEAK_MS,
    )

    private fun engine(): TextToSpeech = ShadowTextToSpeech.getLastTextToSpeechInstance()

    private fun connectEngine() {
        shadowOf(engine()).onInitListener.onInit(TextToSpeech.SUCCESS)
    }

    private suspend fun assertProblem(expected: VoiceProblem, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: VoiceException) {
            assertEquals(expected, e.problem)
        }
    }

    @Test
    fun anEngineThatNeverConnectsFails() = runTest {
        val voice = voice()
        val speaking = async { runCatching { voice.speak("ねこ", File(folder.root, "a.wav")) } }
        runCurrent()
        advanceTimeBy(CONNECT_MS + 1)

        assertProblem(VoiceProblem.NO_ENGINE) { speaking.await().getOrThrow() }
        assertTrue(shadowOf(engine()).isShutdown)
    }

    @Test
    fun anEngineThatNeverFinishesFails_andTheNextCardConnectsAfresh() = runTest {
        val voice = voice()
        val first = async { runCatching { voice.speak("ねこ", File(folder.root, "a.wav")) } }
        runCurrent()
        connectEngine()
        runCurrent()
        val stuck = engine()
        advanceTimeBy(SPEAK_MS + 1)

        assertProblem(VoiceProblem.FAILED) { first.await().getOrThrow() }
        assertTrue(shadowOf(stuck).isShutdown)

        // Not left waiting behind the stuck engine: a new one is connected.
        val second = async { runCatching { voice.speak("いぬ", File(folder.root, "b.wav")) } }
        runCurrent()
        assertNotSame(stuck, engine())
        assertFalse(second.isCompleted)
        second.cancel()
    }

    @Test
    fun aRecordingIsWritten() = runTest {
        val voice = voice()
        val out = File(folder.root, "a.wav")
        val speaking = async { voice.speak("ねこ", out) }
        runCurrent()
        shadowOf(engine()).simulateSynthesizeToFileResult(TextToSpeech.SUCCESS)
        connectEngine()
        runCurrent()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        runCurrent()

        speaking.await()
        assertTrue(out.length() > 0)
    }

    private companion object {
        const val CONNECT_MS = 1_000L
        const val SPEAK_MS = 5_000L
    }
}
