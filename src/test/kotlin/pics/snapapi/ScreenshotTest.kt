package pics.snapapi

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `screenshot requires source`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.screenshot(ScreenshotOptions())
        }
    }

    @Test
    fun `screenshotToStorage requires storage option`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.screenshotToStorage(ScreenshotOptions(url = "https://example.com"))
        }
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `screenshot with url returns binary bytes`() = runTest {
        val expectedBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG header
        server.enqueue(MockResponse().setBody(okio.Buffer().write(expectedBytes)))

        val result = client.screenshot(ScreenshotOptions(url = "https://example.com"))
        assertContentEquals(expectedBytes, result)

        val request = server.takeRequest()
        assertEquals("POST",           request.method)
        assertEquals("/v1/screenshot", request.path)
        assertEquals("sk_test",        request.getHeader("X-Api-Key"))
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
    fun `screenshotToFile writes bytes to disk`() = runTest {
        val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(imageBytes)))

        val tempFile = createTempFile()
        try {
            val bytesWritten = client.screenshotToFile(
                ScreenshotOptions(url = "https://example.com"),
                file = tempFile,
            )
            assertEquals(imageBytes.size, bytesWritten)
            assertContentEquals(imageBytes, tempFile.readBytes())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `screenshot sends Authorization header`() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(0))))
        client.screenshot(ScreenshotOptions(url = "https://example.com"))

        val req = server.takeRequest()
        assertEquals("Bearer sk_test", req.getHeader("Authorization"))
    }

    @Test
    fun `screenshot sends User-Agent header`() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(0))))
        client.screenshot(ScreenshotOptions(url = "https://example.com"))

        val req = server.takeRequest()
        val ua = req.getHeader("User-Agent") ?: ""
        assert(ua.startsWith("snapapi-kotlin/")) { "User-Agent should start with snapapi-kotlin/" }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    @Test
    fun `401 throws AuthenticationException`() = runTest {
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
    fun `402 throws QuotaExceededException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(402))
        assertFailsWith<SnapAPIException.QuotaExceededException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
    }

    @Test
    fun `429 throws RateLimitException with retry delay`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "30"),
        )
        val ex = assertFailsWith<SnapAPIException.RateLimitException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(30,        ex.retryAfter)
        assertEquals(30_000L,   ex.retryAfterMs)
    }

    @Test
    fun `429 without Retry-After uses null retryAfter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val ex = assertFailsWith<SnapAPIException.RateLimitException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(null, ex.retryAfter)
        assertEquals(60_000L, ex.retryAfterMs) // fallback: 60s
    }

    @Test
    fun `422 throws ValidationException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody("""{"error":"VALIDATION_ERROR","message":"Invalid","fields":{"url":"must not be blank"}}"""),
        )
        val ex = assertFailsWith<SnapAPIException.ValidationException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals("must not be blank", ex.fields["url"])
    }

    @Test
    fun `500 throws ServerException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"INTERNAL_ERROR","message":"Something went wrong"}"""),
        )
        val ex = assertFailsWith<SnapAPIException.ServerException> {
            client.screenshot(ScreenshotOptions(url = "https://example.com"))
        }
        assertEquals(500,            ex.statusCode)
        assertEquals("INTERNAL_ERROR", ex.errorCode)
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    @Test
    fun `pdf requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.pdf(PdfOptions(url = ""))
        }
    }

    @Test
    fun `pdf request goes to correct endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                okio.Buffer().write(byteArrayOf(0x25, 0x50, 0x44, 0x46)), // PDF header
            ),
        )
        client.pdf(PdfOptions(url = "https://example.com", pageFormat = PDFPageFormat.A4))

        val req = server.takeRequest()
        assertEquals("/v1/pdf", req.path)
        assertEquals("POST",    req.method)
    }

    @Test
    fun `pdfToFile writes bytes to disk`() = runTest {
        val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // %PDF-
        server.enqueue(MockResponse().setBody(okio.Buffer().write(pdfBytes)))

        val tempFile = createTempFile()
        try {
            val written = client.pdfToFile(PdfOptions(url = "https://example.com"), tempFile)
            assertEquals(pdfBytes.size, written)
            assertContentEquals(pdfBytes, tempFile.readBytes())
        } finally {
            tempFile.delete()
        }
    }
}

private fun createTempFile(): java.io.File {
    val f = java.io.File.createTempFile("snapapi_test_", ".bin")
    f.deleteOnExit()
    return f
}
