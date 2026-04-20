package artinus.backend.assignment.subscription.app.web.dto

import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class HistoryItem(
    val id: Long,
    val channelId: Long,
    val channelName: String,
    val action: HistoryAction,
    val fromStatus: SubscriptionStatus,
    val toStatus: SubscriptionStatus,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val occurredAt: LocalDateTime,
)
