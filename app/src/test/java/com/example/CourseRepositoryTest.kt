package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.auth.AuthState
import com.example.core.auth.SessionManager
import com.example.core.database.BtuDatabase
import com.example.core.database.CourseRepository
import com.example.core.database.SyncOutcome
import com.example.core.network.BtuHttpClient
import com.example.core.parsing.BtuModuleId
import com.example.core.parsing.ModuleCompatibilityStatus
import com.example.core.parsing.PageCompatibilityRegistry
import com.example.core.preferences.UserPreferencesStore
import com.example.core.security.SessionCipher
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseRepositoryTest {

  private lateinit var context: Context
  private lateinit var database: BtuDatabase
  private lateinit var server: MockWebServer
  private lateinit var preferencesStore: UserPreferencesStore
  private lateinit var sessionManager: SessionManager
  private lateinit var compatibilityRegistry: PageCompatibilityRegistry
  private lateinit var repository: CourseRepository
  private lateinit var tempDataStoreFile: File

  private val fakeCipher = object : SessionCipher {
    override fun encrypt(plaintext: String): String = "enc:$plaintext"
    override fun decrypt(encodedBlob: String): String? =
      if (encodedBlob.startsWith("enc:")) encodedBlob.removePrefix("enc:") else null
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, BtuDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    server = MockWebServer()
    server.start()

    tempDataStoreFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
    val dataStore = PreferenceDataStoreFactory.create(produceFile = { tempDataStoreFile })
    preferencesStore = UserPreferencesStore(dataStore, fakeCipher)

    val okClient = OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .build()

    val httpClient = BtuHttpClient(
      baseOrigin = server.url("/").toString().trimEnd('/'),
      allowLocalHttpForTesting = true,
      client = okClient,
    )
    sessionManager = SessionManager(httpClient, preferencesStore)
    compatibilityRegistry = PageCompatibilityRegistry()
    repository = CourseRepository(
      database = database,
      httpClient = httpClient,
      sessionManager = sessionManager,
      preferencesStore = preferencesStore,
      compatibilityRegistry = compatibilityRegistry,
    )
  }

  @After
  fun tearDown() {
    database.close()
    server.shutdown()
    tempDataStoreFile.delete()
  }

  @Test
  fun `syncs courses to Room preserves cache on network or structure failures and clears on sign out`() = runTest {
    val coursesHtml = """
      <html><body>
        <table>
          <thead><tr><th>კოდი</th><th>კურსის დასახელება</th><th>კრედიტი</th><th>ქულა</th></tr></thead>
          <tbody>
            <tr>
              <td>CS101</td>
              <td><a href="/ge/student/me/course/index/101">პროგრამირების საფუძვლები</a></td>
              <td>6</td>
              <td>95</td>
            </tr>
          </tbody>
        </table>
      </body></html>
    """.trimIndent()

    // 1. Capture session from browser and verify over MockWebServer
    sessionManager.onSessionReadyFromBrowser("valid_session_abc")
    server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html").setBody(coursesHtml))
    val verifyResult = sessionManager.verifyCurrentSession(onAuthenticatedReleaseBrowser = {})
    assertEquals(AuthState.Authenticated, sessionManager.authState.value)

    // 2. Persist courses using pre-fetched verified HTML
    val syncOutcome = repository.refreshSelectedCourses(verifyResult.html)
    assertEquals(SyncOutcome.Success, syncOutcome)

    val cachedCourses = repository.observeCourses().first()
    assertEquals(1, cachedCourses.size)
    assertEquals("პროგრამირების საფუძვლები", cachedCourses[0].course.title)
    assertEquals(95.0, cachedCourses[0].enrollment?.totalScore)

    val initialMeta = repository.observeCoursesSyncMetadata().first()
    assertNotNull(initialMeta?.lastSuccessAtMillis)

    // 3. Subsequent unsupported HTML preserves cached courses and marks PARSER_UPDATE_REQUIRED
    server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body><div>New unknown UI</div></body></html>"))
    val brokenOutcome = repository.refreshSelectedCourses()
    assertTrue(brokenOutcome is SyncOutcome.UnsupportedStructure)
    assertEquals(1, repository.observeCourses().first().size)
    assertEquals(
      ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED,
      compatibilityRegistry.entries.value[BtuModuleId.SELECTED_COURSES]?.status,
    )
    // Last success timestamp remains preserved!
    assertEquals(
      initialMeta?.lastSuccessAtMillis,
      repository.observeCoursesSyncMetadata().first()?.lastSuccessAtMillis,
    )

    // 4. Sign out invokes /ge/login/logout, wipes Room and encrypted session
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))
    val logoutResult = sessionManager.signOutRemoteAndClearLocal(
      clearAcademicCache = { repository.clearAllAcademicData() },
      resetBrowserStorage = { it() },
    )
    assertTrue(logoutResult.success)
    assertEquals(AuthState.SignedOut, sessionManager.authState.value)
    assertTrue(repository.observeCourses().first().isEmpty())
    assertNull(preferencesStore.readDecryptedSession())
  }
}
