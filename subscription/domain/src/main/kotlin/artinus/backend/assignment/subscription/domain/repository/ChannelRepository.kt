package artinus.backend.assignment.subscription.domain.repository

import artinus.backend.assignment.subscription.domain.model.Channel

interface ChannelRepository {
    fun findById(id: Long): Channel?

    fun findAll(): List<Channel>
}
