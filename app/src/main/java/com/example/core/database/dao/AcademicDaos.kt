package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.core.database.entities.CourseEntity
import com.example.core.database.entities.CourseWithEnrollment
import com.example.core.database.entities.EnrollmentEntity
import com.example.core.database.entities.GradeEntity
import com.example.core.database.entities.SemesterEntity
import com.example.core.database.entities.StudentEntity
import com.example.core.database.entities.SyncMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
  @Query("SELECT * FROM students WHERE studentKey = :studentKey LIMIT 1")
  fun observeStudent(studentKey: String): Flow<StudentEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertStudent(student: StudentEntity)

  @Query("DELETE FROM students")
  suspend fun deleteAllStudents()
}

@Dao
interface CourseDao {
  @Query("SELECT * FROM semesters WHERE studentKey = :studentKey ORDER BY sortOrder ASC")
  fun observeSemesters(studentKey: String): Flow<List<SemesterEntity>>

  @Transaction
  @Query("SELECT * FROM courses WHERE studentKey = :studentKey ORDER BY semesterKey ASC, title ASC")
  fun observeCoursesWithEnrollment(studentKey: String): Flow<List<CourseWithEnrollment>>

  @Transaction
  @Query("SELECT * FROM courses WHERE courseKey = :courseKey LIMIT 1")
  fun observeCourseWithEnrollment(courseKey: String): Flow<CourseWithEnrollment?>

  @Query("SELECT * FROM grades WHERE courseKey = :courseKey ORDER BY sortOrder ASC")
  fun observeGradesForCourse(courseKey: String): Flow<List<GradeEntity>>

  @Query("SELECT * FROM courses WHERE studentKey = :studentKey")
  suspend fun getCoursesSnapshot(studentKey: String): List<CourseEntity>

  @Query("SELECT COUNT(*) FROM courses")
  suspend fun countAllCourses(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSemesters(semesters: List<SemesterEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCourses(courses: List<CourseEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertEnrollments(enrollments: List<EnrollmentEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGrades(grades: List<GradeEntity>)

  @Query("DELETE FROM semesters WHERE studentKey = :studentKey")
  suspend fun deleteSemestersForStudent(studentKey: String)

  @Query("DELETE FROM courses WHERE studentKey = :studentKey")
  suspend fun deleteCoursesForStudent(studentKey: String)

  @Query("DELETE FROM enrollments WHERE studentKey = :studentKey")
  suspend fun deleteEnrollmentsForStudent(studentKey: String)

  @Query("DELETE FROM grades WHERE courseKey = :courseKey")
  suspend fun deleteGradesForCourse(courseKey: String)

  @Query("DELETE FROM grades")
  suspend fun deleteAllGrades()

  @Query("DELETE FROM enrollments")
  suspend fun deleteAllEnrollments()

  @Query("DELETE FROM courses")
  suspend fun deleteAllCourses()

  @Query("DELETE FROM semesters")
  suspend fun deleteAllSemesters()

  @Transaction
  suspend fun replaceSelectedCoursesSnapshot(
    studentKey: String,
    semesters: List<SemesterEntity>,
    courses: List<CourseEntity>,
    enrollments: List<EnrollmentEntity>,
  ) {
    // Preserve detail metadata already cached for matching courseKey entries
    val existingByKey = getCoursesSnapshot(studentKey).associateBy { it.courseKey }
    val mergedCourses = courses.map { incoming ->
      val prev = existingByKey[incoming.courseKey]
      if (prev != null) {
        incoming.copy(
          lecturer = incoming.lecturer ?: prev.lecturer,
          syllabusSummary = incoming.syllabusSummary ?: prev.syllabusSummary,
          resourcesJson = incoming.resourcesJson ?: prev.resourcesJson,
          detailSyncedAtMillis = incoming.detailSyncedAtMillis ?: prev.detailSyncedAtMillis,
          detailStatus = incoming.detailStatus ?: prev.detailStatus,
        )
      } else {
        incoming
      }
    }

    deleteEnrollmentsForStudent(studentKey)
    deleteCoursesForStudent(studentKey)
    deleteSemestersForStudent(studentKey)

    if (semesters.isNotEmpty()) {
      insertSemesters(semesters)
    }
    if (mergedCourses.isNotEmpty()) {
      insertCourses(mergedCourses)
    }
    if (enrollments.isNotEmpty()) {
      insertEnrollments(enrollments)
    }
  }

  @Transaction
  suspend fun updateCourseDetailAndGrades(
    course: CourseEntity,
    enrollment: EnrollmentEntity?,
    grades: List<GradeEntity>,
  ) {
    insertCourses(listOf(course))
    if (enrollment != null) {
      insertEnrollments(listOf(enrollment))
    }
    deleteGradesForCourse(course.courseKey)
    if (grades.isNotEmpty()) {
      insertGrades(grades)
    }
  }

  @Transaction
  suspend fun deleteAllAcademicData() {
    deleteAllGrades()
    deleteAllEnrollments()
    deleteAllCourses()
    deleteAllSemesters()
  }
}

@Dao
interface SyncMetadataDao {
  @Query("SELECT * FROM sync_metadata")
  fun observeAll(): Flow<List<SyncMetadataEntity>>

  @Query("SELECT * FROM sync_metadata WHERE moduleId = :moduleId LIMIT 1")
  fun observeModule(moduleId: String): Flow<SyncMetadataEntity?>

  @Query("SELECT * FROM sync_metadata WHERE moduleId = :moduleId LIMIT 1")
  suspend fun getModule(moduleId: String): SyncMetadataEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(metadata: SyncMetadataEntity)

  @Query("DELETE FROM sync_metadata")
  suspend fun clearAll()
}
