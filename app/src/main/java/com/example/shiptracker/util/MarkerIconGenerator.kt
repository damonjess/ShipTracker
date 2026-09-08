package com.example.shiptracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.shiptracker.data.ShipState

object MarkerIconGenerator {
    private val bitmapCache = mutableMapOf<Int, Bitmap>()

    fun getTintedShipIcon(context: Context, ship: ShipState, colorInt: Int): Drawable {
        val isCluster = ship.shipType == -1
        val isMoored = ship.speed < 0.5f || ship.navStatus == 1 || ship.navStatus == 5
        
        val cacheKey = if (isCluster) {
            "cluster_${ship.mmsi}".hashCode()
        } else {
            "ship_${colorInt}_${isMoored}".hashCode()
        }

        val bitmap = bitmapCache.getOrPut(cacheKey) {
            val density = context.resources.displayMetrics.density

            if (isCluster) {
                val countText = (ship.mmsi * -1).toString()
                val sizePx = (40 * density).toInt()
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#235DB2") }
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.strokeWidth = 2 * density
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, (sizePx / 2f) - paint.strokeWidth, paint)

                paint.style = Paint.Style.FILL
                paint.textSize = 14 * density
                paint.textAlign = Paint.Align.CENTER
                
                val textBounds = Rect()
                paint.getTextBounds(countText, 0, countText.length, textBounds)
                val yOffset = textBounds.height() / 2f
                canvas.drawText(countText, sizePx / 2f, (sizePx / 2f) + yOffset, paint)

                return@getOrPut bmp
            }

            // IT IS A SHIP: Draw professional marine chart shapes
            val sizePx = (24 * density).toInt()
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // 🚨 UPGRADE 1: If the ship type is unknown, use a clean crisp white instead of dull grey
            val resolvedColor = if (colorInt == Color.GRAY) Color.parseColor("#F8F9FA") else colorInt

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = resolvedColor
                style = Paint.Style.FILL
            }
            
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A1A1A")
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density // Slightly thicker, richer border
            }

            if (isMoored) {
                // 🚨 UPGRADE 2: Draw a mooring circle with a professional center dot
                val radius = (sizePx / 2f) * 0.55f
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, fillPaint)
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, strokePaint)
                
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A1A1A")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, 2f * density, dotPaint)
            } else {
                // 🚨 UPGRADE 3: Use Canvas 'quadTo' curves to draw a sleek boat hull instead of a blocky polygon
                val path = Path().apply {
                    moveTo(sizePx / 2f, sizePx * 0.15f) // Bow tip
                    // Smooth curve back to the shoulders
                    quadTo(sizePx * 0.75f, sizePx * 0.25f, sizePx * 0.75f, sizePx * 0.5f)
                    lineTo(sizePx * 0.75f, sizePx * 0.85f) // Starboard stern
                    lineTo(sizePx * 0.25f, sizePx * 0.85f) // Port stern
                    lineTo(sizePx * 0.25f, sizePx * 0.5f) // Port shoulder
                    // Smooth curve back to the bow
                    quadTo(sizePx * 0.25f, sizePx * 0.25f, sizePx / 2f, sizePx * 0.15f)
                    close()
                }
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }
            bmp
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    fun getShipAndroidColor(aisTypeCode: Int): Int {
        return when (aisTypeCode) {
            30 -> Color.parseColor("#FF9800") // Orange - Fishing vessels
            in 31..35, in 50..59 -> Color.parseColor("#00BCD4") // Cyan/Light Blue - Tugs, Pilot, & Special Craft
            in 36..37 -> Color.parseColor("#E91E63") // Magenta/Pink - Yachts & Pleasure Craft
            in 40..49 -> Color.parseColor("#FFEB3B") // Yellow - High-Speed Craft
            in 60..69 -> Color.parseColor("#2196F3") // Blue - Passenger vessels & Ferries
            in 70..79 -> Color.parseColor("#4CAF50") // Green - Cargo vessels
            in 80..89 -> Color.parseColor("#F44336") // Red - Tankers
            else -> Color.GRAY // Unknown/Other (Which your drawing engine automatically paints Off-White)
        }
    }
}
