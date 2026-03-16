// ServerExample.kt
// Demonstrates server-side Kotlin usage for competitive intelligence pipelines.
//
// Run with: kotlinc -script ServerExample.kt
// Or add to a Ktor/Spring Boot project.

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import pics.snapapi.SnapAPIClient
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.http.RetryPolicy
import pics.snapapi.models.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Competitive intelligence pipeline that:
 * 1. Captures screenshots of competitor landing pages
 * 2. Extracts pricing page content as markdown
 * 3. Saves everything to timestamped directories
 */
fun main() = runBlocking {

    val apiKey = System.getenv("SNAPAPI_KEY") ?: "sk_your_key"

    // Use a generous retry policy for batch operations
    val client = SnapAPIClient(
        apiKey      = apiKey,
        retryPolicy = RetryPolicy(maxAttempts = 5, baseDelayMs = 2_000L),
    )

    val competitors = listOf(
        "https://screenshotone.com",
        "https://urlbox.io",
        "https://firecrawl.dev",
    )

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
    val outputDir = File("competitive_intel/$timestamp")
    outputDir.mkdirs()

    println("Competitive Intelligence Pipeline")
    println("Output: ${outputDir.absolutePath}")
    println("Targets: ${competitors.size} competitors")
    println()

    // Check quota first
    try {
        val usage = client.getUsage()
        println("API usage: ${usage.used}/${usage.total} (${usage.remaining} remaining)")
        if (usage.remaining < competitors.size * 2) {
            println("WARNING: Low quota. Some operations may fail.")
        }
    } catch (e: SnapAPIException) {
        println("Could not check quota: ${e.message}")
    }
    println()

    // Capture screenshots and extract content in parallel
    coroutineScope {
        val jobs = competitors.map { url ->
            async {
                processCompetitor(client, url, outputDir)
            }
        }
        jobs.awaitAll()
    }

    println("\nPipeline complete. Results in: ${outputDir.absolutePath}")
}

private suspend fun processCompetitor(
    client: SnapAPIClient,
    url: String,
    outputDir: File,
) {
    val safeName = url
        .removePrefix("https://")
        .removePrefix("http://")
        .replace("/", "_")
        .replace(".", "_")

    // Capture full-page screenshot
    try {
        print("  Screenshot: $url ... ")
        val bytes = client.screenshot(
            ScreenshotOptions(
                url      = url,
                format   = ScreenshotFormat.PNG,
                fullPage = true,
                width    = 1920,
                blockAds = true,
                blockCookieBanners = true,
            )
        )
        val file = File(outputDir, "${safeName}.png")
        file.writeBytes(bytes)
        println("OK (${bytes.size / 1024} KB)")
    } catch (e: SnapAPIException) {
        println("FAILED: ${e.message}")
    }

    // Extract page content as markdown
    try {
        print("  Extract:    $url ... ")
        val result = client.extractMarkdown(url)
        val content = result.data?.toString() ?: ""
        val file = File(outputDir, "${safeName}.md")
        file.writeText("# $url\n\nExtracted: ${LocalDateTime.now()}\n\n$content")
        println("OK (${content.length} chars)")
    } catch (e: SnapAPIException) {
        println("FAILED: ${e.message}")
    }
}
