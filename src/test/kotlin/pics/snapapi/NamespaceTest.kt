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
import kotlin.test.*

/**
 * Tests for the namespaced sub-clients: Storage, Scheduled, Webhooks, APIKeys.
 */
class NamespaceTest {

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

    // ── Storage ───────────────────────────────────────────────────────────────

    @Test
    fun `storage list calls GET v1 storage files`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"files":[],"hasMore":false}"""),
        )
        val result = client.storage.list()
        assertTrue(result.files.isEmpty())

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertTrue(req.path?.startsWith("/v1/storage/files") == true)
    }

    @Test
    fun `storage list with pagination params`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"files":[],"hasMore":false}"""),
        )
        client.storage.list(StorageListOptions(limit = 10, after = "file_abc"))

        val req = server.takeRequest()
        assertTrue(req.path?.contains("limit=10") == true)
        assertTrue(req.path?.contains("after=file_abc") == true)
    }

    @Test
    fun `storage get calls GET v1 storage files id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"file_123","url":"https://cdn.example.com/file_123.png","mimeType":"image/png","size":4096}""",
            ),
        )
        val file = client.storage.get("file_123")
        assertEquals("file_123", file.id)
        assertEquals("image/png", file.mimeType)

        val req = server.takeRequest()
        assertEquals("GET",                  req.method)
        assertEquals("/v1/storage/files/file_123", req.path)
    }

    @Test
    fun `storage get requires non-blank id`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.storage.get("")
        }
    }

    @Test
    fun `storage delete calls DELETE v1 storage files id`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true,"id":"file_123"}"""),
        )
        val result = client.storage.delete("file_123")
        assertTrue(result.success)
        assertEquals("file_123", result.id)

        val req = server.takeRequest()
        assertEquals("DELETE",               req.method)
        assertEquals("/v1/storage/files/file_123", req.path)
    }

    @Test
    fun `storage delete requires non-blank id`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.storage.delete("")
        }
    }

    // ── Scheduled ─────────────────────────────────────────────────────────────

    @Test
    fun `scheduled create calls POST v1 scheduled`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"sched_abc","url":"https://example.com","active":true,"action":"screenshot"}""",
            ),
        )
        val task = client.scheduled.create(
            ScheduleOptions(url = "https://example.com", interval = ScheduleInterval.DAILY),
        )
        assertEquals("sched_abc", task.id)
        assertTrue(task.active)

        val req = server.takeRequest()
        assertEquals("POST",          req.method)
        assertEquals("/v1/scheduled", req.path)
        assertTrue(req.body.readUtf8().contains("daily"))
    }

    @Test
    fun `scheduled create requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.scheduled.create(ScheduleOptions(url = ""))
        }
    }

    @Test
    fun `scheduled list calls GET v1 scheduled`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"tasks":[]}"""),
        )
        val result = client.scheduled.list()
        assertTrue(result.tasks.isEmpty())

        val req = server.takeRequest()
        assertEquals("GET",           req.method)
        assertEquals("/v1/scheduled", req.path)
    }

    @Test
    fun `scheduled get calls GET v1 scheduled id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"sched_abc","url":"https://example.com","active":true}""",
            ),
        )
        client.scheduled.get("sched_abc")

        val req = server.takeRequest()
        assertEquals("GET",                req.method)
        assertEquals("/v1/scheduled/sched_abc", req.path)
    }

    @Test
    fun `scheduled update calls PATCH v1 scheduled id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"sched_abc","url":"https://example.com","active":false}""",
            ),
        )
        client.scheduled.update("sched_abc", ScheduleUpdateOptions(active = false))

        val req = server.takeRequest()
        assertEquals("PATCH",               req.method)
        assertEquals("/v1/scheduled/sched_abc", req.path)
    }

    @Test
    fun `scheduled delete calls DELETE v1 scheduled id`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true}"""),
        )
        val result = client.scheduled.delete("sched_abc")
        assertTrue(result.success)

        val req = server.takeRequest()
        assertEquals("DELETE",               req.method)
        assertEquals("/v1/scheduled/sched_abc", req.path)
    }

    // ── Webhooks ──────────────────────────────────────────────────────────────

    @Test
    fun `webhooks create calls POST v1 webhooks`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"hook_xyz","url":"https://myapp.com/hooks","active":true,"events":["screenshot.completed"]}""",
            ),
        )
        val hook = client.webhooks.create(
            WebhookOptions(
                url    = "https://myapp.com/hooks",
                events = listOf(WebhookEvent.SCREENSHOT_COMPLETED),
            ),
        )
        assertEquals("hook_xyz",                         hook.id)
        assertEquals(listOf("screenshot.completed"),     hook.events)

        val req = server.takeRequest()
        assertEquals("POST",        req.method)
        assertEquals("/v1/webhooks", req.path)
    }

    @Test
    fun `webhooks create requires url`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.webhooks.create(WebhookOptions(url = ""))
        }
    }

    @Test
    fun `webhooks list calls GET v1 webhooks`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"webhooks":[]}"""),
        )
        val result = client.webhooks.list()
        assertTrue(result.webhooks.isEmpty())

        val req = server.takeRequest()
        assertEquals("GET",         req.method)
        assertEquals("/v1/webhooks", req.path)
    }

    @Test
    fun `webhooks update calls PATCH v1 webhooks id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"hook_xyz","url":"https://myapp.com/hooks","active":false,"events":[]}""",
            ),
        )
        client.webhooks.update("hook_xyz", WebhookUpdateOptions(active = false))

        val req = server.takeRequest()
        assertEquals("PATCH",             req.method)
        assertEquals("/v1/webhooks/hook_xyz", req.path)
    }

    @Test
    fun `webhooks delete calls DELETE v1 webhooks id`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true}"""),
        )
        val result = client.webhooks.delete("hook_xyz")
        assertTrue(result.success)

        val req = server.takeRequest()
        assertEquals("DELETE",             req.method)
        assertEquals("/v1/webhooks/hook_xyz", req.path)
    }

    // ── API Keys ───────────────────────────────────────────────────────────────

    @Test
    fun `apiKeys create calls POST v1 api-keys`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"key_abc","name":"CI pipeline","key":"sk_live_****abcd","fullKey":"sk_live_fullkeyvalue","active":true,"scopes":["screenshot"]}""",
            ),
        )
        val key = client.apiKeys.create(
            ApiKeyOptions(
                name   = "CI pipeline",
                scopes = listOf(ApiKeyScope.SCREENSHOT),
            ),
        )
        assertEquals("key_abc",            key.id)
        assertEquals("CI pipeline",        key.name)
        assertEquals("sk_live_fullkeyvalue", key.fullKey)

        val req = server.takeRequest()
        assertEquals("POST",        req.method)
        assertEquals("/v1/api-keys", req.path)
    }

    @Test
    fun `apiKeys create requires name`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.apiKeys.create(ApiKeyOptions(name = ""))
        }
    }

    @Test
    fun `apiKeys list calls GET v1 api-keys`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"keys":[]}"""),
        )
        val result = client.apiKeys.list()
        assertTrue(result.keys.isEmpty())

        val req = server.takeRequest()
        assertEquals("GET",         req.method)
        assertEquals("/v1/api-keys", req.path)
    }

    @Test
    fun `apiKeys get calls GET v1 api-keys id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"key_abc","name":"test key","active":true,"scopes":[]}""",
            ),
        )
        client.apiKeys.get("key_abc")

        val req = server.takeRequest()
        assertEquals("GET",               req.method)
        assertEquals("/v1/api-keys/key_abc", req.path)
    }

    @Test
    fun `apiKeys update calls PATCH v1 api-keys id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"key_abc","name":"renamed key","active":true,"scopes":[]}""",
            ),
        )
        client.apiKeys.update("key_abc", ApiKeyUpdateOptions(name = "renamed key"))

        val req = server.takeRequest()
        assertEquals("PATCH",             req.method)
        assertEquals("/v1/api-keys/key_abc", req.path)
        assertTrue(req.body.readUtf8().contains("renamed key"))
    }

    @Test
    fun `apiKeys revoke calls DELETE v1 api-keys id`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"success":true}"""),
        )
        val result = client.apiKeys.revoke("key_abc")
        assertTrue(result.success)

        val req = server.takeRequest()
        assertEquals("DELETE",             req.method)
        assertEquals("/v1/api-keys/key_abc", req.path)
    }

    @Test
    fun `apiKeys require non-blank id for get`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.apiKeys.get("")
        }
    }

    @Test
    fun `apiKeys require non-blank id for revoke`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            client.apiKeys.revoke("")
        }
    }

    // ── SnapAPI typealias ─────────────────────────────────────────────────────

    @Test
    fun `SnapAPI typealias works the same as SnapAPIClient`() = runTest {
        val snapAPI = SnapAPI(
            apiKey       = "sk_test",
            baseUrl      = server.url("/").toString().trimEnd('/'),
            okHttpClient = OkHttpClient(),
            retryPolicy  = RetryPolicy.NEVER,
        )
        server.enqueue(
            MockResponse().setBody("""{"status":"ok","timestamp":1742000000000}"""),
        )
        val ping = snapAPI.ping()
        assertEquals("ok", ping.status)
    }
}
