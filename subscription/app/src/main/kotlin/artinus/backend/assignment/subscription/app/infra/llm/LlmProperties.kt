package artinus.backend.assignment.subscription.app.infra.llm

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "llm")
data class LlmProperties(
    val provider: Provider = Provider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = "claude-haiku-4-5-20251001",
    val baseUrl: String = "https://api.anthropic.com",
    val timeout: Duration = Duration.ofSeconds(8),
    val maxTokens: Int = 512,
) {
    enum class Provider { ANTHROPIC, NONE }

    fun enabled(): Boolean = provider == Provider.ANTHROPIC && apiKey.isNotBlank()
}
