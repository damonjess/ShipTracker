package com.example.shiptracker.data

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 1. The Unified State Model for your Compose UI
data class ShipState(
    val mmsi: Long,
    val latitude: Double,
    val longitude: Double,
    val name: String = "Unknown",
    val shipType: Int = 0, // AIS sends types as integer codes (e.g., 70 = Cargo)
    val length: Int = 0, // Calculated from dimensions
    val heading: Float = 0f
) : ClusterItem {
    // Required overrides for the Clustering engine
    override val position: LatLng
        get() = LatLng(latitude, longitude)
    override val title: String
        get() = name
    override val snippet: String
        get() = "MMSI: $mmsi"
    override val zIndex: Float?
        get() = null
}

// 2. The expanded JSON models to catch the Static Data & Position Report payloads
@Serializable
data class AisStreamMessage(
    @SerialName("MessageType") val messageType: String,
    @SerialName("MetaData") val metaData: AisMetaData,
    @SerialName("Message") val message: AisMessagePayload? = null
)

@Serializable
data class AisMetaData(
    @SerialName("MMSI") val mmsi: Long,
    @SerialName("ShipName") val shipName: String = "",
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class AisMessagePayload(
    @SerialName("ShipStaticData") val shipStaticData: ShipStaticData? = null,
    @SerialName("PositionReport") val positionReport: PositionReport? = null
)

@Serializable
data class PositionReport(
    @SerialName("Cog") val cog: Float = 0f,
    @SerialName("TrueHeading") val trueHeading: Int = 511
)

@Serializable
data class ShipStaticData(
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: Int = 0,
    @SerialName("Dimension") val dimension: ShipDimension? = null
)

@Serializable
data class ShipDimension(
    @SerialName("A") val toBow: Int,
    @SerialName("B") val toStern: Int
)
