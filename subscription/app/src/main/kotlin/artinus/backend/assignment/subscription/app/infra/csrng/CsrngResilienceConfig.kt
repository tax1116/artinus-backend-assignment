package artinus.backend.assignment.subscription.app.infra.csrng

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

@Configuration
class CsrngResilienceConfig(
    private val props: CsrngProperties,
) {
    @Bean
    fun csrngRestClient(): RestClient {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(props.connectTimeout)
                .build()
        val factory =
            JdkClientHttpRequestFactory(httpClient).apply {
                setReadTimeout(props.readTimeout)
            }
        return RestClient
            .builder()
            .baseUrl(props.baseUrl)
            .requestFactory(factory)
            .build()
    }

    @Bean
    fun csrngCircuitBreaker(): CircuitBreaker {
        val config =
            CircuitBreakerConfig
                .custom()
                .failureRateThreshold(props.circuitBreaker.failureRateThreshold)
                .slowCallRateThreshold(props.circuitBreaker.slowCallRateThreshold)
                .slowCallDurationThreshold(props.circuitBreaker.slowCallDurationThreshold)
                .minimumNumberOfCalls(props.circuitBreaker.minimumNumberOfCalls)
                .slidingWindowSize(props.circuitBreaker.slidingWindowSize)
                .waitDurationInOpenState(props.circuitBreaker.waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(props.circuitBreaker.permittedCallsInHalfOpenState)
                // csrng 의 정상 응답(random=0) 은 실패가 아니므로 서킷 통계에 포함하지 않는다.
                .ignoreExceptions(ExternalRejectionException::class.java)
                .build()
        return CircuitBreaker.of("csrng", config)
    }

    @Bean
    fun csrngRetry(): Retry {
        val config =
            RetryConfig
                .custom<Any>()
                .maxAttempts(props.retry.maxAttempts)
                .intervalFunction(
                    io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                        props.retry.initialBackoff.toMillis(),
                        props.retry.multiplier,
                        props.retry.maxBackoff.toMillis(),
                    ),
                ).retryOnException { it !is ExternalRejectionException }
                .build()
        return Retry.of("csrng", config)
    }

    @Bean
    fun csrngTimeLimiter(): TimeLimiter {
        val config =
            TimeLimiterConfig
                .custom()
                .timeoutDuration(props.totalTimeout)
                .cancelRunningFuture(true)
                .build()
        return TimeLimiter.of("csrng", config)
    }
}

/** csrng 가 명시적으로 "롤백" 을 지시한 경우. 재시도 대상이 아님. */
class ExternalRejectionException(
    message: String,
) : RuntimeException(message)
