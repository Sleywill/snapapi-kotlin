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
 *         is SnapAPIException.AuthenticationException -> println("Invalid API key")
 *         is SnapAPIException.RateLimitException      -> delay(e.retryAfterMs)
 *         is SnapAPIException.QuotaExceededException  -> println("Upgrade your plan")
 *         is SnapAPIException.ValidationException     -> println("Bad fields: ${e.fields}")
 *         is SnapAPIException.ServerException         -> println("HTTP ${e.statusCode}: ${e.message}")
 *         is SnapAPIException.NetworkException        -> println("Network: ${e.cause?.message}")
 *         is SnapAPIException.DecodingError           -> println("Decode failed: ${e.cause?.message}")
 *     }
 * }
 * ```
 */
sealed class SnapAPIException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    // ── Primary exception types (spec-required names) ─────────────────────────

    /**
     * The API key is missing, invalid, or has been revoked. HTTP 401 or 403.
     */
    class AuthenticationException(
        message: String = "Authentication failed: invalid or missing API key.",
    ) : SnapAPIException(message)

    /**
     * The rate limit was exceeded. HTTP 429.
     *
     * @property retryAfter  Seconds to wait before retrying (from `Retry-After` header), or `null`.
     * @property retryAfterMs Milliseconds to wait before retrying. The SDK's built-in retry policy
     *   uses this automatically.
     */
    class RateLimitException(
        val retryAfter: Int?,
        message: String = if (retryAfter != null)
            "Rate limited. Retry after ${retryAfter}s."
        else
            "Rate limited.",
    ) : SnapAPIException(message) {
        /** Convenience: [retryAfter] expressed in milliseconds. */
        val retryAfterMs: Long get() = (retryAfter?.toLong() ?: 60L) * 1_000L
    }

    /**
     * The account's request quota for the current billing period is exhausted. HTTP 402.
     */
    class QuotaExceededException(
        message: String = "Quota exceeded. Upgrade your plan at snapapi.pics/dashboard.",
    ) : SnapAPIException(message)

    /**
     * One or more request fields failed server-side validation. HTTP 422.
     *
     * @property fields Map of field name to error message.
     */
    class ValidationException(
        val fields: Map<String, String> = emptyMap(),
        message: String = "Validation failed: $fields",
    ) : SnapAPIException(message)

    /**
     * A network-level failure: DNS, TLS, connection reset, timeout, etc.
     */
    class NetworkException(
        message: String,
        cause: Throwable? = null,
    ) : SnapAPIException(message, cause)

    /**
     * The API returned a non-2xx response not covered by a more specific type.
     *
     * @property statusCode HTTP status code.
     * @property errorCode  Short machine-readable code from the server body (e.g. `"INTERNAL_ERROR"`).
     */
    class ServerException(
        val statusCode: Int,
        val errorCode: String = "HTTP_ERROR",
        message: String,
    ) : SnapAPIException(message) {
        /** Whether retrying might succeed (true for 5xx). */
        val isRetryable: Boolean get() = statusCode >= 500
    }

    /**
     * The server response could not be decoded into the expected type.
     */
    class DecodingError(message: String, cause: Throwable? = null) :
        SnapAPIException(message, cause)

    // ── Backward-compatible type aliases ─────────────────────────────────────

    /** Backward-compat alias for [AuthenticationException]. */
    class Unauthorized(
        message: String = "Unauthorized: invalid or missing API key.",
    ) : SnapAPIException(message)

    /** Backward-compat alias for [RateLimitException]. */
    class RateLimited(
        val retryAfterMs: Long,
        message: String = "Rate limited. Retry after ${retryAfterMs}ms.",
    ) : SnapAPIException(message)

    /** Backward-compat alias for [QuotaExceededException]. */
    class QuotaExceeded(
        message: String = "Quota exceeded. Upgrade your plan at snapapi.pics/dashboard.",
    ) : SnapAPIException(message)

    /** Backward-compat alias for [ValidationException]. */
    class InvalidParams(message: String) : SnapAPIException(message)

    /** Backward-compat alias for [NetworkException]. */
    class NetworkError(message: String, cause: Throwable? = null) :
        SnapAPIException(message, cause)

    /** Backward-compat alias for [ServerException]. */
    class ServerError(
        val statusCode: Int,
        val errorCode: String = "HTTP_ERROR",
        message: String,
    ) : SnapAPIException(message) {
        /** Whether retrying might succeed (true for 5xx). */
        val isRetryable: Boolean get() = statusCode >= 500
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Whether this exception is transient and the request can be safely retried.
     */
    val isRetryable: Boolean
        get() = when (this) {
            is RateLimitException -> true
            is NetworkException   -> true
            is ServerException    -> statusCode >= 500
            // backward-compat
            is RateLimited        -> true
            is NetworkError       -> true
            is ServerError        -> statusCode >= 500
            else                  -> false
        }

    /**
     * Milliseconds to wait before retrying, if known (from [RateLimitException] or [RateLimited]).
     * Returns `null` for all other exception types.
     */
    val retryDelayMs: Long?
        get() = when (this) {
            is RateLimitException -> retryAfterMs
            is RateLimited        -> retryAfterMs
            else                  -> null
        }
}
