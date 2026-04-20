package artinus.backend.assignment.subscription.domain.policy

import artinus.backend.assignment.subscription.domain.exception.IllegalTransitionException
import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus.NONE
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus.PREMIUM
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus.STANDARD
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SubscriptionTransitionPolicyTest {
    @Nested
    inner class Subscribe {
        @Test
        fun `NONE 에서 STANDARD 또는 PREMIUM 으로 전이 가능`() {
            SubscriptionTransitionPolicy.assertSubscribe(NONE, STANDARD)
            SubscriptionTransitionPolicy.assertSubscribe(NONE, PREMIUM)
        }

        @Test
        fun `STANDARD 에서 PREMIUM 으로만 전이 가능`() {
            SubscriptionTransitionPolicy.assertSubscribe(STANDARD, PREMIUM)
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertSubscribe(STANDARD, NONE)
            }
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertSubscribe(STANDARD, STANDARD)
            }
        }

        @Test
        fun `PREMIUM 은 구독으로 더 이상 전이 불가`() {
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertSubscribe(PREMIUM, PREMIUM)
            }
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertSubscribe(PREMIUM, STANDARD)
            }
        }

        @Test
        fun `NONE 에서 NONE 자체는 구독으로 전이 불가`() {
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertSubscribe(NONE, NONE)
            }
        }
    }

    @Nested
    inner class Cancel {
        @Test
        fun `PREMIUM 은 NONE 으로만 해지 가능 (다운그레이드 금지)`() {
            SubscriptionTransitionPolicy.assertCancel(PREMIUM, NONE)
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertCancel(PREMIUM, STANDARD)
            }
        }

        @Test
        fun `STANDARD 는 NONE 로만 해지 가능`() {
            SubscriptionTransitionPolicy.assertCancel(STANDARD, NONE)
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertCancel(STANDARD, PREMIUM)
            }
        }

        @Test
        fun `NONE 은 해지 불가`() {
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertCancel(NONE, NONE)
            }
            assertThrows(IllegalTransitionException::class.java) {
                SubscriptionTransitionPolicy.assertCancel(NONE, STANDARD)
            }
        }
    }

    @Nested
    inner class ActionFor {
        @Test
        fun `상향 전이는 SUBSCRIBE`() {
            assertEquals(HistoryAction.SUBSCRIBE, SubscriptionTransitionPolicy.actionFor(NONE, STANDARD))
            assertEquals(HistoryAction.SUBSCRIBE, SubscriptionTransitionPolicy.actionFor(NONE, PREMIUM))
            assertEquals(HistoryAction.SUBSCRIBE, SubscriptionTransitionPolicy.actionFor(STANDARD, PREMIUM))
        }

        @Test
        fun `하향 전이는 CANCEL`() {
            assertEquals(HistoryAction.CANCEL, SubscriptionTransitionPolicy.actionFor(PREMIUM, NONE))
            assertEquals(HistoryAction.CANCEL, SubscriptionTransitionPolicy.actionFor(STANDARD, NONE))
        }
    }
}
