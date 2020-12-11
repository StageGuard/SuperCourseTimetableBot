package stageguard.sctimetable.api

import kotlinx.serialization.Serializable

/**
 * 获取课程返回包
 **/
@Serializable
data class CourseReceiptDTO(
    val `data`: __InternalGenericCourseData,
    val status: Int
)

@Serializable
data class ErrorCourseReceiptDTO(
    val title: String,
    val message: String
)

@Serializable
data class __InternalGenericCourseData(
    val endSchoolYear: String,
    val lessonList: List<__InternalLesson>,
    val maxCount: Int,
    val semester: String,
    val startSchoolYear: String
)

@Serializable
data class __InternalLesson(
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