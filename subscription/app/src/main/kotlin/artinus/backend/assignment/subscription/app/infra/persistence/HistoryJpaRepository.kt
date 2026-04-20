package artinus.backend.assignment.subscription.app.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface HistoryJpaRepository : JpaRepository<HistoryEntity, Long> {
    fun findAllByPhoneNumberOrderByOccurredAtAsc(phoneNumber: String): List<HistoryEntity>

    fun findByIdempotencyKey(idempotencyKey: String): HistoryEntity?
}
