package artinus.backend.assignment.subscription.app.web

import artinus.backend.assignment.subscription.app.application.CancelCommand
import artinus.backend.assignment.subscription.app.application.SubscribeCommand
import artinus.backend.assignment.subscription.app.application.SubscriptionService
import artinus.backend.assignment.subscription.app.web.dto.CancelRequest
import artinus.backend.assignment.subscription.app.web.dto.HistoryItem
import artinus.backend.assignment.subscription.app.web.dto.HistoryResponse
import artinus.backend.assignment.subscription.app.web.dto.MutationResponse
import artinus.backend.assignment.subscription.app.web.dto.SubscribeRequest
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/subscriptions")
@Validated
class SubscriptionController(
    private val service: SubscriptionService,
) {
    @PostMapping
    fun subscribe(
        @Valid @RequestBody req: SubscribeRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idemKey: String?,
    ): ResponseEntity<MutationResponse> {
        val result =
            service.subscribe(
                SubscribeCommand(
                    phoneNumber = PhoneNumber.of(req.phoneNumber),
                    channelId = req.channelId,
                    targetStatus = req.targetStatus,
                    idempotencyKey = idemKey,
                ),
            )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                MutationResponse(
                    phoneNumber = result.phoneNumber.masked(),
                    fromStatus = result.fromStatus,
                    toStatus = result.toStatus,
                    action = result.action,
                    historyId = result.historyId,
                ),
            )
    }

    @PostMapping("/cancel")
    fun cancel(
        @Valid @RequestBody req: CancelRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idemKey: String?,
    ): ResponseEntity<MutationResponse> {
        val result =
            service.cancel(
                CancelCommand(
                    phoneNumber = PhoneNumber.of(req.phoneNumber),
                    channelId = req.channelId,
                    targetStatus = req.targetStatus,
                    idempotencyKey = idemKey,
                ),
            )
        return ResponseEntity.ok(
            MutationResponse(
                phoneNumber = result.phoneNumber.masked(),
                fromStatus = result.fromStatus,
                toStatus = result.toStatus,
                action = result.action,
                historyId = result.historyId,
            ),
        )
    }

    @GetMapping("/history")
    fun history(
        @RequestParam("phoneNumber") @NotBlank phoneNumber: String,
    ): HistoryResponse {
        val phone = PhoneNumber.of(phoneNumber)
        val result = service.history(phone)
        return HistoryResponse(
            phoneNumber = phone.masked(),
            history =
                result.history.map {
                    HistoryItem(
                        id = it.id,
                        channelId = it.channel.id,
                        channelName = it.channel.name,
                        action = it.action,
                        fromStatus = it.fromStatus,
                        toStatus = it.toStatus,
                        occurredAt = it.occurredAt,
                    )
                },
            summary = result.summary,
        )
    }
}
