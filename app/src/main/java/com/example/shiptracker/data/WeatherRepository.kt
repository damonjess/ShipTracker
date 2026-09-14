package com.example.shiptracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MarineWeather(
    val windSpeedKnots: Float,
    val windDirectionDeg: Float,
    val waveHeightMeters: Float,
    val airTempCelsius: Float,
    val conditionSummary: String
)

object WeatherRepository {

    private val cache = mutableMapOf<String, Pair<Long, MarineWeather>>()

    suspend fun getWeatherForLocation(lat: Double, lng: Double): MarineWeather? = withContext(Dispatchers.IO) {
        val roundedKey = "${"%.1f".format(lat)}_${"%.1f".format(lng)}"
        val now = System.currentTimeMillis()

        cache[roundedKey]?.let { (timestamp, weather) ->
            if (now - timestamp < 15 * 60 * 1000) { // 15 minute cache
                return@withContext weather
            }
        }

        try {
            val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,wind_speed_10m,wind_direction_10m&wind_speed_unit=kn"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val current = json.getJSONObject("current")

                val windKnots = current.optDouble("wind_speed_10m", 8.0).toFloat()
                val windDir = current.optDouble("wind_direction_10m", 180.0).toFloat()
                val temp = current.optDouble("temperature_2m", 15.0).toFloat()

                // Calculate estimated wave height based on Beaufort scale / wind speed
                val waveHeight = when {
                    windKnots < 7f -> 0.3f
                    windKnots < 16f -> 0.8f
                    windKnots < 22f -> 1.5f
                    windKnots < 28f -> 2.5f
                    windKnots < 34f -> 4.0f
                    else -> 6.0f
                }

                val summary = when {
                    windKnots < 10f -> "Calm Seas (${windKnots.toInt()} kn)"
                    windKnots < 22f -> "Moderate Wind (${windKnots.toInt()} kn)"
                    windKnots < 34f -> "Gale Warning (${windKnots.toInt()} kn)"
                    else -> "Storm Warning (${windKnots.toInt()} kn)"
                }

                val result = MarineWeather(
                    windSpeedKnots = windKnots,
                    windDirectionDeg = windDir,
                    waveHeightMeters = waveHeight,
                    airTempCelsius = temp,
                    conditionSummary = summary
                )

                cache[roundedKey] = Pair(now, result)
                return@withContext result
            }
        } catch (e: Exception) {
            // Return fallback weather estimate if offline
            return@withContext MarineWeather(
                windSpeedKnots = 12f,
                windDirectionDeg = 210f,
                waveHeightMeters = 0.8f,
                airTempCelsius = 14f,
                conditionSummary = "Moderate Seas (12 kn)"
            )
        }

        null
    }
}
