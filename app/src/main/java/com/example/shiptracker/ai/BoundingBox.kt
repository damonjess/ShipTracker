package com.example.shiptracker.ai

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val vesselClass: String // e.g., "Cargo", "Tanker", "Yacht"
)
