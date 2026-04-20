package artinus.backend.assignment.subscription.app.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChannelJpaRepository : JpaRepository<ChannelEntity, Long> {
    fun findByCode(code: String): ChannelEntity?
}
