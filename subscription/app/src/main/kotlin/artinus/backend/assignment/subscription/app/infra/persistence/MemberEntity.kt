package artinus.backend.assignment.subscription.app.infra.persistence

import artinus.backend.assignment.subscription.domain.model.Member
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import artinus.backend.assignment.subscription.domain.model.SubscriptionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(name = "member")
class MemberEntity(
    @Column(name = "phone_number", nullable = false, unique = true, length = 11)
    val phoneNumber: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 16)
    var currentStatus: SubscriptionStatus,
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,
) {
    fun toDomain(): Member =
        Member.existing(
            id = id ?: error("MemberEntity.id must be assigned before converting to domain"),
            phoneNumber = PhoneNumber.of(phoneNumber),
            currentStatus = currentStatus,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
        )

    fun syncFrom(member: Member) {
        currentStatus = member.currentStatus
        updatedAt = member.updatedAt
    }

    companion object {
        fun fromDomain(member: Member): MemberEntity =
            MemberEntity(
                phoneNumber = member.phoneNumber.value,
                currentStatus = member.currentStatus,
                createdAt = member.createdAt,
                updatedAt = member.updatedAt,
            )
    }
}
