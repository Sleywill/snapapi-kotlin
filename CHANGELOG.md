# Changelog

All notable changes to the SnapAPI Kotlin SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-03-16

### Added
- `SnapAPIException` sealed class hierarchy:
  - `Unauthorized` -- 401/403 responses.
  - `RateLimited(retryAfterMs)` -- 429 with extracted retry delay.
  - `QuotaExceeded` -- 402 responses.
  - `ServerError(statusCode, errorCode, message)` -- all other non-2xx.
  - `NetworkError` -- transport-level failures.
  - `InvalidParams` -- client-side validation errors.
  - `DecodingError` -- JSON parse failures.
- `analyze()` method mapping to `POST /v1/analyze` with LLM provider support.
- `AnalyzeOptions`, `AnalyzeResult`, and `AnalyzeProvider` models.
- `screenshotToFile()` convenience method to write screenshots directly to disk.
- `getUsage()` method as alias for `quota()` matching `GET /v1/usage`.
- `quota()` method mapping to `GET /v1/quota`.
- `pdf()` method mapping to `POST /v1/pdf`.
- `PdfOptions` data class with `pageFormat: PDFPageFormat`.
- `PDFPageFormat`, `ScreenshotFormat`, `ExtractFormat` typed enums.
- `RetryPolicy` data class -- configurable exponential backoff with `Retry-After` support.
- `HttpClient` internal class -- centralises HTTP execution and retry logic.
- `X-Api-Key` authentication header matching the API specification.
- `SnapCookie` type renamed from `Cookie`.
- Models split into `models/Options.kt`, `models/Responses.kt`, `models/Enums.kt`.
- `exceptions/SnapAPIException.kt` -- dedicated exception file.
- `http/HttpClient.kt`, `http/RetryPolicy.kt` -- networking layer.
- Full JUnit 5 test suite with MockWebServer for HTTP-layer tests.
- GitHub Actions CI workflow.
- MIT LICENSE file.
- Android ViewModel example.
- Server-side competitive intelligence pipeline example.

### Changed
- `SnapAPI` class renamed to `SnapAPIClient`.
- Base URL corrected to `https://api.snapapi.pics`.
- Authentication uses `X-Api-Key` header (matching API specification).
- `SnapAPIException` is now a sealed class.
- `ScrapeOptions.waitMs` renamed to `wait` to match API.
- `ExtractOptions.format` typed as `ExtractFormat` enum.
- `ExtractOptions.waitFor` renamed to `wait`.
- JSON serialisation uses `explicitNulls = false` to avoid sending null fields.

### Removed
- Flat `SnapAPIException(message, errorCode, statusCode)` constructor.
- Storage, Scheduled, Webhook, and API Key management methods.

## [2.0.0] - 2025-01-01

### Added
- Initial coroutines-based implementation.
- OkHttp client.
- Basic error handling with `SnapAPIException`.
