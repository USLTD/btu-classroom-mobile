package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.database.dao.CourseDao
import com.example.core.database.dao.StudentDao
import com.example.core.database.dao.SyncMetadataDao
import com.example.core.database.entities.CourseEntity
import com.example.core.database.entities.EnrollmentEntity
import com.example.core.database.entities.GradeEntity
import com.example.core.database.entities.SemesterEntity
import com.example.core.database.entities.StudentEntity
import com.example.core.database.entities.SyncMetadataEntity

@Database(
  entities = [
    StudentEntity::class,
    SemesterEntity::class,
    CourseEntity::class,
    EnrollmentEntity::class,
    GradeEntity::class,
    SyncMetadataEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class BtuDatabase : RoomDatabase() {
  abstract fun studentDao(): StudentDao
  abstract fun courseDao(): CourseDao
  abstract fun syncMetadataDao(): SyncMetadataDao

  companion object {
    @Volatile
    private var INSTANCE: BtuDatabase? = null

    fun getInstance(context: Context): BtuDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          BtuDatabase::class.java,
          "btucm_academic.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}
