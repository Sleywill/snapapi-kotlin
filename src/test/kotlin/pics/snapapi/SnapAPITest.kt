package pics.snapapi

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SnapAPITest {

    @Test
    fun `screenshot requires source`() = runTest {
        val api = SnapAPI("test-key")
        assertFailsWith<IllegalArgumentException> {
            api.screenshot(ScreenshotOptions())
        }
    }

    @Test
    fun `scrape requires url`() = runTest {
        val api = SnapAPI("test-key")
        assertFailsWith<IllegalArgumentException> {
            api.scrape(ScrapeOptions(url = ""))
        }
    }

    @Test
    fun `extract requires url`() = runTest {
        val api = SnapAPI("test-key")
        assertFailsWith<IllegalArgumentException> {
            api.extract(ExtractOptions(url = ""))
        }
    }

    @Test
    fun `analyze requires url`() = runTest {
        val api = SnapAPI("test-key")
        assertFailsWith<IllegalArgumentException> {
            api.analyze(AnalyzeOptions(url = ""))
        }
    }

    @Test
    fun `SnapAPIException isRetryable`() {
        assertTrue(SnapAPIException("Too many requests", "RATE_LIMITED", 429).isRetryable)
        assertTrue(SnapAPIException("Server error", "SERVER_ERROR", 500).isRetryable)
        assert(!SnapAPIException("Bad request", "INVALID_PARAMS", 400).isRetryable)
    }
}
