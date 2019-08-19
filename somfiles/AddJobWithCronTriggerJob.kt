package com.billy.job

import com.billy.service.QuartzServiceImpl
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@DisallowConcurrentExecution
class AddJobWithCronTriggerJob : Job {

    val logger: Logger = LoggerFactory.getLogger(QuartzServiceImpl::class.java)

    override fun execute(context: JobExecutionContext?) {
        //测试是不是变成异步的了
        println("开始执行")
        logger.info("execute当前线程为${Thread.currentThread().name}")
        Thread.sleep(5000)
        println("执行完毕")
    }

}