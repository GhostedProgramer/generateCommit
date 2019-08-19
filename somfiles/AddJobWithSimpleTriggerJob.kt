package com.billy.job

import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@DisallowConcurrentExecution
class AddJobWithSimpleTriggerJob : Job {

    val logger: Logger = LoggerFactory.getLogger(AddJobWithSimpleTriggerJob::class.java)

    override fun execute(context: JobExecutionContext?) {
        logger.info("SimpleTriggerJobExecute当前线程为${Thread.currentThread().name}")
    }

}