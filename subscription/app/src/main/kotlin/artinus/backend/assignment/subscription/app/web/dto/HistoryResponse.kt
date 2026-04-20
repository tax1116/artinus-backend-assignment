package artinus.backend.assignment.subscription.app.web.dto

data class HistoryResponse(
    val phoneNumber: String,
    val history: List<HistoryItem>,
    val summary: String,
)
