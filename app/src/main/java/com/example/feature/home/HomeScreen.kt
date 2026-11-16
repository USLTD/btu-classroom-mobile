package com.example.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.SemesterEntity
import com.example.core.database.entities.StudentEntity
import com.example.core.database.entities.SyncMetadataEntity
import com.example.design.components.BadgeTone
import com.example.design.components.FreshnessBanner
import com.example.design.components.StatusBadge
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
  student: StudentEntity?,
  semesters: List<SemesterEntity>,
  courses: List<CourseWithEnrollment>,
  syncMetadata: SyncMetadataEntity?,
  isSyncing: Boolean,
  isOffline: Boolean,
  onRefresh: () -> Unit,
  onOpenCoursesTab: () -> Unit,
  onOpenCourseDetail: (courseKey: String, courseId: String) -> Unit,
  onOpenScheduleTab: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeSemester = semesters.firstOrNull { it.isCurrent } ?: semesters.firstOrNull()
  val activeCourses = if (activeSemester != null) {
    courses.filter { it.course.semesterKey == activeSemester.semesterKey }
  } else {
    courses
  }
  val displayCourses = activeCourses.ifEmpty { courses }

  val totalCredits = displayCourses.mapNotNull { it.course.credits }.sum()
  val gradedScores = displayCourses.mapNotNull { it.enrollment?.totalScore }
  val averageGraded = if (gradedScores.isNotEmpty()) gradedScores.average() else null

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 720.dp)
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Spacer(modifier = Modifier.height(6.dp))
        // Student / Portal header card
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = Icons.Filled.School,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = student?.displayName ?: stringResource(R.string.home_greeting_default),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              StatusBadge(
                label = stringResource(R.string.unofficial_badge),
                icon = Icons.Filled.Info,
                tone = BadgeTone.PRIMARY,
              )
            }

            if (activeSemester != null) {
              Text(
                text = activeSemester.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
              )
            }
            if (!student?.programName.isNullOrBlank()) {
              Text(
                text = student!!.programName!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
              )
            }
          }
        }
      }

      item {
        FreshnessBanner(
          syncMetadata = syncMetadata,
          isSyncing = isSyncing,
          isOffline = isOffline,
          onRefresh = onRefresh,
        )
      }

      // Selected Courses Overview Card (independent module)
      item {
        OutlinedCard(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_courses_overview_card"),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Filled.Book,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = stringResource(R.string.home_courses_summary_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
              FilledTonalButton(
                onClick = onOpenCoursesTab,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("home_view_courses_button"),
              ) {
                Text(stringResource(R.string.home_view_all_courses))
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              }
            }

            if (displayCourses.isEmpty()) {
              Text(
                text = stringResource(R.string.home_no_courses_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                StatusBadge(
                  label = stringResource(R.string.home_enrolled_count, displayCourses.size),
                  icon = Icons.Filled.Book,
                  tone = BadgeTone.PRIMARY,
                )
                if (totalCredits > 0.0) {
                  StatusBadge(
                    label = stringResource(R.string.home_total_credits, formatNumber(totalCredits)),
                    icon = Icons.Filled.School,
                    tone = BadgeTone.NEUTRAL,
                  )
                }
                StatusBadge(
                  label = stringResource(R.string.home_graded_count, gradedScores.size),
                  icon = if (gradedScores.isNotEmpty()) Icons.Filled.CheckCircle else Icons.Filled.HourglassEmpty,
                  tone = if (gradedScores.isNotEmpty()) BadgeTone.SUCCESS else BadgeTone.NEUTRAL,
                )
                if (averageGraded != null) {
                  StatusBadge(
                    label = stringResource(R.string.home_average_score, formatNumber(averageGraded)),
                    icon = Icons.Filled.CheckCircle,
                    tone = BadgeTone.SUCCESS,
                  )
                }
              }
            }
          }
        }
      }

      // Quick preview of courses
      if (displayCourses.isNotEmpty()) {
        item {
          Text(
            text = stringResource(R.string.home_recent_courses_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
        items(displayCourses.take(4), key = { it.course.courseKey }) { item ->
          OutlinedCard(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenCourseDetail(item.course.courseKey, item.course.courseId) }
              .testTag("home_course_preview_${item.course.courseId}"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                if (item.course.code.isNotBlank()) {
                  Text(
                    text = item.course.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Text(
                  text = item.course.title,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              val score = item.enrollment?.totalScore
              Text(
                text = if (score != null) {
                  stringResource(R.string.course_score_value, formatNumber(score))
                } else {
                  "—"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (score != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Independent Schedule preview module (honest Phase 3 placeholder)
      item {
        OutlinedCard(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenScheduleTab)
            .testTag("home_schedule_preview_card"),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.home_schedule_preview_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(
              text = stringResource(R.string.home_schedule_preview_body),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
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
