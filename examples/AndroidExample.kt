// AndroidExample.kt
// Demonstrates SnapAPI usage in an Android app with Jetpack ViewModel.
//
// Add to your Android project's build.gradle.kts:
//   implementation("pics.snapapi:snapapi-kotlin:3.0.0")
//
// Requires: kotlinx-coroutines-android

package com.example.snapapi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pics.snapapi.SnapAPIClient
import pics.snapapi.exceptions.SnapAPIException
import pics.snapapi.models.*
import java.io.File

/**
 * Android ViewModel showing SnapAPI integration.
 *
 * Usage in a Compose screen:
 * ```kotlin
 * @Composable
 * fun ScreenshotScreen(viewModel: ScreenshotViewModel = viewModel()) {
 *     val state by viewModel.state.collectAsState()
 *     Column {
 *         Button(onClick = { viewModel.capture("https://example.com") }) {
 *             Text("Capture Screenshot")
 *         }
 *         state.imageBytes?.let { bytes ->
 *             val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
 *             Image(bitmap.asImageBitmap(), "Screenshot")
 *         }
 *         state.error?.let { Text("Error: $it", color = Color.Red) }
 *     }
 * }
 * ```
 */
class ScreenshotViewModel : ViewModel() {

    private val client = SnapAPIClient(apiKey = "sk_your_key")

    data class State(
        val isLoading: Boolean = false,
        val imageBytes: ByteArray? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /**
     * Capture a mobile-optimized screenshot.
     */
    fun capture(url: String) {
        viewModelScope.launch {
            _state.value = State(isLoading = true)
            try {
                val bytes = client.screenshot(
                    ScreenshotOptions(
                        url       = url,
                        format    = ScreenshotFormat.PNG,
                        width     = 390,    // mobile viewport
                        fullPage  = false,
                        blockAds  = true,
                        blockCookieBanners = true,
                    )
                )
                _state.value = State(imageBytes = bytes)
            } catch (e: SnapAPIException) {
                _state.value = State(error = when (e) {
                    is SnapAPIException.Unauthorized  -> "Invalid API key"
                    is SnapAPIException.RateLimited   -> "Rate limited, retry in ${e.retryAfterMs / 1000}s"
                    is SnapAPIException.QuotaExceeded -> "Quota exceeded, upgrade plan"
                    is SnapAPIException.ServerError   -> "Server error: ${e.message}"
                    is SnapAPIException.NetworkError  -> "No internet connection"
                    else -> e.message ?: "Unknown error"
                })
            }
        }
    }

    /**
     * Save a screenshot to the app's cache directory.
     */
    fun captureAndSave(url: String, cacheDir: File) {
        viewModelScope.launch {
            _state.value = State(isLoading = true)
            try {
                val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
                val bytes = client.screenshotToFile(
                    ScreenshotOptions(url = url, format = ScreenshotFormat.PNG),
                    file = file,
                )
                println("Saved ${bytes} bytes to ${file.absolutePath}")
                _state.value = State(imageBytes = file.readBytes())
            } catch (e: SnapAPIException) {
                _state.value = State(error = e.message)
            }
        }
    }

    /**
     * Extract article content for offline reading.
     */
    suspend fun extractArticle(url: String): String? {
        return try {
            val result = client.extractArticle(url)
            result.data?.toString()
        } catch (e: SnapAPIException) {
            null
        }
    }

    /**
     * Check remaining API quota.
     */
    suspend fun checkQuota(): String {
        return try {
            val q = client.getUsage()
            "${q.used}/${q.total} used, ${q.remaining} remaining"
        } catch (e: SnapAPIException) {
            "Could not fetch quota: ${e.message}"
        }
    }
}
