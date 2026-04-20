package artinus.backend.assignment.subscription.domain.model

@JvmInline
value class PhoneNumber private constructor(
    val value: String,
) {
    override fun toString(): String = value

    fun masked(): String {
        if (value.length < 7) return value
        val tail = value.takeLast(4)
        val head = value.take(3)
        return "$head-****-$tail"
    }

    companion object {
        private val PATTERN = Regex("^01[016789]\\d{7,8}$")

        fun of(raw: String): PhoneNumber {
            val normalized = raw.replace("-", "").replace(" ", "")
            require(PATTERN.matches(normalized)) {
                "휴대폰 번호 형식이 올바르지 않습니다: $raw"
            }
            return PhoneNumber(normalized)
        }
    }
}
