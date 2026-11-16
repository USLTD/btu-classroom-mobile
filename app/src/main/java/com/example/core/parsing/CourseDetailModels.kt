package com.example.core.parsing

data class ParsedGradeComponent(
  val categoryName: String,
  val score: Double?,
  val maxScore: Double?,
  val notes: String?,
)

data class ParsedCourseResource(
  val title: String,
  val url: String,
)

data class ParsedCourseDetail(
  val title: String,
  val code: String?,
  val semesterName: String?,
  val lecturer: String?,
  val credits: Double?,
  val minCredit: Double?,
  val totalScore: Double?,
  val syllabusSummary: String?,
  val gradeComponents: List<ParsedGradeComponent>,
  val resources: List<ParsedCourseResource>,
)
