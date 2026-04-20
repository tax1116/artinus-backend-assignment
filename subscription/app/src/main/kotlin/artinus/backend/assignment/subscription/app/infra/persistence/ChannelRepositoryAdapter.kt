package artinus.backend.assignment.subscription.app.infra.persistence

import artinus.backend.assignment.subscription.domain.model.Channel
import artinus.backend.assignment.subscription.domain.repository.ChannelRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ChannelRepositoryAdapter(
    private val jpa: ChannelJpaRepository,
) : ChannelRepository {
    override fun findById(id: Long): Channel? = jpa.findByIdOrNull(id)?.toDomain()

    override fun findAll(): List<Channel> = jpa.findAll().map { it.toDomain() }
}
