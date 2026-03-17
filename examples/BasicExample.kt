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
            is SnapAPIException.AuthenticationException -> println("ERROR: invalid API key")
            is SnapAPIException.RateLimitException      -> println("ERROR: rate limited -- retry after ${e.retryAfterMs}ms")
            is SnapAPIException.QuotaExceededException  -> println("ERROR: quota exceeded -- upgrade at snapapi.pics/dashboard")
            is SnapAPIException.ValidationException     -> println("ERROR: validation: ${e.fields}")
            is SnapAPIException.ServerException         -> println("ERROR: server ${e.statusCode}: ${e.message}")
            is SnapAPIException.NetworkException        -> println("ERROR: network -- ${e.message}")
            else                                        -> println("ERROR: ${e.message}")
        }
    }
}

private suspend fun runExamples(client: SnapAPIClient) {

    // ── Health check ──────────────────────────────────────────────────────────
    val pong = client.ping()
    println("API status: ${pong.status}")

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
            blockAds = true,
        )
    )
    File("screenshot.png").writeBytes(screenshotBytes)
    println("Saved screenshot.png (${screenshotBytes.size} bytes)")

    // ── Screenshot to file ────────────────────────────────────────────────────
    println("\nTaking mobile screenshot to file...")
    val bytesWritten = client.screenshotToFile(
        ScreenshotOptions(url = "https://example.com", width = 390),
        file = File("mobile.png"),
    )
    println("Saved mobile.png ($bytesWritten bytes)")

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

    // ── OG Image ──────────────────────────────────────────────────────────────
    println("\nGenerating OG image...")
    try {
        val ogBytes = client.ogImage(OgImageOptions(url = "https://example.com"))
        File("og.png").writeBytes(ogBytes)
        println("Saved og.png (${ogBytes.size} bytes)")
    } catch (e: SnapAPIException.ServerException) {
        println("  OG image unavailable: ${e.message}")
    }

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

    // ── Analyze ──────────────────────────────────────────────────────────────
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
    } catch (e: SnapAPIException.ServerException) {
        if (e.statusCode == 503) {
            println("  Analyze endpoint unavailable (503 -- LLM credits may be exhausted)")
        } else throw e
    }

    // ── Storage namespace ─────────────────────────────────────────────────────
    println("\nStorage namespace (listing files)...")
    try {
        val files = client.storage.list(StorageListOptions(limit = 5))
        println("  ${files.files.size} file(s) stored")
        files.files.forEach { file -> println("  - ${file.id}: ${file.url}") }
    } catch (e: SnapAPIException) {
        println("  Storage unavailable: ${e.message}")
    }

    // ── Webhooks namespace ────────────────────────────────────────────────────
    println("\nWebhooks namespace (listing webhooks)...")
    try {
        val hooks = client.webhooks.list()
        println("  ${hooks.webhooks.size} webhook(s) registered")
    } catch (e: SnapAPIException) {
        println("  Webhooks unavailable: ${e.message}")
    }

    // ── API Keys namespace ─────────────────────────────────────────────────────
    println("\nAPI Keys namespace (listing keys)...")
    try {
        val keys = client.apiKeys.list()
        println("  ${keys.keys.size} key(s) on account")
    } catch (e: SnapAPIException) {
        println("  API keys unavailable: ${e.message}")
    }

    // ── Scheduled namespace ────────────────────────────────────────────────────
    println("\nScheduled namespace (listing tasks)...")
    try {
        val tasks = client.scheduled.list()
        println("  ${tasks.tasks.size} scheduled task(s)")
    } catch (e: SnapAPIException) {
        println("  Scheduled unavailable: ${e.message}")
    }

    // ── Usage ─────────────────────────────────────────────────────────────────
    val usage = client.getUsage()
    println("\nUsage: ${usage.used}/${usage.total} used, ${usage.remaining} remaining")

    println("\nDone.")
}
