package artinus.backend.assignment.subscription.domain.exception

sealed class DomainException(
    message: String,
) : RuntimeException(message)

class IllegalTransitionException(
    message: String,
) : DomainException(message)

class ChannelNotAllowedException(
    message: String,
) : DomainException(message)

class MemberNotFoundException(
    message: String,
) : DomainException(message)

class ChannelNotFoundException(
    message: String,
) : DomainException(message)
