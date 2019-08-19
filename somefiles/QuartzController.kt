package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.musicbible.listener.virtualdata.VirtualDataEventListener
import com.musicbible.mapper.quartz.VirtualDataJobParamsInput
import com.musicbible.service.QuartzService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v0/quartz")
@Api(value = "/api/v0/quartz", tags = ["D 定时任务管理"], description = "Quartz")
class QuartzController(
    @Autowired val quartzService: QuartzService
) : BaseController() {

    @PutMapping("/start")
    @ApiOperation("开启定时器调度器(开启所有未完成的定时任务)")
    fun startJobs() {
        quartzService.startJobs()
    }

    @PutMapping("/shutdown")
    @ApiOperation("关闭定时器调度器(关闭定时任务,新的任务也不会生成)")
    fun shutdownJobs() {
        quartzService.shutdownJobs()
    }

    @PutMapping("/virtual/params")
    @ApiOperation("修改虚拟数据任务的执行时长")
    fun modifyVirtualDataJobParams(@RequestBody input: VirtualDataJobParamsInput) {
        input.totalIntervalForVideoPublish?.also {
            VirtualDataEventListener.TOTAL_INTERVAL_FOR_VIDEO_PUBLISH = it
        }
        input.totalIntervalForVideoPlay?.also {
            VirtualDataEventListener.TOTAL_INTERVAL_FOR_VIDEO_PLAY = it
        }
        @Suppress("MaxLineLength")
        logger.info("虚拟数据任务总时长修改成功,当前视频发布数据虚拟任务时长为${VirtualDataEventListener.TOTAL_INTERVAL_FOR_VIDEO_PUBLISH},视频播放虚拟任务时长为${VirtualDataEventListener.TOTAL_INTERVAL_FOR_VIDEO_PLAY}")
    }
}
