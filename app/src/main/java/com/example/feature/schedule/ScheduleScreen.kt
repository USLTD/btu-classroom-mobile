package com.example.feature.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.network.BtuRoutes
import com.example.design.components.EmptyStateCard

@Composable
fun ScheduleScreen(
  onOpenPortalSchedule: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 560.dp)
        .testTag("schedule_placeholder_container"),
    ) {
      EmptyStateCard(
        icon = Icons.Filled.CalendarMonth,
        title = stringResource(R.string.schedule_placeholder_title),
        message = stringResource(R.string.schedule_placeholder_body),
        actionLabel = stringResource(R.string.schedule_placeholder_action),
        onAction = { onOpenPortalSchedule(BtuRoutes.scheduleUrl()) },
      )
    }
  }
}
