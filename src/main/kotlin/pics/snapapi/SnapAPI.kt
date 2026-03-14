package pics.snapapi

import okhttp3.OkHttpClient
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.http.HttpClient
import pics.snapapi.http.RetryPolicy
import pics.snapapi.models.*

/**
 * Thread-safe SnapAPI client.
 *
 * All methods are `suspend` functions and run on [kotlinx.coroutines.Dispatchers.IO].
 * A single instance can be shared across coroutines without additional synchronisation.
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
 * val q = client.quota()
 * println("Used: ${q.used}/${q.total}")
 * ```
 *
 * All methods throw [SnapAPIException]. Handle with a `when` expression:
 *
 * ```kotlin
 * try {
 *     val bytes = client.screenshot(opts)
 * } catch (e: SnapAPIException) {
 *     when (e) {
 *         is SnapAPIException.RateLimited   -> delay(e.retryAfterMs)
 *         is SnapAPIException.QuotaExceeded -> println("Upgrade plan")
 *         is SnapAPIException.Unauthorized  -> println("Bad API key")
 *         else                              -> throw e
 *     }
 * }
 * ```
 */
class SnapAPIClient(
    apiKey: String,
    baseUrl: String = "https://snapapi.pics",
    okHttpClient: OkHttpClient = HttpClient.defaultOkHttp(),
    retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
) {
    private val http = HttpClient(
        apiKey      = apiKey,
        baseUrl     = baseUrl,
        okHttp      = okHttpClient,
        retryPolicy = retryPolicy,
    )

    // ── Screenshot  POST /v1/screenshot ───────────────────────────────────────

    /**
     * Capture a screenshot of a URL, HTML snippet, or Markdown string.
     *
     * Returns raw binary image data (PNG, JPEG, WEBP, AVIF) or PDF bytes.
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
     * @return [StorageUploadResult] with the file `id` and public `url`.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun screenshotToStorage(options: ScreenshotOptions): StorageUploadResult {
        require(options.url != null || options.html != null || options.markdown != null) {
            "One of url, html, or markdown is required."
        }
        return http.postJson("/v1/screenshot", options)
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
     * Convenience: generate a PDF via the screenshot endpoint (forces
     * `format = PDF`).
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

    // ── Video  POST /v1/video ─────────────────────────────────────────────────

    /**
     * Record a video of a live webpage. Returns raw binary bytes.
     *
     * For structured metadata (base64-encoded video + dimensions) use
     * [videoResult] instead.
     *
     * @param options Video options. [VideoOptions.url] is required.
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
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun videoResult(options: VideoOptions): VideoResult {
        require(options.url.isNotBlank()) { "url is required." }
        return http.postJson("/v1/video", options.copy(responseType = "json"))
    }

    // ── Quota  GET /v1/quota ──────────────────────────────────────────────────

    /**
     * Fetch the account's API usage quota for the current billing period.
     *
     * ```kotlin
     * val q = client.quota()
     * println("Used: ${q.used}/${q.total} — ${q.remaining} remaining")
     * ```
     *
     * @return [QuotaResult] with `used`, `total`, and `remaining` counts.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun quota(): QuotaResult = http.getJson("/v1/quota")

    // ── Ping  GET /v1/ping ────────────────────────────────────────────────────

    /**
     * Check API health.
     *
     * @return [PingResult] with status and timestamp.
     * @throws SnapAPIException on any API or network error.
     */
    suspend fun ping(): PingResult = http.getJson("/v1/ping")
}
