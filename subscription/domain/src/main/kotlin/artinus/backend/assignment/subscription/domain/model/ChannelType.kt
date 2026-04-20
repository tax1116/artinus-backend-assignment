package artinus.backend.assignment.subscription.domain.model

enum class ChannelType(
    val canSubscribe: Boolean,
    val canCancel: Boolean,
) {
    SUBSCRIBE_AND_CANCEL(canSubscribe = true, canCancel = true),
    SUBSCRIBE_ONLY(canSubscribe = true, canCancel = false),
    CANCEL_ONLY(canSubscribe = false, canCancel = true),
}
