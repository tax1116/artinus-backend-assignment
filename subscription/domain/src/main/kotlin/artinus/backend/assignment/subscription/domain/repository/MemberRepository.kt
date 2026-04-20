package artinus.backend.assignment.subscription.domain.repository

import artinus.backend.assignment.subscription.domain.model.Member
import artinus.backend.assignment.subscription.domain.model.PhoneNumber

interface MemberRepository {
    fun findForUpdateByPhoneNumber(phoneNumber: PhoneNumber): Member?

    fun save(member: Member): Member
}
