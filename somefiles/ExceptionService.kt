package com.musicbible.service

import com.boostfield.spring.service.BaseService
import com.musicbible.mapper.exception.UpdateExceptionInput
import com.musicbible.model.Exception
import com.musicbible.repository.ExceptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID


interface ExceptionService<T : Exception> : BaseService<T>, ExceptionRepository<T> {
    override val modelName: String
        get() = "异常"

    fun createException(fields: UpdateExceptionInput, addrIP: String, userId: UUID?): Exception
}

@Service
@Transactional
class ExceptionServiceImp(
    @Autowired val exceptionRepository: ExceptionRepository<Exception>,
    @Autowired val userService: UserService
) : ExceptionService<Exception>, ExceptionRepository<Exception> by exceptionRepository {

    override fun createException(fields: UpdateExceptionInput, addrIP: String, userId: UUID?): Exception {
        var exception = Exception()
        if (userId == null) {
            exception.user = null   //将未登录用户的user字段存为null
        } else {
            exception.user = userService.findOrThrow(userId)
        }
        exception.addrIP = addrIP
        exception.time = fields.time
        exception.context = fields.context
        exception = save(exception)
        return exception
    }
}
