package com.example.shiptracker.util

import android.content.Context
import android.graphics.Bitmap
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
    private val iconCache = mutableMapOf<Int, Drawable>()
    const val MARKER_SIZE_DP = 200f

    fun getTintedShipIcon(context: Context, colorInt: Int): Drawable {
        return iconCache.getOrPut(colorInt) {
            val density = context.resources.displayMetrics.density
            val sizePx = (MARKER_SIZE_DP * density).roundToInt()
            val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
                ?: return@getOrPut ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!
            DrawableCompat.setTint(vectorDrawable, colorInt)

            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = sizePx / 2f
            val haloRadius = sizePx * 0.44f

            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = colorInt
                alpha = 245
            }
            canvas.drawCircle(center, center, haloRadius, haloPaint)

            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f * density
                color = Color.WHITE
            }
            canvas.drawCircle(center, center, haloRadius, outlinePaint)

            val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                color = Color.BLACK
                alpha = 90
            }
            canvas.drawCircle(center, center, haloRadius + 1.5f * density, dropShadowPaint)

            val shipSize = (sizePx * 0.8f).roundToInt()
            val left = (sizePx - shipSize) / 2
            val top = (sizePx - shipSize) / 2
            vectorDrawable.setBounds(left, top, left + shipSize, top + shipSize)
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
