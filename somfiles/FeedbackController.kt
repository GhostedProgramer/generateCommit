package com.musicbible.controller

import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.http.PageResponse
import com.musicbible.mapper.feedback.FeedbackListInput
import com.musicbible.mapper.feedback.FeedbackMapper
import com.musicbible.mapper.feedback.FeedbackOutput
import com.musicbible.model.User
import com.musicbible.security.UserOrThrow
import com.musicbible.service.FeedbackService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/feedback")
@Api("/api/v0/feedback", tags = ["Y 反馈"], description = "Feedback")
class FeedbackController(
    @Autowired val feedbackService: FeedbackService,
    @Autowired val feedBackMapper: FeedbackMapper
) : BaseController() {

    @ApiOperation("用户反馈列表")
    @ApiImplicitParams(
        ApiImplicitParam(name = "sort", allowableValues = "createdAt,name,contactWay,content,type",
            value = "排序可选字段,使用方法为[+/-变量]")
    )
    @PreAuthorize("hasAuthority('MANAGE_FEEDBACK')")
    @GetMapping
    fun feedback(
        @Valid @Validated feedbackInput: FeedbackListInput
    ): PageResponse<FeedbackOutput> {
        val manageFeedBackList = feedbackService
            .manageFeedBackList(feedbackInput)
        return PageResponse(
            feedBackMapper.toListOfFeedbackOutput(manageFeedBackList.content),
            manageFeedBackList.totalElements
        )
    }

    @ApiOperation("修改反馈处理状态")
    @PreAuthorize("hasAuthority('MANAGE_FEEDBACK')")
    @PutMapping("/{id}/solve")
    fun solve(@UserOrThrow user: User, @PathVariable id: UUID) {
        feedbackService.solve(user, id)
    }
}
