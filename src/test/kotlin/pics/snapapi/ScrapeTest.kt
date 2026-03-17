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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScrapeTest {

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

    // ── Scrape ────────────────────────────────────────────────────────────────

    @Test
    fun `scrape requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.scrape(ScrapeOptions(url = ""))
        }
    }

    @Test
    fun `scrape returns structured result`() = runTest {
        val body = """
            {
                "success": true,
                "results": [
                    {"page": 1, "url": "https://example.com", "data": "Hello world"}
                ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))

        val result = client.scrape(ScrapeOptions(url = "https://example.com"))
        assertTrue(result.success)
        assertEquals(1,             result.results.size)
        assertEquals("Hello world", result.results[0].data)
        assertEquals(1,             result.results[0].page)
    }

    @Test
    fun `scrape sends correct headers and path`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"results":[]}"""))
        client.scrape(ScrapeOptions(url = "https://example.com"))

        val req = server.takeRequest()
        assertEquals("POST",        req.method)
        assertEquals("/v1/scrape",  req.path)
        assertEquals("sk_test",     req.getHeader("X-Api-Key"))
        assertTrue(req.getHeader("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun `scrape with selector serializes correctly`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"results":[]}"""))
        client.scrape(ScrapeOptions(url = "https://example.com", selector = "article"))

        val req  = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("article"), "Body should contain selector value")
    }

    // ── Extract ───────────────────────────────────────────────────────────────

    @Test
    fun `extract requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.extract(ExtractOptions(url = ""))
        }
    }

    @Test
    fun `extract markdown convenience sends format field`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"markdown","url":"https://example.com","data":"# Hi","responseTime":100}""",
            ),
        )
        val result = client.extractMarkdown("https://example.com")
        assertEquals("markdown", result.type)

        val req = server.takeRequest()
        assertEquals("/v1/extract", req.path)
        assertTrue(req.body.readUtf8().contains("markdown"))
    }

    @Test
    fun `extract article convenience`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"article","url":"https://example.com","data":"text","responseTime":200}""",
            ),
        )
        val result = client.extractArticle("https://example.com")
        assertEquals("article", result.type)
    }

    @Test
    fun `extract text convenience`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"text","url":"https://example.com","data":"plain text","responseTime":50}""",
            ),
        )
        val result = client.extractText("https://example.com")
        assertEquals("text", result.type)
    }

    @Test
    fun `extract links convenience`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"links","url":"https://example.com","data":[],"responseTime":50}""",
            ),
        )
        val result = client.extractLinks("https://example.com")
        assertEquals("links", result.type)
    }

    @Test
    fun `extract images convenience`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"images","url":"https://example.com","data":[],"responseTime":50}""",
            ),
        )
        val result = client.extractImages("https://example.com")
        assertEquals("images", result.type)
    }

    @Test
    fun `extract metadata convenience`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"metadata","url":"https://example.com","data":{},"responseTime":75}""",
            ),
        )
        val result = client.extractMetadata("https://example.com")
        assertEquals("metadata", result.type)
    }

    // ── Analyze ───────────────────────────────────────────────────────────────

    @Test
    fun `analyze requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.analyze(AnalyzeOptions(url = ""))
        }
    }

    @Test
    fun `analyze returns result`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"result":"This is a summary","url":"https://example.com"}""",
            ),
        )
        val result = client.analyze(
            AnalyzeOptions(
                url      = "https://example.com",
                prompt   = "Summarize",
                provider = AnalyzeProvider.OPENAI,
            ),
        )
        assertEquals("This is a summary",   result.result)
        assertEquals("https://example.com", result.url)

        val req = server.takeRequest()
        assertEquals("/v1/analyze", req.path)
        assertTrue(req.body.readUtf8().contains("openai"))
    }

    @Test
    fun `analyze 503 throws ServerException`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"error":"SERVICE_UNAVAILABLE","message":"LLM credits exhausted"}"""),
        )
        val ex = assertFailsWith<SnapAPIException.ServerException> {
            client.analyze(AnalyzeOptions(url = "https://example.com"))
        }
        assertEquals(503, ex.statusCode)
        assertTrue(ex.isRetryable)
    }

    // ── Usage / Quota ──────────────────────────────────────────────────────────

    @Test
    fun `getUsage calls GET slash v1 slash usage`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"used":25,"total":200,"remaining":175}""",
            ),
        )
        val usage = client.getUsage()
        assertEquals(25,  usage.used)
        assertEquals(175, usage.remaining)

        val req = server.takeRequest()
        assertEquals("GET",       req.method)
        assertEquals("/v1/usage", req.path)
    }

    @Test
    fun `quota is alias for getUsage`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"used":50,"total":500,"remaining":450,"resetAt":"2026-04-01T00:00:00Z"}""",
            ),
        )
        val q = client.quota()
        assertEquals(50,  q.used)
        assertEquals(500, q.total)
        assertEquals(450, q.remaining)
        assertEquals("2026-04-01T00:00:00Z", q.resetAt)

        val req = server.takeRequest()
        assertEquals("GET",       req.method)
        assertEquals("/v1/usage", req.path)   // both getUsage and quota call /v1/usage
    }

    // ── Ping ──────────────────────────────────────────────────────────────────

    @Test
    fun `ping returns status ok`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","timestamp":1742000000000}""",
            ),
        )
        val result = client.ping()
        assertEquals("ok",           result.status)
        assertEquals(1742000000000L, result.timestamp)

        val req = server.takeRequest()
        assertEquals("GET",      req.method)
        assertEquals("/v1/ping", req.path)
    }

    // ── OG Image ──────────────────────────────────────────────────────────────

    @Test
    fun `ogImage requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.ogImage(OgImageOptions(url = ""))
        }
    }

    @Test
    fun `ogImage posts to correct endpoint`() = runTest {
        val imgBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(imgBytes)))

        val result = client.ogImage(OgImageOptions(url = "https://example.com"))
        assertContentEquals(imgBytes, result)

        val req = server.takeRequest()
        assertEquals("POST",         req.method)
        assertEquals("/v1/og-image", req.path)
    }

    // ── Video ──────────────────────────────────────────────────────────────────

    @Test
    fun `video requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.video(VideoOptions(url = ""))
        }
    }

    @Test
    fun `video posts to correct endpoint with binary responseType`() = runTest {
        val videoBytes = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        server.enqueue(MockResponse().setBody(okio.Buffer().write(videoBytes)))

        client.video(VideoOptions(url = "https://example.com", format = VideoFormat.MP4))

        val req  = server.takeRequest()
        val body = req.body.readUtf8()
        assertEquals("POST",      req.method)
        assertEquals("/v1/video", req.path)
        assertTrue(body.contains("binary"))
    }

    @Test
    fun `videoResult posts with json responseType`() = runTest {
        val responseJson = """
            {
                "data":"base64data",
                "mimeType":"video/mp4",
                "format":"mp4",
                "width":1280,
                "height":720,
                "duration":5000,
                "size":102400
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(responseJson))

        val result = client.videoResult(VideoOptions(url = "https://example.com"))
        assertEquals("base64data", result.data)
        assertEquals("video/mp4",  result.mimeType)
        assertEquals(1280,          result.width)

        val req  = server.takeRequest()
        val body = req.body.readUtf8()
        assertTrue(body.contains("json"))
    }
}

private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
    assertEquals(expected.size, actual.size, "Byte array sizes differ")
    expected.forEachIndexed { i, b ->
        assertEquals(b, actual[i], "Byte at index $i differs")
    }
}
