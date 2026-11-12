package com.webclass.plus.data.models

data class SyllabusDepartment(
    val name: String,
    val departmentId: String,
    val publicSubjectsUrl: String
)

data class SyllabusSubject(
    val category: String = "",
    val requirement: String = "",
    val name: String,
    val code: String = "",
    val creditType: String = "",
    val credits: String = "",
    val teachers: String = "",
    val syllabusUrl: String = "",
    val targetYear: String = ""
)

data class SyllabusBasicInfo(
    val school: String = "",
    val year: String = "",
    val subjectName: String = "",
    val subjectCode: String = "",
    val category: String = "",
    val teachingFormat: String = "",
    val creditType: String = "",
    val credits: String = "",
    val department: String = "",
    val targetYear: String = "",
    val semester: String = "",
    val weeklyHours: String = "",
    val textbook: String = "",
    val teachers: String = ""
)

data class RubricItem(
    val item: String,
    val ideal: String,
    val standard: String,
    val notReached: String
)

data class LessonPlanItem(
    val week: String,
    val content: String,
    val target: String
)

data class EvaluationItem(
    val name: String,
    val components: Map<String, Int> = emptyMap(),
    val total: Int = 0
)

data class SyllabusDetail(
    val basicInfo: SyllabusBasicInfo = SyllabusBasicInfo(),
    val goals: String = "",
    val rubric: List<RubricItem> = emptyList(),
    val departmentGoals: List<String> = emptyList(),
    val overview: String = "",
    val methodology: String = "",
    val notes: String = "",
    val activeAttributes: Map<String, Boolean> = emptyMap(),
    val lessonPlan: List<LessonPlanItem> = emptyList(),
    val evaluation: List<EvaluationItem> = emptyList()
)
