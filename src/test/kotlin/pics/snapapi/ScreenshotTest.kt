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
import pics.snapapi.models.*
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScreenshotTest {

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

    @Test
    fun `screenshot requires source`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.screenshot(ScreenshotOptions())
        }
    }

    @Test
    fun `screenshot with url succeeds`() = runTest {
        val expectedBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header
        server.enqueue(MockResponse().setBody(okio.Buffer().write(expectedBytes)))

        val result = client.screenshot(ScreenshotOptions(url = "https://example.com"))
        assertContentEquals(expectedBytes, result)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/screenshot", request.path)
        assertEquals("sk_test", request.getHeader("X-Api-Key"))
    }

    @Test
    fun `screenshot with html source`() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 2, 3))))
        val result = client.screenshot(ScreenshotOptions(html = "<h1>Hello</h1>"))
        assertEquals(3, result.size)
    }

    @Test
    fun `screenshot with markdown source`() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(4, 5, 6))))
        val result = client.screenshot(ScreenshotOptions(markdown = "# Hello"))
        assertEquals(3, result.size)
    }

    @Test
    fun `401 throws Unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFailsWith<SnapAPIException.Unauthorized> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
    }

    @Test
    fun `402 throws QuotaExceeded`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402))
        assertFailsWith<SnapAPIException.QuotaExceeded> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
    }

    @Test
    fun `429 throws RateLimited with retry delay`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "30")
        )
        val ex = assertFailsWith<SnapAPIException.RateLimited> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(30_000L, ex.retryAfterMs)
    }

    @Test
    fun `500 throws ServerError`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"INTERNAL_ERROR","message":"Something went wrong"}""")
        )
        val ex = assertFailsWith<SnapAPIException.ServerError> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(500, ex.statusCode)
        assertEquals("INTERNAL_ERROR", ex.errorCode)
    }

    @Test
    fun `pdf requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.pdf(PdfOptions(url = ""))
        }
    }

    @Test
    fun `pdf request goes to correct endpoint`() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(0x25, 0x50, 0x44, 0x46)))) // PDF header
        client.pdf(PdfOptions(url = "https://example.com", pageFormat = PDFPageFormat.A4))

        val req = server.takeRequest()
        assertEquals("/v1/pdf", req.path)
        assertEquals("POST", req.method)
    }
}
