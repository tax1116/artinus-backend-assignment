package artinus.backend.assignment.subscription.domain.policy

import artinus.backend.assignment.subscription.domain.exception.IllegalTransitionException
import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus.NONE
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus.PREMIUM
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus.STANDARD

object SubscriptionTransitionPolicy {
    private val SUBSCRIBE_TRANSITIONS: Map<SubscriptionStatus, Set<SubscriptionStatus>> =
        mapOf(
            NONE to setOf(STANDARD, PREMIUM),
            STANDARD to setOf(PREMIUM),
            PREMIUM to emptySet(),
        )

    private val CANCEL_TRANSITIONS: Map<SubscriptionStatus, Set<SubscriptionStatus>> =
        mapOf(
            PREMIUM to setOf(NONE),
            STANDARD to setOf(NONE),
            NONE to emptySet(),
        )

    fun assertSubscribe(
        from: SubscriptionStatus,
        to: SubscriptionStatus,
    ) {
        val allowed = SUBSCRIBE_TRANSITIONS[from].orEmpty()
        if (to !in allowed) {
            throw IllegalTransitionException(
                "구독 상태 전이 불가: $from -> $to (허용: $allowed)",
            )
        }
    }

    fun assertCancel(
        from: SubscriptionStatus,
        to: SubscriptionStatus,
    ) {
        val allowed = CANCEL_TRANSITIONS[from].orEmpty()
        if (to !in allowed) {
            throw IllegalTransitionException(
                "구독 해지 상태 전이 불가: $from -> $to (허용: $allowed)",
            )
        }
    }

    fun actionFor(
        from: SubscriptionStatus,
        to: SubscriptionStatus,
    ): HistoryAction {
        val isUpgrade =
            when (from) {
                NONE -> true
                STANDARD -> to == PREMIUM
                PREMIUM -> false
            }
        return if (isUpgrade) HistoryAction.SUBSCRIBE else HistoryAction.CANCEL
    }
}
