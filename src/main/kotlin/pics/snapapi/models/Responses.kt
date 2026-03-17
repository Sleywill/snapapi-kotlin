package pics.snapapi.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Quota / Usage ─────────────────────────────────────────────────────────────

/** Returned by `GET /v1/usage` and `GET /v1/quota`. */
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

// ── Analyze ──────────────────────────────────────────────────────────────────

/** Returned by [pics.snapapi.SnapAPIClient.analyze]. */
@Serializable
data class AnalyzeResult(
    /** The LLM analysis result. */
    val result: String,
    /** The URL that was analyzed. */
    val url: String,
)

// ── Storage namespace ─────────────────────────────────────────────────────────

/** A file stored in SnapAPI's storage backend. */
@Serializable
data class StorageFile(
    /** Unique file identifier. */
    val id: String,
    /** Public URL. */
    val url: String,
    /** MIME type (e.g. `"image/png"`). */
    val mimeType: String? = null,
    /** File size in bytes. */
    val size: Int? = null,
    /** ISO 8601 creation timestamp. */
    val createdAt: String? = null,
)

/** Returned by `GET /v1/storage/files`. */
@Serializable
data class StorageListResult(
    /** List of stored files. */
    val files: List<StorageFile>,
    /** Whether there are more pages of results. */
    val hasMore: Boolean = false,
    /** Cursor for the next page (`after` parameter). */
    val nextCursor: String? = null,
)

/** Returned by `DELETE /v1/storage/files/{id}`. */
@Serializable
data class StorageDeleteResult(
    /** Whether the deletion succeeded. */
    val success: Boolean,
    /** The ID that was deleted. */
    val id: String,
)

// ── Scheduled namespace ───────────────────────────────────────────────────────

/** A scheduled capture task. */
@Serializable
data class ScheduledTask(
    /** Unique task identifier. */
    val id: String,
    /** Target URL. */
    val url: String,
    /** The action performed (e.g. `"screenshot"`). */
    val action: String? = null,
    /** Cron expression. */
    val cron: String? = null,
    /** Human-readable interval. */
    val interval: String? = null,
    /** Webhook notification URL. */
    val webhookUrl: String? = null,
    /** Whether the task is currently active. */
    val active: Boolean,
    /** ISO 8601 timestamp of the next run. */
    val nextRunAt: String? = null,
    /** ISO 8601 creation timestamp. */
    val createdAt: String? = null,
)

/** Returned by `GET /v1/scheduled`. */
@Serializable
data class ScheduledListResult(
    /** All scheduled tasks for this account. */
    val tasks: List<ScheduledTask>,
)

/** Generic operation result. */
@Serializable
data class OperationResult(
    /** Whether the operation succeeded. */
    val success: Boolean,
    /** Optional message from the server. */
    val message: String? = null,
)

// ── Webhooks namespace ────────────────────────────────────────────────────────

/** A registered webhook. */
@Serializable
data class Webhook(
    /** Unique webhook identifier. */
    val id: String,
    /** Target HTTPS URL. */
    val url: String,
    /** Subscribed event types. */
    val events: List<String> = emptyList(),
    /** Whether the webhook is active. */
    val active: Boolean,
    /** ISO 8601 creation timestamp. */
    val createdAt: String? = null,
)

/** Returned by `GET /v1/webhooks`. */
@Serializable
data class WebhookListResult(
    /** All webhooks for this account. */
    val webhooks: List<Webhook>,
)

// ── API Keys namespace ─────────────────────────────────────────────────────────

/** An API key belonging to the account. */
@Serializable
data class ApiKey(
    /** Unique key identifier. */
    val id: String,
    /** Human-readable label. */
    val name: String,
    /** Partially masked key value (e.g. `"sk_live_****abcd"`). */
    val key: String? = null,
    /** Full key value — only present immediately after creation. */
    val fullKey: String? = null,
    /** Permission scopes. */
    val scopes: List<String> = emptyList(),
    /** Whether this key is currently active. */
    val active: Boolean,
    /** ISO 8601 expiry timestamp, or `null` if it never expires. */
    val expiresAt: String? = null,
    /** ISO 8601 creation timestamp. */
    val createdAt: String? = null,
    /** ISO 8601 timestamp of last use, or `null`. */
    val lastUsedAt: String? = null,
)

/** Returned by `GET /v1/api-keys`. */
@Serializable
data class ApiKeyListResult(
    /** All API keys for this account. */
    val keys: List<ApiKey>,
)

// ── Internal error body ───────────────────────────────────────────────────────

@Serializable
internal data class ApiErrorBody(
    val statusCode: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val fields: Map<String, String>? = null,
)
