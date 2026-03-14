package pics.snapapi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Shared sub-types ──────────────────────────────────────────────────────────

/** A browser cookie to inject before navigation. */
@Serializable
data class SnapCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expires: Double? = null,
    val httpOnly: Boolean? = null,
    val secure: Boolean? = null,
    val sameSite: String? = null,
)

/** HTTP Basic Auth credentials. */
@Serializable
data class HttpAuth(
    val username: String,
    val password: String,
)

/** GPS coordinates for geolocation emulation. */
@Serializable
data class Geolocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double? = null,
)

/** PDF layout options used inside [ScreenshotOptions.pdf]. */
@Serializable
data class PdfPageOptions(
    val pageSize: String? = null,
    val landscape: Boolean? = null,
    val marginTop: String? = null,
    val marginRight: String? = null,
    val marginBottom: String? = null,
    val marginLeft: String? = null,
)

/** Storage destination for uploaded screenshots. */
@Serializable
data class StorageDestination(
    val destination: String? = null,
    val format: String? = null,
)

// ── Screenshot ────────────────────────────────────────────────────────────────

/**
 * Options for `POST /v1/screenshot`.
 *
 * At least one of [url], [html], or [markdown] must be supplied.
 *
 * ```kotlin
 * val opts = ScreenshotOptions(
 *     url      = "https://example.com",
 *     format   = ScreenshotFormat.PNG,
 *     fullPage = true,
 *     width    = 1440,
 * )
 * ```
 */
@Serializable
data class ScreenshotOptions(
    /** Public URL to capture. */
    val url: String? = null,
    /** Raw HTML to render. */
    val html: String? = null,
    /** Markdown to render. */
    val markdown: String? = null,
    /** Image format. Defaults to `png` server-side. */
    val format: ScreenshotFormat? = null,
    /** JPEG/WEBP quality 0–100. */
    val quality: Int? = null,
    /** Viewport width in pixels. */
    val width: Int? = null,
    /** Viewport height in pixels. */
    val height: Int? = null,
    /** Emulate a named device (e.g. `"iPhone 14 Pro"`). */
    val device: String? = null,
    /** Capture the full scrollable page height. */
    val fullPage: Boolean? = null,
    /** CSS selector — capture only the matching element. */
    val selector: String? = null,
    /** Delay in milliseconds before capturing. */
    val delay: Int? = null,
    /** Navigation timeout in milliseconds. */
    val timeout: Int? = null,
    /** When to consider navigation finished (`"load"`, `"networkidle"`, etc.). */
    val waitUntil: String? = null,
    /** Wait for this CSS selector before capturing. */
    val waitForSelector: String? = null,
    /** Enable dark mode media query. */
    val darkMode: Boolean? = null,
    /** CSS to inject. */
    val css: String? = null,
    /** JavaScript to execute before capture. */
    val javascript: String? = null,
    /** CSS selectors to hide before capture. */
    val hideSelectors: List<String>? = null,
    /** Click this CSS selector before capture. */
    val clickSelector: String? = null,
    /** Block ad networks. */
    val blockAds: Boolean? = null,
    /** Block analytics trackers. */
    val blockTrackers: Boolean? = null,
    /** Block cookie-consent banners. */
    val blockCookieBanners: Boolean? = null,
    /** Override browser `User-Agent`. */
    val userAgent: String? = null,
    /** Extra HTTP request headers. */
    val extraHeaders: Map<String, String>? = null,
    /** Cookies to inject before navigation. */
    val cookies: List<SnapCookie>? = null,
    /** HTTP Basic Auth credentials. */
    val httpAuth: HttpAuth? = null,
    /** Proxy URL. */
    val proxy: String? = null,
    /** Use a premium residential proxy. */
    val premiumProxy: Boolean? = null,
    /** Emulate GPS location. */
    val geolocation: Geolocation? = null,
    /** IANA timezone identifier (e.g. `"America/New_York"`). */
    val timezone: String? = null,
    /** PDF layout options. Only relevant when [format] is [ScreenshotFormat.PDF]. */
    val pdf: PdfPageOptions? = null,
    /** Upload result to configured storage. */
    val storage: StorageDestination? = null,
    /** Webhook URL to notify on completion. */
    val webhookUrl: String? = null,
)

// ── PDF ───────────────────────────────────────────────────────────────────────

/**
 * Options for `POST /v1/pdf`.
 *
 * ```kotlin
 * val opts = PdfOptions(url = "https://example.com", pageFormat = PDFPageFormat.A4)
 * val bytes = client.pdf(opts)
 * ```
 */
@Serializable
data class PdfOptions(
    /** The URL to render as PDF. Required. */
    val url: String,
    /** Paper size. Defaults to `"a4"` server-side. */
    val pageFormat: PDFPageFormat? = null,
    /** Top margin CSS value (e.g. `"1cm"`). */
    val margin: String? = null,
    /** Render in landscape orientation. */
    val landscape: Boolean? = null,
    /** Wait in milliseconds for dynamic content. */
    val wait: Int? = null,
)

// ── Scrape ────────────────────────────────────────────────────────────────────

/**
 * Options for `POST /v1/scrape`.
 *
 * ```kotlin
 * val opts = ScrapeOptions(url = "https://example.com", selector = "article")
 * ```
 */
@Serializable
data class ScrapeOptions(
    /** The URL to scrape. Required. */
    val url: String,
    /** CSS selector — only return content matching this element. */
    val selector: String? = null,
    /** Wait in milliseconds for dynamic content to load. */
    val wait: Int? = null,
    /** Number of paginated pages to scrape (max 10). */
    val pages: Int? = null,
    /** Proxy URL. */
    val proxy: String? = null,
    /** Use a premium residential proxy. */
    val premiumProxy: Boolean? = null,
    /** Block non-essential resources (images, fonts). */
    val blockResources: Boolean? = null,
    /** Browser locale (e.g. `"en-US"`). */
    val locale: String? = null,
)

// ── Extract ───────────────────────────────────────────────────────────────────

/**
 * Options for `POST /v1/extract`.
 *
 * ```kotlin
 * val opts = ExtractOptions(url = "https://example.com", format = ExtractFormat.MARKDOWN)
 * ```
 */
@Serializable
data class ExtractOptions(
    /** The URL to extract content from. Required. */
    val url: String,
    /** Format of the returned content. Defaults to `"markdown"` server-side. */
    val format: ExtractFormat? = null,
    /** CSS selector — extract only content within this element. */
    val selector: String? = null,
    /** Wait in milliseconds for dynamic content. */
    val wait: Int? = null,
    /** Navigation timeout in milliseconds. */
    val timeout: Int? = null,
    /** Enable dark mode. */
    val darkMode: Boolean? = null,
    /** Block ad networks. */
    val blockAds: Boolean? = null,
    /** Block cookie-consent banners. */
    val blockCookieBanners: Boolean? = null,
    /** Include image URLs in the output. */
    val includeImages: Boolean? = null,
    /** Truncate output to this many characters. */
    val maxLength: Int? = null,
)

// ── Video ─────────────────────────────────────────────────────────────────────

/**
 * Options for `POST /v1/video`.
 */
@Serializable
data class VideoOptions(
    /** The URL to record. Required. */
    val url: String,
    /** Output format. */
    val format: VideoFormat? = null,
    /** Viewport width. */
    val width: Int? = null,
    /** Viewport height. */
    val height: Int? = null,
    /** Recording duration in milliseconds. */
    val duration: Int? = null,
    /** Frames per second (1–60). */
    val fps: Int? = null,
    /** Enable automatic page scrolling. */
    val scrolling: Boolean? = null,
    /** Scroll speed in pixels per second. */
    val scrollSpeed: Int? = null,
    /** Delay before scrolling starts, in milliseconds. */
    val scrollDelay: Int? = null,
    /** Total scroll animation duration in milliseconds. */
    val scrollDuration: Int? = null,
    /** Pixels to scroll per step. */
    val scrollBy: Int? = null,
    /** Easing curve for the scroll animation. */
    val scrollEasing: ScrollEasing? = null,
    /** Scroll back to the top at the end. */
    val scrollBack: Boolean? = null,
    /** Wait for scroll to complete before stopping. */
    val scrollComplete: Boolean? = null,
    /** Enable dark mode. */
    val darkMode: Boolean? = null,
    /** Block ad networks. */
    val blockAds: Boolean? = null,
    /** Block cookie-consent banners. */
    val blockCookieBanners: Boolean? = null,
    /** Delay before recording starts, in milliseconds. */
    val delay: Int? = null,
    /** Internal: `"binary"` or `"json"`. Set automatically by the SDK. */
    val responseType: String? = null,
)
