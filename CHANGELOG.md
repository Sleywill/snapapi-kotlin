# Changelog

All notable changes to the SnapAPI Kotlin SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-03-14

### Added
- `SnapAPIException` sealed class hierarchy replacing flat `SnapAPIException`:
  - `Unauthorized` — 401/403 responses.
  - `RateLimited(retryAfterMs)` — 429 with extracted retry delay.
  - `QuotaExceeded` — 402 responses.
  - `ServerError(statusCode, errorCode, message)` — all other non-2xx.
  - `NetworkError` — transport-level failures.
  - `InvalidParams` — client-side validation errors.
  - `DecodingError` — JSON parse failures.
- `quota()` method mapping to `GET /v1/quota`.
- `pdf()` method mapping to `POST /v1/pdf`.
- `PdfOptions` data class with `pageFormat: PDFPageFormat`.
- `PDFPageFormat`, `ScreenshotFormat`, `ExtractFormat` typed enums.
- `RetryPolicy` data class — configurable exponential backoff with `Retry-After` support.
- `HttpClient` internal class — centralises HTTP execution and retry logic.
- `Authorization: Bearer {key}` header (previously `x-api-key`).
- Correct base URL `https://snapapi.pics` (previously `api.snapapi.pics`).
- `SnapCookie` type renamed from `Cookie`.
- `ScrapeOptions.wait` renamed from `waitMs` to match API.
- `ExtractOptions.format` typed as `ExtractFormat` enum (was `type: String`).
- `ExtractOptions.wait` renamed from `waitFor`.
- Models split into `models/Options.kt`, `models/Responses.kt`, `models/Enums.kt`.
- `exceptions/SnapAPIException.kt` — dedicated exception file.
- `http/HttpClient.kt`, `http/RetryPolicy.kt` — networking layer.
- Full JUnit 5 test suite with MockWebServer for HTTP-layer tests.
- GitHub Actions CI workflow.

### Changed
- `SnapAPI` class renamed to `SnapAPIClient` (more conventional name).
- `SnapAPIException` is now a sealed class (`SnapAPIException.ServerError`, etc.).
- `isRetryable` is now defined on the sealed base class, not just `SnapAPIException`.
- JSON serialisation uses `explicitNulls = false` to avoid sending null fields.

### Removed
- `analyze()` method — endpoint is non-functional server-side.
- Storage, Scheduled, Webhook, and API Key management methods.
- Flat `SnapAPIException(message, errorCode, statusCode)` constructor.

## [2.0.0] - 2025-01-01

### Added
- Initial coroutines-based implementation.
- OkHttp client.
- Basic error handling with `SnapAPIException`.
