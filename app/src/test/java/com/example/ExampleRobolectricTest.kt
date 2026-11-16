package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read English and Georgian strings from context`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("BTUCM", baseContext.getString(R.string.app_name))

    val enContext = LocaleHelper.createLocalizedContext(baseContext, AppLanguage.EN)
    assertEquals("Courses", enContext.getString(R.string.nav_courses))

    val kaContext = LocaleHelper.createLocalizedContext(baseContext, AppLanguage.KA)
    assertEquals("კურსები", kaContext.getString(R.string.nav_courses))
  }
}
