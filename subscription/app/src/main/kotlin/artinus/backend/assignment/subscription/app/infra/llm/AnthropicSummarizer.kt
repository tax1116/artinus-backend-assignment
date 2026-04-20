package artinus.backend.assignment.subscription.app.infra.llm

import artinus.backend.assignment.subscription.app.application.port.HistorySummarizerPort
import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.SubscriptionHistory
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.http.HttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AnthropicMessagesResponse(
    val content: List<ContentBlock> = emptyList(),
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContentBlock(
        val type: String? = null,
        val text: String? = null,
    )
}

@Component
class AnthropicSummarizer(
    private val props: LlmProperties,
) : HistorySummarizerPort {
    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(props.baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(props.timeout).build()).apply {
                    setReadTimeout(props.timeout)
                },
            ).build()
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy년 M월 d일").withZone(ZoneId.of("Asia/Seoul"))

    override fun summarize(histories: List<SubscriptionHistory>): String? {
        if (histories.isEmpty()) return "구독 이력이 없습니다."
        if (!props.enabled()) {
            log.debug { "LLM disabled (provider=${props.provider}, keyPresent=${props.apiKey.isNotBlank()})" }
            return null
        }
        val prompt = buildPrompt(histories)
        return runCatching { callAnthropic(prompt) }
            .onFailure { log.warn(it) { "Anthropic call failed" } }
            .getOrNull()
    }

    private fun callAnthropic(prompt: String): String? {
        val requestBody =
            mapOf(
                "model" to props.model,
                "max_tokens" to props.maxTokens,
                "messages" to
                    listOf(
                        mapOf("role" to "user", "content" to prompt),
                    ),
            )
        val response =
            restClient
                .post()
                .uri("/v1/messages")
                .header("x-api-key", props.apiKey)
                .header("anthropic-version", "2023-06-01")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(requestBody)
                .retrieve()
                .body<AnthropicMessagesResponse>()
        return response
            ?.content
            ?.firstOrNull { it.type == "text" }
            ?.text
            ?.trim()
    }

    private fun buildPrompt(histories: List<SubscriptionHistory>): String {
        val lines =
            histories.joinToString("\n") { h ->
                val date = dateFormatter.format(h.occurredAt)
                val actionLabel = if (h.action == HistoryAction.SUBSCRIBE) "구독" else "해지"
                "$date / 채널=${h.channel.name} / ${label(h.fromStatus)} → ${label(h.toStatus)} / $actionLabel"
            }
        return """
            다음은 한 회원의 구독 이력이다. 사건 순서대로 한국어 자연어 2~4문장으로 요약하라.
            - 날짜, 채널, 상태 변화를 포함할 것
            - 회원의 현재 상태는 마지막 줄의 '→' 다음 상태
            - 개인정보(전화번호 등)는 포함하지 말 것
            - 불필요한 마크다운 없이 평문으로만 작성

            [이력]
            $lines
            """.trimIndent()
    }

    private fun label(status: SubscriptionStatus): String =
        when (status) {
            SubscriptionStatus.NONE -> "구독 안함"
            SubscriptionStatus.STANDARD -> "일반 구독"
            SubscriptionStatus.PREMIUM -> "프리미엄 구독"
        }
}
