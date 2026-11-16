package com.example.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.auth.AuthState
import com.example.core.auth.AuthTelemetryState
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.UserPreferences
import com.example.design.components.BadgeTone
import com.example.design.components.StatusBadge
import com.example.design.theme.LocalIsDarkTheme
import com.example.design.theme.StatusError
import com.example.design.theme.StatusErrorBgDark
import com.example.design.theme.StatusErrorBgLight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
  authState: AuthState,
  telemetry: AuthTelemetryState,
  preferences: UserPreferences,
  onSignInClick: () -> Unit,
  onResumeBrowserClick: () -> Unit,
  onOpenExternalBrowser: () -> Unit,
  onRememberLoginChanged: (Boolean) -> Unit,
  onLanguageChanged: (AppLanguage) -> Unit,
) {
  val isVerifyingLaunch = authState is AuthState.Unknown || authState is AuthState.Verifying
  val isDark = LocalIsDarkTheme.current
  val errBg = if (isDark) StatusErrorBgDark else StatusErrorBgLight
  val errFg = if (isDark) Color(0xFFFCA5A5) else StatusError

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Brand & Language bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.School,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(26.dp),
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = stringResource(R.string.target_host_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          StatusBadge(
            label = stringResource(R.string.unofficial_badge),
            icon = Icons.Filled.Info,
            tone = BadgeTone.PRIMARY,
          )
        }

        // Language quick switcher
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          LanguageChip(
            label = stringResource(R.string.language_system),
            selected = preferences.language == AppLanguage.SYSTEM,
            tag = "lang_system_chip",
            onClick = { onLanguageChanged(AppLanguage.SYSTEM) },
          )
          LanguageChip(
            label = "English",
            selected = preferences.language == AppLanguage.EN,
            tag = "lang_en_chip",
            onClick = { onLanguageChanged(AppLanguage.EN) },
          )
          LanguageChip(
            label = "ქართული",
            selected = preferences.language == AppLanguage.KA,
            tag = "lang_ka_chip",
            onClick = { onLanguageChanged(AppLanguage.KA) },
          )
        }

        // Hero value card
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.onboarding_title),
              style = MaterialTheme.typography.headlineSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = stringResource(R.string.onboarding_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Text(
              text = stringResource(R.string.onboarding_disclaimer),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
          }
        }

        // Privacy summary
        OutlinedCard(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.onboarding_privacy_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = stringResource(R.string.onboarding_privacy_body),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = stringResource(R.string.remember_login_title),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = if (preferences.rememberLogin) {
                    stringResource(R.string.remember_login_subtitle_on)
                  } else {
                    stringResource(R.string.remember_login_subtitle_off)
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = preferences.rememberLogin,
                onCheckedChange = onRememberLoginChanged,
                enabled = !isVerifyingLaunch && !telemetry.isBusy,
                modifier = Modifier.testTag("remember_login_switch"),
              )
            }
          }
        }

        AnimatedVisibility(visible = isVerifyingLaunch) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = if (authState is AuthState.Verifying) {
                  stringResource(R.string.auth_verifying_session)
                } else {
                  stringResource(R.string.auth_restoring_session)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        val errorText = when {
          authState is AuthState.Error -> authState.reason
          !telemetry.errorMessage.isNullOrBlank() -> telemetry.errorMessage
          else -> null
        }

        AnimatedVisibility(visible = errorText != null) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("onboarding_error_banner"),
            shape = RoundedCornerShape(12.dp),
            color = errBg,
            border = BorderStroke(1.dp, errFg.copy(alpha = 0.35f)),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(verticalAlignment = Alignment.Top) {
                Icon(
                  imageVector = Icons.Filled.ErrorOutline,
                  contentDescription = null,
                  tint = errFg,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = errorText.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = errFg,
                )
              }
              if (telemetry.showExternalBrowserFallback) {
                Text(
                  text = stringResource(R.string.auth_external_browser_hint),
                  style = MaterialTheme.typography.bodySmall,
                  color = errFg.copy(alpha = 0.9f),
                )
              }
            }
          }
        }

        // Primary Sign In CTA
        Button(
          onClick = onSignInClick,
          enabled = !isVerifyingLaunch && !telemetry.isBusy,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .minimumInteractiveComponentSize()
            .testTag("sign_in_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Login,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_sign_in),
            style = MaterialTheme.typography.labelLarge,
          )
        }

        if (telemetry.hasActiveBrowserSession && !telemetry.isBrowserModalVisible) {
          OutlinedButton(
            onClick = onResumeBrowserClick,
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("resume_browser_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text(stringResource(R.string.action_resume_browser))
          }
        }

        if (telemetry.showExternalBrowserFallback) {
          OutlinedButton(
            onClick = onOpenExternalBrowser,
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("external_browser_fallback_button"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.OpenInBrowser,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_external_browser))
          }
        }
      }
    }
  }
}

@Composable
private fun LanguageChip(
  label: String,
  selected: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .testTag(tag),
  )
}
