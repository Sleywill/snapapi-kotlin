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
    fun `RateLimitException isRetryable`() {
        val ex = SnapAPIException.RateLimitException(retryAfter = 5)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `NetworkException isRetryable`() {
        val ex = SnapAPIException.NetworkException("timeout")
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `ServerException 5xx isRetryable`() {
        val ex500 = SnapAPIException.ServerException(500, "INTERNAL_ERROR", "oops")
        assertTrue(ex500.isRetryable)

        val ex503 = SnapAPIException.ServerException(503, "UNAVAILABLE", "down")
        assertTrue(ex503.isRetryable)
    }

    @Test
    fun `ServerException 4xx not retryable`() {
        val ex400 = SnapAPIException.ServerException(400, "BAD_REQUEST", "bad")
        assertFalse(ex400.isRetryable)

        val ex404 = SnapAPIException.ServerException(404, "NOT_FOUND", "nope")
        assertFalse(ex404.isRetryable)
    }

    @Test
    fun `AuthenticationException not retryable`() {
        assertFalse(SnapAPIException.AuthenticationException().isRetryable)
    }

    @Test
    fun `QuotaExceededException not retryable`() {
        assertFalse(SnapAPIException.QuotaExceededException().isRetryable)
    }

    @Test
    fun `ValidationException not retryable`() {
        val ex = SnapAPIException.ValidationException(mapOf("url" to "required"))
        assertFalse(ex.isRetryable)
    }

    @Test
    fun `DecodingError not retryable`() {
        assertFalse(SnapAPIException.DecodingError("bad json").isRetryable)
    }

    // ── retryDelayMs ─────────────────────────────────────────────────────────

    @Test
    fun `RateLimitException carries retryAfterMs`() {
        val ex = SnapAPIException.RateLimitException(retryAfter = 30)
        assertEquals(30_000L, ex.retryAfterMs)
        assertEquals(30_000L, ex.retryDelayMs)
    }

    @Test
    fun `RateLimitException with null retryAfter uses 60s fallback`() {
        val ex = SnapAPIException.RateLimitException(retryAfter = null)
        assertEquals(60_000L, ex.retryAfterMs)
    }

    @Test
    fun `other exceptions have null retryDelayMs`() {
        assertNull(SnapAPIException.AuthenticationException().retryDelayMs)
        assertNull(SnapAPIException.ServerException(500, "E", "msg").retryDelayMs)
        assertNull(SnapAPIException.QuotaExceededException().retryDelayMs)
    }

    // ── ValidationException fields ───────────────────────────────────────────

    @Test
    fun `ValidationException carries field errors`() {
        val fields = mapOf("url" to "must not be blank", "format" to "unsupported")
        val ex = SnapAPIException.ValidationException(fields)
        assertEquals("must not be blank", ex.fields["url"])
        assertEquals("unsupported",        ex.fields["format"])
    }

    @Test
    fun `ValidationException empty fields by default`() {
        val ex = SnapAPIException.ValidationException()
        assertTrue(ex.fields.isEmpty())
    }

    // ── Backward-compat types ─────────────────────────────────────────────────

    @Test
    fun `legacy RateLimited isRetryable`() {
        val ex = SnapAPIException.RateLimited(retryAfterMs = 5_000L)
        assertTrue(ex.isRetryable)
        assertEquals(5_000L, ex.retryDelayMs)
    }

    @Test
    fun `legacy NetworkError isRetryable`() {
        val ex = SnapAPIException.NetworkError("timeout")
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `legacy ServerError 5xx isRetryable`() {
        val ex = SnapAPIException.ServerError(500, "INTERNAL_ERROR", "oops")
        assertTrue(ex.isRetryable)
    }

    @Test
    fun `legacy Unauthorized not retryable`() {
        assertFalse(SnapAPIException.Unauthorized().isRetryable)
    }

    @Test
    fun `legacy QuotaExceeded not retryable`() {
        assertFalse(SnapAPIException.QuotaExceeded().isRetryable)
    }

    @Test
    fun `legacy InvalidParams not retryable`() {
        assertFalse(SnapAPIException.InvalidParams("url required").isRetryable)
    }

    // ── HTTP status code mapping ───────────────────────────────────────────────

    @Test
    fun `401 maps to AuthenticationException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFailsWith<SnapAPIException.AuthenticationException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
    }

    @Test
    fun `403 maps to AuthenticationException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFailsWith<SnapAPIException.AuthenticationException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
    }

    @Test
    fun `422 maps to ValidationException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"error":"UNPROCESSABLE","message":"Invalid input","fields":{"url":"required"}}"""),
        )
        val ex = assertFailsWith<SnapAPIException.ValidationException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals("required", ex.fields["url"])
    }

    @Test
    fun `500 maps to ServerException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"INTERNAL_ERROR","message":"oops"}"""),
        )
        val ex = assertFailsWith<SnapAPIException.ServerException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(500,              ex.statusCode)
        assertEquals("INTERNAL_ERROR", ex.errorCode)
    }

    @Test
    fun `429 with Retry-After maps to RateLimitException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "30"),
        )
        val ex = assertFailsWith<SnapAPIException.RateLimitException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(30, ex.retryAfter)
    }

    @Test
    fun `429 without Retry-After header has null retryAfter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val ex = assertFailsWith<SnapAPIException.RateLimitException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertNull(ex.retryAfter)
    }

    // ── RetryPolicy ───────────────────────────────────────────────────────────

    @Test
    fun `NEVER policy does not retry`() {
        val policy = RetryPolicy.NEVER
        assertFalse(policy.shouldRetry(SnapAPIException.RateLimitException(5), attempt = 0))
    }

    @Test
    fun `default policy retries up to 3 times`() {
        val policy  = RetryPolicy.DEFAULT
        val rateLim = SnapAPIException.RateLimitException(1)
        assertTrue(policy.shouldRetry(rateLim, attempt = 0))
        assertTrue(policy.shouldRetry(rateLim, attempt = 2))
        assertFalse(policy.shouldRetry(rateLim, attempt = 3))
    }

    @Test
    fun `exponential backoff grows each attempt`() {
        val policy = RetryPolicy(baseDelayMs = 1_000L, maxDelayMs = 60_000L)
        val d0 = policy.delayMs(0)
        val d1 = policy.delayMs(1)
        val d2 = policy.delayMs(2)
        assertTrue(d1 > d0, "delay should grow: d0=$d0 d1=$d1")
        assertTrue(d2 > d1, "delay should grow: d1=$d1 d2=$d2")
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

    @Test
    fun `AGGRESSIVE policy has 5 retries`() {
        val policy  = RetryPolicy.AGGRESSIVE
        val rateLim = SnapAPIException.RateLimitException(1)
        assertTrue(policy.shouldRetry(rateLim, attempt = 4))
        assertFalse(policy.shouldRetry(rateLim, attempt = 5))
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
            MockResponse().setBody("""{"used":10,"total":100,"remaining":90}"""),
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
        assertFailsWith<SnapAPIException.AuthenticationException> {
            client.quota()
        }
    }

    @Test
    fun `client does not retry on 402`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402))
        assertFailsWith<SnapAPIException.QuotaExceededException> {
            client.quota()
        }
    }
}
