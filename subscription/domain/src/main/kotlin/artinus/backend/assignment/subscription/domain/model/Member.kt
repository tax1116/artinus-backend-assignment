package artinus.backend.assignment.subscription.domain.model

import artinus.backend.assignment.subscription.domain.policy.SubscriptionTransitionPolicy
import java.time.LocalDateTime

data class StatusTransition(
    val from: SubscriptionStatus,
    val to: SubscriptionStatus,
    val action: HistoryAction,
)

class Member private constructor(
    val id: Long?,
    val phoneNumber: PhoneNumber,
    currentStatus: SubscriptionStatus,
    val createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    val version: Long,
) {
    var currentStatus: SubscriptionStatus = currentStatus
        private set
    var updatedAt: LocalDateTime = updatedAt
        private set

    fun subscribe(
        channel: Channel,
        target: SubscriptionStatus,
        now: LocalDateTime = LocalDateTime.now(),
    ): StatusTransition {
        channel.assertCanSubscribe()
        SubscriptionTransitionPolicy.assertSubscribe(currentStatus, target)
        val from = currentStatus
        currentStatus = target
        updatedAt = now
        return StatusTransition(from = from, to = target, action = HistoryAction.SUBSCRIBE)
    }

    fun cancel(
        channel: Channel,
        target: SubscriptionStatus,
        now: LocalDateTime = LocalDateTime.now(),
    ): StatusTransition {
        channel.assertCanCancel()
        SubscriptionTransitionPolicy.assertCancel(currentStatus, target)
        val from = currentStatus
        currentStatus = target
        updatedAt = now
        return StatusTransition(from = from, to = target, action = HistoryAction.CANCEL)
    }

    companion object {
        fun existing(
            id: Long,
            phoneNumber: PhoneNumber,
            currentStatus: SubscriptionStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
            version: Long,
        ): Member =
            Member(
                id = id,
                phoneNumber = phoneNumber,
                currentStatus = currentStatus,
                createdAt = createdAt,
                updatedAt = updatedAt,
                version = version,
            )

        fun newSubscriber(
            phoneNumber: PhoneNumber,
            channel: Channel,
            target: SubscriptionStatus,
            now: LocalDateTime = LocalDateTime.now(),
        ): Pair<Member, StatusTransition> {
            val member =
                Member(
                    id = null,
                    phoneNumber = phoneNumber,
                    currentStatus = SubscriptionStatus.NONE,
                    createdAt = now,
                    updatedAt = now,
                    version = 0,
                )
            val transition = member.subscribe(channel = channel, target = target, now = now)
            return member to transition
        }
    }
}
