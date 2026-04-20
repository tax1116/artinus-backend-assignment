package artinus.backend.assignment.subscription.app.application

import artinus.backend.assignment.subscription.app.application.port.HistorySummarizerPort
import artinus.backend.assignment.subscription.app.application.port.RandomDecisionPort
import artinus.backend.assignment.subscription.domain.exception.ChannelNotFoundException
import artinus.backend.assignment.subscription.domain.exception.MemberNotFoundException
import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.Member
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import artinus.backend.assignment.subscription.domain.model.SubscriptionHistory
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import artinus.backend.assignment.subscription.domain.repository.AppendHistoryCommand
import artinus.backend.assignment.subscription.domain.repository.ChannelRepository
import artinus.backend.assignment.subscription.domain.repository.HistoryRepository
import artinus.backend.assignment.subscription.domain.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}

class ExternalDecisionRejectedException(
    message: String,
) : RuntimeException(message)

data class SubscribeCommand(
    val phoneNumber: PhoneNumber,
    val channelId: Long,
    val targetStatus: SubscriptionStatus,
    val idempotencyKey: String?,
)

data class CancelCommand(
    val phoneNumber: PhoneNumber,
    val channelId: Long,
    val targetStatus: SubscriptionStatus,
    val idempotencyKey: String?,
)

data class MutationResult(
    val phoneNumber: PhoneNumber,
    val fromStatus: SubscriptionStatus,
    val toStatus: SubscriptionStatus,
    val action: HistoryAction,
    val historyId: Long,
)

data class HistoryQueryResult(
    val history: List<SubscriptionHistory>,
    val summary: String,
)

@Service
class SubscriptionService(
    private val memberRepository: MemberRepository,
    private val channelRepository: ChannelRepository,
    private val historyRepository: HistoryRepository,
    private val decisionPort: RandomDecisionPort,
    private val historySummarizer: HistorySummarizerPort,
) {
    @Transactional
    fun subscribe(cmd: SubscribeCommand): MutationResult {
        cmd.idempotencyKey?.let { key ->
            historyRepository.findByIdempotencyKey(key)?.let {
                log.info { "idempotent replay for key=$key → returning existing history id=${it.id}" }
                return it.toMutationResult()
            }
        }

        val channel = loadChannel(cmd.channelId)
        val existing = memberRepository.findForUpdateByPhoneNumber(cmd.phoneNumber)
        val now = LocalDateTime.now()

        val (member, transition) =
            if (existing == null) {
                Member.newSubscriber(
                    phoneNumber = cmd.phoneNumber,
                    channel = channel,
                    target = cmd.targetStatus,
                    now = now,
                )
            } else {
                existing to existing.subscribe(channel = channel, target = cmd.targetStatus, now = now)
            }

        val idempotencyKey = cmd.idempotencyKey ?: UUID.randomUUID().toString()
        commitOrRollback(idempotencyKey)

        val saved = memberRepository.save(member)
        val history =
            historyRepository.append(
                AppendHistoryCommand(
                    phoneNumber = cmd.phoneNumber,
                    channel = channel,
                    transition = transition,
                    occurredAt = now,
                    idempotencyKey = idempotencyKey,
                ),
            )
        log.info {
            "subscribe committed phone=${cmd.phoneNumber.masked()} channel=${channel.code} " +
                "${transition.from} -> ${transition.to} newMember=${existing == null} memberId=${saved.id}"
        }
        return history.toMutationResult()
    }

    @Transactional
    fun cancel(cmd: CancelCommand): MutationResult {
        cmd.idempotencyKey?.let { key ->
            historyRepository.findByIdempotencyKey(key)?.let {
                log.info { "idempotent replay for key=$key → returning existing history id=${it.id}" }
                return it.toMutationResult()
            }
        }

        val channel = loadChannel(cmd.channelId)
        val member =
            memberRepository.findForUpdateByPhoneNumber(cmd.phoneNumber)
                ?: throw MemberNotFoundException("등록되지 않은 회원입니다: ${cmd.phoneNumber.masked()}")

        val now = LocalDateTime.now()
        val transition = member.cancel(channel = channel, target = cmd.targetStatus, now = now)

        val idempotencyKey = cmd.idempotencyKey ?: UUID.randomUUID().toString()
        commitOrRollback(idempotencyKey)

        memberRepository.save(member)
        val history =
            historyRepository.append(
                AppendHistoryCommand(
                    phoneNumber = cmd.phoneNumber,
                    channel = channel,
                    transition = transition,
                    occurredAt = now,
                    idempotencyKey = idempotencyKey,
                ),
            )
        log.info {
            "cancel committed phone=${cmd.phoneNumber.masked()} channel=${channel.code} " +
                "${transition.from} -> ${transition.to} memberId=${member.id}"
        }
        return history.toMutationResult()
    }

    private fun loadChannel(channelId: Long) =
        channelRepository.findById(channelId)
            ?: throw ChannelNotFoundException("채널을 찾을 수 없습니다: id=$channelId")

    @Transactional(readOnly = true)
    fun history(phoneNumber: PhoneNumber): HistoryQueryResult {
        val domain = historyRepository.findAllByPhoneNumberOrderByOccurredAtAsc(phoneNumber)
        val summary =
            runCatching { historySummarizer.summarize(domain) }
                .onFailure { log.warn(it) { "summary generation failed, falling back" } }
                .getOrNull()
                ?: fallbackSummary(domain)
        return HistoryQueryResult(history = domain, summary = summary)
    }

    private fun commitOrRollback(idempotencyKey: String) {
        // ExternalDecisionUnavailableException 은 GlobalExceptionHandler 에서 503 으로 매핑되도록 그대로 전파.
        if (!decisionPort.decide(idempotencyKey)) {
            throw ExternalDecisionRejectedException(
                "외부 시스템이 요청을 거절했습니다 (csrng random=0). 트랜잭션을 롤백합니다.",
            )
        }
    }

    private fun SubscriptionHistory.toMutationResult(): MutationResult =
        MutationResult(
            phoneNumber = phoneNumber,
            fromStatus = fromStatus,
            toStatus = toStatus,
            action = action,
            historyId = id,
        )

    private fun fallbackSummary(history: List<SubscriptionHistory>): String {
        if (history.isEmpty()) return "이력이 없습니다."
        val last = history.last()
        return "현재 상태는 ${last.toStatus} 이며 총 ${history.size} 건의 이력이 있습니다."
    }
}
