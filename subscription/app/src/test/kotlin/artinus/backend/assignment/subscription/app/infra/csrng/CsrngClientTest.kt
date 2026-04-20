package artinus.backend.assignment.subscription.app.infra.csrng

import artinus.backend.assignment.subscription.app.application.port.ExternalDecisionUnavailableException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.time.Duration

class CsrngClientTest {
    private lateinit var server: MockWebServer
    private lateinit var props: CsrngProperties
    private lateinit var client: CsrngClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        props =
            CsrngProperties(
                baseUrl = server.url("/").toString().trimEnd('/'),
                connectTimeout = Duration.ofSeconds(1),
                readTimeout = Duration.ofSeconds(1),
                totalTimeout = Duration.ofSeconds(2),
                retry = CsrngProperties.RetryProps(maxAttempts = 3, initialBackoff = Duration.ofMillis(10)),
                circuitBreaker =
                    CsrngProperties.CircuitBreakerProps(
                        minimumNumberOfCalls = 100,
                        slidingWindowSize = 100,
                    ),
                bypassOnOutage = false,
            )
        val restClient = RestClient.builder().baseUrl(props.baseUrl).build()
        val cb =
            CircuitBreaker.of(
                "test",
                CircuitBreakerConfig
                    .custom()
                    .minimumNumberOfCalls(props.circuitBreaker.minimumNumberOfCalls)
                    .slidingWindowSize(props.circuitBreaker.slidingWindowSize)
                    .build(),
            )
        val retry =
            Retry.of(
                "test",
                RetryConfig
                    .custom<Any>()
                    .maxAttempts(props.retry.maxAttempts)
                    .intervalFunction(
                        IntervalFunction.ofExponentialBackoff(
                            props.retry.initialBackoff.toMillis(),
                            2.0,
                            props.retry.maxBackoff.toMillis(),
                        ),
                    ).retryOnException { it !is ExternalRejectionException }
                    .build(),
            )
        val tl =
            TimeLimiter.of(
                "test",
                TimeLimiterConfig.custom().timeoutDuration(props.totalTimeout).build(),
            )
        client = CsrngClient(restClient, cb, retry, tl, props)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `random=1 응답은 true`() {
        server.enqueue(jsonOk("""[{"status":"success","min":0,"max":1,"random":1}]"""))
        assertTrue(client.decide("k1"))
    }

    @Test
    fun `random=0 응답은 롤백 의도 → false`() {
        server.enqueue(jsonOk("""[{"status":"success","min":0,"max":1,"random":0}]"""))
        assertFalse(client.decide("k2"))
    }

    @Test
    fun `HTTP 500 은 재시도 후 ExternalDecisionUnavailableException`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) }
        assertThrows(ExternalDecisionUnavailableException::class.java) {
            client.decide("k3")
        }
        assertEquals(3, server.requestCount) // 재시도 3회
    }

    @Test
    fun `첫 호출 실패 후 재시도 성공 시 true`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(jsonOk("""[{"status":"success","random":1}]"""))
        assertTrue(client.decide("k4"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `random=0 이 반복돼도 CircuitBreaker 는 OPEN 되지 않는다`() {
        // 실 운영 설정과 동일하게 ExternalRejectionException 을 CB 집계에서 제외
        val sensitiveCb =
            CircuitBreaker.of(
                "rej-ignore",
                CircuitBreakerConfig
                    .custom()
                    .failureRateThreshold(50.0f)
                    .minimumNumberOfCalls(5)
                    .slidingWindowSize(10)
                    .ignoreExceptions(ExternalRejectionException::class.java)
                    .build(),
            )
        val rejClient =
            CsrngClient(
                RestClient.builder().baseUrl(props.baseUrl).build(),
                sensitiveCb,
                Retry.of("test", RetryConfig.custom<Any>().maxAttempts(1).build()),
                TimeLimiter.of("test", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build()),
                props,
            )
        repeat(10) {
            server.enqueue(jsonOk("""[{"status":"success","min":0,"max":1,"random":0}]"""))
        }
        repeat(10) { assertFalse(rejClient.decide("rej-$it")) }
        assertEquals(CircuitBreaker.State.CLOSED, sensitiveCb.state)
    }

    @Test
    fun `bypassOnOutage true 면 장애 시 true 반환`() {
        val bypassProps = props.copy(bypassOnOutage = true)
        val bypassClient =
            CsrngClient(
                RestClient.builder().baseUrl(bypassProps.baseUrl).build(),
                CircuitBreaker.ofDefaults("test"),
                Retry.of("test", RetryConfig.custom<Any>().maxAttempts(1).build()),
                TimeLimiter.of("test", TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build()),
                bypassProps,
            )
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(bypassClient.decide("k5"))
    }

    private fun jsonOk(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
}
