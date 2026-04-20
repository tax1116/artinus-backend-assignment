package artinus.backend.assignment.subscription.domain.model

import artinus.backend.assignment.subscription.domain.exception.ChannelNotAllowedException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ChannelTest {
    @Test
    fun `구독만 가능 채널은 해지 호출 시 예외`() {
        val naver = Channel(id = 3, code = "NAVER", name = "네이버", type = ChannelType.SUBSCRIBE_ONLY)
        naver.assertCanSubscribe()
        assertThrows(ChannelNotAllowedException::class.java) { naver.assertCanCancel() }
    }

    @Test
    fun `해지만 가능 채널은 구독 호출 시 예외`() {
        val callCenter = Channel(id = 5, code = "CALL_CENTER", name = "콜센터", type = ChannelType.CANCEL_ONLY)
        callCenter.assertCanCancel()
        assertThrows(ChannelNotAllowedException::class.java) { callCenter.assertCanSubscribe() }
    }

    @Test
    fun `둘 다 가능 채널은 양쪽 모두 허용`() {
        val homepage = Channel(id = 1, code = "HOMEPAGE", name = "홈페이지", type = ChannelType.SUBSCRIBE_AND_CANCEL)
        homepage.assertCanSubscribe()
        homepage.assertCanCancel()
    }
}
