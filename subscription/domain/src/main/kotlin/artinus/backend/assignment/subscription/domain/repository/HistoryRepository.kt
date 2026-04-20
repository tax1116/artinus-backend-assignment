package artinus.backend.assignment.subscription.domain.repository

import artinus.backend.assignment.subscription.domain.model.Channel
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import artinus.backend.assignment.subscription.domain.model.StatusTransition
import artinus.backend.assignment.subscription.domain.model.SubscriptionHistory
import java.time.LocalDateTime

data class AppendHistoryCommand(
    val phoneNumber: PhoneNumber,
    val channel: Channel,
    val transition: StatusTransition,
    val occurredAt: LocalDateTime,
    val idempotencyKey: String,
)

interface HistoryRepository {
    fun findByIdempotencyKey(idempotencyKey: String): SubscriptionHistory?

    fun findAllByPhoneNumberOrderByOccurredAtAsc(phoneNumber: PhoneNumber): List<SubscriptionHistory>

    fun append(command: AppendHistoryCommand): SubscriptionHistory
}
