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
    private val bitmapCache = mutableMapOf<Int, Bitmap>()

    fun getTintedShipIcon(context: Context, colorInt: Int): Drawable {
        val bitmap = bitmapCache.getOrPut(colorInt) {
            val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
                ?: ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!

            DrawableCompat.setTint(vectorDrawable, colorInt)

            // Drop this from 48 down to 24 for a much sharper, professional map aesthetic
            val density = context.resources.displayMetrics.density
            val sizePx = (24 * density).toInt() 

            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            
            vectorDrawable.setBounds(0, 0, sizePx, sizePx)
            vectorDrawable.draw(canvas)
            bmp
        }

        // Return a fresh wrapper so Osmdroid safely manages the bounds internally
        return BitmapDrawable(context.resources, bitmap)
    }

    fun getShipAndroidColor(aisTypeCode: Int): Int {
        return when (aisTypeCode) {
            in 30..30 -> Color.CYAN
            in 31..32 -> Color.MAGENTA
            in 36..37 -> Color.YELLOW
            in 60..69 -> Color.BLUE
            in 70..79 -> Color.GREEN
            in 80..89 -> Color.RED
            else -> Color.GRAY
        }
    }
}
