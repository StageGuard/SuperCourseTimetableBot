/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package me.stageguard.sctimetable.api.edu_system.`super`

import kotlinx.serialization.Serializable

/**
 * 登录返回包
 **/
@Serializable
data class LoginReceiptDTO(
    val `data`: __InternalData,
    val status: Int
)

@Serializable
data class ErrorLoginReceiptDTO(
    val `data`: __InternalErrorLoginMsg,
    val status: Int
)

@Serializable
data class __InternalErrorLoginMsg(
    val errorStr: String,
    val isRegister: Int = 1,
    val statusInt: Int = 1
)

@Serializable
data class __InternalData(
    val isRegister: Int = 1,
    val statusInt: Int = 1,
    val student: __InternalStudent
)

@Serializable
data class __InternalStudent(
    val attachmentBO: __InternalAttachmentBO,
    val nickName: String,
    val schoolId: Int,
    val schoolName: String,
    val studentId: Int,
)

@Serializable
data class __InternalAttachmentBO(
    val myTermList: List<__InternalMyTerm>,
)

@Serializable
data class __InternalMyTerm(
    val beginYear: Int,
    val courseTimeList: __InternalCourseTimeList,
    val term: Int
)

@Serializable
data class __InternalCourseTimeList(
    val courseTimeBO: List<__InternalCourseTimeBO> = listOf()
)

@Serializable
data class __InternalCourseTimeBO(
    val beginTimeStr: String,
    val endTimeStr: String
)