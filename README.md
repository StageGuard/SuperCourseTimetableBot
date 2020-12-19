# SuperCourseTimetableBot

一个基于 [mirai](https://github.com/mamoe/mirai/) 和 [mirai-console](https://github.com/mamoe/mirai-console) 的 超级课程表 提醒 mirai-console 插件。

## 特性：

<img align="right" src="https://cdn.jsdelivr.net/gh/KonnyakuCamp/SuperCourseTimetableBot/blob/main/static/screenshot1.png" height="400">

- #### Interactive Conversation Mode - 交互式聊天模式的用户接口

抛弃了传统的命令式交互，采用了更友好的交互式聊天模式。

- #### 允许用户更方便地修改时间表信息

可能超级课表上的作息时间表与学校不吻合，用户可以发送 **修改时间表** 来修改。

在 交互式聊天模式 的优势下，修改时间表的步骤变得非常容易。

- #### 允许用户自定义提醒时间

用户发送 **修改提前提醒时间** 即可通过步骤引导修改。

<img align="right" src="https://cdn.jsdelivr.net/gh/KonnyakuCamp/SuperCourseTimetableBot/blob/main/static/screenshot2.png" height="400">

- #### 适配几乎所有使用超级课程表的高校

插件工作时，为每个正在使用的用户的高校分别计算当前周数和时间表，互不冲突。

- #### 数据库存储数据

使用 [MySQL](https://www.mysql.com/) 或 [MariaDB](https://mariadb.org/) 存储用户的数据，当用户数量较多时依然保持良好的数据读取性能。

## 使用：

SuperCourseTimetableBot 是**基于 `mirai-core 2.0-M1` 版本和 `mirai-console 2.0-M1` 版本的插件，不兼容 1.x 版本**。

1. 运行一个新的或使用现有的 MySQL 或 MariaDB 数据库，在数据库中新建一个 database，名称随意。
2. 在 [Releases](https://github.com/KonnyakuCamp/SuperCourseTimetableBot/releases/) 中下载 `SCTimetableBot-x.x.mirai.jar` 将其放入 mirai-console 的 插件文件夹下。
3. 启动 mirai-console， 会有如下提示：

```
2020-12-19 12:28:20 E/SuperCourseTimetable: stageguard.sctimetable.database.InvalidDatabaseConfigException: Database password is not set in config file SG.SCTimeTableBotConfig.
stageguard.sctimetable.database.InvalidDatabaseConfigException: Database password is not set in config file SG.SCTimeTableBotConfig.
        at stageguard.sctimetable.database.Database.hikariDataSourceProvider(Database.kt:75)
        at stageguard.sctimetable.database.Database.connect(Database.kt:43)
        at stageguard.sctimetable.PluginMain.onEnable(PluginMain.kt:32)
        at ...
```

4. 停止运行 mirai-console，进入 SuperCourseTimetableBot 配置文件 `config/SuperCourseTimetable/SG.SCTimeTableBotConfig.yml`，按照如下提示修改配置。

```yaml
# 用于工作的BOT的QQ号，只有这个 Bot 上线后 SuperCourseTimetableBot 才会开始工作。
qq: 123456789
# 默认提前多长时间提醒(单位：分钟)。
# 此值会在用户第一次被添加进数据库时设置给这个用户。
# 注意：如果你修改了这个值，在修改之前已经被设置的用户和自己设定值的用户不会受到影响。
advancedTipTime: 15
database: 
  # MariaDB 或 MySQL 数据库的地址.
  address: localhost
  # 数据库登入用户.
  user: root
  # 数据库登入密码，删掉''后修改
  password: ''
  # 填入第一步你创建的数据库的名称。
  # 用户的数据都会被存储在这个数据库里。
  table: sctimetabledb
  # 最大连接数，非特殊情况不需要修改。
  maximumPoolSize: 10
```

5. 重新运行 mirai-console，登录在第四部配置中指定的账号，SuperCourseTimetableBot 会输出如下提示：

```
2020-12-19 12:39:07 I/SuperCourseTimetable: TimeProviderService: Job YearUpdater is executed. (currentYear -> 2020)
2020-12-19 12:39:07 I/SuperCourseTimetable: TimeProviderService: Job SemesterUpdater is executed. (currentSemester -> 1)
2020-12-19 12:39:07 I/SuperCourseTimetable: ScheduleListenerService: Notification distribution job has executed.
2020-12-19 12:39:07 I/SuperCourseTimetable: TimeProviderService: Job SchoolWeekPeriodUpdater is executed.
```

这时 SuperCourseTimetable 就已经成功工作了。

 

