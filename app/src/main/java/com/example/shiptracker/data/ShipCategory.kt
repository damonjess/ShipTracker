package com.example.shiptracker.data

enum class ShipCategory(val displayName: String, val typeCodes: IntRange) {
    CARGO("Cargo", 70..79),
    TANKER("Tanker", 80..89),
    PASSENGER("Passenger", 60..69),
    TUG("Tugs", 31..35),
    YACHT("Yachts", 36..37);

    fun matches(aisTypeCode: Int): Boolean {
        return when (this) {
            TANKER -> aisTypeCode in 80..89
            PASSENGER -> aisTypeCode in 60..69 || aisTypeCode in 40..49
            TUG -> aisTypeCode in 31..35 || aisTypeCode in 50..59
            YACHT -> aisTypeCode in 36..37
            CARGO -> aisTypeCode in 70..79 || aisTypeCode == 0 || aisTypeCode in 20..29 || aisTypeCode == 30 || aisTypeCode in 90..99 || (aisTypeCode !in 31..37 && aisTypeCode !in 40..69 && aisTypeCode !in 80..89)
        }
    }
}

