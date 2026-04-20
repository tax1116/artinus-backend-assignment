package artinus.backend.assignment.subscription.app.web

import artinus.backend.assignment.subscription.app.application.ExternalDecisionRejectedException
import artinus.backend.assignment.subscription.app.application.port.ExternalDecisionUnavailableException
import artinus.backend.assignment.subscription.domain.exception.ChannelNotAllowedException
import artinus.backend.assignment.subscription.domain.exception.ChannelNotFoundException
import artinus.backend.assignment.subscription.domain.exception.IllegalTransitionException
import artinus.backend.assignment.subscription.domain.exception.MemberNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ProblemDetail =
        problem(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            e.bindingResult.allErrors.joinToString("; ") {
                "${if (it is org.springframework.validation.FieldError) it.field else it.objectName}: ${it.defaultMessage}"
            },
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "bad-request", e.message ?: "bad request")

    @ExceptionHandler(IllegalTransitionException::class)
    fun handleTransition(e: IllegalTransitionException): ProblemDetail = problem(HttpStatus.CONFLICT, "illegal-transition", e.message ?: "illegal transition")

    @ExceptionHandler(ChannelNotAllowedException::class)
    fun handleChannelForbidden(e: ChannelNotAllowedException): ProblemDetail = problem(HttpStatus.FORBIDDEN, "channel-not-allowed", e.message ?: "channel not allowed")

    @ExceptionHandler(ChannelNotFoundException::class)
    fun handleChannelNotFound(e: ChannelNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "channel-not-found", e.message ?: "channel not found")

    @ExceptionHandler(MemberNotFoundException::class)
    fun handleMemberNotFound(e: MemberNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "member-not-found", e.message ?: "member not found")

    @ExceptionHandler(ExternalDecisionRejectedException::class)
    fun handleRejected(e: ExternalDecisionRejectedException): ProblemDetail {
        log.info { "external decision rejected: ${e.message}" }
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "external-rejected", e.message ?: "rejected")
    }

    @ExceptionHandler(ExternalDecisionUnavailableException::class)
    fun handleUnavailable(e: ExternalDecisionUnavailableException): ProblemDetail {
        log.error(e) { "external decision unavailable" }
        return problem(
            HttpStatus.SERVICE_UNAVAILABLE,
            "external-unavailable",
            "외부 시스템 장애로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception): ProblemDetail {
        log.error(e) { "unhandled exception" }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "internal server error")
    }

    private fun problem(
        status: HttpStatus,
        type: String,
        detail: String,
    ): ProblemDetail {
        val pd = ProblemDetail.forStatus(status)
        pd.title = status.reasonPhrase
        pd.type = URI.create("urn:problem-type:$type")
        pd.detail = detail
        return pd
    }
}
