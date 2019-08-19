package com.musicbible.service

import com.boostfield.spring.exception.AppError
import com.musicbible.constant.Constants.Companion.JOB_DATA_MAP_PARAM
import com.musicbible.service.QuartzService.Companion.JOB_GROUP_NAME
import com.musicbible.service.QuartzService.Companion.TRIGGER_GROUP_NAME
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.quartz.impl.triggers.CronTriggerImpl
import org.quartz.impl.triggers.SimpleTriggerImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date


interface QuartzService {
    companion object {
        const val JOB_GROUP_NAME: String = "DEFAULT_JOB_GROUP_NAME"
        const val TRIGGER_GROUP_NAME: String = "DEFAULT_TRIGGER_GROUP_NAME"
    }

    //  添加一个不带参数的定时任务(标准),带CronTrigger
    fun addJobWithCronTrigger(jobName: String, clazz: Class<out Job>, time: String)

    //  添加一个带参数的定时任务,带CronTrigger
    fun addJobWithCronTriggerWithData(jobName: String, clazz: Class<out Job>, time: String, any: Any)

    //  添加一个不带参数的定时任务(标准),带SimpleTrigger
    fun addJobWithSimpleTrigger(jobName: String, clazz: Class<out Job>, repeatCount: Int, repeatInterval: Long, delayTime: Long)

    //  添加一个带参数的定时任务(标准),带SimpleTrigger
    fun addJobWithSimpleTriggerWithData(
        jobName: String, clazz: Class<out Job>,
        repeatCount: Int, repeatInterval: Long,
        any: Any, delayTime: Long
    )

    //  关闭定时器调度器(关闭所有任务)
    fun shutdownJobs()

    //  重启定时器调度器(开启所有任务)
    fun startJobs()

    //  移除任务
    /**
     * @param jobName 根据任务名移除任务
     * */
    fun removeJob(jobName: String)

    fun countQuartzTime(time: String): String

    /**
     * @param time 输入的字符串时间,格式为 yyyy/MM/dd HH:mm:ss
     * @return 返回ZonedDateTime
     */
    fun transFromStringToZonedDateTime(time: String): ZonedDateTime

}

@Suppress("TooGenericExceptionCaught")
@Service
@Transactional
class QuartzServiceImpl(
    @Autowired val scheduler: Scheduler
) : QuartzService {

    val logger: Logger = LoggerFactory.getLogger(QuartzServiceImpl::class.java)

    override fun addJobWithCronTrigger(jobName: String, clazz: Class<out Job>, time: String) {
        try {
            // 任务名，任务组，任务执行类
            val jobDetail = JobBuilder.newJob(clazz)
                .withIdentity(jobName, JOB_GROUP_NAME).build()

            // 创建触发器
            val trigger = CronTriggerImpl()
            trigger.name = jobDetail.key.name
            trigger.group = TRIGGER_GROUP_NAME
            trigger.cronExpression = time
            // 添加调度任务
            if (!scheduler.isShutdown) {
                scheduler.scheduleJob(jobDetail, trigger)
            }
            // 启动调度器
            if (!scheduler.isStarted) {
                scheduler.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "添加一个定时任务失败: 方法(addJobWithCronTrigger)")
        }
    }

    override fun addJobWithCronTriggerWithData(jobName: String, clazz: Class<out Job>, time: String, any: Any) {
        try {
            // 任务名，任务组，任务执行类

            val jobDataMap = JobDataMap()
            jobDataMap[JOB_DATA_MAP_PARAM] = any
            val jobDetail = JobBuilder.newJob(clazz)
                .withIdentity(jobName, JOB_GROUP_NAME).usingJobData(jobDataMap).build()
            // 创建触发器
            val trigger = CronTriggerImpl()
            trigger.name = jobDetail.key.name
            trigger.group = TRIGGER_GROUP_NAME
            trigger.cronExpression = time
            // 添加调度任务
            if (!scheduler.isShutdown) {
                scheduler.scheduleJob(jobDetail, trigger)
            }
            // 启动调度器
            if (!scheduler.isStarted) {
                scheduler.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "添加一个定时任务失败: 方法(addJobWithCronTriggerWithData)")
        }
    }

    /**
     * @param jobName: 任务名称,每个任务需不同,推荐使用实体id
     * @param clazz: 任务业务逻辑所在类
     * @param repeatCount: 任务重复次数,第一次执行不计入
     * @param repeatInterval: 任务执行间隔时间毫秒数
     * @param delayTime: 初次任务执行时间比当前时间延迟的时间毫秒数
     * */
    override fun addJobWithSimpleTrigger(jobName: String, clazz: Class<out Job>, repeatCount: Int, repeatInterval: Long, delayTime: Long) {
        try {
            // 任务名，任务组，任务执行类
            val jobDetail = JobBuilder.newJob(clazz)
                .withIdentity(jobName, JOB_GROUP_NAME).build()

            // 创建触发器
            val trigger = TriggerBuilder.newTrigger().withIdentity(jobDetail.key.name, TRIGGER_GROUP_NAME)
                .build() as SimpleTriggerImpl
            trigger.repeatCount = repeatCount
            trigger.repeatInterval = repeatInterval
            trigger.startTime = Date.from(ZonedDateTime.now().plusSeconds(delayTime / @Suppress("MagicNumber") 1000L).toInstant())
            // 添加调度任务
            if (!scheduler.isShutdown) {
                scheduler.scheduleJob(jobDetail, trigger)
            }
            // 启动调度器
            if (!scheduler.isStarted) {
                scheduler.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "添加一个定时任务失败: 方法(addJobWithSimpleTrigger)")
        }
    }

    /**
     * @param jobName: 任务名称,每个任务需不同,推荐使用实体id
     * @param clazz: 任务业务逻辑所在类
     * @param repeatCount: 任务重复次数,第一次执行不计入
     * @param repeatInterval: 任务执行间隔时间毫秒数
     * @param delayTime: 初次任务执行时间比当前时间延迟的时间毫秒数
     * @param any: 需放入定时任务执行的参数,任务内部封装在一个JobDataMap中
     * */
    override fun addJobWithSimpleTriggerWithData(
        jobName: String, clazz: Class<out Job>,
        repeatCount: Int, repeatInterval: Long,
        any: Any, delayTime: Long
    ) {
        try {
            val jobDataMap = JobDataMap()
            jobDataMap[JOB_DATA_MAP_PARAM] = any
            // 任务名，任务组，任务执行类
            val jobDetail = JobBuilder.newJob(clazz).usingJobData(jobDataMap)
                .withIdentity(jobName, JOB_GROUP_NAME).build()

            // 创建触发器
            val trigger = TriggerBuilder.newTrigger().withIdentity(jobDetail.key.name, TRIGGER_GROUP_NAME)
                .build() as SimpleTriggerImpl
            trigger.repeatCount = repeatCount
            trigger.repeatInterval = repeatInterval
            trigger.startTime = Date.from(ZonedDateTime.now().plusSeconds(delayTime / @Suppress("MagicNumber") 1000L).toInstant())
            // 添加调度任务
            if (!scheduler.isShutdown) {
                scheduler.scheduleJob(jobDetail, trigger)
            }
            // 启动调度器
            if (!scheduler.isStarted) {
                scheduler.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "添加一个定时任务失败: 方法(addJobWithSimpleTriggerWithData)")
        }
    }

    override fun removeJob(jobName: String) {
        try {
            scheduler.pauseTrigger(TriggerKey(jobName, TRIGGER_GROUP_NAME))    // 停止触发器
            scheduler.unscheduleJob(TriggerKey(jobName, TRIGGER_GROUP_NAME))   // 移除触发器
            scheduler.deleteJob(JobKey(jobName, JOB_GROUP_NAME))           // 删除任务
            logger.info("任务$jobName 关闭成功")
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "删除一个定时任务失败: 方法(removeJob)")
        }

    }

    override fun shutdownJobs() {
        try {
            if (!scheduler.isShutdown) {
                scheduler.shutdown()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "请求(shutdownJobs)失败,请稍后重试")
        }
    }

    override fun startJobs() {
        try {
            scheduler.start()
        } catch (e: Exception) {
            e.printStackTrace()
            throw AppError.Internal.default(msg = "请求(startJobs)失败,请稍后重试")
        }
    }

    /**
     * 根据时间 计算出Quartz定时的时间表达式(eg: "0 22 14 12 8 ? 2015")
     * @param time 定时时间字符串  精确到时分秒 注意: 不能为过去时
     * @return 返回Quartz指定的时间表达式   如果转换失败 返回null
     */
    override fun countQuartzTime(time: String): String {
        val sendCalendar = Calendar.getInstance() //定时时间
        val currCalendar = Calendar.getInstance() //当前时间

        //将字符串转化为日期对象
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        val sendDate: Date?
        try {
            sendDate = format.parse(time)
        } catch (e: ParseException) {
            e.printStackTrace()
            logger.info("计算时间表达式失败,输入的时间为:$time")
            throw AppError.BadRequest.default(msg = "时间无法解析,请输入格式为 yyyy/MM/dd HH:mm:ss的时间")
        }

        sendCalendar.time = sendDate
        currCalendar.time = Date()

        if (sendCalendar.after(currCalendar)) {
            val seconds = sendCalendar.get(Calendar.SECOND)
            val minutes = sendCalendar.get(Calendar.MINUTE)
            val hours = sendCalendar.get(Calendar.HOUR_OF_DAY)
            val day = sendCalendar.get(Calendar.DAY_OF_MONTH)
            val month = sendCalendar.get(Calendar.MONTH) + 1
            val year = sendCalendar.get(Calendar.YEAR)

            //拼接表达式
            val sb = StringBuffer()
            sb.append(seconds).append(" ").append(minutes).append(" ").append(hours).append(" ").append(day)
                .append(" ").append(month).append(" ").append("?").append(" ").append(year)
            return sb.toString()
        }
        throw AppError.Internal.default(msg = "计算时间表达式失败: 方法(countQuartzTime) 失败原因: 参数时间已是过时时间，必须是未来时间!")
    }

    override fun transFromStringToZonedDateTime(time: String): ZonedDateTime {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
        return ZonedDateTime.parse(time, dateTimeFormatter).withZoneSameInstant(ZoneId.of("UTC"))
    }
}
