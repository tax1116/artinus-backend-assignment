package artinus.backend.assignment.subscription.app.web.dto

import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CancelRequest(
    @field:NotBlank
    val phoneNumber: String,
    @field:Positive
    val channelId: Long,
    val targetStatus: SubscriptionStatus,
)
