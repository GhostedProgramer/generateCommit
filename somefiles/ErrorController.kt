package com.musicbible.controller

import com.boostfield.spring.http.RestErrorResponse
import com.boostfield.spring.http.RestResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest

@RestController
class ErrorController(
    errorAttributes: DefaultErrorAttributes
) : AbstractErrorController(errorAttributes) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun getErrorPath() = "/error"

    @GetMapping("/error")
    fun error(request: HttpServletRequest): RestErrorResponse {
        val errorAttributes = getErrorAttributes(request, true)
        logger.warn("Unhandled error: {}: {}", errorAttributes["status"], errorAttributes["message"])

        return RestResponse.error(
            getStatus(request), 0, errorAttributes["message"].toString()
        )
    }
}
