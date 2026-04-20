package artinus.backend.assignment.subscription.app.infra.csrng

import artinus.backend.assignment.subscription.app.application.port.ExternalDecisionUnavailableException
import artinus.backend.assignment.subscription.app.application.port.RandomDecisionPort
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.timelimiter.TimeLimiter
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.concurrent.CompletableFuture

private val log = KotlinLogging.logger {}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CsrngResponseItem(
    val status: String? = null,
    val min: Int? = null,
    val max: Int? = null,
    val random: Int? = null,
    val code: String? = null,
    val reason: String? = null,
)

@Component
class CsrngClient(
    private val restClient: RestClient,
    private val circuitBreaker: CircuitBreaker,
    private val retry: Retry,
    private val timeLimiter: TimeLimiter,
    private val props: CsrngProperties,
) : RandomDecisionPort {
    override fun decide(idempotencyKey: String): Boolean {
        val supplier = {
            CompletableFuture.supplyAsync { callOnce(idempotencyKey) }
        }
        val decorated =
            Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(circuitBreaker) {
                    timeLimiter.executeFutureSupplier(supplier)
                },
            )
        return try {
            decorated.get()
        } catch (e: ExternalRejectionException) {
            // random=0 → 롤백 의도를 표현. 호출자가 false 로 받아 처리.
            log.info { "csrng returned rejection (random=0) idemKey=$idempotencyKey" }
            false
        } catch (e: CallNotPermittedException) {
            handleOutage("circuit-open", e, idempotencyKey)
        } catch (e: Throwable) {
            // TimeoutException, IOException, RestClientResponseException 등
            handleOutage(e.javaClass.simpleName, e, idempotencyKey)
        }
    }

    private fun handleOutage(
        tag: String,
        cause: Throwable,
        idempotencyKey: String,
    ): Boolean {
        log.warn(cause) { "csrng outage [$tag] idemKey=$idempotencyKey" }
        if (props.bypassOnOutage) {
            log.warn { "csrng bypass enabled → treating as COMMIT for key=$idempotencyKey" }
            return true
        }
        throw ExternalDecisionUnavailableException(
            "csrng unavailable ($tag)",
            cause,
        )
    }

    private fun callOnce(idempotencyKey: String): Boolean {
        log.debug { "csrng call idemKey=$idempotencyKey" }
        val items: List<CsrngResponseItem> =
            restClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/csrng/csrng.php")
                        .queryParam("min", 0)
                        .queryParam("max", 1)
                        .build()
                }.header("X-Idempotency-Key", idempotencyKey)
                .retrieve()
                .body(object : ParameterizedTypeReference<List<CsrngResponseItem>>() {})
                ?: emptyList()
        val first =
            items.firstOrNull()
                ?: throw IllegalStateException("csrng empty response")
        if (first.status != null && first.status != "success") {
            // csrng 가 success 외의 상태 (예: rate-limit) → 재시도 대상
            throw IllegalStateException("csrng status=${first.status} code=${first.code} reason=${first.reason}")
        }
        val random =
            first.random
                ?: throw IllegalStateException("csrng response missing random field")
        return when (random) {
            1 -> true
            0 -> throw ExternalRejectionException("csrng random=0")
            else -> throw IllegalStateException("csrng unexpected random=$random")
        }
    }
}
