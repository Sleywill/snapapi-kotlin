package pics.snapapi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * SnapAPI — Official Kotlin SDK (v2.0.0)
 *
 * All suspend functions run on [Dispatchers.IO].
 *
 * ```kotlin
 * val api = SnapAPI("your-api-key")
 *
 * val imageBytes = api.screenshot(ScreenshotOptions(url = "https://example.com"))
 * File("screenshot.png").writeBytes(imageBytes)
 * ```
 */
class SnapAPI(
    private val apiKey: String,
    private val baseUrl: String = "https://api.snapapi.pics",
    private val client: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    // ── Screenshot  POST /v1/screenshot ───────────────────────────────────────

    /**
     * Capture a screenshot of a URL or HTML/Markdown source.
     *
     * Returns raw PNG/JPEG/WEBP/AVIF/PDF bytes.
     * When [ScreenshotOptions.storage] is set use [screenshotToStorage] instead.
     *
     * @throws SnapAPIException on API or network errors.
     */
    suspend fun screenshot(options: ScreenshotOptions): ByteArray {
        require(options.url != null || options.html != null || options.markdown != null) {
            "One of url, html, or markdown is required."
        }
        return post("/v1/screenshot", options)
    }

    /**
     * Capture a screenshot and upload to configured storage.
     *
     * @return [StorageUploadResult] with `id` and `url`.
     */
    suspend fun screenshotToStorage(options: ScreenshotOptions): StorageUploadResult {
        val bytes = screenshot(options)
        return json.decodeFromString(bytes.decodeToString())
    }

    /**
     * Generate a PDF (forces `format = "pdf"`).
     */
    suspend fun pdf(options: ScreenshotOptions): ByteArray =
        screenshot(options.copy(format = "pdf"))

    // ── Scrape  POST /v1/scrape ───────────────────────────────────────────────

    /**
     * Scrape text, HTML, or links from a URL (up to 10 pages).
     *
     * @throws SnapAPIException on API or network errors.
     */
    suspend fun scrape(options: ScrapeOptions): ScrapeResult {
        require(options.url.isNotBlank()) { "url is required." }
        return postJson("/v1/scrape", options)
    }

    // ── Extract  POST /v1/extract ─────────────────────────────────────────────

    /**
     * Extract structured content from a webpage.
     *
     * @throws SnapAPIException on API or network errors.
     */
    suspend fun extract(options: ExtractOptions): ExtractResult {
        require(options.url.isNotBlank()) { "url is required." }
        return postJson("/v1/extract", options)
    }

    /** Extract Markdown content. */
    suspend fun extractMarkdown(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "markdown"))

    /** Extract article content. */
    suspend fun extractArticle(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "article"))

    /** Extract plain text. */
    suspend fun extractText(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "text"))

    /** Extract all links. */
    suspend fun extractLinks(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "links"))

    /** Extract all images. */
    suspend fun extractImages(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "images"))

    /** Extract page metadata. */
    suspend fun extractMetadata(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "metadata"))

    /** Extract structured data. */
    suspend fun extractStructured(url: String): ExtractResult =
        extract(ExtractOptions(url = url, type = "structured"))

    // ── Analyze  POST /v1/analyze ─────────────────────────────────────────────

    /**
     * Perform AI-powered analysis of a webpage.
     *
     * @throws SnapAPIException on API or network errors.
     */
    suspend fun analyze(options: AnalyzeOptions): AnalyzeResult {
        require(options.url.isNotBlank()) { "url is required." }
        return postJson("/v1/analyze", options)
    }

    // ── Storage  /v1/storage/* ────────────────────────────────────────────────

    /** List all stored files. */
    suspend fun listStorageFiles(): StorageFilesResult =
        getJson("/v1/storage/files")

    /** Delete a stored file by ID. */
    suspend fun deleteStorageFile(id: String) =
        delete("/v1/storage/files/$id")

    /** Get storage usage statistics. */
    suspend fun storageUsage(): StorageUsageResult =
        getJson("/v1/storage/usage")

    /** Configure an S3-compatible storage backend. */
    suspend fun configureS3(config: S3Config) {
        post("/v1/storage/s3", config)
    }

    /** Test the configured S3 connection. */
    suspend fun testS3() {
        postRaw("/v1/storage/s3/test", "{}")
    }

    // ── Scheduled  /v1/scheduled/* ────────────────────────────────────────────

    /** Create a scheduled screenshot job. */
    suspend fun createScheduled(options: ScheduledOptions): ScheduledJob =
        postJson("/v1/scheduled", options)

    /** List all scheduled jobs. */
    suspend fun listScheduled(): ScheduledListResult =
        getJson("/v1/scheduled")

    /** Delete a scheduled job. */
    suspend fun deleteScheduled(id: String) =
        delete("/v1/scheduled/$id")

    // ── Webhooks  /v1/webhooks/* ──────────────────────────────────────────────

    /** Register a new webhook. */
    suspend fun createWebhook(options: WebhookOptions): Webhook =
        postJson("/v1/webhooks", options)

    /** List all registered webhooks. */
    suspend fun listWebhooks(): WebhooksListResult =
        getJson("/v1/webhooks")

    /** Delete a webhook. */
    suspend fun deleteWebhook(id: String) =
        delete("/v1/webhooks/$id")

    // ── API Keys  /v1/keys/* ──────────────────────────────────────────────────

    /** List all API keys. */
    suspend fun listKeys(): KeysListResult =
        getJson("/v1/keys")

    /** Create a new API key. The returned [ApiKey.key] is only shown once. */
    suspend fun createKey(name: String): ApiKey =
        postJson("/v1/keys", mapOf("name" to name))

    /** Revoke an API key. */
    suspend fun deleteKey(id: String) =
        delete("/v1/keys/$id")

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun headers() = mapOf(
        "x-api-key"    to apiKey,
        "Content-Type" to "application/json",
        "User-Agent"   to "snapapi-kotlin/2.0.0",
        "Accept"       to "*/*",
    )

    private suspend inline fun <reified T> post(path: String, body: T): ByteArray =
        withContext(Dispatchers.IO) {
            val jsonBody = json.encodeToString(body).toRequestBody(jsonMediaType)
            val request  = Request.Builder()
                .url("$baseUrl$path")
                .post(jsonBody)
                .apply { headers().forEach { (k, v) -> header(k, v) } }
                .build()
            execute(request)
        }

    private suspend fun postRaw(path: String, bodyStr: String): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .post(bodyStr.toRequestBody(jsonMediaType))
                .apply { headers().forEach { (k, v) -> header(k, v) } }
                .build()
            execute(request)
        }

    private suspend inline fun <reified B, reified R> postJson(path: String, body: B): R {
        val bytes = post(path, body)
        return json.decodeFromString(bytes.decodeToString())
    }

    private suspend inline fun <reified R> getJson(path: String): R =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .get()
                .apply { headers().forEach { (k, v) -> header(k, v) } }
                .build()
            json.decodeFromString(execute(request).decodeToString())
        }

    private suspend fun delete(path: String) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .delete()
                .apply { headers().forEach { (k, v) -> header(k, v) } }
                .build()
            execute(request)
        }

    private fun execute(request: Request): ByteArray {
        val response: Response
        try {
            response = client.newCall(request).execute()
        } catch (e: Exception) {
            throw SnapAPIException("Network error: ${e.message}", "CONNECTION_ERROR", 0, e)
        }

        val bodyBytes = response.body?.bytes() ?: ByteArray(0)

        if (!response.isSuccessful) {
            val bodyStr = bodyBytes.decodeToString()
            val parsed  = runCatching { json.decodeFromString<ApiErrorResponse>(bodyStr) }.getOrNull()
            val message = parsed?.message ?: "HTTP ${response.code}"
            val code    = parsed?.error   ?: "HTTP_ERROR"
            throw SnapAPIException(message, code, response.code)
        }

        return bodyBytes
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

// ── Exception ─────────────────────────────────────────────────────────────────

/**
 * Exception thrown for SnapAPI errors.
 *
 * @property errorCode   Short machine-readable error code (e.g. "RATE_LIMITED").
 * @property statusCode  HTTP status code (0 for network errors).
 */
class SnapAPIException(
    message: String,
    val errorCode: String,
    val statusCode: Int,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Returns `true` if retrying the request might succeed. */
    val isRetryable: Boolean
        get() = errorCode == "RATE_LIMITED" || errorCode == "TIMEOUT" || statusCode >= 500

    override fun toString(): String = "[$errorCode] $message (HTTP $statusCode)"
}
