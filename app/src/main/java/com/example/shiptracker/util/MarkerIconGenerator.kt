package com.example.shiptracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.shiptracker.R
import com.example.shiptracker.data.ShipState

object MarkerIconGenerator {
    private val bitmapCache = mutableMapOf<Int, Bitmap>()

    fun getTintedShipIcon(context: Context, colorInt: Int): Drawable {
        val bitmap = bitmapCache.getOrPut(colorInt) {
            val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
                ?: ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!

            DrawableCompat.setTint(vectorDrawable, colorInt)

            val density = context.resources.displayMetrics.density
            val sizePx = (24 * density).toInt()

            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            vectorDrawable.setBounds(0, 0, sizePx, sizePx)
            vectorDrawable.draw(canvas)
            bmp
        }

        return BitmapDrawable(context.resources, bitmap)
    }

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

            // IT IS A SHIP
            val sizePx = (24 * density).toInt()
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val resolvedColor = if (colorInt == Color.GRAY) Color.parseColor("#78909C") else colorInt

            if (isMoored) {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = resolvedColor
                    style = Paint.Style.FILL
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A1A1A")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f * density
                }

                val radius = (sizePx / 2f) * 0.55f
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, fillPaint)
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, strokePaint)

                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A1A1A")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(sizePx / 2f, sizePx / 2f, 2f * density, dotPaint)
            } else {
                val vectorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ship_arrow)?.mutate()
                    ?: ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)!!

                DrawableCompat.setTint(vectorDrawable, resolvedColor)
                vectorDrawable.setBounds(0, 0, sizePx, sizePx)
                vectorDrawable.draw(canvas)
            }
            bmp
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    fun inferShipTypeFromName(shipName: String): Int {
        val upperName = shipName.uppercase()
        return when {
            // Tankers (80-89)
            upperName.contains("TANKER") || upperName.contains("OIL") || upperName.contains("CHEM") || 
            upperName.contains("GAS") || upperName.contains("PETRO") || upperName.contains("M/T") || 
            upperName.startsWith("MT ") || upperName.contains("STEN ") || upperName.contains("BOW ") || 
            upperName.contains("STOLT ") || upperName.contains("HAFNIA") || upperName.contains("TORM") || 
            upperName.contains("EKTANK") || upperName.contains("TERNTANK") || upperName.contains("BITU") || 
            upperName.contains("VLCC") || upperName.contains("PRODUCT") -> 80

            // Cargo / Containers / General Cargo (70-79)
            upperName.contains("CARGO") || upperName.contains("CONTAINER") || upperName.contains("BULK") || 
            upperName.contains("EXPRESS") || upperName.contains("LOGISTICS") || upperName.contains("CARRIER") || 
            upperName.contains("FREIGHT") || upperName.contains("MAERSK") || upperName.contains("MSC ") || 
            upperName.contains("CMA CGM") || upperName.contains("COSCO") || upperName.contains("EVER ") || 
            upperName.contains("HAPAG") || upperName.contains("ONE ") || upperName.contains("NYK") || 
            upperName.contains("WILHELMSEN") || upperName.contains("HOEGH") || upperName.contains("GRIMALDI") || 
            upperName.contains("M/V") || upperName.startsWith("MV ") || upperName.contains("ARKLOW") -> 70

            // Passenger / Ferries / Cruise (60-69)
            upperName.contains("FERRY") || upperName.contains("CRUISE") || upperName.contains("PASSENGER") || 
            upperName.contains("LINER") || upperName.contains("QUEEN") || upperName.contains("PRINCESS") || 
            upperName.contains("STENA") || upperName.contains("DFDS") || upperName.contains("VIKING") || 
            upperName.contains("TALLINK") || upperName.contains("SILJA") || upperName.contains("COLOR") || 
            upperName.contains("M/S") || upperName.startsWith("MS ") -> 60

            // Tugs, Workboats, Special Craft (31-35, 50-59)
            upperName.contains("TUG") || upperName.contains("TOW") || upperName.contains("PUSHER") || 
            upperName.contains("PILOT") || upperName.contains("SAR ") || upperName.contains("PATROL") || 
            upperName.contains("SAFETY") || upperName.contains("WORKBOAT") || upperName.contains("DREDG") || 
            upperName.contains("RESCUE") || upperName.contains("RESEARCH") || upperName.contains("SURVEY") -> 31

            // Yachts / Sailing (36-37)
            upperName.contains("YACHT") || upperName.contains("SAIL") || upperName.contains("CATAMARAN") || 
            upperName.startsWith("S/Y") || upperName.startsWith("M/Y") -> 36

            // Fishing (30)
            upperName.contains("FISH") || upperName.contains("TRAWLER") || upperName.contains("SEINER") -> 30

            else -> 0
        }
    }

    fun getShipAndroidColor(ship: ShipState): Int {
        return getShipAndroidColor(ship.shipType, ship.name)
    }

    fun getShipAndroidColor(aisTypeCode: Int, shipName: String = ""): Int {
        val effectiveType = if (aisTypeCode == 0 && shipName.isNotEmpty()) {
            inferShipTypeFromName(shipName)
        } else {
            aisTypeCode
        }

        return when (effectiveType) {
            30 -> Color.parseColor("#FF9800") // Orange - Fishing vessels
            in 31..35, in 50..59 -> Color.parseColor("#00BCD4") // Cyan/Light Blue - Tugs, Pilot, & Special Craft
            in 36..37 -> Color.parseColor("#E91E63") // Magenta/Pink - Yachts & Pleasure Craft
            in 40..49 -> Color.parseColor("#FFEB3B") // Yellow - High-Speed Craft
            in 60..69 -> Color.parseColor("#2196F3") // Blue - Passenger vessels & Ferries
            in 70..79 -> Color.parseColor("#4CAF50") // Green - Cargo vessels
            in 80..89 -> Color.parseColor("#F44336") // Red - Tankers
            in 20..29 -> Color.parseColor("#8BC34A") // Lime Green - Wing in Ground
            in 90..99 -> Color.parseColor("#78909C") // Slate Gray - Other / Special
            else -> Color.parseColor("#78909C") // Slate Gray for Unspecified/Unknown
        }
    }
}
