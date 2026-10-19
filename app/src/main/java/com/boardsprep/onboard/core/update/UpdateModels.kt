package com.boardsprep.onboard.core.update

import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Raw GitHub Release API response DTO.
 */
data class GitHubReleaseDto(
    @SerializedName("tag_name")
    val tagName: String = "",
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("body")
    val body: String? = null,
    @SerializedName("published_at")
    val publishedAt: String? = null,
    @SerializedName("html_url")
    val htmlUrl: String? = null,
    @SerializedName("prerelease")
    val isPrerelease: Boolean = false,
    @SerializedName("draft")
    val isDraft: Boolean = false,
    @SerializedName("assets")
    val assets: List<GitHubAssetDto> = emptyList()
)

/**
 * Raw GitHub Release Asset DTO.
 */
data class GitHubAssetDto(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("size")
    val size: Long = 0L,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String = "",
    @SerializedName("content_type")
    val contentType: String? = null
)

/**
 * Clean domain representation of an available app update.
 */
data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val apkFileName: String,
    val fileSizeBytes: Long,
    val htmlUrl: String,
    val publishedAt: String
)

/**
 * State machine governing in-app update checks, downloading, and installation handoff.
 */
sealed interface UpdateState {
    /** Resting state. */
    data object Idle : UpdateState

    /** Active network probe against GitHub API. */
    data class Checking(val isManual: Boolean = false) : UpdateState

    /** New verified release found with attached APK. */
    data class UpdateAvailable(
        val release: AppReleaseInfo,
        val isManual: Boolean = false
    ) : UpdateState

    /** Current installed version matches or exceeds remote release. */
    data class UpToDate(
        val currentVersion: String,
        val isManual: Boolean = false
    ) : UpdateState

    /** In-flight byte stream download from GitHub CDN. */
    data class Downloading(
        val release: AppReleaseInfo,
        val progress: Float, // 0.0f to 1.0f
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState

    /** APK file verified and ready for package installer handoff. */
    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val apkFile: File
    ) : UpdateState

    /** Failure encountered during check or download. */
    data class Error(
        val message: String,
        val isManual: Boolean = false,
        val canRetry: Boolean = true
    ) : UpdateState
}
