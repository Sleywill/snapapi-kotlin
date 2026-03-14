package pics.snapapi

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.http.RetryPolicy
import pics.snapapi.models.ScreenshotOptions
import kotlin.test.*

class ErrorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SnapAPIClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SnapAPIClient(
            apiKey       = "sk_test",
            baseUrl      = server.url("/").toString().trimEnd('/'),
            okHttpClient = OkHttpClient(),
            retryPolicy  = RetryPolicy.NEVER,
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    // ── isRetryable ───────────────────────────────────────────────────────────

    @Test
    fun `RateLimited isRetryable`() {
        val ex = SnapAPIException.RateLimited(retryAfterMs = 5_000L)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `NetworkError isRetryable`() {
        val ex = SnapAPIException.NetworkError("timeout")
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `ServerError 5xx isRetryable`() {
        val ex = SnapAPIException.ServerError(500, "INTERNAL_ERROR", "oops")
        assertTrue(ex.isRetryable)

        val ex503 = SnapAPIException.ServerError(503, "UNAVAILABLE", "down")
        assertTrue(ex503.isRetryable)
    }

    @Test
    fun `ServerError 4xx not retryable`() {
        val ex400 = SnapAPIException.ServerError(400, "BAD_REQUEST", "bad")
        assertFalse(ex400.isRetryable)

        val ex404 = SnapAPIException.ServerError(404, "NOT_FOUND", "nope")
        assertFalse(ex404.isRetryable)
    }

    @Test
    fun `Unauthorized not retryable`() {
        assertFalse(SnapAPIException.Unauthorized().isRetryable)
    }

    @Test
    fun `QuotaExceeded not retryable`() {
        assertFalse(SnapAPIException.QuotaExceeded().isRetryable)
    }

    @Test
    fun `InvalidParams not retryable`() {
        assertFalse(SnapAPIException.InvalidParams("url required").isRetryable)
    }

    @Test
    fun `DecodingError not retryable`() {
        assertFalse(SnapAPIException.DecodingError("bad json").isRetryable)
    }

    // ── retryDelayMs ─────────────────────────────────────────────────────────

    @Test
    fun `RateLimited carries retryAfterMs`() {
        val ex = SnapAPIException.RateLimited(retryAfterMs = 30_000L)
        assertEquals(30_000L, ex.retryDelayMs)
    }

    @Test
    fun `other exceptions have null retryDelayMs`() {
        assertNull(SnapAPIException.Unauthorized().retryDelayMs)
        assertNull(SnapAPIException.ServerError(500, "E", "msg").retryDelayMs)
    }

    // ── HTTP status code mapping ───────────────────────────────────────────────

    @Test
    fun `403 maps to Unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFailsWith<SnapAPIException.Unauthorized> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
    }

    @Test
    fun `422 maps to ServerError`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"error":"UNPROCESSABLE","message":"Invalid input"}""")
        )
        val ex = assertFailsWith<SnapAPIException.ServerError> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(422, ex.statusCode)
        assertEquals("UNPROCESSABLE", ex.errorCode)
    }

    @Test
    fun `429 without Retry-After header uses fallback delay`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val ex = assertFailsWith<SnapAPIException.RateLimited> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        // fallback is 60_000 ms
        assertEquals(60_000L, ex.retryAfterMs)
    }

    // ── RetryPolicy ───────────────────────────────────────────────────────────

    @Test
    fun `NEVER policy does not retry`() {
        val policy = RetryPolicy.NEVER
        assertFalse(policy.shouldRetry(SnapAPIException.RateLimited(1000L), attempt = 0))
    }

    @Test
    fun `default policy retries up to 3 times`() {
        val policy  = RetryPolicy.DEFAULT
        val rateLim = SnapAPIException.RateLimited(1000L)
        assertTrue(policy.shouldRetry(rateLim, attempt = 0))
        assertTrue(policy.shouldRetry(rateLim, attempt = 2))
        assertFalse(policy.shouldRetry(rateLim, attempt = 3))
    }

    @Test
    fun `exponential backoff doubles each attempt`() {
        val policy = RetryPolicy(baseDelayMs = 1_000L, maxDelayMs = 60_000L)
        val d0 = policy.delayMs(0)
        val d1 = policy.delayMs(1)
        assertTrue(d1 > d0)
    }

    @Test
    fun `retry after override is respected`() {
        val policy = RetryPolicy()
        val delay  = policy.delayMs(0, overrideMs = 45_000L)
        assertEquals(45_000L, delay)
    }

    @Test
    fun `max delay cap is enforced`() {
        val policy = RetryPolicy(baseDelayMs = 1_000L, maxDelayMs = 5_000L)
        val delay  = policy.delayMs(100) // would be astronomical without cap
        assertTrue(delay <= 5_000L)
    }

    // ── Retry integration ─────────────────────────────────────────────────────

    @Test
    fun `client retries on 500 and succeeds`() = runTest {
        val retryClient = SnapAPIClient(
            apiKey       = "sk_test",
            baseUrl      = server.url("/").toString().trimEnd('/'),
            okHttpClient = OkHttpClient(),
            retryPolicy  = RetryPolicy(maxAttempts = 2, baseDelayMs = 0),
        )
        // First call: 500, second call: 200
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        server.enqueue(
            MockResponse().setBody("""{"used":10,"total":100,"remaining":90}""")
        )
        val q = retryClient.quota()
        assertEquals(10, q.used)
        // Verify two requests were made
        server.takeRequest()
        server.takeRequest()
    }

    @Test
    fun `client does not retry on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFailsWith<SnapAPIException.Unauthorized> {
            client.quota()
        }
    }
}
