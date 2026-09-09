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
    val length: Int = 0, // Calculated from dimensions A + B
    val width: Int = 0, // Calculated from dimensions C + D
    val heading: Float = 0f, // True Heading
    val cog: Float = 0f, // Course Over Ground
    val speed: Float = 0f,
    val destination: String = "UNKNOWN",
    val draught: Float = 0f,
    val navStatus: Int = 15, // 15 means 'Undefined' in AIS protocol
    val imo: Long = 0L,
    val callSign: String = "",
    val rot: Int = -128, // Rate of turn
    val eta: String = "",
    val transponderClass: String = "Class A",
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val isSatelliteAis: Boolean = false,
    val distanceFromShoreNm: Double = 0.0,
    val trackingSource: String = "Terrestrial AIS",
    val satelliteConstellation: String = "",
    val satelliteSignalQuality: String = "",
    val oceanZone: String = ""
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
    @SerialName("MessageType") val messageType: String = "",
    @SerialName("MetaData") val metaData: AisMetaData? = null,
    @SerialName("Message") val message: AisMessagePayload? = null
)

@Serializable
data class AisMetaData(
    @SerialName("MMSI") val mmsi: Long = 0L,
    @SerialName("ShipName") val shipName: String = "",
    @SerialName("Latitude") val latitude: Double = 0.0,
    @SerialName("Longitude") val longitude: Double = 0.0,
    @SerialName("latitude") val latitudeAlt: Double? = null,
    @SerialName("longitude") val longitudeAlt: Double? = null
) {
    val effectiveLatitude: Double
        get() = if (latitude != 0.0) latitude else (latitudeAlt ?: 0.0)
    val effectiveLongitude: Double
        get() = if (longitude != 0.0) longitude else (longitudeAlt ?: 0.0)
}

@Serializable
data class AisMessagePayload(
    @SerialName("ShipStaticData") val shipStaticData: ShipStaticData? = null,
    @SerialName("PositionReport") val positionReport: PositionReport? = null,
    @SerialName("StandardClassBPositionReport") val standardClassBPositionReport: PositionReport? = null,
    @SerialName("ExtendedClassBPositionReport") val extendedClassBPositionReport: PositionReport? = null
) {
    val effectivePositionReport: PositionReport?
        get() = positionReport ?: standardClassBPositionReport ?: extendedClassBPositionReport

    val isClassB: Boolean
        get() = standardClassBPositionReport != null || extendedClassBPositionReport != null
}

@Serializable
data class PositionReport(
    @SerialName("Cog") val cog: Float = 0f,
    @SerialName("Sog") val sog: Float = 0f, // Speed Over Ground
    @SerialName("TrueHeading") val trueHeading: Int = 511,
    @SerialName("NavigationalStatus") val navStatus: Int = 15,
    @SerialName("RateOfTurn") val rateOfTurn: Int = -128,
    @SerialName("Latitude") val latitude: Double? = null,
    @SerialName("Longitude") val longitude: Double? = null
)

@Serializable
data class ShipStaticData(
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: Int = 0,
    @SerialName("ImoNumber") val imoNumber: Long = 0L,
    @SerialName("CallSign") val callSign: String = "",
    @SerialName("Dimension") val dimension: ShipDimension? = null,
    @SerialName("Destination") val destination: String = "",
    @SerialName("MaximumStaticDraught") val draught: Float = 0f,
    @SerialName("Eta") val eta: AisEta? = null
)

@Serializable
data class AisEta(
    @SerialName("Month") val month: Int = 0,
    @SerialName("Day") val day: Int = 0,
    @SerialName("Hour") val hour: Int = 0,
    @SerialName("Minute") val minute: Int = 0
)

@Serializable
data class ShipDimension(
    @SerialName("A") val toBow: Int = 0,
    @SerialName("B") val toStern: Int = 0,
    @SerialName("C") val toPort: Int = 0,
    @SerialName("D") val toStarboard: Int = 0
)
