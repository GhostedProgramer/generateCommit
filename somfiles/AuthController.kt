package com.musicbible.controller

import com.boostfield.captchaspringbootstarter.CaptchaService
import com.boostfield.captchaspringbootstarter.CaptchaValidationResult
import com.boostfield.spring.auth.JwtProvider
import com.boostfield.spring.controller.BaseController
import com.boostfield.spring.exception.AppError
import com.boostfield.verifycodespringbootstarter.VerifyCodeService
import com.musicbible.config.properties.AppProperties
import com.musicbible.mapper.auth.CaptureResponse
import com.musicbible.mapper.auth.JwtAuthenticationResponse
import com.musicbible.mapper.auth.LoginRequest
import com.musicbible.mapper.auth.PhoneLoginInput
import com.musicbible.model.User
import com.musicbible.service.UserService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime
import javax.validation.Valid

@RestController
@RequestMapping("/api/v0/auth")
@Api(value = "/api/v0/auth", tags = ["R 认证接口"], description = "Auth")
class AuthController(
    @Autowired val authenticationManager: AuthenticationManager,
    @Autowired val appProperties: AppProperties,
    @Autowired val captchaService: CaptchaService,
    @Autowired val verifyCodeService: VerifyCodeService,
    @Autowired val userService: UserService
) : BaseController() {

    @PostMapping("/login")
    @ApiOperation(value = "登录")
    fun login(@Valid @RequestBody param: LoginRequest): JwtAuthenticationResponse {
        when (captchaService.validateCaptcha(param.captcha, param.secret)) {
            CaptchaValidationResult.INVALID -> throw AppError.BadRequest.badCaptcha()
            CaptchaValidationResult.TIMEOUT -> throw AppError.BadRequest.captchaExpired()
            CaptchaValidationResult.OK -> Unit
        }

        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(param.username, param.password)
        )

        val user = authentication.principal as User
        SecurityContextHolder.getContext().authentication = authentication

        // 限制非root账号登录
        if (!user.isRoot) {
            throw AppError.Forbidden.default(msg = "仅限开发者root登录")
        }

        return buildToken(user)
    }

    @PostMapping("/phone/login")
    @ApiOperation(value = "手机号登录")
    fun loginByPhone(@Valid @RequestBody input: PhoneLoginInput): JwtAuthenticationResponse {
        if (!verifyCodeService.verifyCode(input.code, input.phone)) {
            throw AppError.BadRequest.invalidSmsCode()
        }

        val user = userService.findByPhone(input.phone) ?: throw AppError.NotFound.default()

        // 校验是否是admin
        if (!user.isAdmin) {
            throw AppError.BadRequest.default(msg = "此手机号无后台权限，请联系管理员！")
        }

        // 校验是否被冻结
        if (user.blocked) {
            throw AppError.Forbidden.userBlocked()
        }
        return buildToken(user)
    }

    private fun buildToken(user: User): JwtAuthenticationResponse {
        val tokenExpireAt = ZonedDateTime.now().plusSeconds(appProperties.auth.expire)
        val token = JwtProvider(appProperties.auth.secret)
            .build(user, tokenExpireAt)

        userService.refreshLastLoginAt(user)

        return JwtAuthenticationResponse(
            accessToken = token,
            expireAt = tokenExpireAt
        )
    }


    @GetMapping("/captcha")
    @ApiOperation(value = "获取图片验证码")
    fun captcha(): CaptureResponse {
        val captcha = captchaService.getCaptcha()
        logger.debug("captcha:${captcha.text}")
        return CaptureResponse(captcha.img, captcha.encodedText)
    }
}
