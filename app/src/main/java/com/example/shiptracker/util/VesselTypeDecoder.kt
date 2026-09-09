package com.example.shiptracker.util

object VesselTypeDecoder {
    fun getGeneralVesselType(aisTypeCode: Int): String {
        return when (aisTypeCode) {
            30 -> "Fishing"
            31, 32, 52 -> "Tug"
            36 -> "Sailing"
            37 -> "Pleasure Craft"
            in 40..49 -> "High Speed Craft"
            in 60..69 -> "Passenger"
            in 70..79 -> "Cargo"
            in 80..89 -> "Tanker"
            in 90..99 -> "Special Craft"
            else -> "Cargo"
        }
    }

    fun getDetailedVesselType(aisTypeCode: Int, shipName: String = ""): String {
        val lowerName = shipName.lowercase()
        return when {
            lowerName.contains("container") -> "Container Ship"
            lowerName.contains("bulk") -> "Self Discharging Bulk Carrier"
            lowerName.contains("tanker") || lowerName.contains("oil") -> "Crude Oil Tanker"
            lowerName.contains("chem") -> "Chemical Tanker"
            lowerName.contains("gas") || lowerName.contains("lng") -> "LNG Carrier"
            lowerName.contains("ferry") -> "Ro-Ro Passenger Ship"
            lowerName.contains("express") || lowerName.contains("fast") -> "High Speed Passenger Craft"
            lowerName.contains("tug") -> "Harbor Tug"
            lowerName.contains("pilot") -> "Pilot Vessel"
            
            else -> when (aisTypeCode) {
                30 -> "Fishing Vessel"
                31, 32, 52 -> "Tug / Towing Vessel"
                33 -> "Dredger"
                34 -> "Diving Support Vessel"
                35 -> "Naval Auxiliary Vessel"
                36 -> "Sailing Vessel"
                37 -> "Yacht / Pleasure Craft"
                40 -> "High Speed Craft"
                50 -> "Pilot Vessel"
                51 -> "Search and Rescue Vessel"
                53 -> "Port Tender"
                55 -> "Patrol Vessel"
                60 -> "Passenger Ship"
                69 -> "Cruise Ship"
                70 -> "General Cargo Ship"
                71 -> "Container Ship"
                79 -> "Self Discharging Bulk Carrier"
                80 -> "Oil Tanker"
                81, 82 -> "Chemical Tanker"
                89 -> "LPG Tanker"
                90 -> "Offshore Supply Vessel"
                else -> decodeType(aisTypeCode)
            }
        }
    }

    fun decodeType(aisTypeCode: Int): String {
        return when (aisTypeCode) {
            0 -> "Cargo vessel"
            in 20..29 -> "Wing in Ground (WIG)"
            30 -> "Fishing vessel"
            31, 32 -> "Towing / Tug"
            33 -> "Dredging or underwater ops"
            34 -> "Diving ops"
            35 -> "Military ops"
            36 -> "Sailing vessel"
            37 -> "Pleasure Craft"
            in 40..49 -> "High Speed Craft (HSC)"
            50 -> "Pilot Vessel"
            51 -> "Search and Rescue vessel"
            52 -> "Tug"
            53 -> "Port Tender"
            54 -> "Anti-pollution equipment"
            55 -> "Law Enforcement"
            58 -> "Medical Transport"
            59 -> "Noncombatant ship"
            in 60..69 -> "Passenger vessel"
            in 70..79 -> "Cargo vessel"
            in 80..89 -> "Tanker"
            in 90..99 -> "Other / Special"
            else -> "Cargo vessel"
        }
    }
}
