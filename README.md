# SnapAPI Kotlin SDK

[![Kotlin 1.9+](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![CI](https://github.com/Sleywill/snapapi-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/Sleywill/snapapi-kotlin/actions)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

Official Kotlin SDK for [SnapAPI.pics](https://snapapi.pics) -- screenshot, scrape, extract, analyze, and PDF generation as a service.

**v3.0.0** -- Sealed exception hierarchy, typed enums, retry with exponential backoff.

## Requirements

| Requirement | Minimum |
|-------------|---------|
| Kotlin      | 1.9     |
| JVM         | 11      |
| Android     | API 21+ |

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("pics.snapapi:snapapi-kotlin:3.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>pics.snapapi</groupId>
    <artifactId>snapapi-kotlin</artifactId>
    <version>3.0.0</version>
</dependency>
```

## Quickstart

```kotlin
import pics.snapapi.SnapAPIClient
import pics.snapapi.models.*
import java.io.File

val client = SnapAPIClient(apiKey = "sk_your_key")

// Screenshot
val png = client.screenshot(ScreenshotOptions(url = "https://example.com"))
File("shot.png").writeBytes(png)

// Screenshot to file
client.screenshotToFile(
    ScreenshotOptions(url = "https://example.com"),
    file = File("output.png")
)

// Scrape
val page = client.scrape(ScrapeOptions(url = "https://example.com"))
println(page.results.firstOrNull()?.data)

// Extract
val md = client.extractMarkdown("https://example.com")

// PDF
val pdf = client.pdf(PdfOptions(url = "https://example.com"))

// Analyze (LLM-powered)
val analysis = client.analyze(AnalyzeOptions(
    url = "https://example.com",
    prompt = "Summarize this page"
))

// Usage / Quota
val q = client.getUsage()
println("Used: ${q.used}/${q.total}")
```

## Endpoints

### Screenshot -- `POST /v1/screenshot`

```kotlin
val bytes = client.screenshot(
    ScreenshotOptions(
        url      = "https://example.com",
        format   = ScreenshotFormat.PNG,   // PNG | JPEG | WEBP | AVIF | PDF
        fullPage = true,
        width    = 1440,
        darkMode = true,
        blockAds = true,
    )
)
```

Capture from raw HTML or Markdown:

```kotlin
val png = client.screenshot(ScreenshotOptions(html = "<h1>Hello</h1>"))
```

Save directly to a file:

```kotlin
val bytesWritten = client.screenshotToFile(
    ScreenshotOptions(url = "https://example.com"),
    file = File("output.png")
)
println("Wrote $bytesWritten bytes")
```

### PDF -- `POST /v1/pdf`

```kotlin
val pdfBytes = client.pdf(
    PdfOptions(
        url        = "https://example.com",
        pageFormat = PDFPageFormat.A4,  // A4 | LETTER | A3 | LEGAL | TABLOID
        landscape  = false,
    )
)
File("page.pdf").writeBytes(pdfBytes)
```

### Scrape -- `POST /v1/scrape`

```kotlin
val result = client.scrape(
    ScrapeOptions(
        url      = "https://example.com",
        selector = "article",
        wait     = 1000,   // ms to wait for dynamic content
    )
)
result.results.forEach { item ->
    println("Page ${item.page}: ${item.data.take(100)}")
}
```

### Extract -- `POST /v1/extract`

```kotlin
// Convenience wrappers
val markdown = client.extractMarkdown("https://example.com")
val article  = client.extractArticle("https://example.com")
val text     = client.extractText("https://example.com")
val links    = client.extractLinks("https://example.com")
val images   = client.extractImages("https://example.com")
val metadata = client.extractMetadata("https://example.com")

// Full control
val result = client.extract(
    ExtractOptions(
        url       = "https://example.com",
        format    = ExtractFormat.MARKDOWN,
        maxLength = 4096,
    )
)
```

### Analyze -- `POST /v1/analyze`

Uses an LLM provider to analyze webpage content. This endpoint may return
HTTP 503 when LLM credits are exhausted on the server.

```kotlin
val result = client.analyze(
    AnalyzeOptions(
        url      = "https://example.com",
        prompt   = "Summarize the main points of this page",
        provider = AnalyzeProvider.OPENAI,
    )
)
println(result.result)
```

### Usage -- `GET /v1/usage`

```kotlin
val usage = client.getUsage()
println("Used: ${usage.used} / ${usage.total} -- ${usage.remaining} remaining")
println("Resets: ${usage.resetAt}")
```

## Error Handling

All methods throw `SnapAPIException` (a sealed class):

```kotlin
import pics.snapapi.exceptions.SnapAPIException

try {
    val bytes = client.screenshot(opts)
} catch (e: SnapAPIException) {
    when (e) {
        is SnapAPIException.Unauthorized  -> println("Invalid API key")
        is SnapAPIException.RateLimited   -> delay(e.retryAfterMs)
        is SnapAPIException.QuotaExceeded -> println("Upgrade plan at snapapi.pics/dashboard")
        is SnapAPIException.ServerError   -> println("HTTP ${e.statusCode} [${e.errorCode}]: ${e.message}")
        is SnapAPIException.NetworkError  -> println("Network error: ${e.message}")
        is SnapAPIException.InvalidParams -> println("Bad parameters: ${e.message}")
        is SnapAPIException.DecodingError -> println("Decode failed: ${e.message}")
    }
}
```

`isRetryable` is available on all exception types:

```kotlin
if (e.isRetryable) {
    delay(e.retryDelayMs ?: 5_000L)
    // retry...
}
```

## Retry Policy

The client retries transient errors automatically with exponential backoff.
The `Retry-After` header is always honoured on 429 responses.

```kotlin
import pics.snapapi.http.RetryPolicy

val client = SnapAPIClient(
    apiKey      = "sk_...",
    retryPolicy = RetryPolicy(
        maxAttempts = 5,
        baseDelayMs = 2_000L,  // 2 s for first retry
        maxDelayMs  = 60_000L  // cap at 60 s
    )
)

// Disable retries
val strict = SnapAPIClient(apiKey = "sk_...", retryPolicy = RetryPolicy.NEVER)
```

## Custom OkHttpClient

```kotlin
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val ok = OkHttpClient.Builder()
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

val client = SnapAPIClient(apiKey = "sk_...", okHttpClient = ok)
```

## Android Use Cases

Capture website screenshots in your Android app:

```kotlin
// In a Jetpack ViewModel
class MyViewModel : ViewModel() {
    private val client = SnapAPIClient(apiKey = "sk_...")

    fun capture(url: String) = viewModelScope.launch {
        val bytes = client.screenshot(
            ScreenshotOptions(url = url, width = 390, blockAds = true)
        )
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        _screenshot.value = bitmap
    }
}
```

## Server-Side Use Cases

Competitive intelligence pipeline:

```kotlin
// Capture competitor pages in parallel
coroutineScope {
    val jobs = competitors.map { url ->
        async {
            val bytes = client.screenshot(ScreenshotOptions(url = url, fullPage = true))
            File("output/${url.host}.png").writeBytes(bytes)
        }
    }
    jobs.awaitAll()
}
```

## Testing

Use MockWebServer to test without real network calls:

```kotlin
val server = MockWebServer()
server.enqueue(MockResponse().setBody("""{"used":10,"total":100,"remaining":90}"""))

val client = SnapAPIClient(
    apiKey      = "test",
    baseUrl     = server.url("/").toString().trimEnd('/'),
    retryPolicy = RetryPolicy.NEVER,
)
val q = client.quota()
assertEquals(10, q.used)
```

Run all tests:

```bash
./gradlew test
```

## Building

```bash
./gradlew build
```

## Examples

See the `examples/` directory for complete working examples:

- **BasicExample.kt** -- Quickstart covering all endpoints
- **AndroidExample.kt** -- Jetpack ViewModel integration for Android apps
- **ServerExample.kt** -- Server-side competitive intelligence pipeline

## License

MIT. See [LICENSE](LICENSE).
