package com.musicbible.service

import com.boostfield.extension.toDate
import com.musicbible.es.model.EsUserAction
import com.musicbible.es.model.Origin
import com.musicbible.es.model.UserAction
import com.musicbible.es.model.UserDoc
import com.musicbible.es.repository.UserActionEsRepository
import com.musicbible.model.User
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.*

interface UserStaticService {
    fun recordLoginAsync(user: User, origin: Origin): Deferred<Unit>
    fun recordRegisterAsync(user: User, origin: Origin): Deferred<Unit>
}

@Service
@Transactional
class UserStaticSericeImpl(
    @Autowired val userActionRepository: UserActionEsRepository
) : UserStaticService {

    private fun recordUserAction(user: User, action: UserAction, origin: Origin) {
        val userDoc = UserDoc(
            user.id,
            user.nickName,
            user.avatar
        )
        val userAction = EsUserAction()
            .also {
                it.id = UUID.randomUUID()
                it.user = userDoc
                it.timestamp = ZonedDateTime.now().toDate()
                it.action = action
                it.origin = origin
            }
        userActionRepository.save(userAction)
    }

    override fun recordLoginAsync(user: User, origin: Origin) = GlobalScope.async { recordUserAction(user, UserAction.LOGIN, origin) }

    override fun recordRegisterAsync(user: User, origin: Origin) = GlobalScope.async { recordUserAction(user, UserAction.REGISTER, origin) }
}
