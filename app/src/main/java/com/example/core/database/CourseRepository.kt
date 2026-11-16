package com.example.core.database

import com.example.core.auth.LogLevel
import com.example.core.auth.SessionManager
import com.example.core.database.entities.CourseEntity
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.EnrollmentEntity
import com.example.core.database.entities.GradeEntity
import com.example.core.database.entities.SemesterEntity
import com.example.core.database.entities.StudentEntity
import com.example.core.database.entities.SyncMetadataEntity
import com.example.core.network.BtuHttpClient
import com.example.core.network.BtuRoutes
import com.example.core.network.HttpFetchResult
import com.example.core.parsing.BtuModuleId
import com.example.core.parsing.CourseDetailParser
import com.example.core.parsing.ModuleCompatibilityStatus
import com.example.core.parsing.PageCompatibilityRegistry
import com.example.core.parsing.ParseResult
import com.example.core.parsing.ParsedCourseResource
import com.example.core.parsing.SelectedCoursesPageData
import com.example.core.parsing.SelectedCoursesParser
import com.example.core.preferences.UserPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface SyncOutcome {
  data object Success : SyncOutcome
  data object Empty : SyncOutcome
  data object SessionExpired : SyncOutcome
  data class OfflineOrFailed(val message: String, val isOffline: Boolean) : SyncOutcome
  data class UnsupportedStructure(val reason: String) : SyncOutcome
}

@Serializable
data class CachedCourseResourceDto(
  val title: String,
  val url: String,
)

/**
 * Offline-first repository backed by Room as the single source of truth for UI screens.
 */
class CourseRepository(
  private val database: BtuDatabase,
  private val httpClient: BtuHttpClient,
  private val sessionManager: SessionManager,
  private val preferencesStore: UserPreferencesStore,
  private val compatibilityRegistry: PageCompatibilityRegistry,
  private val selectedCoursesParser: SelectedCoursesParser = SelectedCoursesParser(),
  private val courseDetailParser: CourseDetailParser = CourseDetailParser(),
) {

  private val studentDao = database.studentDao()
  private val courseDao = database.courseDao()
  private val syncMetadataDao = database.syncMetadataDao()

  private val json = Json { ignoreUnknownKeys = true }

  fun observeStudent(studentKey: String = UserPreferencesStore.DEFAULT_STUDENT_KEY): Flow<StudentEntity?> =
    studentDao.observeStudent(studentKey)

  fun observeSemesters(studentKey: String = UserPreferencesStore.DEFAULT_STUDENT_KEY): Flow<List<SemesterEntity>> =
    courseDao.observeSemesters(studentKey)

  fun observeCourses(studentKey: String = UserPreferencesStore.DEFAULT_STUDENT_KEY): Flow<List<CourseWithEnrollment>> =
    courseDao.observeCoursesWithEnrollment(studentKey)

  fun observeCourseDetail(courseKey: String): Flow<CourseWithEnrollment?> =
    courseDao.observeCourseWithEnrollment(courseKey)

  fun observeCourseGrades(courseKey: String): Flow<List<GradeEntity>> =
    courseDao.observeGradesForCourse(courseKey)

  fun observeCoursesSyncMetadata(): Flow<SyncMetadataEntity?> =
    syncMetadataDao.observeModule(BtuModuleId.SELECTED_COURSES.id)

  suspend fun hasCachedCourses(): Boolean = courseDao.countAllCourses() > 0

  suspend fun refreshSelectedCourses(preFetchedHtml: String? = null): SyncOutcome {
    val now = System.currentTimeMillis()
    val htmlToParse = if (!preFetchedHtml.isNullOrBlank()) {
      preFetchedHtml
    } else {
      val sessionCookie = sessionManager.currentSessionValue()
      if (sessionCookie.isNullOrBlank()) {
        syncMetadataDao.upsert(
          SyncMetadataEntity(
            moduleId = BtuModuleId.SELECTED_COURSES.id,
            lastAttemptAtMillis = now,
            lastSuccessAtMillis = null,
            outcomeStatus = "SESSION_EXPIRED",
            message = "No active session",
          ),
        )
        return SyncOutcome.SessionExpired
      }

      when (val fetch = httpClient.fetchHtml(BtuRoutes.STUDENT_COURSES, sessionCookie)) {
        is HttpFetchResult.SuccessHtml -> fetch.html
        is HttpFetchResult.SessionExpired -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.SESSION_EXPIRED,
            fetch.reason,
            now,
          )
          recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, "SESSION_EXPIRED", fetch.reason)
          sessionManager.handleServerSessionExpired(fetch.reason)
          return SyncOutcome.SessionExpired
        }
        is HttpFetchResult.CloudflareBlocked -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
            fetch.reason,
            now,
          )
          recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, "CLOUDFLARE_BLOCKED", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
        }
        is HttpFetchResult.TemporarilyUnavailable -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE,
            fetch.reason,
            now,
          )
          recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, "TEMPORARILY_UNAVAILABLE", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
        }
        is HttpFetchResult.NetworkError -> {
          sessionManager.markOfflineFallback(true)
          recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, "OFFLINE", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = true)
        }
        is HttpFetchResult.UnexpectedResponse -> {
          compatibilityRegistry.update(
            BtuModuleId.SELECTED_COURSES,
            ModuleCompatibilityStatus.UNEXPECTED_HTML,
            fetch.reason,
            now,
          )
          recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, "UNEXPECTED_RESPONSE", fetch.reason)
          return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
        }
      }
    }

    sessionManager.markOfflineFallback(false)
    val parseResult = selectedCoursesParser.parse(htmlToParse)
    compatibilityRegistry.recordResult(BtuModuleId.SELECTED_COURSES, parseResult, now)

    return when (parseResult) {
      is ParseResult.Success -> {
        persistSelectedCourses(parseResult.data, now)
        syncMetadataDao.upsert(
          SyncMetadataEntity(
            moduleId = BtuModuleId.SELECTED_COURSES.id,
            lastAttemptAtMillis = now,
            lastSuccessAtMillis = now,
            outcomeStatus = "SUCCESS",
            message = "${parseResult.data.courses.size} courses synchronized",
          ),
        )
        sessionManager.appendLog(
          "Selected courses synced (${parseResult.data.courses.size} course(s))",
          LogLevel.SUCCESS,
        )
        SyncOutcome.Success
      }
      ParseResult.Empty -> {
        val studentKey = preferencesStore.readLastStudentKey()
        courseDao.replaceSelectedCoursesSnapshot(studentKey, emptyList(), emptyList(), emptyList())
        syncMetadataDao.upsert(
          SyncMetadataEntity(
            moduleId = BtuModuleId.SELECTED_COURSES.id,
            lastAttemptAtMillis = now,
            lastSuccessAtMillis = now,
            outcomeStatus = "EMPTY",
            message = "No selected courses returned",
          ),
        )
        SyncOutcome.Empty
      }
      ParseResult.SessionExpired -> {
        recordFailureSyncMeta(BtuModuleId.SELECTED_COURSES.id, now, "SESSION_EXPIRED", "Login page returned")
        sessionManager.handleServerSessionExpired("Selected courses endpoint returned login markup")
        SyncOutcome.SessionExpired
      }
      ParseResult.TemporarilyUnavailable -> {
        recordFailureSyncMeta(
          BtuModuleId.SELECTED_COURSES.id,
          now,
          "TEMPORARILY_UNAVAILABLE",
          "Portal reported module unavailable",
        )
        SyncOutcome.OfflineOrFailed("Selected courses temporarily unavailable", isOffline = false)
      }
      is ParseResult.UnsupportedStructure -> {
        recordFailureSyncMeta(
          BtuModuleId.SELECTED_COURSES.id,
          now,
          "UNSUPPORTED_STRUCTURE",
          parseResult.reason,
        )
        sessionManager.appendLog("Selected courses parser mismatch: ${parseResult.reason}", LogLevel.WARNING)
        SyncOutcome.UnsupportedStructure(parseResult.reason)
      }
    }
  }

  suspend fun refreshCourseDetail(
    courseKey: String,
    courseId: String,
    detailPath: String?,
  ): SyncOutcome {
    val now = System.currentTimeMillis()
    val sessionCookie = sessionManager.currentSessionValue()
      ?: return SyncOutcome.SessionExpired

    val targetPath = detailPath?.ifBlank { null } ?: BtuRoutes.courseDetailPath(courseId)
    val html = when (val fetch = httpClient.fetchHtml(targetPath, sessionCookie)) {
      is HttpFetchResult.SuccessHtml -> fetch.html
      is HttpFetchResult.SessionExpired -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.SESSION_EXPIRED, fetch.reason, now)
        sessionManager.handleServerSessionExpired(fetch.reason)
        return SyncOutcome.SessionExpired
      }
      is HttpFetchResult.NetworkError -> {
        sessionManager.markOfflineFallback(true)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = true)
      }
      is HttpFetchResult.CloudflareBlocked -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE, fetch.reason, now)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
      }
      is HttpFetchResult.TemporarilyUnavailable -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.TEMPORARILY_UNAVAILABLE, fetch.reason, now)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
      }
      is HttpFetchResult.UnexpectedResponse -> {
        compatibilityRegistry.update(BtuModuleId.COURSE_DETAILS, ModuleCompatibilityStatus.UNEXPECTED_HTML, fetch.reason, now)
        return SyncOutcome.OfflineOrFailed(fetch.reason, isOffline = false)
      }
    }

    sessionManager.markOfflineFallback(false)
    val parsed = courseDetailParser.parse(html)
    compatibilityRegistry.recordResult(BtuModuleId.COURSE_DETAILS, parsed, now)

    val existing = database.courseDao().getCoursesSnapshot(preferencesStore.readLastStudentKey())
      .firstOrNull { it.courseKey == courseKey }

    return when (parsed) {
      is ParseResult.Success -> {
        val detail = parsed.data
        val studentKey = existing?.studentKey ?: preferencesStore.readLastStudentKey()
        val semesterKey = existing?.semesterKey ?: "${studentKey}::default"
        val updatedCourse = CourseEntity(
          courseKey = courseKey,
          courseId = existing?.courseId ?: courseId,
          studentKey = studentKey,
          semesterKey = semesterKey,
          semesterName = detail.semesterName ?: existing?.semesterName ?: SelectedCoursesParser.DEFAULT_SEMESTER_TITLE,
          code = detail.code ?: existing?.code.orEmpty(),
          title = detail.title.ifBlank { existing?.title.orEmpty() },
          credits = detail.credits ?: existing?.credits,
          minCredit = detail.minCredit ?: existing?.minCredit,
          lecturer = detail.lecturer ?: existing?.lecturer,
          syllabusSummary = detail.syllabusSummary ?: existing?.syllabusSummary,
          detailPath = existing?.detailPath ?: targetPath,
          resourcesJson = encodeResources(detail.resources),
          detailSyncedAtMillis = now,
          detailStatus = "COMPATIBLE",
        )
        val updatedEnrollment = if (detail.totalScore != null) {
          EnrollmentEntity(
            enrollmentKey = courseKey,
            studentKey = studentKey,
            semesterKey = semesterKey,
            courseId = updatedCourse.courseId,
            totalScore = detail.totalScore,
            statusLabel = null,
            updatedAtMillis = now,
          )
        } else {
          null
        }
        val gradeEntities = detail.gradeComponents.mapIndexed { idx, comp ->
          GradeEntity(
            gradeKey = "$courseKey::$idx",
            courseKey = courseKey,
            studentKey = studentKey,
            semesterKey = semesterKey,
            courseId = updatedCourse.courseId,
            categoryName = comp.categoryName,
            score = comp.score,
            maxScore = comp.maxScore,
            notes = comp.notes,
            sortOrder = idx,
          )
        }
        courseDao.updateCourseDetailAndGrades(updatedCourse, updatedEnrollment, gradeEntities)
        SyncOutcome.Success
      }
      ParseResult.Empty -> SyncOutcome.Empty
      ParseResult.SessionExpired -> {
        sessionManager.handleServerSessionExpired("Course detail returned login page")
        SyncOutcome.SessionExpired
      }
      ParseResult.TemporarilyUnavailable ->
        SyncOutcome.OfflineOrFailed("Course detail temporarily unavailable", isOffline = false)
      is ParseResult.UnsupportedStructure -> {
        if (existing != null) {
          courseDao.insertCourses(
            listOf(
              existing.copy(
                detailSyncedAtMillis = now,
                detailStatus = "UNSUPPORTED_STRUCTURE",
              ),
            ),
          )
        }
        SyncOutcome.UnsupportedStructure(parsed.reason)
      }
    }
  }

  fun decodeResources(resourcesJson: String?): List<CachedCourseResourceDto> {
    if (resourcesJson.isNullOrBlank()) return emptyList()
    return try {
      json.decodeFromString<List<CachedCourseResourceDto>>(resourcesJson)
    } catch (_: Throwable) {
      emptyList()
    }
  }

  suspend fun clearAllAcademicData() {
    courseDao.deleteAllAcademicData()
    studentDao.deleteAllStudents()
    syncMetadataDao.clearAll()
    compatibilityRegistry.resetAll()
  }

  private suspend fun persistSelectedCourses(data: SelectedCoursesPageData, now: Long) {
    val studentKey = preferencesStore.readLastStudentKey()
    val currentSemRawKey = data.semesters.firstOrNull { it.isCurrent }?.semesterKey

    data.studentSummary?.let { summary ->
      studentDao.upsertStudent(
        StudentEntity(
          studentKey = studentKey,
          displayName = summary.displayName,
          programName = summary.programName,
          activeSemesterKey = currentSemRawKey?.let { "$studentKey::$it" },
          updatedAtMillis = now,
        ),
      )
    }

    val semesterEntities = data.semesters.map { sem ->
      SemesterEntity(
        semesterKey = "$studentKey::${sem.semesterKey}",
        rawSemesterKey = sem.semesterKey,
        studentKey = studentKey,
        title = sem.title,
        sortOrder = sem.sortOrder,
        isCurrent = sem.isCurrent,
      )
    }

    val courseEntities = data.courses.map { item ->
      val semComposite = "$studentKey::${item.semesterKey}"
      val courseComposite = "$semComposite::${item.courseId}"
      CourseEntity(
        courseKey = courseComposite,
        courseId = item.courseId,
        studentKey = studentKey,
        semesterKey = semComposite,
        semesterName = item.semesterName,
        code = item.code,
        title = item.title,
        credits = item.credits,
        minCredit = item.minCredit,
        lecturer = null,
        syllabusSummary = null,
        detailPath = item.detailPath ?: BtuRoutes.courseDetailPath(item.courseId),
        resourcesJson = null,
        detailSyncedAtMillis = null,
        detailStatus = null,
      )
    }

    val enrollmentEntities = data.courses.map { item ->
      val semComposite = "$studentKey::${item.semesterKey}"
      val courseComposite = "$semComposite::${item.courseId}"
      EnrollmentEntity(
        enrollmentKey = courseComposite,
        studentKey = studentKey,
        semesterKey = semComposite,
        courseId = item.courseId,
        totalScore = item.score,
        statusLabel = item.statusLabel,
        updatedAtMillis = now,
      )
    }

    courseDao.replaceSelectedCoursesSnapshot(
      studentKey = studentKey,
      semesters = semesterEntities,
      courses = courseEntities,
      enrollments = enrollmentEntities,
    )
  }

  private suspend fun recordFailureSyncMeta(
    moduleId: String,
    now: Long,
    outcomeStatus: String,
    message: String?,
  ) {
    // Preserve any existing lastSuccessAtMillis timestamp rather than overwriting with null
    val existingSuccess = findLastSuccessTimestamp(moduleId)
    syncMetadataDao.upsert(
      SyncMetadataEntity(
        moduleId = moduleId,
        lastAttemptAtMillis = now,
        lastSuccessAtMillis = existingSuccess,
        outcomeStatus = outcomeStatus,
        message = message,
      ),
    )
  }

  private suspend fun findLastSuccessTimestamp(moduleId: String): Long? {
    return syncMetadataDao.getModule(moduleId)?.lastSuccessAtMillis
  }

  private fun encodeResources(resources: List<ParsedCourseResource>): String? {
    if (resources.isEmpty()) return null
    return json.encodeToString(resources.map { CachedCourseResourceDto(it.title, it.url) })
  }
}
