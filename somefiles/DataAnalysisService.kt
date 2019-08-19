package com.musicbible.service

import java.time.ZonedDateTime


/**
 * 数据分析统一接口，用于后台录入数据分析页面。
 *
 * @since 2019年8月5日, PM 03:57:19
 */
interface DataAnalysisService {

    val modelName: String

    /**
     * 统计目标时间段内录入的有效数量。
     *
     * @param begin 开始统计的时间点
     * @param end 结束统计的时间点
     * @return 统计结果
     * @since 2019年8月5日, PM 04:07:09
     */
    fun newlyEnteringCountBetween(begin: ZonedDateTime, end: ZonedDateTime): Long

    /**
     * 统计库内所有的有效数据量。
     *
     * @return 统计结果
     * @since 2019年8月5日, PM 04:11:15
     */
    fun total(): Long

    /**
     * 统计目标时间段内的增长量。
     *
     * @param begin 开始统计时间
     * @param end   结束统计时间
     * @param linearType 分析类型
     * @param platform   分析平台
     * @sine 2019年8月6日, PM 03:23:18
     */
    fun linearAnalysis(begin: ZonedDateTime, end: ZonedDateTime, linearType: LinearType, platform: PlatformType): Map<String, Long>

    fun newlyEnteringCountBetweenToday(): Long {
        val now = ZonedDateTime.now()
        val early = now.withHour(0).withMinute(0)
        return newlyEnteringCountBetween(early, now)
    }

    fun newlyEnteringCountBetweenYesterday(): Long {
        val now = ZonedDateTime.now()
        val early = now.withHour(0).withMinute(0)
        val yesterday = early.minusDays(1)
        return newlyEnteringCountBetween(yesterday, early)
    }
}

/**
 * 折线图类型：
 * - 月视图
 * - 年视图
 */
enum class LinearType {

    MONTH_LINEAR,
    YEAR_LINEAR
}

/**
 * 发布类型：
 * - 全平台
 * - 前台
 * - 后台
 *
 * @since 2019年8月5日, PM 04:26:30
 */
enum class PlatformType {

    BACKEND,
    FRONTEND
}
