package app.hongo.reader.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppearanceSettingsTest {
    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @Test
    fun defaultsToPurple() {
        assertEquals(Accent.PURPLE, AppearanceSettings(preferences()).accent.value)
    }

    @Test
    fun keepsTheChosenAccent() {
        AppearanceSettings(preferences()).setAccent(Accent.TEAL)

        assertEquals(Accent.TEAL, AppearanceSettings(preferences()).accent.value)
    }

    @Test
    fun unknownStoredAccentFallsBackToDefault() {
        preferences().edit().putString("accent", "MAGENTA").commit()

        assertEquals(Accent.DEFAULT, AppearanceSettings(preferences()).accent.value)
    }
}
