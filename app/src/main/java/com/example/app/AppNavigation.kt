package com.example.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.BuildConfig
import com.example.R
import com.example.core.auth.AuthState
import com.example.core.network.BtuRoutes
import com.example.core.preferences.LocaleHelper
import com.example.design.theme.BtucmTheme
import com.example.feature.auth.GeckoBrowserModal
import com.example.feature.courses.CourseDetailScreen
import com.example.feature.courses.CoursesScreen
import com.example.feature.diagnostics.DiagnosticsScreen
import com.example.feature.home.HomeScreen
import com.example.feature.more.MoreScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.schedule.ScheduleScreen
import com.example.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object CoursesRoute
@Serializable data class CourseDetailRoute(val courseKey: String, val courseId: String)
@Serializable data object ScheduleRoute
@Serializable data object MoreRoute
@Serializable data object SettingsRoute
@Serializable data object DiagnosticsRoute

private enum class TopTab(
  val route: Any,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
) {
  HOME(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "tab_home"),
  COURSES(CoursesRoute, R.string.nav_courses, Icons.Filled.Book, Icons.Outlined.Book, "tab_courses"),
  SCHEDULE(ScheduleRoute, R.string.nav_schedule, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "tab_schedule"),
  MORE(MoreRoute, R.string.nav_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "tab_more"),
}

@Composable
fun BtucmAppRoot(viewModel: MainViewModel) {
  val authState by viewModel.authState.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val geckoSession by viewModel.activeGeckoSession.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val stayOfflineOverride by viewModel.stayOfflineOverride.collectAsStateWithLifecycle()
  val courses by viewModel.courses.collectAsStateWithLifecycle()
  val snackbarNotice by viewModel.snackbarNotice.collectAsStateWithLifecycle()

  val baseContext = LocalContext.current
  val localizedContext = remember(baseContext, preferences.language) {
    LocaleHelper.createLocalizedContext(baseContext, preferences.language)
  }

  CompositionLocalProvider(
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedContext.resources.configuration,
  ) {
    BtucmTheme(
      themeMode = preferences.themeMode,
      dynamicColor = preferences.dynamicColor,
    ) {
      val showMainShell = authState is AuthState.Authenticated ||
        (stayOfflineOverride && courses.isNotEmpty())

      if (showMainShell) {
        AuthenticatedShell(
          viewModel = viewModel,
          snackbarNotice = snackbarNotice,
          onOpenExternalUrl = { url -> openUrlExternally(baseContext, url) },
        )
      } else {
        OnboardingScreen(
          authState = authState,
          telemetry = telemetry,
          preferences = preferences,
          onSignInClick = viewModel::startLogin,
          onResumeBrowserClick = viewModel::openBrowserModal,
          onOpenExternalBrowser = { openUrlExternally(baseContext, BtuRoutes.loginUrl()) },
          onRememberLoginChanged = viewModel::setRememberLogin,
          onLanguageChanged = viewModel::setLanguage,
        )
      }

      if (telemetry.isBrowserModalVisible) {
        GeckoBrowserModal(
          telemetry = telemetry,
          geckoSession = geckoSession,
          onBackPressed = { viewModel.handleBrowserBack() },
          onCloseModal = viewModel::closeBrowserModal,
          onOpenExternalBrowser = { openUrlExternally(baseContext, BtuRoutes.loginUrl()) },
        )
      }

      if (telemetry.isReauthPromptVisible) {
        AlertDialog(
          onDismissRequest = viewModel::dismissReauthPromptAndStayOffline,
          modifier = Modifier.testTag("reauth_dialog"),
          title = { Text(stringResource(R.string.reauth_dialog_title)) },
          text = { Text(stringResource(R.string.reauth_dialog_message)) },
          confirmButton = {
            Button(
              onClick = viewModel::reauthenticateFromPrompt,
              modifier = Modifier.testTag("reauth_confirm_button"),
            ) {
              Text(stringResource(R.string.reauth_dialog_confirm))
            }
          },
          dismissButton = {
            TextButton(
              onClick = viewModel::dismissReauthPromptAndStayOffline,
              modifier = Modifier.testTag("reauth_dismiss_button"),
            ) {
              Text(stringResource(R.string.reauth_dialog_dismiss))
            }
          },
        )
      }

      val isSignOutDialogVisible by viewModel.isSignOutDialogVisible.collectAsStateWithLifecycle()
      if (isSignOutDialogVisible) {
        val unconfirmedRemoteMsg = stringResource(R.string.remote_logout_unconfirmed)
        AlertDialog(
          onDismissRequest = viewModel::dismissSignOutDialog,
          modifier = Modifier.testTag("logout_confirm_dialog"),
          title = { Text(stringResource(R.string.logout_dialog_title)) },
          text = { Text(stringResource(R.string.logout_dialog_message)) },
          confirmButton = {
            Button(
              onClick = { viewModel.confirmSignOut(unconfirmedRemoteMsg) },
              modifier = Modifier.testTag("logout_confirm_button"),
            ) {
              Text(stringResource(R.string.logout_dialog_confirm))
            }
          },
          dismissButton = {
            TextButton(
              onClick = viewModel::dismissSignOutDialog,
              modifier = Modifier.testTag("logout_cancel_button"),
            ) {
              Text(stringResource(R.string.dialog_cancel))
            }
          },
        )
      }

      val isClearDataDialogVisible by viewModel.isClearDataDialogVisible.collectAsStateWithLifecycle()
      if (isClearDataDialogVisible) {
        AlertDialog(
          onDismissRequest = viewModel::dismissClearDataDialog,
          modifier = Modifier.testTag("clear_data_confirm_dialog"),
          title = { Text(stringResource(R.string.clear_data_dialog_title)) },
          text = { Text(stringResource(R.string.clear_data_dialog_message)) },
          confirmButton = {
            Button(
              onClick = viewModel::confirmClearLocalData,
              modifier = Modifier.testTag("clear_data_confirm_button"),
            ) {
              Text(stringResource(R.string.clear_data_dialog_confirm))
            }
          },
          dismissButton = {
            TextButton(
              onClick = viewModel::dismissClearDataDialog,
              modifier = Modifier.testTag("clear_data_cancel_button"),
            ) {
              Text(stringResource(R.string.dialog_cancel))
            }
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
  viewModel: MainViewModel,
  snackbarNotice: String?,
  onOpenExternalUrl: (String) -> Unit,
) {
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarNotice) {
    if (!snackbarNotice.isNullOrBlank()) {
      snackbarHostState.showSnackbar(snackbarNotice)
      viewModel.consumeSnackbarNotice()
    }
  }

  val isTopLevelRoute = currentDestination?.let { dest ->
    dest.hasRoute(HomeRoute::class) ||
      dest.hasRoute(CoursesRoute::class) ||
      dest.hasRoute(ScheduleRoute::class) ||
      dest.hasRoute(MoreRoute::class)
  } ?: true

  val topBarTitleRes = when {
    currentDestination?.hasRoute(CoursesRoute::class) == true -> R.string.courses_title
    currentDestination?.hasRoute(ScheduleRoute::class) == true -> R.string.schedule_title
    currentDestination?.hasRoute(MoreRoute::class) == true -> R.string.more_title
    else -> R.string.app_full_name
  }

  val authState by viewModel.authState.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val student by viewModel.student.collectAsStateWithLifecycle()
  val semesters by viewModel.semesters.collectAsStateWithLifecycle()
  val courses by viewModel.courses.collectAsStateWithLifecycle()
  val syncMetadata by viewModel.coursesSyncMetadata.collectAsStateWithLifecycle()
  val compatibilityEntries by viewModel.compatibilityEntries.collectAsStateWithLifecycle()
  val isSyncingCourses by viewModel.isSyncingCourses.collectAsStateWithLifecycle()
  val isSyncingDetail by viewModel.isSyncingDetail.collectAsStateWithLifecycle()

  fun navigateTopTab(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        if (isTopLevelRoute) {
          TopAppBar(
            title = { Text(stringResource(topBarTitleRes)) },
          )
        }
      },
      bottomBar = {
        if (isTopLevelRoute && !useNavigationRail) {
          NavigationBar {
            TopTab.entries.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTopTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }
      },
      containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (isTopLevelRoute && useNavigationRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            TopTab.entries.forEach { tab ->
              val selected = currentDestination?.hasRoute(tab.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTopTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(
            navController = navController,
            startDestination = HomeRoute,
          ) {
            composable<HomeRoute> {
              HomeScreen(
                student = student,
                semesters = semesters,
                courses = courses,
                syncMetadata = syncMetadata,
                isSyncing = isSyncingCourses,
                isOffline = telemetry.isOfflineFallback,
                onRefresh = viewModel::refreshCourses,
                onOpenCoursesTab = { navigateTopTab(CoursesRoute) },
                onOpenCourseDetail = { key, id ->
                  navController.navigate(CourseDetailRoute(courseKey = key, courseId = id))
                },
                onOpenScheduleTab = { navigateTopTab(ScheduleRoute) },
              )
            }

            composable<CoursesRoute> {
              CoursesScreen(
                semesters = semesters,
                courses = courses,
                syncMetadata = syncMetadata,
                compatibility = compatibilityEntries[com.example.core.parsing.BtuModuleId.SELECTED_COURSES],
                isSyncing = isSyncingCourses,
                isOffline = telemetry.isOfflineFallback,
                onRefresh = viewModel::refreshCourses,
                onCourseClick = { key, id ->
                  navController.navigate(CourseDetailRoute(courseKey = key, courseId = id))
                },
              )
            }

            composable<CourseDetailRoute> { backStackEntry ->
              val args = backStackEntry.toRoute<CourseDetailRoute>()
              val courseWithEnrollment by viewModel
                .observeCourseDetail(args.courseKey)
                .collectAsStateWithLifecycle(initialValue = null)
              val grades by viewModel
                .observeCourseGrades(args.courseKey)
                .collectAsStateWithLifecycle(initialValue = emptyList())
              val resources = remember(courseWithEnrollment?.course?.resourcesJson) {
                viewModel.decodeResources(courseWithEnrollment?.course?.resourcesJson)
              }

              CourseDetailScreen(
                courseWithEnrollment = courseWithEnrollment,
                grades = grades,
                resources = resources,
                isLoading = isSyncingDetail,
                isOffline = telemetry.isOfflineFallback,
                onSyncDetail = {
                  viewModel.refreshCourseDetail(
                    courseKey = args.courseKey,
                    courseId = args.courseId,
                    detailPath = courseWithEnrollment?.course?.detailPath,
                  )
                },
                onOpenSafeUrl = { url ->
                  BtuRoutes.resolveSafeBtuUrl(url)?.let(onOpenExternalUrl)
                },
                onNavigateBack = { navController.popBackStack() },
              )
            }

            composable<ScheduleRoute> {
              ScheduleScreen(
                onOpenPortalSchedule = { url ->
                  BtuRoutes.resolveSafeBtuUrl(url)?.let(onOpenExternalUrl)
                },
              )
            }

            composable<MoreRoute> {
              MoreScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenDiagnostics = {
                  if (BuildConfig.DEBUG) {
                    navController.navigate(DiagnosticsRoute)
                  }
                },
                onRequestSignOut = viewModel::requestSignOut,
                onRequestClearLocalData = viewModel::requestClearLocalData,
              )
            }

            composable<SettingsRoute> {
              SettingsScreen(
                preferences = preferences,
                onLanguageChanged = viewModel::setLanguage,
                onThemeModeChanged = viewModel::setThemeMode,
                onDynamicColorChanged = viewModel::setDynamicColor,
                onRememberLoginChanged = viewModel::setRememberLogin,
                onRequestSignOut = viewModel::requestSignOut,
                onRequestClearLocalData = viewModel::requestClearLocalData,
                onNavigateBack = { navController.popBackStack() },
              )
            }

            if (BuildConfig.DEBUG) {
              composable<DiagnosticsRoute> {
                DiagnosticsScreen(
                  authState = authState,
                  telemetry = telemetry,
                  compatibilityEntries = compatibilityEntries,
                  onVerifySession = viewModel::verifySession,
                  onCheckExpiry = viewModel::checkSessionExpiration,
                  onRefreshCourses = viewModel::refreshCourses,
                  onClearLogs = viewModel::clearLogs,
                  onNavigateBack = { navController.popBackStack() },
                )
              }
            }
          }
        }
      }
    }
  }
}

private fun openUrlExternally(context: Context, url: String) {
  try {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  } catch (_: Throwable) {
  }
}
