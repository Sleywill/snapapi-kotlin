package pics.snapapi.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Quota ─────────────────────────────────────────────────────────────────────

/** Returned by `GET /v1/quota`. */
@Serializable
data class QuotaResult(
    /** Number of API calls used in the current billing period. */
    val used: Int,
    /** Total calls allowed in the current billing period. */
    val total: Int,
    /** Remaining calls. */
    val remaining: Int,
    /** ISO 8601 timestamp when the quota resets. */
    val resetAt: String? = null,
)

// ── Ping ─────────────────────────────────────────────────────────────────────

/** Returned by `GET /v1/ping`. */
@Serializable
data class PingResult(
    /** Status string — always `"ok"` for healthy responses. */
    val status: String,
    /** Unix timestamp in milliseconds. */
    val timestamp: Long,
)

// ── Screenshot ────────────────────────────────────────────────────────────────

/**
 * Returned by [pics.snapapi.SnapAPIClient.screenshotToStorage] when the API
 * uploads the result to the configured storage backend.
 */
@Serializable
data class StorageUploadResult(
    /** Unique identifier of the stored file. */
    val id: String,
    /** Public URL of the stored file. */
    val url: String,
)

// ── Scrape ────────────────────────────────────────────────────────────────────

/** The response from `POST /v1/scrape`. */
@Serializable
data class ScrapeResult(
    /** Whether the request succeeded. */
    val success: Boolean,
    /** One item per scraped page. */
    val results: List<ScrapeItem>,
)

/** A single scraped page. */
@Serializable
data class ScrapeItem(
    /** 1-based page number. */
    val page: Int,
    /** The URL that was scraped. */
    val url: String,
    /** The scraped content (text, HTML, or links depending on request type). */
    val data: String,
)

// ── Extract ───────────────────────────────────────────────────────────────────

/** The response from `POST /v1/extract`. */
@Serializable
data class ExtractResult(
    /** Whether the request succeeded. */
    val success: Boolean,
    /** The format of the returned [data]. */
    val type: String,
    /** The URL that was processed. */
    val url: String,
    /** Extracted content. Structure depends on the requested format. */
    val data: JsonElement? = null,
    /** Server-side processing time in milliseconds. */
    val responseTime: Int,
)

// ── Video ─────────────────────────────────────────────────────────────────────

/** Returned by [pics.snapapi.SnapAPIClient.videoResult]. */
@Serializable
data class VideoResult(
    /** Base64-encoded video data. */
    val data: String,
    /** MIME type (e.g. `"video/mp4"`). */
    val mimeType: String,
    /** Output format string (e.g. `"mp4"`). */
    val format: String,
    /** Video width in pixels. */
    val width: Int,
    /** Video height in pixels. */
    val height: Int,
    /** Duration in milliseconds. */
    val duration: Int,
    /** File size in bytes. */
    val size: Int,
)

// ── Internal error body ───────────────────────────────────────────────────────

@Serializable
internal data class ApiErrorBody(
    val statusCode: Int? = null,
    val error: String? = null,
    val message: String? = null,
)
