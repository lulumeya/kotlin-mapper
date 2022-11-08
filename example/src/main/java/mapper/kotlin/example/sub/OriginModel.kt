package mapper.kotlin.example.sub

data class OriginModel(
    val id: Long,
    val name: String,
    val budget: Long,
    val remains: Long?,
    val loan: Long?
)