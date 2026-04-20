package artinus.backend.assignment.subscription.app.infra.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface MemberJpaRepository : JpaRepository<MemberEntity, Long> {
    fun findByPhoneNumber(phoneNumber: String): MemberEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByPhoneNumber(phoneNumber: String): MemberEntity?
}
