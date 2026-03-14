package pics.snapapi.exceptions

/**
 * Sealed hierarchy of all errors thrown by the SnapAPI Kotlin SDK.
 *
 * Use a `when` expression to handle specific failure modes:
 *
 * ```kotlin
 * try {
 *     val data = client.screenshot(opts)
 * } catch (e: SnapAPIException) {
 *     when (e) {
 *         is SnapAPIException.Unauthorized    -> println("Invalid API key")
 *         is SnapAPIException.RateLimited     -> delay(e.retryAfterMs)
 *         is SnapAPIException.QuotaExceeded   -> println("Upgrade your plan")
 *         is SnapAPIException.ServerError     -> println("HTTP ${e.statusCode}: ${e.message}")
 *         is SnapAPIException.NetworkError    -> println("Network: ${e.cause?.message}")
 *         is SnapAPIException.InvalidParams   -> println("Bad params: ${e.message}")
 *         is SnapAPIException.DecodingError   -> println("Decode failed: ${e.cause?.message}")
 *     }
 * }
 * ```
 */
sealed class SnapAPIException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * The API key is missing, invalid, or has been revoked.
     * HTTP 401 or 403.
     */
    class Unauthorized(message: String = "Unauthorized: invalid or missing API key.") :
        SnapAPIException(message)

    /**
     * The rate limit was exceeded.
     *
     * @property retryAfterMs Milliseconds to wait before retrying. The SDK's
     *   built-in retry policy uses this automatically.
     */
    class RateLimited(
        val retryAfterMs: Long,
        message: String = "Rate limited. Retry after ${retryAfterMs}ms.",
    ) : SnapAPIException(message)

    /**
     * The account's request quota for the current billing period is exhausted.
     * HTTP 402.
     */
    class QuotaExceeded(message: String = "Quota exceeded. Upgrade your plan at snapapi.pics/dashboard.") :
        SnapAPIException(message)

    /**
     * The API returned a non-2xx response.
     *
     * @property statusCode HTTP status code.
     * @property errorCode  Short machine-readable code from the server body (e.g. `"RATE_LIMITED"`).
     */
    class ServerError(
        val statusCode: Int,
        val errorCode: String,
        message: String,
    ) : SnapAPIException(message) {

        /** Whether retrying might succeed (true for 5xx). */
        val isRetryable: Boolean get() = statusCode >= 500
    }

    /**
     * A network-level failure: DNS, TLS, connection reset, timeout, etc.
     */
    class NetworkError(message: String, cause: Throwable? = null) :
        SnapAPIException(message, cause)

    /**
     * A required parameter was missing or had an invalid value.
     */
    class InvalidParams(message: String) : SnapAPIException(message)

    /**
     * The server response could not be decoded into the expected type.
     */
    class DecodingError(message: String, cause: Throwable? = null) :
        SnapAPIException(message, cause)

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Whether this exception is transient and the request can be safely retried.
     */
    val isRetryable: Boolean
        get() = when (this) {
            is RateLimited  -> true
            is NetworkError -> true
            is ServerError  -> statusCode >= 500
            else            -> false
        }

    /**
     * Milliseconds to wait before retrying, if known (from [RateLimited]).
     * Returns `null` for all other exception types.
     */
    val retryDelayMs: Long?
        get() = (this as? RateLimited)?.retryAfterMs
}
