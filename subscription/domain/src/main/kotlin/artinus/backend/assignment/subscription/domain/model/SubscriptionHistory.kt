package artinus.backend.assignment.subscription.domain.model

import java.time.LocalDateTime

data class SubscriptionHistory(
    val id: Long,
    val phoneNumber: PhoneNumber,
    val channel: Channel,
    val action: HistoryAction,
    val fromStatus: SubscriptionStatus,
    val toStatus: SubscriptionStatus,
    val occurredAt: LocalDateTime,
)
