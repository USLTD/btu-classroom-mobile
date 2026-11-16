package com.example.app

import android.content.Context
import com.example.core.auth.GeckoAuthManager
import com.example.core.auth.LogLevel
import com.example.core.auth.SessionManager
import com.example.core.database.BtuDatabase
import com.example.core.database.CourseRepository
import com.example.core.network.BtuHttpClient
import com.example.core.parsing.PageCompatibilityRegistry
import com.example.core.preferences.UserPreferencesStore
import com.example.core.security.AndroidKeystoreSessionCipher

class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  val cipher = AndroidKeystoreSessionCipher()
  val preferencesStore = UserPreferencesStore(appContext, cipher)
  val database = BtuDatabase.getInstance(appContext)
  val compatibilityRegistry = PageCompatibilityRegistry()

  lateinit var sessionManager: SessionManager
    private set

  val httpClient: BtuHttpClient = BtuHttpClient(
    onDiagnosticEvent = { message ->
      if (::sessionManager.isInitialized) {
        sessionManager.appendLog(message, LogLevel.INFO)
      }
    },
  )

  init {
    sessionManager = SessionManager(
      httpClient = httpClient,
      preferencesStore = preferencesStore,
    )
  }

  val courseRepository = CourseRepository(
    database = database,
    httpClient = httpClient,
    sessionManager = sessionManager,
    preferencesStore = preferencesStore,
    compatibilityRegistry = compatibilityRegistry,
  )

  fun createGeckoAuthManager(
    onAuthenticatedSessionReady: (String) -> Unit,
    onSessionExpired: (String) -> Unit,
  ): GeckoAuthManager {
    return GeckoAuthManager(
      context = appContext,
      sessionManager = sessionManager,
      onAuthenticatedSessionReady = onAuthenticatedSessionReady,
      onSessionExpired = onSessionExpired,
    )
  }
}
