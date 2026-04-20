package artinus.backend.assignment.subscription.app.application.port

/**
 * 외부 난수 기반 커밋/롤백 결정자.
 * 구현체는 csrng.net 과 같은 외부 API 호출을 캡슐화하고, 장애 시 예외를 던진다.
 */
interface RandomDecisionPort {
    /**
     * @param idempotencyKey 동일 키로의 재호출에 대해 일관된 응답을 반환하도록 구현해야 한다
     * @return true 면 트랜잭션 커밋, false 면 롤백이 의도된 결정
     * @throws ExternalDecisionUnavailableException 외부 시스템 장애로 결정을 받지 못한 경우
     */
    fun decide(idempotencyKey: String): Boolean
}

class ExternalDecisionUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
