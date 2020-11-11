package stageguard.sctimetable.api

import kotlinx.serialization.Serializable

@Serializable
data class LoginReceiptDTO(
    val `data`: Data,
    val status: Int
)

@Serializable
data class Data(
    val isRegister: Int,
    val statusInt: Int,
    val student: Student
)

@Serializable
data class Student(
    val academyId: Int,
    val academyName: String,
    val addTime: Long,
    val attachmentBO: AttachmentBO,
    val avatarUrl: String,
    val avaterReview: Int,
    val beginYear: Int,
    val bigAvatarUrl: String,
    val bornCityId: Int,
    val bornDate: Int,
    val bornProvinceId: Int,
    val certificationType: Int,
    val fullAvatarUrl: String,
    val gender: Int,
    val grade: Int,
    val highSchoolId: Int,
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
    val oldAvatarUrl: String,
    val oldNickName: String,
    val oldnicknamereview: Int,
    val organization: String,
    val photoBO: List<PhotoBO>,
    val profession: String,
    val publishType: Int,
    val rate: Int,
    val realName: String,
    val schoolId: Int,
    val schoolName: String,
    val schoolRoll: Int,
    val showRate: Boolean,
    val studentId: Int,
    val studentNum: String,
    val studentType: Int,
    val superId: String,
    val supportAuto: Boolean,
    val term: Int,
    val verify: String,
    val versionId: Int,
    val vipLevel: Int,
    val weiboAccount: Int,
    val weiboExpiresIn: Int
)

@Serializable
data class AttachmentBO(
    val contactStatus: Int,
    val courseRemind: Int,
    val courseRemindTime: Long,
    val dayOfWeek: Int,
    val defaultImgUrl: List<String>,
    val defaultOpen: Int,
    val gopushBO: GopushBO,
    val hasTermList: Boolean,
    val hasVerCode: Boolean,
    val identity: String,
    val myTermList: List<MyTerm>,
    val needSASL: Int,
    val nowWeekMsg: NowWeekMsg,
    val openGopush: Boolean,
    val openJpush: Boolean,
    val openRubLessonInt: Int,
    val purviewValue: Int,
    val pushTime: Long,
    val rate: Int,
    val realNameMsgNum: Int,
    val rubLessonTips: String,
    val schoolInfo: SchoolInfo,
    val showRate: Boolean,
    val supportAuto: Boolean,
    val termBOList: List<TermBO>,
    val type: String,
    val vipLevel: Int,
    val xmppDomain: String
)

@Serializable
data class PhotoBO(
    val avatar: Boolean,
    val id: Int,
    val photoId: Int,
    val photoUrl: String,
    val studentId: Int,
    val thumUrl: String
)

@Serializable
data class GopushBO(
    val aliasName: String,
    val mid: Int,
    val pmid: Int
)

@Serializable
data class MyTerm(
    val addTime: Long,
    val beginYear: Int,
    val courseTimeList: CourseTimeList,
    val id: Int,
    val maxCount: Int,
    val studentId: Int,
    val term: Int
)

@Serializable
data class NowWeekMsg(
    val nowWeek: Int,
    val setTime: Long
)

@Serializable
data class SchoolInfo(
    val firstDayOfWeek: Int
)

@Serializable
data class TermBO(
    val content: String,
    val termId: Int
)

@Serializable
data class CourseTimeList(
    val courseTimeBO: List<CourseTimeBO>
)

@Serializable
data class CourseTimeBO(
    val beginTimeStr: String,
    val endTimeStr: String
)