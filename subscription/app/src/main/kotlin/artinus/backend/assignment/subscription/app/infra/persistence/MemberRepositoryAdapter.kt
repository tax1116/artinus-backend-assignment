package artinus.backend.assignment.subscription.app.infra.persistence

import artinus.backend.assignment.subscription.domain.model.Member
import artinus.backend.assignment.subscription.domain.model.PhoneNumber
import artinus.backend.assignment.subscription.domain.repository.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MemberRepositoryAdapter(
    private val jpa: MemberJpaRepository,
) : MemberRepository {
    override fun findForUpdateByPhoneNumber(phoneNumber: PhoneNumber): Member? = jpa.findForUpdateByPhoneNumber(phoneNumber.value)?.toDomain()

    override fun save(member: Member): Member {
        val id = member.id ?: return jpa.save(MemberEntity.fromDomain(member)).toDomain()
        // 동일 트랜잭션의 findForUpdate 결과가 L1 영속성 컨텍스트에 살아 있으므로 추가 SELECT 없음.
        // syncFrom 후 dirty checking 이 UPDATE 를 발행하고 @Version 이 증가한다.
        val entity =
            jpa.findByIdOrNull(id)
                ?: error("member id=$id not found in current persistence context")
        entity.syncFrom(member)
        return entity.toDomain()
    }
}
