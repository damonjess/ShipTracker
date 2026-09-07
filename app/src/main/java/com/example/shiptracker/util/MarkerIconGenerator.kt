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
    private val bitmapCache = mutableMapOf<Int, Bitmap>()
    const val MARKER_SIZE_DP = 220f

    private fun buildBaseBitmap(context: Context, colorInt: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (MARKER_SIZE_DP * density).roundToInt()

        val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
            ?: run {
                val fallback = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!
                DrawableCompat.setTint(fallback, colorInt)
                fallback
            }
        DrawableCompat.setTint(vectorDrawable, Color.BLACK)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        val haloRadius = sizePx * 0.46f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = colorInt
            alpha = 250
        }
        canvas.drawCircle(center, center, haloRadius, haloPaint)

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f * density
            color = Color.WHITE
        }
        canvas.drawCircle(center, center, haloRadius, outlinePaint)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            color = Color.BLACK
            alpha = 120
        }
        canvas.drawCircle(center, center, haloRadius + 2.5f * density, shadowPaint)

        val shipSize = (sizePx * 0.85f).roundToInt()
        val left = (sizePx - shipSize) / 2
        val top = (sizePx - shipSize) / 2
        vectorDrawable.setBounds(left, top, left + shipSize, top + shipSize)
        vectorDrawable.draw(canvas)

        return bitmap
    }

    fun getTintedShipIcon(context: Context, colorInt: Int): Drawable {
        val base = bitmapCache.getOrPut(colorInt) { buildBaseBitmap(context, colorInt) }
        val copy = base.copy(Bitmap.Config.ARGB_8888, true)!!
        return BitmapDrawable(context.resources, copy)
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
