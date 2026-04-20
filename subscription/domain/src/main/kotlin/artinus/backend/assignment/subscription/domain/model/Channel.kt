package artinus.backend.assignment.subscription.domain.model

data class Channel(
    val id: Long,
    val code: String,
    val name: String,
    val type: ChannelType,
) {
    fun assertCanSubscribe() {
        if (!type.canSubscribe) {
            throw artinus.backend.assignment.subscription.domain.exception.ChannelNotAllowedException(
                "채널 '$name'(id=$id) 은 구독을 수행할 수 없습니다.",
            )
        }
    }

    fun assertCanCancel() {
        if (!type.canCancel) {
            throw artinus.backend.assignment.subscription.domain.exception.ChannelNotAllowedException(
                "채널 '$name'(id=$id) 은 해지를 수행할 수 없습니다.",
            )
        }
    }
}
