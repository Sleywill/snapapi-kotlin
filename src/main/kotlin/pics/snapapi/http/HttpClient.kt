package pics.snapapi.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.models.ApiErrorBody
import java.util.concurrent.TimeUnit

private const val USER_AGENT = "snapapi-kotlin/3.0.0"
private const val BASE_URL   = "https://snapapi.pics"

/**
 * Low-level HTTP client that handles authentication, serialisation, error
 * mapping, and retry logic for the SnapAPI REST API.
 *
 * Callers should use [SnapAPIClient][pics.snapapi.SnapAPIClient] rather than
 * interacting with this class directly.
 */
internal class HttpClient(
    private val apiKey: String,
    private val baseUrl: String = BASE_URL,
    private val okHttp: OkHttpClient = defaultOkHttp(),
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
) {
    internal val json = Json {
        encodeDefaults    = false
        ignoreUnknownKeys = true
        isLenient         = true
        explicitNulls     = false
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Public API ────────────────────────────────────────────────────────────

    /** POST with a serialisable body; returns raw bytes. */
    suspend inline fun <reified B> post(path: String, body: B): ByteArray {
        val encoded = json.encodeToString(body)
        return execute(path, "POST", encoded)
    }

    /** POST with a serialisable body; deserialises the response. */
    suspend inline fun <reified B, reified R> postJson(path: String, body: B): R {
        val bytes = post(path, body)
        return decode(bytes)
    }

    /** GET; deserialises the response. */
    suspend inline fun <reified R> getJson(path: String): R {
        val bytes = execute(path, "GET", null)
        return decode(bytes)
    }

    /** DELETE; ignores the response body. */
    suspend fun delete(path: String) {
        execute(path, "DELETE", null)
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    suspend fun execute(path: String, method: String, bodyStr: String?): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return performOnce(path, method, bodyStr)
            } catch (e: SnapAPIException) {
                if (!retryPolicy.shouldRetry(e, attempt)) throw e
                val waitMs = retryPolicy.delayMs(attempt, e.retryDelayMs)
                delay(waitMs)
                attempt++
            }
        }
    }

    private suspend fun performOnce(path: String, method: String, bodyStr: String?): ByteArray =
        withContext(Dispatchers.IO) {
            val reqBody = bodyStr?.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl$path")
                .method(method, if (method == "GET" || method == "DELETE") null else reqBody)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type",  "application/json")
                .header("Accept",        "*/*")
                .header("User-Agent",    USER_AGENT)
                .build()

            val response = try {
                okHttp.newCall(request).execute()
            } catch (e: Exception) {
                throw SnapAPIException.NetworkError("Network error: ${e.message}", e)
            }

            val bodyBytes = response.body?.bytes() ?: ByteArray(0)

            if (!response.isSuccessful) {
                val bodyStr2  = bodyBytes.decodeToString()
                val parsed    = runCatching { json.decodeFromString<ApiErrorBody>(bodyStr2) }.getOrNull()
                val message   = parsed?.message ?: "HTTP ${response.code}"
                val errorCode = parsed?.error   ?: "HTTP_ERROR"

                throw when (response.code) {
                    401, 403 -> SnapAPIException.Unauthorized()
                    402      -> SnapAPIException.QuotaExceeded()
                    429      -> {
                        val retryAfterMs = response.header("Retry-After")
                            ?.toLongOrNull()
                            ?.times(1000L)
                            ?: 60_000L
                        SnapAPIException.RateLimited(retryAfterMs)
                    }
                    else -> SnapAPIException.ServerError(
                        statusCode = response.code,
                        errorCode  = errorCode,
                        message    = message,
                    )
                }
            }

            bodyBytes
        }

    internal inline fun <reified R> decode(bytes: ByteArray): R =
        try {
            json.decodeFromString<R>(bytes.decodeToString())
        } catch (e: Exception) {
            throw SnapAPIException.DecodingError("Failed to decode response: ${e.message}", e)
        }

    companion object {
        fun defaultOkHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
