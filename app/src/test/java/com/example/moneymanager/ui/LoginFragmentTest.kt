package com.example.moneymanager.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import com.example.moneymanager.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LoginFragmentTest {

    @Test
    fun loadingStateHelpersDoNotCrashAfterViewIsDestroyed() {
        lateinit var fragment: LoginFragment
        val scenario = launchFragmentInContainer<LoginFragment>(
            themeResId = R.style.Theme_MoneyManager
        )

        scenario.onFragment {
            fragment = it
            invokePrivate(it, "showLoading")
        }

        scenario.moveToState(Lifecycle.State.DESTROYED)

        invokePrivate(fragment, "hideLoading")
        invokePrivate(fragment, "showLoading")
    }

    private fun invokePrivate(fragment: LoginFragment, methodName: String) {
        val method = LoginFragment::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(fragment)
    }
}
