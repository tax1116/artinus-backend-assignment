package artinus.backend.assignment.subscription.app.application

import artinus.backend.assignment.subscription.app.application.port.ExternalDecisionUnavailableException
import artinus.backend.assignment.subscription.app.application.port.HistorySummarizerPort
import artinus.backend.assignment.subscription.app.application.port.RandomDecisionPort
import artinus.backend.assignment.subscription.app.infra.persistence.ChannelJpaRepository
import artinus.backend.assignment.subscription.app.infra.persistence.HistoryJpaRepository
import artinus.backend.assignment.subscription.app.infra.persistence.MemberJpaRepository
import artinus.backend.assignment.subscription.domain.exception.ChannelNotAllowedException
import artinus.backend.assignment.subscription.domain.exception.IllegalTransitionException
import artinus.backend.assignment.subscription.domain.exception.MemberNotFoundException
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionServiceTest {
    @TestConfiguration
    class Stubs {
        @Bean
        @Primary
        fun randomDecisionPort(): FakeDecisionPort = FakeDecisionPort()

        @Bean
        @Primary
        fun historySummarizerPort(): HistorySummarizerPort = HistorySummarizerPort { null }
    }

    class FakeDecisionPort : RandomDecisionPort {
        var nextDecision: Decision = Decision.COMMIT

        override fun decide(idempotencyKey: String): Boolean =
            when (nextDecision) {
                Decision.COMMIT -> true
                Decision.ROLLBACK -> false
                Decision.UNAVAILABLE -> throw ExternalDecisionUnavailableException("stubbed outage")
            }

        enum class Decision { COMMIT, ROLLBACK, UNAVAILABLE }
    }

    @Autowired lateinit var service: SubscriptionService

    @Autowired lateinit var decisionPort: FakeDecisionPort

    @Autowired lateinit var members: MemberJpaRepository

    @Autowired lateinit var histories: HistoryJpaRepository

    @Autowired lateinit var channels: ChannelJpaRepository

    @BeforeEach
    fun reset() {
        histories.deleteAll()
        members.deleteAll()
        decisionPort.nextDecision = FakeDecisionPort.Decision.COMMIT
    }

    @Test
    fun `구독 성공 후 회원과 이력이 생성된다`() {
        val phone = PhoneNumber.of("010-1111-2222")
        service.subscribe(
            SubscribeCommand(
                phoneNumber = phone,
                channelId = 1,
                targetStatus = SubscriptionStatus.STANDARD,
                idempotencyKey = null,
            ),
        )
        val member = members.findByPhoneNumber(phone.value)!!
        assertEquals(SubscriptionStatus.STANDARD, member.currentStatus)
        assertEquals(1, histories.findAllByPhoneNumberOrderByOccurredAtAsc(phone.value).size)
    }

    @Test
    fun `csrng 가 ROLLBACK 을 지시하면 회원과 이력 둘 다 저장되지 않는다`() {
        val phone = PhoneNumber.of("010-1111-3333")
        decisionPort.nextDecision = FakeDecisionPort.Decision.ROLLBACK
        assertThrows(ExternalDecisionRejectedException::class.java) {
            service.subscribe(
                SubscribeCommand(phone, 1, SubscriptionStatus.STANDARD, null),
            )
        }
        assertEquals(null, members.findByPhoneNumber(phone.value))
        assertEquals(0, histories.findAllByPhoneNumberOrderByOccurredAtAsc(phone.value).size)
    }

    @Test
    fun `csrng 장애가 발생하면 ExternalDecisionUnavailableException 이 전파되고 이력이 없다`() {
        val phone = PhoneNumber.of("010-1111-4444")
        decisionPort.nextDecision = FakeDecisionPort.Decision.UNAVAILABLE
        assertThrows(ExternalDecisionUnavailableException::class.java) {
            service.subscribe(
                SubscribeCommand(phone, 1, SubscriptionStatus.STANDARD, null),
            )
        }
        assertEquals(null, members.findByPhoneNumber(phone.value))
    }

    @Test
    fun `구독만 가능한 채널로 해지 요청 시 ChannelNotAllowedException`() {
        val phone = PhoneNumber.of("010-2222-3333")
        service.subscribe(
            SubscribeCommand(phone, 1, SubscriptionStatus.PREMIUM, null),
        )
        // 채널 id 3 = 네이버 (SUBSCRIBE_ONLY)
        assertThrows(ChannelNotAllowedException::class.java) {
            service.cancel(
                CancelCommand(phone, 3, SubscriptionStatus.STANDARD, null),
            )
        }
    }

    @Test
    fun `PREMIUM 으로 재구독 시도는 IllegalTransitionException`() {
        val phone = PhoneNumber.of("010-2222-4444")
        service.subscribe(
            SubscribeCommand(phone, 1, SubscriptionStatus.PREMIUM, null),
        )
        assertThrows(IllegalTransitionException::class.java) {
            service.subscribe(
                SubscribeCommand(phone, 1, SubscriptionStatus.PREMIUM, null),
            )
        }
    }

    @Test
    fun `등록되지 않은 회원 해지 요청 시 MemberNotFoundException`() {
        val phone = PhoneNumber.of("010-5555-6666")
        // 채널 id 5 = 콜센터 (CANCEL_ONLY)
        assertThrows(MemberNotFoundException::class.java) {
            service.cancel(
                CancelCommand(phone, 5, SubscriptionStatus.NONE, null),
            )
        }
    }

    @Test
    fun `SKT 채널은 구독과 해지 둘 다 가능하다`() {
        val phone = PhoneNumber.of("010-3333-4444")
        // 채널 id 4 = SKT (SUBSCRIBE_AND_CANCEL)
        service.subscribe(
            SubscribeCommand(phone, 4, SubscriptionStatus.STANDARD, null),
        )
        service.cancel(
            CancelCommand(phone, 4, SubscriptionStatus.NONE, null),
        )
        val member = members.findByPhoneNumber(phone.value)!!
        assertEquals(SubscriptionStatus.NONE, member.currentStatus)
        assertEquals(2, histories.findAllByPhoneNumberOrderByOccurredAtAsc(phone.value).size)
    }

    @Test
    fun `PREMIUM 에서 STANDARD 로 다운그레이드는 IllegalTransitionException`() {
        val phone = PhoneNumber.of("010-4444-5555")
        service.subscribe(
            SubscribeCommand(phone, 1, SubscriptionStatus.PREMIUM, null),
        )
        assertThrows(IllegalTransitionException::class.java) {
            service.cancel(
                CancelCommand(phone, 1, SubscriptionStatus.STANDARD, null),
            )
        }
    }

    @Test
    fun `동일 Idempotency-Key 재요청은 기존 이력을 반환한다`() {
        val phone = PhoneNumber.of("010-7777-8888")
        val key = "test-key-001"
        val first =
            service.subscribe(
                SubscribeCommand(phone, 1, SubscriptionStatus.STANDARD, key),
            )
        // 두 번째 호출에서 decisionPort 가 ROLLBACK 으로 설정돼도 이미 저장된 이력을 반환해야 함
        decisionPort.nextDecision = FakeDecisionPort.Decision.ROLLBACK
        val second =
            service.subscribe(
                SubscribeCommand(phone, 1, SubscriptionStatus.STANDARD, key),
            )
        assertEquals(first.historyId, second.historyId)
        assertEquals(1, histories.findAllByPhoneNumberOrderByOccurredAtAsc(phone.value).size)
    }
}
