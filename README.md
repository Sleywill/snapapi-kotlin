# snapapi-kotlin

Official Kotlin SDK (v2.0.0) for [SnapAPI](https://snapapi.pics) — lightning-fast screenshot, PDF, scrape, extract, and AI web analysis API.

## Requirements

- Kotlin 1.9+
- JVM 11+
- OkHttp 4 (bundled)

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("pics.snapapi:snapapi-kotlin:2.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>pics.snapapi</groupId>
    <artifactId>snapapi-kotlin</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Quick Start

```kotlin
import pics.snapapi.SnapAPI
import pics.snapapi.ScreenshotOptions
import java.io.File

suspend fun main() {
    val api = SnapAPI(apiKey = System.getenv("SNAPAPI_KEY"))

    val imageBytes = api.screenshot(
        ScreenshotOptions(url = "https://example.com", format = "png", fullPage = true)
    )
    File("screenshot.png").writeBytes(imageBytes)
    println("Saved ${imageBytes.size} bytes")
}
```

## Authentication

```kotlin
val api = SnapAPI(apiKey = System.getenv("SNAPAPI_KEY") ?: error("No key"))
```

## Endpoints

### Screenshot — `POST /v1/screenshot`

```kotlin
// Basic PNG
val png = api.screenshot(
    ScreenshotOptions(url = "https://example.com", format = "png", width = 1440)
)

// Full-page dark mode
val img = api.screenshot(ScreenshotOptions(
    url                = "https://example.com",
    fullPage           = true,
    darkMode           = true,
    blockAds           = true,
    blockCookieBanners = true,
))

// From HTML
val htmlImg = api.screenshot(
    ScreenshotOptions(html = "<h1>Hello!</h1>", format = "png")
)

// Device emulation
val mobile = api.screenshot(
    ScreenshotOptions(url = "https://example.com", device = "iphone-15-pro")
)
```

### PDF — `POST /v1/screenshot` (format=pdf)

```kotlin
val pdf = api.pdf(ScreenshotOptions(
    url = "https://example.com",
    pdf = PdfPageOptions(pageSize = "A4", landscape = false),
))
File("page.pdf").writeBytes(pdf)
```

### Screenshot to Storage

```kotlin
val result = api.screenshotToStorage(ScreenshotOptions(
    url     = "https://example.com",
    storage = StorageDestination(destination = "s3"),
))
println(result.url)
```

### Scrape — `POST /v1/scrape`

```kotlin
val result = api.scrape(ScrapeOptions(
    url   = "https://example.com",
    type  = "text",    // text|html|links
    pages = 3,
))
result.results.forEach { page ->
    println("Page ${page.page}: ${page.data.take(100)}")
}
```

### Extract — `POST /v1/extract`

```kotlin
// Convenience helpers
val article  = api.extractArticle("https://example.com/post")
val markdown = api.extractMarkdown("https://example.com")
val links    = api.extractLinks("https://example.com")
val images   = api.extractImages("https://example.com")
val metadata = api.extractMetadata("https://example.com")

// Full control
val result = api.extract(ExtractOptions(
    url           = "https://example.com",
    type          = "structured",
    includeImages = true,
    maxLength     = 5000,
))
println("Response time: ${result.responseTime}ms")
```

### Analyze — `POST /v1/analyze`

```kotlin
val result = api.analyze(AnalyzeOptions(
    url               = "https://example.com",
    prompt            = "What is the main purpose of this page?",
    provider          = "openai",     // openai|anthropic
    llmApiKey         = "sk-...",     // your LLM API key
    includeScreenshot = true,
))
println(result.analysis)
```

### Storage — `/v1/storage/*`

```kotlin
// List files
val files = api.listStorageFiles()

// Usage
val usage = api.storageUsage()
println("Used: ${usage.used} bytes")

// Configure S3
api.configureS3(S3Config(
    bucket          = "my-bucket",
    region          = "us-east-1",
    accessKeyId     = "AKIA...",
    secretAccessKey = "...",
))

// Delete a file
api.deleteStorageFile("file-id")
```

### Scheduled — `/v1/scheduled/*`

```kotlin
// Create hourly job
val job = api.createScheduled(ScheduledOptions(
    url            = "https://example.com",
    cronExpression = "0 * * * *",
    format         = "png",
    fullPage       = true,
    webhookUrl     = "https://myapp.com/hook",
))

// List all
val jobs = api.listScheduled()

// Delete
api.deleteScheduled(job.id)
```

### Webhooks — `/v1/webhooks/*`

```kotlin
// Create
val hook = api.createWebhook(WebhookOptions(
    url    = "https://myapp.com/snapapi",
    events = listOf("screenshot.completed", "scheduled.run"),
    secret = "my-secret",
))

// List / delete
val hooks = api.listWebhooks()
api.deleteWebhook(hook.id)
```

### API Keys — `/v1/keys/*`

```kotlin
// List
val keys = api.listKeys()

// Create (key shown only once)
val key = api.createKey("production")
println(key.key)

// Revoke
api.deleteKey(key.id)
```

## Error Handling

```kotlin
import pics.snapapi.SnapAPIException

try {
    val data = api.screenshot(ScreenshotOptions(url = "https://example.com"))
} catch (e: SnapAPIException) {
    println("Error [${e.errorCode}]: ${e.message} (HTTP ${e.statusCode})")
    if (e.isRetryable) {
        // retry with exponential back-off
    }
} catch (e: IllegalArgumentException) {
    println("Bad options: ${e.message}")
}
```

### Error Codes

| Code | Meaning |
|------|---------|
| `INVALID_PARAMS` | Missing or invalid parameters |
| `UNAUTHORIZED` | Invalid or missing API key |
| `FORBIDDEN` | Feature not available on your plan |
| `RATE_LIMITED` | Too many requests |
| `TIMEOUT` | Page load timed out |
| `QUOTA_EXCEEDED` | Monthly quota reached |
| `CONNECTION_ERROR` | Network error |
| `SERVER_ERROR` | Upstream server error |

## Custom OkHttpClient

```kotlin
val httpClient = OkHttpClient.Builder()
    .readTimeout(120, TimeUnit.SECONDS)
    .addInterceptor(loggingInterceptor)
    .build()

val api = SnapAPI(apiKey = "...", client = httpClient)
```

## Building

```bash
./gradlew build
./gradlew test
```

## License

MIT
