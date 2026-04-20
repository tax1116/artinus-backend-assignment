package artinus.backend.assignment.subscription.app.application.port

import artinus.backend.assignment.subscription.domain.model.SubscriptionHistory

fun interface HistorySummarizerPort {
    /**
     * 이력을 한국어 자연어로 요약. LLM 호출 실패 시 null 반환 → 호출자는 폴백 처리.
     */
    fun summarize(histories: List<SubscriptionHistory>): String?
}
