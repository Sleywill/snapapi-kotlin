# SnapAPI Kotlin SDK

[![Kotlin 1.9+](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![CI](https://github.com/Sleywill/snapapi-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/Sleywill/snapapi-kotlin/actions)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

Official Kotlin SDK for [SnapAPI.pics](https://snapapi.pics) -- screenshot, scrape, extract, analyze, PDF, video, and OG image generation as a service.

**v3.1.0** -- Full endpoint coverage, sealed exception hierarchy, Storage/Scheduled/Webhooks/APIKeys namespaces, retry with exponential backoff.

**Base URL:** `https://api.snapapi.pics`

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
    implementation("pics.snapapi:snapapi-kotlin:3.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>pics.snapapi</groupId>
    <artifactId>snapapi-kotlin</artifactId>
    <version>3.1.0</version>
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
File("page.pdf").writeBytes(pdf)

// OG Image
val og = client.ogImage(OgImageOptions(url = "https://example.com"))
File("og.png").writeBytes(og)

// Analyze (LLM-powered)
val analysis = client.analyze(AnalyzeOptions(
    url    = "https://example.com",
    prompt = "Summarize this page",
))

// Usage / Quota
val q = client.getUsage()
println("Used: ${q.used}/${q.total}")
```

The `SnapAPI` typealias is also available if you prefer that name:

```kotlin
val client = SnapAPI(apiKey = "sk_your_key")
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
val png = client.screenshot(ScreenshotOptions(markdown = "# Hello World"))
```

Write directly to a file:

```kotlin
val bytesWritten = client.screenshotToFile(
    ScreenshotOptions(url = "https://example.com"),
    file = File("output.png")
)
println("Wrote $bytesWritten bytes")
```

Upload to storage (returns URL):

```kotlin
val uploaded = client.screenshotToStorage(
    ScreenshotOptions(
        url     = "https://example.com",
        storage = StorageDestination(destination = "s3"),
    )
)
println("Stored at: ${uploaded.url}")
```

### PDF -- `POST /v1/pdf`

```kotlin
val pdfBytes = client.pdf(
    PdfOptions(
        url        = "https://example.com",
        pageFormat = PDFPageFormat.A4,      // A4 | LETTER | A3 | A5 | LEGAL | TABLOID
        landscape  = false,
        wait       = 1000,
    )
)
File("page.pdf").writeBytes(pdfBytes)

// Write directly to a file
client.pdfToFile(PdfOptions(url = "https://example.com"), File("page.pdf"))
```

### Scrape -- `POST /v1/scrape`

```kotlin
val result = client.scrape(
    ScrapeOptions(
        url      = "https://example.com",
        selector = "article",
        wait     = 1000,    // ms to wait for dynamic content
        pages    = 3,       // paginate through up to 3 pages
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

Uses an LLM provider to analyze webpage content. May return HTTP 503 when
LLM credits are exhausted on the server.

```kotlin
val result = client.analyze(
    AnalyzeOptions(
        url      = "https://example.com",
        prompt   = "Summarize the main points of this page",
        provider = AnalyzeProvider.OPENAI,    // OPENAI | ANTHROPIC | GOOGLE
    )
)
println(result.result)
```

### Video -- `POST /v1/video`

```kotlin
// Raw bytes
val bytes = client.video(
    VideoOptions(
        url         = "https://example.com",
        format      = VideoFormat.MP4,       // MP4 | WEBM | GIF
        duration    = 5000,
        scrolling   = true,
        scrollSpeed = 200,
        blockAds    = true,
    )
)
File("recording.mp4").writeBytes(bytes)

// Structured response with metadata
val result = client.videoResult(VideoOptions(url = "https://example.com"))
println("${result.width}x${result.height}, ${result.duration}ms")
```

### OG Image -- `POST /v1/og-image`

```kotlin
val bytes = client.ogImage(
    OgImageOptions(
        url    = "https://example.com",
        width  = 1200,
        height = 630,
        format = ScreenshotFormat.PNG,
    )
)
File("og.png").writeBytes(bytes)
```

### Usage -- `GET /v1/usage`

```kotlin
val usage = client.getUsage()
println("Used: ${usage.used} / ${usage.total} -- ${usage.remaining} remaining")
println("Resets: ${usage.resetAt}")

// quota() is an alias
val q = client.quota()
```

### Ping -- `GET /v1/ping`

```kotlin
val pong = client.ping()
println("API status: ${pong.status}")
```

## Namespaced APIs

### Storage -- `client.storage`

```kotlin
// List stored files
val files = client.storage.list(StorageListOptions(limit = 20))
files.files.forEach { println("${it.id}: ${it.url}") }

// Get a single file
val file = client.storage.get("file_abc123")

// Delete a file
val deleted = client.storage.delete("file_abc123")
```

### Scheduled Captures -- `client.scheduled`

```kotlin
// Create a daily screenshot task
val task = client.scheduled.create(
    ScheduleOptions(
        url        = "https://example.com",
        interval   = ScheduleInterval.DAILY,   // HOURLY | DAILY | WEEKLY | MONTHLY
        action     = "screenshot",
        webhookUrl = "https://myapp.com/hooks/snapapi",
    )
)
println("Created task: ${task.id}")

// List all tasks
val tasks = client.scheduled.list()

// Update (pause) a task
client.scheduled.update(task.id, ScheduleUpdateOptions(active = false))

// Delete
client.scheduled.delete(task.id)
```

### Webhooks -- `client.webhooks`

```kotlin
// Register a webhook
val hook = client.webhooks.create(
    WebhookOptions(
        url    = "https://myapp.com/hooks/snapapi",
        events = listOf(WebhookEvent.SCREENSHOT_COMPLETED, WebhookEvent.SCRAPE_COMPLETED),
        secret = "my_signing_secret",
    )
)
println("Webhook: ${hook.id}")

// List all webhooks
val hooks = client.webhooks.list()

// Update
client.webhooks.update(hook.id, WebhookUpdateOptions(active = false))

// Delete
client.webhooks.delete(hook.id)
```

### API Keys -- `client.apiKeys`

```kotlin
// Create a scoped key
val key = client.apiKeys.create(
    ApiKeyOptions(
        name   = "CI pipeline key",
        scopes = listOf(ApiKeyScope.SCREENSHOT, ApiKeyScope.PDF),
    )
)
println("Full key (save this!): ${key.fullKey}")

// List all keys
val keys = client.apiKeys.list()

// Rename / change scopes
client.apiKeys.update(key.id, ApiKeyUpdateOptions(name = "renamed key"))

// Revoke
client.apiKeys.revoke(key.id)
```

## Error Handling

All methods throw `SnapAPIException` (a sealed class):

```kotlin
import pics.snapapi.exceptions.SnapAPIException

try {
    val bytes = client.screenshot(opts)
} catch (e: SnapAPIException) {
    when (e) {
        is SnapAPIException.AuthenticationException -> println("Invalid API key")
        is SnapAPIException.RateLimitException      -> delay(e.retryAfterMs)
        is SnapAPIException.QuotaExceededException  -> println("Upgrade plan at snapapi.pics/dashboard")
        is SnapAPIException.ValidationException     -> println("Bad fields: ${e.fields}")
        is SnapAPIException.ServerException         -> println("HTTP ${e.statusCode} [${e.errorCode}]: ${e.message}")
        is SnapAPIException.NetworkException        -> println("Network error: ${e.message}")
        is SnapAPIException.DecodingError           -> println("Decode failed: ${e.message}")
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
        baseDelayMs = 2_000L,   // 2 s for first retry
        maxDelayMs  = 60_000L,  // cap at 60 s
    )
)

// Pre-built policies
val noRetry    = SnapAPIClient(apiKey = "sk_...", retryPolicy = RetryPolicy.NEVER)
val aggressive = SnapAPIClient(apiKey = "sk_...", retryPolicy = RetryPolicy.AGGRESSIVE) // 5 retries
```

## Custom OkHttpClient

```kotlin
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val ok = OkHttpClient.Builder()
    .readTimeout(120, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        // custom logging, etc.
        chain.proceed(chain.request())
    }
    .build()

val client = SnapAPIClient(apiKey = "sk_...", okHttpClient = ok)
```

## Java Interop

The constructor is annotated with `@JvmOverloads`, so you can use it from Java:

```java
SnapAPIClient client = new SnapAPIClient("sk_your_key");

// All suspend functions are usable via kotlinx-coroutines-jdk8 / rx adapters.
// From Java, wrap with runBlocking or use a CoroutineScope adapter.
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
coroutineScope {
    val jobs = competitors.map { url ->
        async {
            val bytes = client.screenshot(ScreenshotOptions(url = url, fullPage = true))
            File("output/${URI(url).host}.png").writeBytes(bytes)
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
