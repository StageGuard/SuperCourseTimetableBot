/*
 * Copyright 2020-2021 StageGuard.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.api.edu_system.`super`

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
    val lessonList: List<__InternalLesson> = listOf(),
    val maxCount: Int,
    val semester: String,
    val startSchoolYear: String
)

@Serializable
data class __InternalLesson(
    val courseId: Int,
    val day: Int,
    val endSchoolYear: String,
    val id: Int,
    val locale: String,
    val name: String,
    val period: String,
    val schoolId: Int,
    val schoolName: String,
    val sectionend: Int,
    val sectionstart: Int,
    val semester: String,
    val smartPeriod: String,
    val startSchoolYear: String,
    val teacher: String = "",
)