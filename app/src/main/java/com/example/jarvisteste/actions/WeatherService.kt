package com.example.jarvisteste.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Consulta clima e previsão na API gratuita do Open-Meteo (sem chave).
 * O resultado volta como texto para o Gemini falar — nenhum navegador é aberto.
 */
object WeatherService {

    suspend fun forecast(context: Context, cidade: String?, days: Int = 2): String {
        val limit = days.coerceIn(1, 5)
        val query = cidade?.trim().orEmpty()

        val place = if (query.isEmpty()) {
            deviceLocation(context)
        } else {
            geocode(query)
        }

        if (place == null) {
            return if (query.isEmpty()) {
                "Não consigo saber a localização do aparelho. Pergunte ao usuário em qual cidade ele quer o clima."
            } else {
                "Não encontrei a cidade \"$query\". Peça para o usuário repetir o nome."
            }
        }

        val (lat, lon, label) = place
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=$limit"

        val response = get(url) ?: return "Não consegui consultar a previsão do tempo agora."
        return try {
            format(label, JSONObject(response), limit)
        } catch (error: Exception) {
            "Não consegui ler a previsão do tempo agora."
        }
    }

    private fun format(label: String, json: JSONObject, days: Int): String {
        val current = json.optJSONObject("current") ?: return "Não recebi dados do clima."
        val temp = current.optDouble("temperature_2m", Double.NaN).round()
        val humidity = current.optInt("relative_humidity_2m", -1)
        val wind = current.optDouble("wind_speed_10m", Double.NaN)
        val condition = describe(current.optInt("weather_code", -1))

        val parts = mutableListOf<String>()
        parts.add(
            buildString {
                append("Em $label agora: $temp graus, $condition")
                if (humidity >= 0) append(", umidade $humidity por cento")
                if (!wind.isNaN()) append(", vento ${wind.round()} quilômetros por hora")
                append(".")
            }
        )

        val daily = json.optJSONObject("daily")
        if (daily != null) {
            val highs = daily.optJSONArray("temperature_2m_max")
            val lows = daily.optJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weather_code")
            for (index in 0 until days.coerceAtMost(highs?.length() ?: 0)) {
                val whenLabel = when (index) {
                    0 -> "Hoje"
                    1 -> "Amanhã"
                    else -> "Em $index dias"
                }
                val high = highs?.optDouble(index, Double.NaN)?.round() ?: "—"
                val low = lows?.optDouble(index, Double.NaN)?.round() ?: "—"
                val dayCondition = codes?.optInt(index, -1)?.let(::describe).orEmpty()
                parts.add("$whenLabel: $dayCondition, mínima $low e máxima $high graus.")
            }
        }
        return parts.joinToString(" ")
    }

    private fun geocode(cidade: String): Triple<Double, Double, String>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=" + URLEncoder.encode(cidade, Charsets.UTF_8.name()) +
            "&count=1&language=pt&format=json"
        val response = get(url) ?: return null
        val item = runCatching {
            JSONObject(response).optJSONArray("results")?.optJSONObject(0)
        }.getOrNull() ?: return null

        val lat = item.optDouble("latitude", Double.NaN)
        val lon = item.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val name = item.optString("name").ifBlank { cidade }
        val country = item.optString("country").ifBlank { "" }
        return Triple(lat, lon, if (country.isBlank()) name else "$name, $country")
    }

    private fun deviceLocation(context: Context): Triple<Double, Double, String>? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val location = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (error: SecurityException) { null }
            }
        return location?.let { Triple(it.latitude, it.longitude, "sua localização") }
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun Double.round(): String = if (isNaN()) "—" else Math.round(this).toString()

    private fun describe(code: Int): String = when (code) {
        0 -> "céu limpo"
        1, 2 -> "parcialmente nublado"
        3 -> "nublado"
        45, 48 -> "névoa"
        51, 53, 55, 56, 57 -> "garoa"
        61, 63, 65, 66, 67 -> "chuva"
        71, 73, 75, 77 -> "neve"
        80, 81, 82 -> "pancadas de chuva"
        85, 86 -> "pancadas de neve"
        95 -> "trovoadas"
        96, 99 -> "trovoadas com granizo"
        else -> "tempo variável"
    }
}
