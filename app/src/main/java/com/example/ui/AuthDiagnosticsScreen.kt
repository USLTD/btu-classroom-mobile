package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AuthDiagnosticState
import com.example.model.DiagnosticLogEntry
import com.example.model.ExtensionInstallStatus
import com.example.model.GeckoInitStatus
import com.example.model.GoogleLoginStatus
import com.example.model.LogLevel
import com.example.model.VerificationStatus
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleTextPrimary
import com.example.ui.theme.ConsoleTextTimestamp
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import org.mozilla.geckoview.GeckoSession

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthDiagnosticsScreen(
  state: AuthDiagnosticState,
  geckoSession: GeckoSession?,
  onStartLogin: () -> Unit,
  onVerifySession: () -> Unit,
  onResetSession: () -> Unit,
  onRequestRemoteLogout: () -> Unit,
  onDismissLogoutDialog: () -> Unit,
  onConfirmRemoteLogout: () -> Unit,
  onOpenBrowserModal: () -> Unit,
  onCloseBrowserModal: () -> Unit,
  onBrowserBackPressed: () -> Unit,
  onClearLogs: () -> Unit,
) {
  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 680.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        HeaderCard(state = state)

        AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
          ErrorCalloutCard(message = state.errorMessage.orEmpty())
        }

        TelemetryStatusCard(state = state)

        ControlsCard(
          state = state,
          onStartLogin = onStartLogin,
          onVerifySession = onVerifySession,
          onResetSession = onResetSession,
          onRequestRemoteLogout = onRequestRemoteLogout,
          onOpenBrowserModal = onOpenBrowserModal,
        )

        DiagnosticLogCard(
          logs = state.logs,
          onClearLogs = onClearLogs,
        )
      }
    }
  }

  if (state.isBrowserModalVisible) {
    GeckoBrowserModal(
      state = state,
      geckoSession = geckoSession,
      onBackPressed = onBrowserBackPressed,
      onCloseModal = onCloseBrowserModal,
    )
  }

  if (state.isLogoutDialogVisible) {
    AlertDialog(
      onDismissRequest = onDismissLogoutDialog,
      modifier = Modifier.testTag("logout_confirm_dialog"),
      title = { Text(stringResource(R.string.logout_dialog_title)) },
      text = { Text(stringResource(R.string.logout_dialog_message)) },
      confirmButton = {
        Button(
          onClick = onConfirmRemoteLogout,
          modifier = Modifier.testTag("logout_confirm_button"),
        ) {
          Text(stringResource(R.string.logout_dialog_confirm))
        }
      },
      dismissButton = {
        TextButton(
          onClick = onDismissLogoutDialog,
          modifier = Modifier.testTag("logout_cancel_button"),
        ) {
          Text(stringResource(R.string.logout_dialog_cancel))
        }
      },
    )
  }
}

@Composable
private fun HeaderCard(state: AuthDiagnosticState) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    tonalElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
          Text(
            text = stringResource(R.string.subtitle_poc_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.target_host_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))
      VerificationBadge(status = state.verificationStatus)
    }
  }
}

@Composable
private fun VerificationBadge(status: VerificationStatus) {
  val (bg, fg) = when (status) {
    VerificationStatus.AUTHENTICATED -> StatusSuccessBg to StatusSuccess
    VerificationStatus.CLOUDFLARE_BLOCKED,
    VerificationStatus.UNAUTHENTICATED,
    VerificationStatus.UNEXPECTED_RESPONSE -> StatusWarningBg to StatusWarning
    VerificationStatus.NETWORK_ERROR -> StatusErrorBg to StatusError
    VerificationStatus.VERIFYING,
    VerificationStatus.IDLE -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = bg,
    border = BorderStroke(1.dp, fg.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (status == VerificationStatus.AUTHENTICATED) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = fg,
          modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Text(
        text = status.label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ErrorCalloutCard(message: String) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("error_callout_card"),
    shape = RoundedCornerShape(12.dp),
    color = StatusErrorBg,
    border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Icon(
        imageVector = Icons.Filled.ErrorOutline,
        contentDescription = null,
        tint = StatusError,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = stringResource(R.string.label_error_message),
          style = MaterialTheme.typography.titleSmall,
          color = StatusError,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = StatusError,
        )
      }
    }
  }
}

@Composable
private fun TelemetryStatusCard(state: AuthDiagnosticState) {
  val yesLabel = stringResource(R.string.value_yes)
  val noLabel = stringResource(R.string.value_no)
  val noneLabel = stringResource(R.string.value_none)

  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.section_telemetry),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

      StatusRow(
        label = stringResource(R.string.label_geckoview_status),
        value = state.geckoInitStatus.label,
        tone = when (state.geckoInitStatus) {
          GeckoInitStatus.READY -> RowTone.SUCCESS
          GeckoInitStatus.FAILED -> RowTone.ERROR
          GeckoInitStatus.INITIALIZING -> RowTone.WARNING
          GeckoInitStatus.NOT_INITIALIZED -> RowTone.NEUTRAL
        },
      )
      StatusRow(
        label = stringResource(R.string.label_extension_status),
        value = state.extensionInstallStatus.label,
        tone = when (state.extensionInstallStatus) {
          ExtensionInstallStatus.INSTALLED -> RowTone.SUCCESS
          ExtensionInstallStatus.FAILED -> RowTone.ERROR
          ExtensionInstallStatus.INSTALLING -> RowTone.WARNING
          ExtensionInstallStatus.NOT_INSTALLED -> RowTone.NEUTRAL
        },
      )
      StatusRow(
        label = stringResource(R.string.label_anon_session_detected),
        value = if (state.anonymousSessionDetected) yesLabel else noLabel,
        tone = if (state.anonymousSessionDetected) RowTone.SUCCESS else RowTone.NEUTRAL,
      )
      StatusRow(
        label = stringResource(R.string.label_google_login_status),
        value = state.googleLoginStatus.label,
        tone = when (state.googleLoginStatus) {
          GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED -> RowTone.SUCCESS
          GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT,
          GoogleLoginStatus.FAILED -> RowTone.ERROR
          GoogleLoginStatus.OAUTH_IN_PROGRESS,
          GoogleLoginStatus.GOOGLE_LOGIN_PAGE_LOADED,
          GoogleLoginStatus.BTU_CALLBACK_OBSERVED -> RowTone.WARNING
          GoogleLoginStatus.BTU_LOGIN_LOADED,
          GoogleLoginStatus.IDLE -> RowTone.NEUTRAL
        },
      )
      StatusRow(
        label = stringResource(R.string.label_btu_callback_detected),
        value = if (state.btuCallbackDetected) yesLabel else noLabel,
        tone = if (state.btuCallbackDetected) RowTone.SUCCESS else RowTone.NEUTRAL,
      )
      StatusRow(
        label = stringResource(R.string.label_session_captured),
        value = if (state.sessionCaptured) yesLabel else noLabel,
        tone = if (state.sessionCaptured) RowTone.SUCCESS else RowTone.NEUTRAL,
      )
      StatusRow(
        label = stringResource(R.string.label_native_verification_status),
        value = state.verificationStatus.label,
        tone = when (state.verificationStatus) {
          VerificationStatus.AUTHENTICATED -> RowTone.SUCCESS
          VerificationStatus.NETWORK_ERROR -> RowTone.ERROR
          VerificationStatus.UNAUTHENTICATED,
          VerificationStatus.CLOUDFLARE_BLOCKED,
          VerificationStatus.UNEXPECTED_RESPONSE,
          VerificationStatus.VERIFYING -> RowTone.WARNING
          VerificationStatus.IDLE -> RowTone.NEUTRAL
        },
      )
      StatusRow(
        label = stringResource(R.string.label_last_http_status),
        value = state.lastHttpStatusCode?.toString() ?: noneLabel,
        tone = when (state.lastHttpStatusCode) {
          200 -> RowTone.SUCCESS
          null -> RowTone.NEUTRAL
          in 300..399 -> RowTone.WARNING
          else -> RowTone.ERROR
        },
      )
      StatusRow(
        label = stringResource(R.string.label_error_message),
        value = state.errorMessage ?: noneLabel,
        tone = if (state.errorMessage != null) RowTone.ERROR else RowTone.NEUTRAL,
      )
    }
  }
}

private enum class RowTone {
  NEUTRAL,
  SUCCESS,
  WARNING,
  ERROR,
}

@Composable
private fun StatusRow(
  label: String,
  value: String,
  tone: RowTone,
) {
  val valueColor = when (tone) {
    RowTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    RowTone.SUCCESS -> StatusSuccess
    RowTone.WARNING -> StatusWarning
    RowTone.ERROR -> StatusError
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .weight(1f)
        .padding(end = 12.dp),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      color = valueColor,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsCard(
  state: AuthDiagnosticState,
  onStartLogin: () -> Unit,
  onVerifySession: () -> Unit,
  onResetSession: () -> Unit,
  onRequestRemoteLogout: () -> Unit,
  onOpenBrowserModal: () -> Unit,
) {
  OutlinedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.section_controls),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
          )
        }
      }

      Button(
        onClick = onStartLogin,
        enabled = !state.isBusy,
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("start_login_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Login,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.action_start_login))
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = onVerifySession,
          enabled = !state.isBusy,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("verify_session_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.VerifiedUser,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_verify_session))
        }

        OutlinedButton(
          onClick = onResetSession,
          enabled = !state.isBusy,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("reset_session_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.RestartAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_reset_session))
        }

        OutlinedButton(
          onClick = onRequestRemoteLogout,
          enabled = !state.isBusy,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("logout_btu_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_logout_btu))
        }

        if (state.hasActiveBrowserSession && !state.isBrowserModalVisible) {
          FilledTonalButton(
            onClick = onOpenBrowserModal,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("inspect_browser_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.OpenInBrowser,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_open_browser))
          }
        }
      }
    }
  }
}

@Composable
private fun DiagnosticLogCard(
  logs: List<DiagnosticLogEntry>,
  onClearLogs: () -> Unit,
) {
  val listState = rememberLazyListState()

  LaunchedEffect(logs.size) {
    if (logs.isNotEmpty()) {
      listState.animateScrollToItem(logs.lastIndex)
    }
  }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = ConsoleSurface,
    border = BorderStroke(1.dp, ConsoleBorder),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.Terminal,
            contentDescription = null,
            tint = Color(0xFF93C5FD),
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.section_event_log),
            style = MaterialTheme.typography.titleMedium,
            color = ConsoleTextPrimary,
          )
        }

        if (logs.isNotEmpty()) {
          TextButton(
            onClick = onClearLogs,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("clear_log_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = null,
              tint = Color(0xFF94A3B8),
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.action_clear_log),
              color = Color(0xFF94A3B8),
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (logs.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .background(Color(0xFF070C16), RoundedCornerShape(10.dp))
            .padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.empty_log_message),
            style = MaterialTheme.typography.labelSmall,
            color = ConsoleTextTimestamp,
          )
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 280.dp)
            .background(Color(0xFF070C16), RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("diagnostic_log_list"),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          items(logs, key = { it.id }) { entry ->
            val msgColor = when (entry.level) {
              LogLevel.INFO -> ConsoleTextPrimary
              LogLevel.SUCCESS -> Color(0xFF34D399)
              LogLevel.WARNING -> Color(0xFFFBBF24)
              LogLevel.ERROR -> Color(0xFFF87171)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "[${entry.timestamp}] ",
                style = MaterialTheme.typography.labelSmall,
                color = ConsoleTextTimestamp,
              )
              Text(
                text = entry.message,
                style = MaterialTheme.typography.labelSmall,
                color = msgColor,
              )
            }
          }
        }
      }
    }
  }
}
