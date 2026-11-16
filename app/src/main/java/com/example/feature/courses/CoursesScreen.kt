package com.example.feature.courses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.SemesterEntity
import com.example.core.database.entities.SyncMetadataEntity
import com.example.core.parsing.ModuleCompatibilityEntry
import com.example.core.parsing.ModuleCompatibilityStatus
import com.example.design.components.BadgeTone
import com.example.design.components.EmptyStateCard
import com.example.design.components.FreshnessBanner
import com.example.design.components.StatusBadge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoursesScreen(
  semesters: List<SemesterEntity>,
  courses: List<CourseWithEnrollment>,
  syncMetadata: SyncMetadataEntity?,
  compatibility: ModuleCompatibilityEntry?,
  isSyncing: Boolean,
  isOffline: Boolean,
  onRefresh: () -> Unit,
  onCourseClick: (courseKey: String, courseId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSemesterKey by rememberSaveable { mutableStateOf<String?>(null) }

  val filteredCourses = if (selectedSemesterKey == null) {
    courses
  } else {
    courses.filter { it.course.semesterKey == selectedSemesterKey }
  }

  PullToRefreshBox(
    isRefreshing = isSyncing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize()
          .testTag("courses_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          FreshnessBanner(
            syncMetadata = syncMetadata,
            isSyncing = isSyncing,
            isOffline = isOffline,
            onRefresh = onRefresh,
          )
        }

        // Compatibility notice when parser/module reports structural mismatch or temporary lock
        val showCompatNotice = compatibility != null &&
          compatibility.status in setOf(
            ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
            ModuleCompatibilityStatus.PARSER_UPDATE_REQUIRED,
            ModuleCompatibilityStatus.UNEXPECTED_HTML,
          )

        item {
          AnimatedVisibility(visible = showCompatNotice) {
            EmptyStateCard(
              icon = Icons.Filled.WarningAmber,
              title = if (compatibility?.status == ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE) {
                stringResource(R.string.courses_unavailable_title)
              } else {
                stringResource(R.string.courses_unsupported_title)
              },
              message = if (compatibility?.status == ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE) {
                stringResource(R.string.courses_unavailable_body)
              } else {
                stringResource(R.string.courses_unsupported_body)
              },
              actionLabel = stringResource(R.string.action_retry),
              onAction = onRefresh,
            )
          }
        }

        // Semester selector chips when multiple semesters exist
        if (semesters.size > 1) {
          item {
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              item {
                FilterChip(
                  selected = selectedSemesterKey == null,
                  onClick = { selectedSemesterKey = null },
                  label = { Text(stringResource(R.string.courses_filter_all)) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("semester_chip_all"),
                )
              }
              items(semesters, key = { it.semesterKey }) { semester ->
                FilterChip(
                  selected = selectedSemesterKey == semester.semesterKey,
                  onClick = { selectedSemesterKey = semester.semesterKey },
                  label = { Text(semester.title) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("semester_chip_${semester.rawSemesterKey}"),
                )
              }
            }
          }
        }

        if (filteredCourses.isEmpty() && !showCompatNotice) {
          item {
            EmptyStateCard(
              icon = Icons.Filled.Book,
              title = stringResource(R.string.courses_empty_title),
              message = stringResource(R.string.courses_empty_body),
              actionLabel = stringResource(R.string.action_refresh),
              onAction = onRefresh,
            )
          }
        } else {
          items(filteredCourses, key = { it.course.courseKey }) { item ->
            CourseCard(
              item = item,
              showSemesterLabel = semesters.size > 1 && selectedSemesterKey == null,
              onClick = { onCourseClick(item.course.courseKey, item.course.courseId) },
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseCard(
  item: CourseWithEnrollment,
  showSemesterLabel: Boolean,
  onClick: () -> Unit,
) {
  val course = item.course
  val score = item.enrollment?.totalScore

  OutlinedCard(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("course_card_${course.courseId}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
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
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          if (course.code.isNotBlank()) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Text(
                text = course.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
          }
          // Properly wraps long Georgian or English course names without harsh clipping
          Text(
            text = course.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (showSemesterLabel && course.semesterName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = course.semesterName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = stringResource(R.string.course_detail_title),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp),
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (score != null) {
          StatusBadge(
            label = stringResource(R.string.course_score_value, formatNumber(score)),
            icon = Icons.Filled.CheckCircle,
            tone = BadgeTone.SUCCESS,
          )
        } else {
          StatusBadge(
            label = stringResource(R.string.course_score_ungraded),
            icon = Icons.Filled.HourglassEmpty,
            tone = BadgeTone.NEUTRAL,
          )
        }

        course.credits?.let { cr ->
          StatusBadge(
            label = stringResource(R.string.course_credits_label, formatNumber(cr)),
            icon = Icons.Filled.School,
            tone = BadgeTone.PRIMARY,
          )
        }

        course.minCredit?.let { minCr ->
          StatusBadge(
            label = stringResource(R.string.course_min_credit_label, formatNumber(minCr)),
            icon = Icons.Filled.Book,
            tone = BadgeTone.NEUTRAL,
          )
        }
      }
    }
  }
}

private fun formatNumber(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}
