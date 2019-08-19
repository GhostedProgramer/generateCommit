package com.musicbible.service

import com.boostfield.extension.exception.stackTraceString
import com.boostfield.spring.filter.REQUEST_ID_HEADER
import com.boostfield.spring.util.HttpUtil
import com.boostfield.spring.util.SecurityHelper
import com.musicbible.config.properties.AppProperties
import com.musicbible.model.User
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.mail.MailProperties
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.servlet.http.HttpServletRequest
import java.io.InputStream
import java.nio.charset.Charset


class ExceptionReportRenderer {
    lateinit var remoteIp: String
    lateinit var method: String
    lateinit var url: String
    lateinit var exception: Exception
    var requestId: String? = null
    var body: String = ""
    var gitRef: String = ""

    var user: String = ""
    var timestamp: LocalDateTime = LocalDateTime.now()

    fun render(): String {
        val md = """
            |# Code
            |* Git ref: $gitRef
            |
            |# Session
            |* RemoteIp: $remoteIp
            |* Timestamp: $timestamp
            |* RequestId: $requestId
            |* User: $user
            |
            |# Request
            |```
            |$method $url
            |$body
            |```
            |
            |# Exception
            |```
            |${exception.stackTraceString}
            ```
        """.trimMargin()

        val parser = Parser.builder().build()
        val document = parser.parse(md)
        val renderer = HtmlRenderer.builder().build()
        return renderer.render(document)
    }

    fun render(taskname: String, dateTime: LocalDateTime, throwable: Throwable?): String {
        var md = """
            |# 任务命令：
            |## $taskname
            |# Timestamp
            |## $dateTime
            """ + if (throwable != null) """
                |# Exception
                | ```
                | ${throwable.stackTraceString}
                | ```
                """ else """
                |# 执行成功
                """

        md = md.trimMargin()

        val parser = Parser.builder().build()
        val document = parser.parse(md)
        val renderer = HtmlRenderer.builder().build()
        return renderer.render(document)
    }
}


interface ExceptionReporter {
    fun report(exception: Exception, request: HttpServletRequest)
    fun reportTask(taskname: String, throwable: Throwable?)
}

@Service
class ExceptionReporterImpl(
    @Value("\${spring.application.name}") val appName: String,
    @Autowired val appProperties: AppProperties,
    @Autowired val mailProperties: MailProperties,
    @Autowired val javaMailSender: JavaMailSender,
    @Autowired val environment: Environment
) : ExceptionReporter {
    val logger: Logger = LoggerFactory.getLogger(ExceptionReporter::class.java)

    private fun getRequestBody(request: HttpServletRequest): String {
        val mediaType = MediaType.parseMediaType(request.contentType)
        return if (
            mediaType.includes(MediaType.APPLICATION_JSON) ||
            mediaType.includes(MediaType.TEXT_PLAIN)
        )
            BufferedReader(InputStreamReader(request.inputStream)).readText()
        else
            ""
    }

    override fun report(exception: Exception, request: HttpServletRequest) {
        if (!appProperties.reportException)
            return

        val timestamp = LocalDateTime.now()
        val renderer = ExceptionReportRenderer()
        renderer.gitRef = getGitVersion() ?: "Unknown"
        renderer.remoteIp = request.let { HttpUtil.getRemoteHost(it) }
        renderer.requestId = request.getAttribute(REQUEST_ID_HEADER).toString()
        renderer.method = request.method
        renderer.body = getRequestBody(request)
        renderer.url = request.requestURI
        if (request.queryString != null)
            renderer.url += "?${request.queryString}"
        renderer.timestamp = timestamp
        val user = SecurityHelper.user() as? User
        renderer.user = user?.stringId ?: "Anonymous"
        renderer.exception = exception

        val currentProfile = environment.activeProfiles.joinToString(",")
        val message = javaMailSender.createMimeMessage()
        MimeMessageHelper(message, true).also {
            it.setFrom(mailProperties.username)
            it.setSubject("[$appName] [$currentProfile] 未处理的异常 ${timestamp.format(DateTimeFormatter.ISO_DATE_TIME)}")
            it.setTo(appProperties.developers)
            it.setText(renderer.render(), true)
        }

        try {
            javaMailSender.send(message)
            logger.info("Send exception report success")
        } catch (ex: MailException) {
            logger.warn("Send email failed", ex)
        }
    }

    private fun getGitVersion(): String? {
        return javaClass
            .classLoader
            .getResourceAsStream("git-version.txt")
            ?.readBytes()
            ?.toString(Charset.forName("utf-8"))
    }

    override fun reportTask(taskname: String, throwable: Throwable?) {
        val timestamp = LocalDateTime.now()
        val renderer = ExceptionReportRenderer()

        val message = javaMailSender.createMimeMessage()
        MimeMessageHelper(message, true).also {
            it.setFrom(mailProperties.username)
            val taskState = if (throwable != null) "出现异常" else "执行成功"
            it.setSubject("[$appName] 任务:[$taskname] $taskState ${timestamp.format(DateTimeFormatter.ISO_DATE_TIME)}")
            it.setTo(appProperties.developers)
            it.setText(renderer.render(taskname, timestamp, throwable), true)
        }

        try {
            javaMailSender.send(message)
            logger.info("Send exception report success")
        } catch (ex: MailException) {
            logger.warn("Send email failed", ex)
        }
    }


}
