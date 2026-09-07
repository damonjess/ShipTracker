package com.example.shiptracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.shiptracker.R
import kotlin.math.roundToInt

object MarkerIconGenerator {
    const val MARKER_SIZE_DP = 48f
    private val iconCache = mutableMapOf<Int, Drawable>()

    fun getTintedShipIcon(context: Context, colorInt: Int): Drawable {
        return iconCache.getOrPut(colorInt) {
            val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
                ?: return@getOrPut ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!

            DrawableCompat.setTint(vectorDrawable, colorInt)

            // Force a standard 48dp touch target size
            val density = context.resources.displayMetrics.density
            val sizePx = (48 * density).toInt() 

            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw the vector exactly to our bounding box
            vectorDrawable.setBounds(0, 0, sizePx, sizePx)
            vectorDrawable.draw(canvas)

            BitmapDrawable(context.resources, bitmap).apply {
                // CRITICAL: Tell osmdroid exactly how big this is so it doesn't double-scale
                setBounds(0, 0, sizePx, sizePx)
            }
        }
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
