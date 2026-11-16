package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AuthDiagnosticsScreen
import com.example.ui.AuthViewModel
import com.example.ui.theme.BtucmAuthPocTheme

class MainActivity : ComponentActivity() {

  private val viewModel: AuthViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val geckoSession by viewModel.activeGeckoSession.collectAsStateWithLifecycle()

      BtucmAuthPocTheme {
        AuthDiagnosticsScreen(
          state = state,
          geckoSession = geckoSession,
          onStartLogin = viewModel::startLogin,
          onVerifySession = viewModel::verifySession,
          onResetSession = viewModel::resetSession,
          onRequestRemoteLogout = viewModel::requestRemoteLogout,
          onDismissLogoutDialog = viewModel::dismissLogoutDialog,
          onConfirmRemoteLogout = viewModel::confirmRemoteLogout,
          onOpenBrowserModal = viewModel::openBrowserModal,
          onCloseBrowserModal = viewModel::closeBrowserModal,
          onBrowserBackPressed = { viewModel.handleBrowserBack() },
          onClearLogs = viewModel::clearLogs,
        )
      }
    }
  }
}
