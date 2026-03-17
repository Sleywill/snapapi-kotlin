package pics.snapapi

import okhttp3.OkHttpClient
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.http.HttpClient
import pics.snapapi.http.RetryPolicy
import pics.snapapi.models.*
import java.io.File

/**
 * Thread-safe SnapAPI client.
 *
 * All methods are `suspend` functions and execute on [kotlinx.coroutines.Dispatchers.IO].
 * A single instance may be shared freely across coroutines.
 *
 * ### Quickstart
 *
 * ```kotlin
 * val client = SnapAPIClient(apiKey = "sk_your_key")
 *
 * // Screenshot
 * val png = client.screenshot(ScreenshotOptions(url = "https://example.com"))
 *
 * // Scrape
 * val page = client.scrape(ScrapeOptions(url = "https://example.com"))
 * println(page.results.firstOrNull()?.data)
 *
 * // Extract
 * val md = client.extractMarkdown("https://example.com")
 *
 * // Quota
 * val q = client.getUsage()
 * println("Used: ${q.used}/${q.total}")
 * ```
 *
 * ### Error handling
 *
 * ```kotlin
 * try {
 *     val bytes = client.screenshot(opts)
 * } catch (e: SnapAPIException) {
 *     when (e) {
 *         is SnapAPIException.AuthenticationException -> println("Invalid API key")
 *         is SnapAPIException.RateLimitException      -> delay(e.retryAfterMs)
 *         is SnapAPIException.QuotaExceededException  -> println("Upgrade plan")
 *         is SnapAPIException.ValidationException     -> println("Bad fields: ${e.fields}")
 *         is SnapAPIException.ServerException         -> println("HTTP ${e.statusCode}: ${e.message}")
 *         is SnapAPIException.NetworkException        -> println("Network: ${e.cause?.message}")
 *         else                                        -> throw e
 *     }
 * }
 * ```
 *
 * @param apiKey       Your SnapAPI key (`sk_live_...` or `sk_test_...`).
 * @param baseUrl      Override the API base URL (default: `https://api.snapapi.pics`).
 * @param okHttpClient Optional pre-configured [OkHttpClient] for custom timeouts / interceptors.
 * @param retryPolicy  Retry behaviour. Defaults to [RetryPolicy.DEFAULT] (3 retries, exponential backoff).
 */
class SnapAPIClient @JvmOverloads constructor(
    apiKey: String,
    baseUrl: String = "https://api.snapapi.pics",
    okHttpClient: OkHttpClient = HttpClient.defaultOkHttp(),
    retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
) {
    private val http = HttpClient(
        apiKey      = apiKey,
        baseUrl     = baseUrl,
        okHttp      = okHttpClient,
        retryPolicy = retryPolicy,
    )

    // ── Namespaced sub-clients ─────────────────────────────────────────────────

    /** Storage management: list, retrieve, and delete stored files. */
    val storage: StorageNamespace = StorageNamespace(http)

    /** Scheduled captures: create, list, update, and delete scheduled tasks. */
    val scheduled: ScheduledNamespace = ScheduledNamespace(http)

    /** Webhook management: register, list, update, and delete webhooks. */
    val webhooks: WebhooksNamespace = WebhooksNamespace(http)

    /** API key management: create, list, update, and revoke API keys. */
    val apiKeys: ApiKeysNamespace = ApiKeysNamespace(http)

    // ── Screenshot  POST /v1/screenshot ───────────────────────────────────────

    /**
     * Capture a screenshot of a URL, HTML snippet, or Markdown string.
     *
     * Returns raw binary image data (PNG, JPEG, WEBP, AVIF) or PDF bytes
     * when `format = ScreenshotFormat.PDF`.
     *
     * @param options At least one of [ScreenshotOptions.url], [ScreenshotOptions.html],
     *   or [ScreenshotOptions.markdown] must be set.
     * @return Raw image bytes.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun screenshot(options: ScreenshotOptions): ByteArray {
        require(options.url != null || options.html != null || options.markdown != null) {
            "One of url, html, or markdown is required."
        }
        return http.post("/v1/screenshot", options)
    }

    /**
     * Capture a screenshot and upload it to the configured storage backend.
     *
     * Pass a [StorageDestination] in [ScreenshotOptions.storage] to specify the
     * upload target. The response contains the file `id` and public `url`.
     *
     * @return [StorageUploadResult] with the file `id` and public `url`.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun screenshotToStorage(options: ScreenshotOptions): StorageUploadResult {
        require(options.url != null || options.html != null || options.markdown != null) {
            "One of url, html, or markdown is required."
        }
        require(options.storage != null) {
            "ScreenshotOptions.storage must be set for screenshotToStorage."
        }
        return http.postJson("/v1/screenshot", options)
    }

    /**
     * Capture a screenshot and write it directly to a local file.
     *
     * @param options Screenshot options.
     * @param file    The [File] to write the image data to.
     * @return The number of bytes written.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun screenshotToFile(options: ScreenshotOptions, file: File): Int {
        val bytes = screenshot(options)
        file.writeBytes(bytes)
        return bytes.size
    }

    // ── PDF  POST /v1/pdf ─────────────────────────────────────────────────────

    /**
     * Generate a PDF of a URL.
     *
     * ```kotlin
     * val bytes = client.pdf(PdfOptions(url = "https://example.com", pageFormat = PDFPageFormat.A4))
     * File("page.pdf").writeBytes(bytes)
     * ```
     *
     * @param options PDF rendering options. [PdfOptions.url] is required.
     * @return Raw PDF bytes.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun pdf(options: PdfOptions): ByteArray {
        require(options.url.isNotBlank()) { "url is required." }
        return http.post("/v1/pdf", options)
    }

    /**
     * Generate a PDF and write it directly to a local file.
     *
     * @param options PDF rendering options.
     * @param file    The [File] to write the PDF bytes to.
     * @return The number of bytes written.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun pdfToFile(options: PdfOptions, file: File): Int {
        val bytes = pdf(options)
        file.writeBytes(bytes)
        return bytes.size
    }

    /**
     * Convenience: generate a PDF via the screenshot endpoint (forces
     * `format = PDF`). Use [pdf] for the dedicated endpoint.
     */
    suspend fun pdfFromScreenshot(options: ScreenshotOptions): ByteArray {
        require(options.url != null || options.html != null || options.markdown != null) {
            "One of url, html, or markdown is required."
        }
        return http.post("/v1/screenshot", options.copy(format = ScreenshotFormat.PDF))
    }

    // ── Scrape  POST /v1/scrape ───────────────────────────────────────────────

    /**
     * Scrape text, HTML, or links from a URL.
     *
     * ```kotlin
     * val result = client.scrape(ScrapeOptions(url = "https://example.com", selector = "article"))
     * println(result.results.firstOrNull()?.data)
     * ```
     *
     * @param options Scrape options. [ScrapeOptions.url] is required.
     * @return [ScrapeResult] with one [ScrapeItem] per page.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun scrape(options: ScrapeOptions): ScrapeResult {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/scrape", options)
    }

    // ── Extract  POST /v1/extract ─────────────────────────────────────────────

    /**
     * Extract structured content from a webpage.
     *
     * @param options Extract options. [ExtractOptions.url] is required.
     * @return [ExtractResult] with the requested content.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun extract(options: ExtractOptions): ExtractResult {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/extract", options)
    }

    /** Extract page content as Markdown. */
    suspend fun extractMarkdown(url: String): ExtractResult =
        extract(ExtractOptions(url = url, format = ExtractFormat.MARKDOWN))

    /** Extract article body. */
    suspend fun extractArticle(url: String): ExtractResult =
        extract(ExtractOptions(url = url, format = ExtractFormat.ARTICLE))

    /** Extract plain text. */
    suspend fun extractText(url: String): ExtractResult =
        extract(ExtractOptions(url = url, format = ExtractFormat.TEXT))

    /** Extract all hyperlinks. */
    suspend fun extractLinks(url: String): ExtractResult =
        extract(ExtractOptions(url = url, format = ExtractFormat.LINKS))

    /** Extract all image URLs. */
    suspend fun extractImages(url: String): ExtractResult =
        extract(ExtractOptions(url = url, format = ExtractFormat.IMAGES))

    /** Extract page metadata (Open Graph, meta tags). */
    suspend fun extractMetadata(url: String): ExtractResult =
        extract(ExtractOptions(url = url, format = ExtractFormat.METADATA))

    // ── Analyze  POST /v1/analyze ─────────────────────────────────────────────

    /**
     * Analyze a webpage using an LLM provider.
     *
     * This endpoint extracts content from the URL and sends it to an LLM.
     * It may return HTTP 503 when LLM credits are exhausted server-side —
     * handle [SnapAPIException.ServerException] with `statusCode == 503`.
     *
     * ```kotlin
     * val result = client.analyze(AnalyzeOptions(
     *     url      = "https://example.com",
     *     prompt   = "Summarize this page",
     *     provider = AnalyzeProvider.OPENAI,
     * ))
     * println(result.result)
     * ```
     *
     * @param options Analyze options. [AnalyzeOptions.url] is required.
     * @return [AnalyzeResult] with the LLM analysis.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun analyze(options: AnalyzeOptions): AnalyzeResult {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/analyze", options)
    }

    // ── Video  POST /v1/video ─────────────────────────────────────────────────

    /**
     * Record a video of a live webpage and return raw binary bytes.
     *
     * For structured metadata (dimensions, duration, base64 payload) use
     * [videoResult] instead.
     *
     * @param options Video options. [VideoOptions.url] is required.
     * @return Raw video bytes (MP4, WebM, or GIF).
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun video(options: VideoOptions): ByteArray {
        require(options.url.isNotBlank()) { "url is required." }
        return http.post("/v1/video", options.copy(responseType = "binary"))
    }

    /**
     * Record a video and return structured [VideoResult] metadata including
     * a base64-encoded video payload.
     *
     * @param options Video options. [VideoOptions.url] is required.
     * @return [VideoResult] with dimensions, duration, and encoded data.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun videoResult(options: VideoOptions): VideoResult {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/video", options.copy(responseType = "json"))
    }

    // ── OG Image  POST /v1/og-image ───────────────────────────────────────────

    /**
     * Generate a social-media Open Graph image for a URL.
     *
     * Returns raw image bytes, typically 1200 x 630 PNG.
     *
     * ```kotlin
     * val bytes = client.ogImage(OgImageOptions(url = "https://example.com"))
     * File("og.png").writeBytes(bytes)
     * ```
     *
     * @param options OG image options. [OgImageOptions.url] is required.
     * @return Raw image bytes.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun ogImage(options: OgImageOptions): ByteArray {
        require(options.url.isNotBlank()) { "url is required." }
        return http.post("/v1/og-image", options)
    }

    // ── Usage  GET /v1/usage ──────────────────────────────────────────────────

    /**
     * Fetch the account's API usage for the current billing period.
     *
     * ```kotlin
     * val u = client.getUsage()
     * println("Used: ${u.used}/${u.total} — ${u.remaining} remaining")
     * ```
     *
     * @return [QuotaResult] with `used`, `total`, and `remaining` counts.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun getUsage(): QuotaResult = http.getJson("/v1/usage")

    /**
     * Alias for [getUsage], kept for backward compatibility.
     *
     * @return [QuotaResult] with `used`, `total`, and `remaining` counts.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun quota(): QuotaResult = getUsage()

    // ── Ping  GET /v1/ping ────────────────────────────────────────────────────

    /**
     * Check API health.
     *
     * @return [PingResult] with `status` (`"ok"`) and `timestamp`.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun ping(): PingResult = http.getJson("/v1/ping")
}

// ── Typealias for the spec-required name ─────────────────────────────────────

/**
 * Convenience alias. Both `SnapAPI` and `SnapAPIClient` refer to the same
 * class.
 *
 * ```kotlin
 * val client = SnapAPI(apiKey = "sk_your_key")
 * ```
 */
typealias SnapAPI = SnapAPIClient

// ══════════════════════════════════════════════════════════════════════════════
// Namespaced sub-clients
// ══════════════════════════════════════════════════════════════════════════════

// ── Storage namespace ─────────────────────────────────────────────────────────

/**
 * Provides access to the SnapAPI storage management endpoints.
 *
 * Obtain an instance via [SnapAPIClient.storage].
 *
 * ```kotlin
 * val files = client.storage.list()
 * client.storage.delete("file_abc123")
 * ```
 */
class StorageNamespace internal constructor(private val http: HttpClient) {

    /**
     * List all files stored in your SnapAPI account.
     *
     * @param options Pagination options.
     * @return [StorageListResult] with a list of [StorageFile] objects.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun list(options: StorageListOptions = StorageListOptions()): StorageListResult {
        val queryParts = buildList {
            options.limit?.let { add("limit=$it") }
            options.after?.let { add("after=$it") }
        }
        val query = if (queryParts.isEmpty()) "" else "?" + queryParts.joinToString("&")
        return http.getJson("/v1/storage/files$query")
    }

    /**
     * Retrieve metadata for a single stored file.
     *
     * @param id The file identifier.
     * @return [StorageFile] metadata.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun get(id: String): StorageFile {
        require(id.isNotBlank()) { "id is required." }
        return http.getJson("/v1/storage/files/$id")
    }

    /**
     * Delete a stored file by ID.
     *
     * @param id The file identifier.
     * @return [StorageDeleteResult] confirming deletion.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun delete(id: String): StorageDeleteResult {
        require(id.isNotBlank()) { "id is required." }
        return http.deleteJson("/v1/storage/files/$id")
    }
}

// ── Scheduled namespace ───────────────────────────────────────────────────────

/**
 * Provides access to the SnapAPI scheduled-capture endpoints.
 *
 * Obtain an instance via [SnapAPIClient.scheduled].
 *
 * ```kotlin
 * val task = client.scheduled.create(ScheduleOptions(
 *     url      = "https://example.com",
 *     interval = ScheduleInterval.DAILY,
 *     action   = "screenshot",
 * ))
 * println("Created task: ${task.id}")
 * ```
 */
class ScheduledNamespace internal constructor(private val http: HttpClient) {

    /**
     * Create a new scheduled capture task.
     *
     * @param options Schedule options. [ScheduleOptions.url] is required.
     * @return The created [ScheduledTask].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun create(options: ScheduleOptions): ScheduledTask {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/scheduled", options)
    }

    /**
     * List all scheduled tasks for this account.
     *
     * @return [ScheduledListResult] with all tasks.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun list(): ScheduledListResult = http.getJson("/v1/scheduled")

    /**
     * Retrieve a single scheduled task by ID.
     *
     * @param id Task identifier.
     * @return The [ScheduledTask].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun get(id: String): ScheduledTask {
        require(id.isNotBlank()) { "id is required." }
        return http.getJson("/v1/scheduled/$id")
    }

    /**
     * Update an existing scheduled task.
     *
     * @param id      Task identifier.
     * @param options Fields to update.
     * @return The updated [ScheduledTask].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun update(id: String, options: ScheduleUpdateOptions): ScheduledTask {
        require(id.isNotBlank()) { "id is required." }
        return http.patchJson("/v1/scheduled/$id", options)
    }

    /**
     * Delete a scheduled task.
     *
     * @param id Task identifier.
     * @return [OperationResult] confirming deletion.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun delete(id: String): OperationResult {
        require(id.isNotBlank()) { "id is required." }
        return http.deleteJson("/v1/scheduled/$id")
    }
}

// ── Webhooks namespace ────────────────────────────────────────────────────────

/**
 * Provides access to the SnapAPI webhook management endpoints.
 *
 * Obtain an instance via [SnapAPIClient.webhooks].
 *
 * ```kotlin
 * val hook = client.webhooks.create(WebhookOptions(
 *     url    = "https://myapp.com/hooks/snapapi",
 *     events = listOf(WebhookEvent.SCREENSHOT_COMPLETED),
 * ))
 * println("Created webhook: ${hook.id}")
 * ```
 */
class WebhooksNamespace internal constructor(private val http: HttpClient) {

    /**
     * Register a new webhook.
     *
     * @param options Webhook configuration. [WebhookOptions.url] is required.
     * @return The created [Webhook].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun create(options: WebhookOptions): Webhook {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/webhooks", options)
    }

    /**
     * List all registered webhooks for this account.
     *
     * @return [WebhookListResult] with all webhooks.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun list(): WebhookListResult = http.getJson("/v1/webhooks")

    /**
     * Retrieve a single webhook by ID.
     *
     * @param id Webhook identifier.
     * @return The [Webhook].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun get(id: String): Webhook {
        require(id.isNotBlank()) { "id is required." }
        return http.getJson("/v1/webhooks/$id")
    }

    /**
     * Update an existing webhook.
     *
     * @param id      Webhook identifier.
     * @param options Fields to update.
     * @return The updated [Webhook].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun update(id: String, options: WebhookUpdateOptions): Webhook {
        require(id.isNotBlank()) { "id is required." }
        return http.patchJson("/v1/webhooks/$id", options)
    }

    /**
     * Delete a webhook.
     *
     * @param id Webhook identifier.
     * @return [OperationResult] confirming deletion.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun delete(id: String): OperationResult {
        require(id.isNotBlank()) { "id is required." }
        return http.deleteJson("/v1/webhooks/$id")
    }
}

// ── API Keys namespace ─────────────────────────────────────────────────────────

/**
 * Provides access to the SnapAPI key management endpoints.
 *
 * Obtain an instance via [SnapAPIClient.apiKeys].
 *
 * ```kotlin
 * val key = client.apiKeys.create(ApiKeyOptions(
 *     name   = "CI pipeline",
 *     scopes = listOf(ApiKeyScope.SCREENSHOT),
 * ))
 * println("New key: ${key.fullKey}")
 * ```
 */
class ApiKeysNamespace internal constructor(private val http: HttpClient) {

    /**
     * Create a new API key.
     *
     * The [ApiKey.fullKey] field is only populated in the creation response.
     * Store it securely — it will not be shown again.
     *
     * @param options Key options. [ApiKeyOptions.name] is required.
     * @return The created [ApiKey] (with full key value).
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun create(options: ApiKeyOptions): ApiKey {
        require(options.name.isNotBlank()) { "name is required." }
        return http.postJson("/v1/api-keys", options)
    }

    /**
     * List all API keys for this account.
     *
     * @return [ApiKeyListResult] with all keys (full key values are masked).
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun list(): ApiKeyListResult = http.getJson("/v1/api-keys")

    /**
     * Retrieve a single API key by ID.
     *
     * @param id Key identifier.
     * @return The [ApiKey].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun get(id: String): ApiKey {
        require(id.isNotBlank()) { "id is required." }
        return http.getJson("/v1/api-keys/$id")
    }

    /**
     * Update an existing API key (rename, change scopes, activate/deactivate).
     *
     * @param id      Key identifier.
     * @param options Fields to update.
     * @return The updated [ApiKey].
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun update(id: String, options: ApiKeyUpdateOptions): ApiKey {
        require(id.isNotBlank()) { "id is required." }
        return http.patchJson("/v1/api-keys/$id", options)
    }

    /**
     * Revoke (delete) an API key.
     *
     * @param id Key identifier.
     * @return [OperationResult] confirming revocation.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun revoke(id: String): OperationResult {
        require(id.isNotBlank()) { "id is required." }
        return http.deleteJson("/v1/api-keys/$id")
    }
}
