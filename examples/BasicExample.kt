// BasicExample.kt
// Demonstrates common SnapAPI Kotlin SDK usage patterns.
//
// Run from the repo root:
//   ./gradlew run -PmainClass=BasicExampleKt
// Or compile and run directly with kotlinc.
//
// Environment variable: SNAPAPI_KEY=sk_your_key

import kotlinx.coroutines.runBlocking
import pics.snapapi.SnapAPIClient
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.models.*
import java.io.File

fun main() = runBlocking {
    val apiKey = System.getenv("SNAPAPI_KEY") ?: "sk_your_key"
    val client = SnapAPIClient(apiKey = apiKey)

    try {
        runExamples(client)
    } catch (e: SnapAPIException) {
        when (e) {
            is SnapAPIException.Unauthorized  -> println("ERROR: invalid API key")
            is SnapAPIException.RateLimited   -> println("ERROR: rate limited — retry after ${e.retryAfterMs}ms")
            is SnapAPIException.QuotaExceeded -> println("ERROR: quota exceeded — upgrade at snapapi.pics/dashboard")
            is SnapAPIException.ServerError   -> println("ERROR: server error ${e.statusCode}: ${e.message}")
            is SnapAPIException.NetworkError  -> println("ERROR: network — ${e.message}")
            else                              -> println("ERROR: ${e.message}")
        }
    }
}

private suspend fun runExamples(client: SnapAPIClient) {

    // ── Quota ─────────────────────────────────────────────────────────────────
    val q = client.quota()
    println("Quota: ${q.used}/${q.total} used (resets: ${q.resetAt ?: "unknown"})")

    // ── Screenshot ────────────────────────────────────────────────────────────
    println("\nTaking screenshot...")
    val screenshotBytes = client.screenshot(
        ScreenshotOptions(
            url      = "https://example.com",
            format   = ScreenshotFormat.PNG,
            fullPage = true,
            width    = 1440,
        )
    )
    File("screenshot.png").writeBytes(screenshotBytes)
    println("Saved screenshot.png (${screenshotBytes.size} bytes)")

    // ── PDF ───────────────────────────────────────────────────────────────────
    println("\nGenerating PDF...")
    val pdfBytes = client.pdf(
        PdfOptions(
            url        = "https://example.com",
            pageFormat = PDFPageFormat.A4,
        )
    )
    File("page.pdf").writeBytes(pdfBytes)
    println("Saved page.pdf (${pdfBytes.size} bytes)")

    // ── Scrape ────────────────────────────────────────────────────────────────
    println("\nScraping...")
    val scrapeResult = client.scrape(
        ScrapeOptions(
            url      = "https://example.com",
            selector = "body",
        )
    )
    scrapeResult.results.forEach { item ->
        val preview = item.data.take(80)
        println("  Page ${item.page}: $preview...")
    }

    // ── Extract ───────────────────────────────────────────────────────────────
    println("\nExtracting article...")
    val article = client.extractArticle("https://example.com")
    println("  type=${article.type}  responseTime=${article.responseTime}ms")

    println("\nExtracting as Markdown...")
    val md = client.extractMarkdown("https://example.com")
    println("  type=${md.type}")

    // ── Analyze ──────────────────────────────────────────────────────────
    println("\nAnalyzing page (may return 503 if LLM credits are down)...")
    try {
        val analysis = client.analyze(
            AnalyzeOptions(
                url      = "https://example.com",
                prompt   = "Summarize this page in one sentence",
                provider = AnalyzeProvider.OPENAI,
            )
        )
        println("  Analysis: ${analysis.result.take(200)}...")
    } catch (e: SnapAPIException.ServerError) {
        if (e.statusCode == 503) {
            println("  Analyze endpoint unavailable (503 -- LLM credits may be exhausted)")
        } else throw e
    }

    // ── Usage ──────────────────────────────────────────────────────────────
    val usage = client.getUsage()
    println("\nUsage: ${usage.used}/${usage.total} used, ${usage.remaining} remaining")

    println("\nDone.")
}
