package com.example.shiptracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.shiptracker.data.VesselTrackPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    fun exportAndShareTrack(
        context: Context,
        vesselName: String,
        mmsi: Long,
        trackPoints: List<VesselTrackPoint>
    ) {
        if (trackPoints.isEmpty()) return

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val safeName = vesselName.ifBlank { "Vessel_$mmsi" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val fileName = "Track_${safeName}_$mmsi.gpx"

        val gpxBuilder = StringBuilder()
        gpxBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        gpxBuilder.append("""<gpx version="1.1" creator="ShipTracker Android App" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")
        gpxBuilder.append("  <trk>\n")
        gpxBuilder.append("    <name>").append(vesselName).append(" (MMSI: ").append(mmsi).append(")</name>\n")
        gpxBuilder.append("    <trkseg>\n")

        trackPoints.forEach { pt ->
            val timeStr = isoFormatter.format(Date(pt.timestamp))
            gpxBuilder.append("      <trkpt lat=\"").append(pt.latitude).append("\" lon=\"").append(pt.longitude).append("\">\n")
            gpxBuilder.append("        <time>").append(timeStr).append("</time>\n")
            gpxBuilder.append("      </trkpt>\n")
        }

        gpxBuilder.append("    </trkseg>\n")
        gpxBuilder.append("  </trk>\n")
        gpxBuilder.append("</gpx>\n")

        try {
            val exportDir = File(context.cacheDir, "gpx_exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val gpxFile = File(exportDir, fileName)
            gpxFile.writeText(gpxBuilder.toString())

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                gpxFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Voyage Track for $vesselName")
                putExtra(Intent.EXTRA_TEXT, "Exported GPX voyage track for $vesselName (MMSI: $mmsi).")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share GPX Track Log"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
