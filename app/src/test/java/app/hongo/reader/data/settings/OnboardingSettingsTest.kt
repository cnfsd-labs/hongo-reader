package app.hongo.reader.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingSettingsTest {
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("onboarding-test", Context.MODE_PRIVATE)

    @Test
    fun showsUntilFinished_thenNeverAgain() {
        val settings = OnboardingSettings(preferences())
        assertFalse(settings.done.value)

        settings.finish()

        assertTrue(settings.done.value)
        assertTrue(OnboardingSettings(preferences()).done.value)
    }
}
