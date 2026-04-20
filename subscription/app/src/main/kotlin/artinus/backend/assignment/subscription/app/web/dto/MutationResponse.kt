package artinus.backend.assignment.subscription.app.web.dto

import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus

data class MutationResponse(
    val phoneNumber: String,
    val fromStatus: SubscriptionStatus,
    val toStatus: SubscriptionStatus,
    val action: HistoryAction,
    val historyId: Long,
)
