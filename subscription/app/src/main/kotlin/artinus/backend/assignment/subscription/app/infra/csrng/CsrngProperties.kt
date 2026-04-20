package artinus.backend.assignment.subscription.app.infra.csrng

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "csrng")
data class CsrngProperties(
    val baseUrl: String = "https://csrng.net",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val readTimeout: Duration = Duration.ofSeconds(3),
    val totalTimeout: Duration = Duration.ofSeconds(4),
    val retry: RetryProps = RetryProps(),
    val circuitBreaker: CircuitBreakerProps = CircuitBreakerProps(),
    val bypassOnOutage: Boolean = false,
) {
    data class RetryProps(
        val maxAttempts: Int = 3,
        val initialBackoff: Duration = Duration.ofMillis(200),
        val maxBackoff: Duration = Duration.ofSeconds(2),
        val multiplier: Double = 2.0,
    )

    data class CircuitBreakerProps(
        val failureRateThreshold: Float = 50f,
        val slowCallRateThreshold: Float = 80f,
        val slowCallDurationThreshold: Duration = Duration.ofSeconds(3),
        val minimumNumberOfCalls: Int = 10,
        val slidingWindowSize: Int = 20,
        val waitDurationInOpenState: Duration = Duration.ofSeconds(30),
        val permittedCallsInHalfOpenState: Int = 3,
    )
}
