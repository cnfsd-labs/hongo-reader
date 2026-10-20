package app.honmani.reader.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingPageTest {
    @Test
    fun downloadOrSkipLeadsToTheBook() {
        assertEquals(OnboardingPage.BOOK, OnboardingPage.DICTIONARIES.next())
    }

    @Test
    fun backFromTheBookReturnsToTheDictionaries() {
        assertEquals(OnboardingPage.DICTIONARIES, OnboardingPage.BOOK.back())
    }

    @Test
    fun backFromTheDictionariesLeavesTheFirstStart() {
        assertNull(OnboardingPage.DICTIONARIES.back())
    }
}
