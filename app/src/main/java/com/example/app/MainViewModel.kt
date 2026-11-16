package com.example.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BtuCmApplication
import com.example.core.auth.AuthState
import com.example.core.auth.AuthTelemetryState
import com.example.core.auth.GeckoAuthManager
import com.example.core.database.CachedCourseResourceDto
import com.example.core.database.SyncOutcome
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.GradeEntity
import com.example.core.database.entities.SemesterEntity
import com.example.core.database.entities.StudentEntity
import com.example.core.database.entities.SyncMetadataEntity
import com.example.core.network.VerificationStatus
import com.example.core.parsing.BtuModuleId
import com.example.core.parsing.ModuleCompatibilityEntry
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.preferences.UserPreferences
import com.example.core.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val container = (application as BtuCmApplication).container
  private val sessionManager = container.sessionManager
  private val preferencesStore = container.preferencesStore
  private val courseRepository = container.courseRepository
  private val compatibilityRegistry = container.compatibilityRegistry

  private val geckoManager: GeckoAuthManager = container.createGeckoAuthManager(
    onAuthenticatedSessionReady = { verifyCapturedSessionAndSync() },
    onSessionExpired = { reason -> handleCookieExpirationEvent(reason) },
  )

  val authState: StateFlow<AuthState> = sessionManager.authState
  val telemetry: StateFlow<AuthTelemetryState> = sessionManager.telemetry
  val activeGeckoSession: StateFlow<GeckoSession?> = geckoManager.activeSession

  val preferences: StateFlow<UserPreferences> = preferencesStore.preferencesFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000L),
    initialValue = UserPreferences(),
  )

  val student: StateFlow<StudentEntity?> = courseRepository
    .observeStudent(UserPreferencesStore.DEFAULT_STUDENT_KEY)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

  val semesters: StateFlow<List<SemesterEntity>> = courseRepository
    .observeSemesters(UserPreferencesStore.DEFAULT_STUDENT_KEY)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val courses: StateFlow<List<CourseWithEnrollment>> = courseRepository
    .observeCourses(UserPreferencesStore.DEFAULT_STUDENT_KEY)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

  val coursesSyncMetadata: StateFlow<SyncMetadataEntity?> = courseRepository
    .observeCoursesSyncMetadata()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

  val compatibilityEntries: StateFlow<Map<BtuModuleId, ModuleCompatibilityEntry>> =
    compatibilityRegistry.entries

  private val _isSyncingCourses = MutableStateFlow(false)
  val isSyncingCourses: StateFlow<Boolean> = _isSyncingCourses.asStateFlow()

  private val _isSyncingDetail = MutableStateFlow(false)
  val isSyncingDetail: StateFlow<Boolean> = _isSyncingDetail.asStateFlow()

  private val _isSignOutDialogVisible = MutableStateFlow(false)
  val isSignOutDialogVisible: StateFlow<Boolean> = _isSignOutDialogVisible.asStateFlow()

  private val _isClearDataDialogVisible = MutableStateFlow(false)
  val isClearDataDialogVisible: StateFlow<Boolean> = _isClearDataDialogVisible.asStateFlow()

  private val _stayOfflineOverride = MutableStateFlow(false)
  val stayOfflineOverride: StateFlow<Boolean> = _stayOfflineOverride.asStateFlow()

  private val _snackbarNotice = MutableStateFlow<String?>(null)
  val snackbarNotice: StateFlow<String?> = _snackbarNotice.asStateFlow()

  init {
    viewModelScope.launch {
      val hasCache = courseRepository.hasCachedCourses()
      val result = sessionManager.restoreAndVerifyOnLaunch(hasCachedAcademicData = hasCache)
      if (result?.status == VerificationStatus.AUTHENTICATED) {
        val preFetchedHtml = sessionManager.consumeCachedInitialVerifiedHtml()
        _isSyncingCourses.value = true
        courseRepository.refreshSelectedCourses(preFetchedHtml)
        _isSyncingCourses.value = false
      }
    }
  }

  fun startLogin() {
    if (telemetry.value.isBusy) return
    _stayOfflineOverride.value = false
    geckoManager.startLogin()
  }

  fun openBrowserModal() {
    if (activeGeckoSession.value != null) {
      sessionManager.setBrowserModalVisible(true)
    }
  }

  fun closeBrowserModal() {
    sessionManager.setBrowserModalVisible(false)
  }

  fun handleBrowserBack(): Boolean {
    return if (geckoManager.goBackInBrowser()) {
      true
    } else {
      sessionManager.setBrowserModalVisible(false)
      true
    }
  }

  fun verifySession() {
    verifyCapturedSessionAndSync()
  }

  private fun verifyCapturedSessionAndSync() {
    viewModelScope.launch {
      val result = sessionManager.verifyCurrentSession(
        onAuthenticatedReleaseBrowser = { geckoManager.releaseLoginSession() },
      )
      if (result.status == VerificationStatus.AUTHENTICATED) {
        _stayOfflineOverride.value = false
        val preFetchedHtml = sessionManager.consumeCachedInitialVerifiedHtml()
        _isSyncingCourses.value = true
        courseRepository.refreshSelectedCourses(preFetchedHtml)
        _isSyncingCourses.value = false
      }
    }
  }

  private fun handleCookieExpirationEvent(reason: String) {
    viewModelScope.launch {
      sessionManager.verifyExpirationOrRecover(reason)
    }
  }

  fun checkSessionExpiration() {
    geckoManager.checkSessionExpiration()
    viewModelScope.launch {
      if (!sessionManager.currentSessionValue().isNullOrBlank()) {
        sessionManager.verifyCurrentSession(
          onAuthenticatedReleaseBrowser = { geckoManager.releaseLoginSession() },
        )
      }
    }
  }

  fun refreshCourses() {
    if (_isSyncingCourses.value) return
    viewModelScope.launch {
      _isSyncingCourses.value = true
      courseRepository.refreshSelectedCourses()
      _isSyncingCourses.value = false
    }
  }

  fun observeCourseDetail(courseKey: String): Flow<CourseWithEnrollment?> =
    courseRepository.observeCourseDetail(courseKey)

  fun observeCourseGrades(courseKey: String): Flow<List<GradeEntity>> =
    courseRepository.observeCourseGrades(courseKey)

  fun decodeResources(resourcesJson: String?): List<CachedCourseResourceDto> =
    courseRepository.decodeResources(resourcesJson)

  fun refreshCourseDetail(courseKey: String, courseId: String, detailPath: String?) {
    if (_isSyncingDetail.value) return
    viewModelScope.launch {
      _isSyncingDetail.value = true
      courseRepository.refreshCourseDetail(courseKey, courseId, detailPath)
      _isSyncingDetail.value = false
    }
  }

  fun dismissReauthPromptAndStayOffline() {
    sessionManager.dismissReauthPrompt()
    if (courses.value.isNotEmpty()) {
      _stayOfflineOverride.value = true
    }
  }

  fun reauthenticateFromPrompt() {
    sessionManager.dismissReauthPrompt()
    _stayOfflineOverride.value = false
    geckoManager.resetBrowserSession {
      geckoManager.startLogin()
    }
  }

  fun setRememberLogin(remember: Boolean) {
    viewModelScope.launch {
      preferencesStore.setRememberLogin(remember)
      if (remember) {
        sessionManager.currentSessionValue()?.let { activeCookie ->
          preferencesStore.saveEncryptedSession(activeCookie)
        }
      }
    }
  }

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch {
      preferencesStore.setLanguage(language)
    }
  }

  fun setThemeMode(themeMode: AppThemeMode) {
    viewModelScope.launch {
      preferencesStore.setThemeMode(themeMode)
    }
  }

  fun setDynamicColor(enabled: Boolean) {
    viewModelScope.launch {
      preferencesStore.setDynamicColor(enabled)
    }
  }

  fun requestSignOut() {
    _isSignOutDialogVisible.value = true
  }

  fun dismissSignOutDialog() {
    _isSignOutDialogVisible.value = false
  }

  fun confirmSignOut(unconfirmedRemoteMessage: String) {
    _isSignOutDialogVisible.value = false
    _stayOfflineOverride.value = false
    viewModelScope.launch {
      val logoutResult = sessionManager.signOutRemoteAndClearLocal(
        clearAcademicCache = { courseRepository.clearAllAcademicData() },
        resetBrowserStorage = { done -> geckoManager.resetBrowserSession(done) },
      )
      if (!logoutResult.success) {
        _snackbarNotice.value = unconfirmedRemoteMessage
      }
    }
  }

  fun requestClearLocalData() {
    _isClearDataDialogVisible.value = true
  }

  fun dismissClearDataDialog() {
    _isClearDataDialogVisible.value = false
  }

  fun confirmClearLocalData() {
    _isClearDataDialogVisible.value = false
    _stayOfflineOverride.value = false
    viewModelScope.launch {
      sessionManager.clearLocalCredentialsAndCache(
        clearAcademicCache = { courseRepository.clearAllAcademicData() },
        resetBrowserStorage = { done -> geckoManager.resetBrowserSession(done) },
      )
    }
  }

  fun consumeSnackbarNotice() {
    _snackbarNotice.value = null
  }

  fun clearLogs() {
    sessionManager.clearLogs()
  }

  override fun onCleared() {
    geckoManager.releaseLoginSession()
    super.onCleared()
  }
}
