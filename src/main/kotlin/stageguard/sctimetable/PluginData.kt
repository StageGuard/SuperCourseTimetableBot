package stageguard.sctimetable

import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object PluginData : AutoSavePluginData("User") {
    @ValueDescription("提前几分钟提醒")
    val advancedTipOffset: MutableMap<Long, Int> by value(mutableMapOf())
}