package artinus.backend.assignment.subscription.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PhoneNumberTest {
    @Test
    fun `하이픈 정규화`() {
        assertEquals("01012345678", PhoneNumber.of("010-1234-5678").value)
        assertEquals("01012345678", PhoneNumber.of("01012345678").value)
        assertEquals("01012345678", PhoneNumber.of("010 1234 5678").value)
    }

    @Test
    fun `유효하지 않은 번호는 거부`() {
        assertThrows(IllegalArgumentException::class.java) { PhoneNumber.of("02-1234-5678") }
        assertThrows(IllegalArgumentException::class.java) { PhoneNumber.of("010-12-5678") }
        assertThrows(IllegalArgumentException::class.java) { PhoneNumber.of("abcd") }
    }

    @Test
    fun `마스킹 형식`() {
        assertEquals("010-****-5678", PhoneNumber.of("010-1234-5678").masked())
    }
}
