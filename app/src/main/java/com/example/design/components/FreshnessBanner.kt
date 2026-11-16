package com.example.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.database.entities.SyncMetadataEntity

@Composable
fun FreshnessBanner(
  syncMetadata: SyncMetadataEntity?,
  isSyncing: Boolean,
  isOffline: Boolean,
  onRefresh: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val text = when {
    isSyncing -> stringResource(R.string.status_syncing)
    isOffline -> stringResource(R.string.freshness_offline_cached)
    syncMetadata?.lastSuccessAtMillis != null -> formatRelativeFreshness(syncMetadata.lastSuccessAtMillis)
    else -> stringResource(R.string.freshness_never_synced)
  }

  val containerColor = if (isOffline) {
    MaterialTheme.colorScheme.secondaryContainer
  } else {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
  }
  val contentColor = if (isOffline) {
    MaterialTheme.colorScheme.onSecondaryContainer
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("freshness_banner"),
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = if (onRefresh != null) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (isSyncing) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
          )
        } else {
          Icon(
            imageVector = when {
              isOffline -> Icons.Filled.CloudOff
              syncMetadata?.lastSuccessAtMillis != null -> Icons.Filled.CloudDone
              else -> Icons.Filled.Sync
            },
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = text,
          style = MaterialTheme.typography.labelSmall,
          color = contentColor,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (onRefresh != null) {
        IconButton(
          onClick = onRefresh,
          enabled = !isSyncing,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("freshness_refresh_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.action_refresh),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun formatRelativeFreshness(timestampMillis: Long): String {
  val diffMinutes = ((System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)) / 60_000L
  return when {
    diffMinutes < 1L -> stringResource(R.string.freshness_updated_just_now)
    diffMinutes < 60L -> stringResource(R.string.freshness_updated_minutes, diffMinutes.toInt())
    diffMinutes < 1440L -> stringResource(R.string.freshness_updated_hours, (diffMinutes / 60L).toInt())
    else -> stringResource(R.string.freshness_updated_days, (diffMinutes / 1440L).toInt())
  }
}
