package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthState
import com.example.core.auth.AuthTelemetryState
import com.example.core.database.entities.CourseEntity
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.EnrollmentEntity
import com.example.core.database.entities.SemesterEntity
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.LocaleHelper
import com.example.core.preferences.UserPreferences
import com.example.design.theme.BtucmTheme
import com.example.feature.courses.CoursesScreen
import com.example.feature.onboarding.OnboardingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUiRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `onboarding displays sign in CTA and switches between English and Georgian at runtime`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()

    composeTestRule.setContent {
      var prefs by remember { mutableStateOf(UserPreferences(language = AppLanguage.EN)) }
      val localizedContext = remember(baseContext, prefs.language) {
        LocaleHelper.createLocalizedContext(baseContext, prefs.language)
      }
      CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
      ) {
        BtucmTheme {
          OnboardingScreen(
            authState = AuthState.SignedOut,
            telemetry = AuthTelemetryState(),
            preferences = prefs,
            onSignInClick = {},
            onResumeBrowserClick = {},
            onOpenExternalBrowser = {},
            onRememberLoginChanged = { prefs = prefs.copy(rememberLogin = it) },
            onLanguageChanged = { prefs = prefs.copy(language = it) },
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sign_in_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Sign In with BTU Classroom").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("lang_ka_chip").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("შესვლა BTU Classroom-ით").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `courses screen renders Georgian course titles and distinguishes ungraded from scored courses`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    val localizedEn = LocaleHelper.createLocalizedContext(baseContext, AppLanguage.EN)

    val semester = SemesterEntity(
      semesterKey = "default_student::2026_fall",
      rawSemesterKey = "2026_fall",
      studentKey = "default_student",
      title = "2026 Fall",
      sortOrder = 0,
      isCurrent = true,
    )
    val gradedCourse = CourseWithEnrollment(
      course = CourseEntity(
        courseKey = "k1",
        courseId = "501",
        studentKey = "default_student",
        semesterKey = semester.semesterKey,
        semesterName = "2026 Fall",
        code = "CS301",
        title = "ალგორითმები და მონაცემთა სტრუქტურები",
        credits = 6.0,
        minCredit = 51.0,
        lecturer = null,
        syllabusSummary = null,
        detailPath = "/ge/student/me/course/index/501",
        resourcesJson = null,
        detailSyncedAtMillis = null,
        detailStatus = null,
      ),
      enrollment = EnrollmentEntity(
        enrollmentKey = "k1",
        studentKey = "default_student",
        semesterKey = semester.semesterKey,
        courseId = "501",
        totalScore = 92.0,
        statusLabel = null,
        updatedAtMillis = System.currentTimeMillis(),
      ),
    )
    val ungradedCourse = CourseWithEnrollment(
      course = gradedCourse.course.copy(
        courseKey = "k2",
        courseId = "502",
        code = "CS302",
        title = "მობილური აპლიკაციების დეველოპმენტი",
      ),
      enrollment = EnrollmentEntity(
        enrollmentKey = "k2",
        studentKey = "default_student",
        semesterKey = semester.semesterKey,
        courseId = "502",
        totalScore = null,
        statusLabel = null,
        updatedAtMillis = System.currentTimeMillis(),
      ),
    )

    composeTestRule.setContent {
      CompositionLocalProvider(
        LocalContext provides localizedEn,
        LocalConfiguration provides localizedEn.resources.configuration,
      ) {
        BtucmTheme {
          CoursesScreen(
            semesters = listOf(semester),
            courses = listOf(gradedCourse, ungradedCourse),
            syncMetadata = null,
            compatibility = null,
            isSyncing = false,
            isOffline = true,
            onRefresh = {},
            onCourseClick = { _, _ -> },
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("freshness_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Offline — showing saved data").assertIsDisplayed()
    composeTestRule.onNodeWithTag("course_card_501").assertIsDisplayed()
    composeTestRule.onNodeWithText("92 pts").assertIsDisplayed()
    composeTestRule.onNodeWithTag("course_card_502").assertIsDisplayed()
    composeTestRule.onNodeWithText("Ungraded (—)").assertIsDisplayed()
  }
}
