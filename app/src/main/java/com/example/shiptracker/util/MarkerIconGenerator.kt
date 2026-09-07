package com.example.shiptracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.shiptracker.R

object MarkerIconGenerator {
    // Cache map to prevent recreating bitmaps 10,000 times a second
    private val iconCache = mutableMapOf<Int, Drawable>()

    fun getTintedShipIcon(context: Context, colorInt: Int): Drawable {
        return iconCache.getOrPut(colorInt) {
            // Load custom ship vector shape (points UP/North by default)
            val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
                ?: return@getOrPut ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!

            // Apply dynamic tint color based on ship type
            DrawableCompat.setTint(vectorDrawable, colorInt)

            // Convert to Bitmap for maximum Osmdroid rendering performance
            val bitmap = Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vectorDrawable.draw(canvas)

            BitmapDrawable(context.resources, bitmap)
        }
    }

    // Map AIS integer codes directly to native Android Color integers
    fun getShipAndroidColor(aisTypeCode: Int): Int {
        return when (aisTypeCode) {
            in 30..30 -> Color.CYAN      // Fishing
            in 31..32 -> Color.MAGENTA   // Towing/Tugs
            in 36..37 -> Color.YELLOW    // Pleasure Craft/Yacht
            in 60..69 -> Color.BLUE      // Passenger
            in 70..79 -> Color.GREEN     // Cargo
            in 80..89 -> Color.RED       // Tanker
            else -> Color.GRAY           // Other
        }
    }
}
