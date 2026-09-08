package com.loldraft.data.soloq.riot

import kotlinx.serialization.Serializable

@Serializable
data class RiotAccountDto(
    val puuid: String,
    val gameName: String,
    val tagLine: String,
)

@Serializable
data class RiotParticipantDto(
    val puuid: String,
    val championId: Int = 0,
    val championName: String = "",
    val teamPosition: String = "",
    val individualPosition: String = "",
    val win: Boolean = false,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val totalMinionsKilled: Int = 0,
    val neutralMinionsKilled: Int = 0,
)

@Serializable
data class RiotInfoDto(
    val gameCreation: Long = 0L,
    val gameDuration: Long = 0L,
    val gameEndTimestamp: Long = 0L,
    val gameVersion: String = "",
    val queueId: Int = 420,
    val participants: List<RiotParticipantDto> = emptyList(),
)

@Serializable
data class RiotMetadataDto(
    val matchId: String = "",
    val participants: List<String> = emptyList(),
)

@Serializable
data class RiotMatchDto(
    val metadata: RiotMetadataDto = RiotMetadataDto(),
    val info: RiotInfoDto = RiotInfoDto(),
)
