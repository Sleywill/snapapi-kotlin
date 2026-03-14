package pics.snapapi

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        assertEquals(1, result.results.size)
        assertEquals("Hello world", result.results[0].data)
        assertEquals(1, result.results[0].page)
    }

    @Test
    fun `scrape sends correct headers`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"results":[]}"""))
        client.scrape(ScrapeOptions(url = "https://example.com"))

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/scrape", req.path)
        assertEquals("Bearer sk_test", req.getHeader("Authorization"))
        assertTrue(req.getHeader("Content-Type")?.contains("application/json") == true)
    }

    @Test
    fun `extract requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.extract(ExtractOptions(url = ""))
        }
    }

    @Test
    fun `extract markdown convenience`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"type":"markdown","url":"https://example.com","data":"# Hi","responseTime":100}"""
            )
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
                """{"success":true,"type":"article","url":"https://example.com","data":"text","responseTime":200}"""
            )
        )
        val result = client.extractArticle("https://example.com")
        assertEquals("article", result.type)
    }

    @Test
    fun `quota returns parsed result`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"used":50,"total":500,"remaining":450,"resetAt":"2026-04-01T00:00:00Z"}"""
            )
        )
        val q = client.quota()
        assertEquals(50,  q.used)
        assertEquals(500, q.total)
        assertEquals(450, q.remaining)
        assertEquals("2026-04-01T00:00:00Z", q.resetAt)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/v1/quota", req.path)
    }
}
