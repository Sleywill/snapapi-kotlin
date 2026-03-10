package pics.snapapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Shared ────────────────────────────────────────────────────────────────────

@Serializable
data class Cookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: Double? = null,
    val httpOnly: Boolean? = null,
    val secure: Boolean? = null,
    val sameSite: String? = null,
)

@Serializable
data class HttpAuth(
    val username: String,
    val password: String,
)

@Serializable
data class Geolocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
)

@Serializable
data class PdfPageOptions(
    val pageSize: String? = null,
    val landscape: Boolean? = null,
    val marginTop: String? = null,
    val marginRight: String? = null,
    val marginBottom: String? = null,
    val marginLeft: String? = null,
)

@Serializable
data class StorageDestination(
    val destination: String? = null,
    val format: String? = null,
)

// ── Screenshot ────────────────────────────────────────────────────────────────

/**
 * Options for POST /v1/screenshot.
 * At least one of [url], [html], or [markdown] must be set.
 */
@Serializable
data class ScreenshotOptions(
    val url: String? = null,
    val html: String? = null,
    val markdown: String? = null,
    val format: String? = null,         // png|jpeg|webp|avif|pdf
    val quality: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val device: String? = null,
    val fullPage: Boolean? = null,
    val selector: String? = null,
    val delay: Int? = null,
    val timeout: Int? = null,
    val waitUntil: String? = null,
    val waitForSelector: String? = null,
    val darkMode: Boolean? = null,
    val css: String? = null,
    val javascript: String? = null,
    val hideSelectors: List<String>? = null,
    val clickSelector: String? = null,
    val blockAds: Boolean? = null,
    val blockTrackers: Boolean? = null,
    val blockCookieBanners: Boolean? = null,
    val userAgent: String? = null,
    val extraHeaders: Map<String, String>? = null,
    val cookies: List<Cookie>? = null,
    val httpAuth: HttpAuth? = null,
    val proxy: String? = null,
    val premiumProxy: Boolean? = null,
    val geolocation: Geolocation? = null,
    val timezone: String? = null,
    val pdf: PdfPageOptions? = null,
    val storage: StorageDestination? = null,
    val webhookUrl: String? = null,
)

@Serializable
data class StorageUploadResult(
    val id: String,
    val url: String,
)

// ── Scrape ────────────────────────────────────────────────────────────────────

/**
 * Options for POST /v1/scrape.
 */
@Serializable
data class ScrapeOptions(
    val url: String,
    val type: String? = null,           // text|html|links
    val pages: Int? = null,
    val waitMs: Int? = null,
    val proxy: String? = null,
    val premiumProxy: Boolean? = null,
    val blockResources: Boolean? = null,
    val locale: String? = null,
)

@Serializable
data class ScrapeItem(
    val page: Int,
    val url: String,
    val data: String,
)

@Serializable
data class ScrapeResult(
    val success: Boolean,
    val results: List<ScrapeItem>,
)

// ── Extract ───────────────────────────────────────────────────────────────────

/**
 * Options for POST /v1/extract.
 */
@Serializable
data class ExtractOptions(
    val url: String,
    val type: String? = null,           // html|text|markdown|article|links|images|metadata|structured
    val selector: String? = null,
    val waitFor: String? = null,
    val timeout: Int? = null,
    val darkMode: Boolean? = null,
    val blockAds: Boolean? = null,
    val blockCookieBanners: Boolean? = null,
    val includeImages: Boolean? = null,
    val maxLength: Int? = null,
)

@Serializable
data class ExtractResult(
    val success: Boolean,
    val type: String,
    val url: String,
    val data: JsonElement? = null,      // String, list, or object depending on type
    val responseTime: Int,
)

// ── Analyze ───────────────────────────────────────────────────────────────────

/**
 * Options for POST /v1/analyze.
 */
@Serializable
data class AnalyzeOptions(
    val url: String,
    val prompt: String? = null,
    val provider: String? = null,       // openai|anthropic
    @SerialName("apiKey") val llmApiKey: String? = null,
    val model: String? = null,
    val jsonSchema: String? = null,
    val includeScreenshot: Boolean? = null,
    val includeMetadata: Boolean? = null,
    val maxContentLength: Int? = null,
)

@Serializable
data class AnalyzeResult(
    val success: Boolean,
    val url: String,
    val metadata: JsonElement? = null,
    val analysis: JsonElement? = null,
    val provider: String? = null,
    val model: String? = null,
    val responseTime: Int,
)

// ── Storage ───────────────────────────────────────────────────────────────────

@Serializable
data class StorageFile(
    val id: String,
    val url: String,
    val size: Long? = null,
    val format: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class StorageFilesResult(
    val success: Boolean,
    val files: List<StorageFile>,
    val total: Int? = null,
)

@Serializable
data class StorageUsageResult(
    val success: Boolean,
    val used: Long,
    val limit: Long? = null,
)

@Serializable
data class S3Config(
    val bucket: String,
    val region: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val endpoint: String? = null,
    val publicUrl: String? = null,
)

// ── Scheduled ─────────────────────────────────────────────────────────────────

@Serializable
data class ScheduledOptions(
    val url: String,
    val cronExpression: String,
    val format: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fullPage: Boolean? = null,
    val webhookUrl: String? = null,
)

@Serializable
data class ScheduledJob(
    val id: String,
    val url: String,
    val cronExpression: String,
    val format: String? = null,
    val active: Boolean? = null,
    val createdAt: String? = null,
    val nextRunAt: String? = null,
)

@Serializable
data class ScheduledListResult(
    val success: Boolean,
    val jobs: List<ScheduledJob>,
)

// ── Webhooks ──────────────────────────────────────────────────────────────────

@Serializable
data class WebhookOptions(
    val url: String,
    val events: List<String>,
    val secret: String? = null,
)

@Serializable
data class Webhook(
    val id: String,
    val url: String,
    val events: List<String>,
    val active: Boolean? = null,
    val createdAt: String? = null,
)

@Serializable
data class WebhooksListResult(
    val success: Boolean,
    val webhooks: List<Webhook>,
)

// ── API Keys ──────────────────────────────────────────────────────────────────

@Serializable
data class ApiKey(
    val id: String,
    val name: String,
    val key: String? = null,           // only present on creation
    val createdAt: String? = null,
)

@Serializable
data class KeysListResult(
    val success: Boolean,
    val keys: List<ApiKey>,
)

// ── Error response ────────────────────────────────────────────────────────────

@Serializable
internal data class ApiErrorResponse(
    val statusCode: Int? = null,
    val error: String? = null,
    val message: String? = null,
)

// ─── Video ────────────────────────────────────────────────────────────────────

/** Easing function for scroll animation in video recording. */
enum class ScrollEasing {
    linear, ease_in, ease_out, ease_in_out, ease_in_out_quint
}

/** Output format for video recordings. */
enum class VideoFormat { webm, mp4, gif }

/** Options for [SnapAPI.video]. */
@kotlinx.serialization.Serializable
data class VideoOptions(
    val url: String,
    val format: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Int? = null,
    val fps: Int? = null,
    val scrolling: Boolean? = null,
    val scrollSpeed: Int? = null,
    val scrollDelay: Int? = null,
    val scrollDuration: Int? = null,
    val scrollBy: Int? = null,
    val scrollEasing: String? = null,
    val scrollBack: Boolean? = null,
    val scrollComplete: Boolean? = null,
    val darkMode: Boolean? = null,
    val blockAds: Boolean? = null,
    val blockCookieBanners: Boolean? = null,
    val delay: Int? = null,
    /** "binary" returns raw bytes; "base64"/"json" returns [VideoResult]. */
    val responseType: String? = null,
)

/** Returned by [SnapAPI.videoResult] when responseType is "base64" or "json". */
@kotlinx.serialization.Serializable
data class VideoResult(
    val data: String,
    val mimeType: String,
    val format: String,
    val width: Int,
    val height: Int,
    val duration: Int,
    val size: Int,
)

// ─── Account Usage ────────────────────────────────────────────────────────────

/** Returned by [SnapAPI.usage]. */
@kotlinx.serialization.Serializable
data class AccountUsageResult(
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val resetAt: String? = null,
)

// ─── Ping ─────────────────────────────────────────────────────────────────────

/** Returned by [SnapAPI.ping]. */
@kotlinx.serialization.Serializable
data class PingResult(
    val status: String,
    val timestamp: Long,
)
