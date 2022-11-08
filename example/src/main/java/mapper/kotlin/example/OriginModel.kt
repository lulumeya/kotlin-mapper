package mapper.kotlin.example

import mapper.kotlin.Mapper

@Mapper(
    mapper.kotlin.example.sub.OriginModel::class,
    DestinationModel::class,
    DestinationModel2::class,
    DestinationModel3::class
)
data class OriginModel(
    val id: Long,
    val name: String,
    val budget: Long,
    val remains: Long?,
    val loan: Long?
)

data class DestinationModel(
    val id: Long,
    val name: String,
    val budget: Long,
    val remains: Long?,
    val loan: Long?,
    val rich: Boolean
)

data class DestinationModel2(
    val id: Long,
    val name: String,
    val budget: Long,
    val remains: Long?,
    val loan: Long?,
    val rich: Boolean,
    val happy: Boolean?
)

data class DestinationModel3(
    val id: Long,
    val name: String,
    val budget: Long,
    val remains: Long?,
    val loan: Long?,
    val rich: Boolean,
    val happy: Boolean?,
    val retired: Boolean
)
