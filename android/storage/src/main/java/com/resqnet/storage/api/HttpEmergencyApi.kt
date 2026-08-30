package com.resqnet.storage.api

import com.resqnet.storage.repository.EmergencyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Posts to the existing backend `POST /api/emergency` route.
 * Base URL is injectable; no credentials or MongoDB settings are stored here.
 */
class HttpEmergencyApi(
    private val baseUrl: String = DEFAULT_BASE_URL
) : EmergencyApi {

    override suspend fun uploadEmergency(record: EmergencyRecord): Result<Unit> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${baseUrl.trimEnd('/')}/api/emergency")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                val body = emergencyRequestJson(record).toString()
                connection.outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("HTTP $code"))
                }
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                connection?.disconnect()
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:5000"
        private const val TIMEOUT_MS = 15_000
    }
}

internal fun emergencyRequestJson(record: EmergencyRecord): JSONObject {
    return JSONObject()
        .put("messageId", record.messageId)
        .put("sourceNodeId", record.sourceNodeId)
        .put("type", record.emergencyType)
        .put("latitude", record.latitude)
        .put("longitude", record.longitude)
        .put("timestamp", formatEmergencyTimestampUtc(record.timestamp))
}

internal fun formatEmergencyTimestampUtc(epochMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(epochMillis))
}
