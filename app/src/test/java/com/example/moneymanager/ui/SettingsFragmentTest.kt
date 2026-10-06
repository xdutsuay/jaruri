package com.example.moneymanager.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.moneymanager.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsFragmentTest {

    @Test
    fun testSettingsSaveButton() {
        launchFragmentInContainer<SettingsFragment>(themeResId = R.style.Theme_MoneyManager)

        // Save sits below new toggles — scroll before click in ScrollView.
        onView(withId(R.id.btn_save_settings)).perform(scrollTo(), click())
    }
}
