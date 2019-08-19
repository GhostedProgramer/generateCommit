package com.musicbible.controller

import com.boostfield.captchaspringbootstarter.CaptchaService
import com.boostfield.captchaspringbootstarter.CaptchaValidationResult
import com.boostfield.extension.exception.stackTraceString
import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.exception.AppError
import com.boostfield.verifycodespringbootstarter.VerifyCodeService
import com.boostfield.verifycodespringbootstarter.VerifyCodeServiceException
import com.musicbible.mapper.verifycode.VerifyCodeInput
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/verifycode")
@Api("/api/v0/verifycode", tags = ["S 手机短信验证码"])
class VerifyCodeController(
    @Autowired val verifyCodeService: VerifyCodeService,
    @Autowired val captchaService: CaptchaService
) : BaseController() {

    @ApiOperation("获取短信验证码")
    @GetMapping
    @kotlin.ExperimentalUnsignedTypes
    fun get(@Valid input: VerifyCodeInput) {
        when (captchaService.validateCaptcha(input.imgText, input.secret)) {
            CaptchaValidationResult.INVALID -> throw AppError.BadRequest.badCaptcha()
            CaptchaValidationResult.TIMEOUT -> throw AppError.BadRequest.captchaExpired()
            CaptchaValidationResult.OK -> UInt
        }
        try {
            verifyCodeService.sendCode(input.phone, "yanzhengma")
        } catch (e: VerifyCodeServiceException) {
            logger.error("${e.message}: ${e.stackTraceString}")
            throw AppError.ServiceUnavailable.default(msg = "验证码服务失效")
        }
    }
}
