/*
 * Copyright 2020-2021 KonnyakuCamp.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/KonnyakuCamp/SuperCourseTimetableBot/blob/main/LICENSE
 */
package stageguard.sctimetable.api.edu_system.`super`

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
    val academyId: Int,
    val academyName: String,
    val addTime: Long,
    val attachmentBO: __InternalAttachmentBO,
    val avatarUrl: String,
    val avaterReview: Int,
    val beginYear: Int,
    val bigAvatarUrl: String,
    val bornCity: String = "",
    val bornCityId: Int,
    val bornDate: Long,
    val bornProvince: String = "",
    val bornProvinceId: Int,
    val certificationType: Int,
    val fullAvatarUrl: String,
    val gender: Int,
    val grade: Int,
    val highSchoolId: Int,
    val hobby: String = "",
    val id: Int,
    val identity: String,
    val isCancel: Boolean,
    val isCelebrity: Int,
    val lastLoginTime: Long,
    val loveState: Int,
    val maxCount: Int,
    val memberShipType: Int,
    val mobileNumber: String,
    val nickName: String,
    val nickNameReview: Int,
    val nowStatus: String = "",
    val oldAvatarUrl: String,
    val oldNickName: String,
    val oldnicknamereview: Int,
    val organization: String,
    val photoBO: List<__InternalPhotoBO>,
    val profession: String,
    val publishType: Int,
    val rate: Int,
    val realName: String,
    val schoolId: Int,
    val schoolName: String,
    val schoolRoll: Int,
    val showRate: Boolean,
    val studentId: Int,
    val studentNum: String = "",
    val studentType: Int,
    val superId: String,
    val supportAuto: Boolean,
    val term: Int,
    val verify: String,
    val versionId: Int,
    val vipLevel: Int,
    val weiboAccount: Int,
    val weiboExpiresIn: Int,
    val classes: Int = 1,
    val hometown: String = ""
)

@Serializable
data class __InternalAttachmentBO(
    val contactStatus: Int,
    val courseRemind: Int,
    val courseRemindTime: Long,
    val dayOfWeek: Int,
    val defaultImgUrl: List<String>,
    val defaultOpen: Int,
    val gopushBO: __InternalGopushBO,
    val hasTermList: Boolean,
    val hasVerCode: Boolean,
    val hobby: String = "",
    val identity: String,
    val myTermList: List<__InternalMyTerm>,
    val needSASL: Int,
    val nowWeekMsg: __InternalNowWeekMsg,
    val openGopush: Boolean,
    val openJpush: Boolean,
    val openRubLessonInt: Int,
    val purviewValue: Int,
    val pushTime: Long,
    val rate: Int,
    val realNameMsgNum: Int,
    val rubLessonTips: String,
    val schoolInfo: __InternalSchoolInfo,
    val showRate: Boolean,
    val supportAuto: Boolean,
    val termBOList: List<__InternalTermBO>,
    val type: String,
    val vipLevel: Int,
    val xmppDomain: String
)

@Serializable
data class __InternalPhotoBO(
    val avatar: Boolean,
    val id: Int,
    val photoId: Int,
    val photoUrl: String,
    val studentId: Int,
    val thumUrl: String
)

@Serializable
data class __InternalGopushBO(
    val aliasName: String,
    val mid: Int,
    val pmid: Int
)

@Serializable
data class __InternalMyTerm(
    val addTime: Long,
    val beginYear: Int,
    val courseTimeList: __InternalCourseTimeList,
    val id: Int,
    val maxCount: Int,
    val studentId: Int,
    val term: Int
)

@Serializable
data class __InternalNowWeekMsg(
    val nowWeek: Int,
    val setTime: Long
)

@Serializable
data class __InternalSchoolInfo(
    val firstDayOfWeek: Int
)

@Serializable
data class __InternalTermBO(
    val content: String,
    val termId: Int
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