package com.musicbible.service

import com.musicbible.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

interface ContributionService {
    fun list(user: User, action: Int, q: String?)
}

@Service
@Transactional
class ContributionServiceImpl : ContributionService {
    override fun list(user: User, action: Int, q: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

enum class Action {
    PASS,
    CHECKING,
    NO_PASS;

    companion object {
        fun valueOf(code: Int): Action {
            return when (code) {
                0 -> Action.PASS
                1 -> Action.CHECKING
                2 -> Action.NO_PASS
                else -> throw IllegalArgumentException("Unknown how to convert code[$code] to Action")
            }
        }
    }
}
