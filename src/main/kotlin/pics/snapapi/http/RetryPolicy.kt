package pics.snapapi.http

import pics.snapapi.exceptions.SnapAPIException
import kotlin.math.min
import kotlin.math.pow

/**
 * Configures how the SDK retries failed requests.
 *
 * The default policy retries up to **3 times** with exponential backoff and
 * always honours the `Retry-After` header value from [SnapAPIException.RateLimited].
 *
 * ```kotlin
 * // Custom policy: 5 retries, 2 s base delay
 * val client = SnapAPIClient(
 *     apiKey = "sk_...",
 *     retryPolicy = RetryPolicy(maxAttempts = 5, baseDelayMs = 2_000L)
 * )
 * ```
 */
data class RetryPolicy(
    /** Maximum number of retry attempts (not counting the initial request). */
    val maxAttempts: Int = 3,
    /** Base delay in milliseconds for the first retry. Doubles each attempt. */
    val baseDelayMs: Long = 1_000L,
    /** Upper cap for any single wait, in milliseconds. */
    val maxDelayMs: Long = 30_000L,
) {
    companion object {
        /** The default policy used by [pics.snapapi.SnapAPIClient]. */
        val DEFAULT = RetryPolicy()

        /** A policy that never retries. */
        val NEVER = RetryPolicy(maxAttempts = 0)
    }

    /**
     * Returns the delay in milliseconds before attempt number [attempt]
     * (0-based, so 0 = first retry).
     *
     * If [overrideMs] is provided (from a `Retry-After` header) it takes precedence.
     */
    fun delayMs(attempt: Int, overrideMs: Long? = null): Long {
        if (overrideMs != null) return min(overrideMs, maxDelayMs)
        val exponential = (baseDelayMs * 2.0.pow(attempt.toDouble())).toLong()
        return min(exponential, maxDelayMs)
    }

    /**
     * Returns `true` if the exception is transient and we have attempts left.
     */
    fun shouldRetry(exception: SnapAPIException, attempt: Int): Boolean {
        if (attempt >= maxAttempts) return false
        return exception.isRetryable
    }
}
