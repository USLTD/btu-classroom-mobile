package com.example.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.core.preferences.AppLanguage
import com.example.core.preferences.AppThemeMode
import com.example.core.preferences.UserPreferences

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  onLanguageChanged: (AppLanguage) -> Unit,
  onThemeModeChanged: (AppThemeMode) -> Unit,
  onDynamicColorChanged: (Boolean) -> Unit,
  onRememberLoginChanged: (Boolean) -> Unit,
  onRequestSignOut: () -> Unit,
  onRequestClearLocalData: () -> Unit,
  onNavigateBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.nav_back),
            )
          }
        },
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 680.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Preferences section
        item {
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
              Text(
                text = stringResource(R.string.section_preferences),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

              Text(
                text = stringResource(R.string.setting_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilterChip(
                  selected = preferences.language == AppLanguage.SYSTEM,
                  onClick = { onLanguageChanged(AppLanguage.SYSTEM) },
                  label = { Text(stringResource(R.string.language_system)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_lang_system"),
                )
                FilterChip(
                  selected = preferences.language == AppLanguage.EN,
                  onClick = { onLanguageChanged(AppLanguage.EN) },
                  label = { Text(stringResource(R.string.language_english)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_lang_en"),
                )
                FilterChip(
                  selected = preferences.language == AppLanguage.KA,
                  onClick = { onLanguageChanged(AppLanguage.KA) },
                  label = { Text(stringResource(R.string.language_georgian)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_lang_ka"),
                )
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

              Text(
                text = stringResource(R.string.setting_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                FilterChip(
                  selected = preferences.themeMode == AppThemeMode.SYSTEM,
                  onClick = { onThemeModeChanged(AppThemeMode.SYSTEM) },
                  label = { Text(stringResource(R.string.theme_system)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_theme_system"),
                )
                FilterChip(
                  selected = preferences.themeMode == AppThemeMode.LIGHT,
                  onClick = { onThemeModeChanged(AppThemeMode.LIGHT) },
                  label = { Text(stringResource(R.string.theme_light)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_theme_light"),
                )
                FilterChip(
                  selected = preferences.themeMode == AppThemeMode.DARK,
                  onClick = { onThemeModeChanged(AppThemeMode.DARK) },
                  label = { Text(stringResource(R.string.theme_dark)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_theme_dark"),
                )
              }

              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                      text = stringResource(R.string.setting_dynamic_color),
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = stringResource(R.string.setting_dynamic_color_desc),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Switch(
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                    modifier = Modifier.testTag("settings_dynamic_color_switch"),
                  )
                }
              }
            }
          }
        }

        // Session & Storage section
        item {
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
              Text(
                text = stringResource(R.string.section_session_security),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                  Text(
                    text = stringResource(R.string.setting_session_mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = if (preferences.rememberLogin) {
                      stringResource(R.string.session_mode_remember)
                    } else {
                      stringResource(R.string.session_mode_memory)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Switch(
                  checked = preferences.rememberLogin,
                  onCheckedChange = onRememberLoginChanged,
                  modifier = Modifier.testTag("settings_remember_login_switch"),
                )
              }

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = onRequestSignOut,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_sign_out_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.action_sign_out))
                }

                OutlinedButton(
                  onClick = onRequestClearLocalData,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_clear_data_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = stringResource(R.string.action_clear_local_data),
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }

        // About section
        item {
          OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = stringResource(R.string.section_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
