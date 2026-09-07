package com.example.shiptracker.data

enum class ShipCategory(val displayName: String, val typeCodes: IntRange) {
    CARGO("Cargo", 70..79),
    TANKER("Tanker", 80..89),
    PASSENGER("Passenger", 60..69),
    TUG("Tugs", 31..32),
    YACHT("Yachts", 36..37)
}
