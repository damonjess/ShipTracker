package com.example.shiptracker.util

object FerryDatabase {
    // Returns the max passenger capacity based on the ship's name or IMO
    fun getCapacity(shipName: String): Int? {
        val name = shipName.uppercase().trim()

        return when {
            // P&O Ferries (Hull to Rotterdam)
            name.contains("PRIDE OF HULL") -> 1360
            name.contains("PRIDE OF ROTTERDAM") -> 1360

            // Stena Line (Hook of Holland)
            name.contains("STENA TRANSPORTER") -> 300
            name.contains("STENA BRITANNICA") -> 1200
            name.contains("STENA HOLLANDICA") -> 1200

            // DFDS Seaways (Newcastle/Amsterdam)
            name.contains("KING SEAWAYS") -> 1325
            name.contains("PRINCESS SEAWAYS") -> 1250

            // Return null if it's a ship we haven't manually added yet
            else -> null
        }
    }
}
