package pics.snapapi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Output image format for a screenshot request. */
@Serializable
enum class ScreenshotFormat(val value: String) {
    @SerialName("png")  PNG("png"),
    @SerialName("jpeg") JPEG("jpeg"),
    @SerialName("webp") WEBP("webp"),
    @SerialName("avif") AVIF("avif"),
    @SerialName("pdf")  PDF("pdf"),
}

/** Content format returned by the extract endpoint. */
@Serializable
enum class ExtractFormat(val value: String) {
    @SerialName("markdown")   MARKDOWN("markdown"),
    @SerialName("text")       TEXT("text"),
    @SerialName("html")       HTML("html"),
    @SerialName("article")    ARTICLE("article"),
    @SerialName("links")      LINKS("links"),
    @SerialName("images")     IMAGES("images"),
    @SerialName("metadata")   METADATA("metadata"),
    @SerialName("structured") STRUCTURED("structured"),
}

/** Paper size for PDF generation. */
@Serializable
enum class PDFPageFormat(val value: String) {
    @SerialName("a4")      A4("a4"),
    @SerialName("letter")  LETTER("letter"),
    @SerialName("a3")      A3("a3"),
    @SerialName("a5")      A5("a5"),
    @SerialName("legal")   LEGAL("legal"),
    @SerialName("tabloid") TABLOID("tabloid"),
}

/** Output format for video recordings. */
@Serializable
enum class VideoFormat(val value: String) {
    @SerialName("webm") WEBM("webm"),
    @SerialName("mp4")  MP4("mp4"),
    @SerialName("gif")  GIF("gif"),
}

/** LLM provider for the analyze endpoint. */
@Serializable
enum class AnalyzeProvider(val value: String) {
    @SerialName("openai")    OPENAI("openai"),
    @SerialName("anthropic") ANTHROPIC("anthropic"),
    @SerialName("google")    GOOGLE("google"),
}

/** Easing curve for automated scroll animations in video recording. */
@Serializable
enum class ScrollEasing(val value: String) {
    @SerialName("linear")            LINEAR("linear"),
    @SerialName("ease_in")           EASE_IN("ease_in"),
    @SerialName("ease_out")          EASE_OUT("ease_out"),
    @SerialName("ease_in_out")       EASE_IN_OUT("ease_in_out"),
    @SerialName("ease_in_out_quint") EASE_IN_OUT_QUINT("ease_in_out_quint"),
}

/** Webhook event type. */
@Serializable
enum class WebhookEvent(val value: String) {
    @SerialName("screenshot.completed") SCREENSHOT_COMPLETED("screenshot.completed"),
    @SerialName("screenshot.failed")    SCREENSHOT_FAILED("screenshot.failed"),
    @SerialName("scrape.completed")     SCRAPE_COMPLETED("scrape.completed"),
    @SerialName("scrape.failed")        SCRAPE_FAILED("scrape.failed"),
    @SerialName("pdf.completed")        PDF_COMPLETED("pdf.completed"),
    @SerialName("pdf.failed")           PDF_FAILED("pdf.failed"),
}

/** Scheduled task recurrence interval. */
@Serializable
enum class ScheduleInterval(val value: String) {
    @SerialName("hourly")  HOURLY("hourly"),
    @SerialName("daily")   DAILY("daily"),
    @SerialName("weekly")  WEEKLY("weekly"),
    @SerialName("monthly") MONTHLY("monthly"),
}

/** API key permission scope. */
@Serializable
enum class ApiKeyScope(val value: String) {
    @SerialName("read")       READ("read"),
    @SerialName("write")      WRITE("write"),
    @SerialName("screenshot") SCREENSHOT("screenshot"),
    @SerialName("scrape")     SCRAPE("scrape"),
    @SerialName("extract")    EXTRACT("extract"),
    @SerialName("pdf")        PDF("pdf"),
    @SerialName("video")      VIDEO("video"),
    @SerialName("full")       FULL("full"),
}
