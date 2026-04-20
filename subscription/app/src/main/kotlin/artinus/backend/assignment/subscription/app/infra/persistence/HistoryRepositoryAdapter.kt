package artinus.backend.assignment.subscription.app.infra.persistence

import artinus.backend.assignment.subscription.domain.exception.ChannelNotFoundException
import artinus.backend.assignment.subscription.domain.model.Channel
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import artinus.backend.assignment.subscription.domain.model.SubscriptionHistory
import artinus.backend.assignment.subscription.domain.repository.AppendHistoryCommand
import artinus.backend.assignment.subscription.domain.repository.HistoryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class HistoryRepositoryAdapter(
    private val jpa: HistoryJpaRepository,
    private val channelJpa: ChannelJpaRepository,
) : HistoryRepository {
    override fun findByIdempotencyKey(idempotencyKey: String): SubscriptionHistory? {
        val entity = jpa.findByIdempotencyKey(idempotencyKey) ?: return null
        return entity.toDomain()
    }

    override fun findAllByPhoneNumberOrderByOccurredAtAsc(phoneNumber: PhoneNumber): List<SubscriptionHistory> {
        val rows = jpa.findAllByPhoneNumberOrderByOccurredAtAsc(phoneNumber.value)
        val channelIds = rows.map { it.channelId }.toSet()
        val channels = channelJpa.findAllById(channelIds).associateBy({ it.id }, { it.toDomain() })
        return rows.map { it.toDomain(channels) }
    }

    override fun append(command: AppendHistoryCommand): SubscriptionHistory {
        val saved =
            jpa.save(
                HistoryEntity(
                    phoneNumber = command.phoneNumber.value,
                    channelId = command.channel.id,
                    action = command.transition.action,
                    fromStatus = command.transition.from,
                    toStatus = command.transition.to,
                    occurredAt = command.occurredAt,
                    idempotencyKey = command.idempotencyKey,
                ),
            )
        return SubscriptionHistory(
            id = saved.id ?: error("history id must be assigned after save"),
            phoneNumber = command.phoneNumber,
            channel = command.channel,
            action = saved.action,
            fromStatus = saved.fromStatus,
            toStatus = saved.toStatus,
            occurredAt = saved.occurredAt,
        )
    }

    private fun HistoryEntity.toDomain(): SubscriptionHistory {
        val channel =
            channelJpa.findByIdOrNull(channelId)?.toDomain()
                ?: throw ChannelNotFoundException("channel id=$channelId not found")
        return SubscriptionHistory(
            id = id ?: error("history id must be assigned after save"),
            phoneNumber = PhoneNumber.of(phoneNumber),
            channel = channel,
            action = action,
            fromStatus = fromStatus,
            toStatus = toStatus,
            occurredAt = occurredAt,
        )
    }

    private fun HistoryEntity.toDomain(channelsById: Map<Long, Channel>): SubscriptionHistory {
        val channel =
            channelsById[channelId]
                ?: throw ChannelNotFoundException("channel id=$channelId not found")
        return SubscriptionHistory(
            id = id ?: error("history id must be assigned after save"),
            phoneNumber = PhoneNumber.of(phoneNumber),
            channel = channel,
            action = action,
            fromStatus = fromStatus,
            toStatus = toStatus,
            occurredAt = occurredAt,
        )
    }
}
