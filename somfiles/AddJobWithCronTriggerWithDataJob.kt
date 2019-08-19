package com.billy.job

import com.billy.controller.Student
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext

@DisallowConcurrentExecution
class AddJobWithCronTriggerWithDataJob : Job {

    override fun execute(context: JobExecutionContext?) {
        val data = context!!.mergedJobDataMap["params"] as Student
        println(data)
    }

}