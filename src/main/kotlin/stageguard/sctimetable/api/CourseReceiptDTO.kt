package stageguard.sctimetable.api

import kotlinx.serialization.Serializable

@Serializable
data class CourseReceiptDTO(
    val `data`: GenericCourseData,
    val status: Int
)

@Serializable
data class GenericCourseData(
    val endSchoolYear: String,
    val lessonList: List<Lesson>,
    val maxCount: Int,
    val semester: String,
    val startSchoolYear: String
)

@Serializable
data class Lesson(
    val autoEntry: Boolean,
    val courseId: Int,
    val courseMark: Int,
    val courseType: Int,
    val day: Int,
    val endSchoolYear: String,
    val id: Int,
    val locale: String,
    val maxCount: Int,
    val name: String,
    val period: String,
    val schoolId: Int,
    val schoolName: String,
    val sectionend: Int,
    val sectionstart: Int,
    val semester: String,
    val smartPeriod: String,
    val startSchoolYear: String,
    val teacher: String,
    val verifyStatus: Int
)