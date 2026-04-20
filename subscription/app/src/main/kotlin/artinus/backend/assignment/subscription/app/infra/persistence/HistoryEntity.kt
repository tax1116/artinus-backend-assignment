package artinus.backend.assignment.subscription.app.infra.persistence

import artinus.backend.assignment.subscription.domain.model.HistoryAction
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(
    name = "subscription_history",
    indexes = [
        Index(name = "idx_history_phone_occurred", columnList = "phone_number,occurred_at"),
        Index(name = "idx_history_idem_key", columnList = "idempotency_key", unique = true),
    ],
)
class HistoryEntity(
    @Column(name = "phone_number", nullable = false, length = 11)
    val phoneNumber: String,
    @Column(name = "channel_id", nullable = false)
    val channelId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    val action: HistoryAction,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 16)
    val fromStatus: SubscriptionStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    val toStatus: SubscriptionStatus,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime,
    @Column(name = "idempotency_key", nullable = false, length = 80)
    val idempotencyKey: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,
)
