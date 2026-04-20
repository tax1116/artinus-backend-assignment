package artinus.backend.assignment.subscription.app.infra.persistence

import artinus.backend.assignment.subscription.domain.model.Channel
import artinus.backend.assignment.subscription.domain.model.ChannelType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "channel")
class ChannelEntity(
    @Id
    @Column(name = "id")
    val id: Long,
    @Column(name = "code", nullable = false, unique = true, length = 32)
    val code: String,
    @Column(name = "name", nullable = false, length = 64)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    val type: ChannelType,
) {
    fun toDomain(): Channel = Channel(id = id, code = code, name = name, type = type)
}
