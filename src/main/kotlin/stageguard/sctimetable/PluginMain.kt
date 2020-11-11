package stageguard.sctimetable

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.message.uploadImage
import net.mamoe.mirai.utils.ExternalImage
import net.mamoe.mirai.utils.info
import stageguard.sctimetable.api.CourseReceiptDTO
import stageguard.sctimetable.api.LoginReceiptDTO
import stageguard.sctimetable.api.SuperCourseApiService

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "stageguard.sctimetable",
        version = "0.1.0",
        name = "SCTimetable"
    )
) {
    override fun onEnable() {
        MySimpleCommand.register()
        logger.info { "Plugin loaded" }
    }

    override fun onDisable() {
        SuperCourseApiService.closeHttpClient()
    }
}

object MySimpleCommand : SimpleCommand (
    PluginMain,
    "bind",
    description = "Bind your account."
) {
    @Handler
    suspend fun CommandSender.handle(scAccount: Long, scPassword: String) {
        sendMessage("Logging to SuperCourse...")
        var term: Int = 1
        var beginYear: Int = 2020

        val responseCookie = SuperCourseApiService.login(scAccount, scPassword) {
            kotlin.runCatching {
                val data = Json.decodeFromString<LoginReceiptDTO>(it)

                val avatarIStream = HttpClient().use { client ->
                    client.get(data.data.student.avatarUrl)
                }
                sendMessage("""
                    Successfully login to SuperCourse!
                    Your information: 
                      Name: ${data.data.student.nickName}
                      School: ${data.data.student.schoolName}
                      Student No.: ${data.data.student.studentNum}
                """.trimIndent())
                term = data.data.student.attachmentBO.myTermList[0].term
                beginYear = data.data.student.attachmentBO.myTermList[0].beginYear
            }.onFailure {
                sendMessage("Failed to login SuperCourse!\nDetail: $it")
            }
        }
        SuperCourseApiService.getCourse(responseCookie, beginYear, term) {
            kotlin.runCatching {
                val data = Json.decodeFromString<CourseReceiptDTO>(it)
                PluginMain.logger.verbose(it)
            }.onFailure {

            }
        }
    }
}