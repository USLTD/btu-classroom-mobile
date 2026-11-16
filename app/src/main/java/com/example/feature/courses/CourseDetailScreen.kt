package com.example.feature.courses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.database.CachedCourseResourceDto
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.GradeEntity
import com.example.design.components.BadgeTone
import com.example.design.components.EmptyStateCard
import com.example.design.components.StatusBadge
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
  courseWithEnrollment: CourseWithEnrollment?,
  grades: List<GradeEntity>,
  resources: List<CachedCourseResourceDto>,
  isLoading: Boolean,
  isOffline: Boolean,
  onSyncDetail: () -> Unit,
  onOpenSafeUrl: (String) -> Unit,
  onNavigateBack: () -> Unit,
) {
  LaunchedEffect(courseWithEnrollment?.course?.courseKey) {
    onSyncDetail()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.course_detail_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("course_detail_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.nav_back),
            )
          }
        },
        actions = {
          IconButton(
            onClick = onSyncDetail,
            enabled = !isLoading,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("course_detail_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = stringResource(R.string.action_refresh),
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
      Column(
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize(),
      ) {
        if (isLoading) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val course = courseWithEnrollment?.course
        if (course == null) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            EmptyStateCard(
              icon = Icons.Filled.Info,
              title = stringResource(R.string.course_detail_title),
              message = stringResource(R.string.course_detail_unavailable),
              actionLabel = stringResource(R.string.action_retry),
              onAction = onSyncDetail,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            if (isOffline) {
              item {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = stringResource(R.string.course_detail_offline_notice),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  )
                }
              }
            }

            // Title & score header
            item {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Column(
                  modifier = Modifier.padding(18.dp),
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  if (course.code.isNotBlank()) {
                    Text(
                      text = course.code,
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                  Text(
                    text = course.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )

                  val totalScore = courseWithEnrollment.enrollment?.totalScore
                  if (totalScore != null) {
                    StatusBadge(
                      label = "${stringResource(R.string.course_detail_score)}: ${formatNumber(totalScore)}",
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
                }
              }
            }

            // Overview metadata card
            item {
              OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = stringResource(R.string.course_detail_overview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                  if (course.code.isNotBlank()) {
                    DetailMetaRow(stringResource(R.string.course_detail_code), course.code)
                  }
                  if (course.semesterName.isNotBlank()) {
                    DetailMetaRow(stringResource(R.string.course_detail_semester), course.semesterName)
                  }
                  course.lecturer?.let { lec ->
                    DetailMetaRow(stringResource(R.string.course_detail_lecturer), lec)
                  }
                  course.credits?.let { cr ->
                    DetailMetaRow(stringResource(R.string.course_detail_credits), formatNumber(cr))
                  }
                  if (!course.syllabusSummary.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = course.syllabusSummary,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            // Grade breakdown components if available
            if (grades.isNotEmpty()) {
              item {
                Text(
                  text = stringResource(R.string.course_detail_grades_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
              }
              items(grades, key = { it.gradeKey }) { grade ->
                OutlinedCard(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(12.dp),
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
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                      Text(
                        text = grade.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      if (!grade.notes.isNullOrBlank()) {
                        Text(
                          text = grade.notes,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    val scoreText = when {
                      grade.score != null && grade.maxScore != null ->
                        "${formatNumber(grade.score)} / ${formatNumber(grade.maxScore)}"
                      grade.score != null -> formatNumber(grade.score)
                      grade.maxScore != null -> "— / ${formatNumber(grade.maxScore)}"
                      else -> "—"
                    }
                    Text(
                      text = scoreText,
                      style = MaterialTheme.typography.labelLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = if (grade.score != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            } else if (course.detailStatus == "UNSUPPORTED_STRUCTURE") {
              item {
                EmptyStateCard(
                  icon = Icons.Filled.Info,
                  title = stringResource(R.string.course_detail_title),
                  message = stringResource(R.string.course_detail_unavailable),
                )
              }
            }

            // Safe resources list
            if (resources.isNotEmpty()) {
              item {
                Text(
                  text = stringResource(R.string.course_detail_resources_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
              }
              items(resources, key = { it.url }) { res ->
                OutlinedCard(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSafeUrl(res.url) },
                  shape = RoundedCornerShape(12.dp),
                  colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = res.title,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                      imageVector = Icons.Filled.OpenInBrowser,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailMetaRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 12.dp),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private fun formatNumber(value: Double): String {
  return if (value % 1.0 == 0.0) {
    value.toInt().toString()
  } else {
    String.format(Locale.US, "%.1f", value)
  }
}
