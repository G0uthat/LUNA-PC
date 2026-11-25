package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

enum class UpdateEnforcementMode {
    OPTIONAL_SKIPPABLE,
    MANDATORY_BLOCKING
}

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String,
    val apkSizeBytes: Long = 0L,
    val mode: UpdateEnforcementMode = UpdateEnforcementMode.OPTIONAL_SKIPPABLE,
    val blockMessage: String = ""
)

sealed class UpdateCheckOutcome {
    data object Idle : UpdateCheckOutcome()
    data object Checking : UpdateCheckOutcome()
    data class UpToDate(val checkedVersion: String) : UpdateCheckOutcome()
    data class UpdateFound(val info: AppUpdateInfo) : UpdateCheckOutcome()
    data class Error(val message: String) : UpdateCheckOutcome()
}

/**
 * In-app GitHub `Luma.apk` updater for `G0uthat/LUNA`:
 * - Only shows in-app modal dialogs while the user is actively inside Luma (zero background notifications).
 * - Reliably discovers `Luma.apk` and its version across ALL GitHub publishing flows:
 *   1) GitHub Releases (latest AND pre-releases via `/releases?per_page=10`), prioritizing `Luma.apk`.
 *   2) Direct `Luma.apk` stored in the repository (`Luma.apk`, `releases/Luma.apk`, `apk/Luma.apk`, etc.)
 *      via Contents API + CDN raw fallback (immune to REST API rate limits).
 *   3) Lightweight HTTP `Range` inspection of `Luma.apk`'s embedded binary `AndroidManifest.xml`
 *      (~30–100 KB instead of downloading the full APK) to read its true `versionName` and `versionCode`.
 *   4) Remote `update-policy.json` and `app/build.gradle.kts` (cache-busted with `?t=timestamp`).
 *   5) Latest successful GitHub Actions `Build APK` (`Luma-APK`) workflow run fallback.
 * - Compares installed version (`BuildConfig.VERSION_NAME` / `BuildConfig.VERSION_CODE`) against the
 *   remote `Luma.apk` version: if they match -> up to date; if they differ -> prompts to update!
 * - Supports TWO enforcement modes controlled from GitHub:
 *   - OPTIONAL_SKIPPABLE: update now, remind later, or skip that specific version.
 *   - MANDATORY_BLOCKING: non-skippable modal + offline lock cache preventing Airplane Mode bypass.
 * - Opens verified HTTPS GitHub APK download URLs via ACTION_VIEW (zero REQUEST_INSTALL_PACKAGES).
 */
object GitHubUpdateManager {

    const val GITHUB_REPO = "G0uthat/LUNA"

    private const val PREFS_NAME = "luma_github_updates_v1"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"

    // Persisted mandatory lock cache (prevents offline/airplane-mode bypass)
    private const val KEY_LOCKED_VERSION_NAME = "locked_version_name"
    private const val KEY_LOCKED_VERSION_CODE = "locked_version_code"
    private const val KEY_LOCKED_APK_URL = "locked_apk_url"
    private const val KEY_LOCKED_RELEASE_URL = "locked_release_url"
    private const val KEY_LOCKED_CHANGELOG = "locked_changelog"
    private const val KEY_LOCKED_BLOCK_MESSAGE = "locked_block_message"
    private const val KEY_LOCKED_APK_SIZE = "locked_apk_size"

    // Persisted ETag & conditional-GET (304 Not Modified) cache keys
    private const val KEY_ETAG_RELEASES_LATEST = "etag_releases_latest"
    private const val KEY_CACHE_RELEASES_LATEST = "cache_releases_latest"
    private const val KEY_ETAG_RELEASES_LIST = "etag_releases_list"
    private const val KEY_CACHE_RELEASES_LIST = "cache_releases_list"
    private const val KEY_ETAG_POLICY = "etag_policy"
    private const val KEY_CACHE_POLICY = "cache_policy"
    private const val KEY_POLICY_BRANCH = "policy_branch"
    private const val KEY_ETAG_GRADLE = "etag_gradle"
    private const val KEY_CACHE_GRADLE_NAME = "cache_gradle_ver_name"
    private const val KEY_CACHE_GRADLE_CODE = "cache_gradle_ver_code"
    private const val KEY_GRADLE_BRANCH = "gradle_branch"
    private const val KEY_DIRECT_APK_BRANCH = "direct_apk_branch"
    private const val KEY_DIRECT_APK_PATH = "direct_apk_path"
    private const val KEY_DIRECT_APK_ETAG = "direct_apk_etag"
    private const val KEY_DIRECT_APK_SIZE = "direct_apk_size"
    private const val KEY_CACHE_APK_URL = "cache_apk_url"
    private const val KEY_CACHE_APK_ETAG = "cache_apk_etag"
    private const val KEY_CACHE_APK_VER_NAME = "cache_apk_ver_name"
    private const val KEY_CACHE_APK_VER_CODE = "cache_apk_ver_code"

    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours between silent auto-checks

    // Pre-compiled Regex constants (zero per-check allocation)
    private val VERSION_CODE_REGEX = Regex("""versionCode\s*=\s*(\d+)""")
    private val VERSION_NAME_REGEX = Regex("""versionName\s*=\s*"([^"]+)"""")
    private val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
    private val FORCE_UPDATE_REGEX = Regex("force_update\\s*[:=]\\s*true", RegexOption.IGNORE_CASE)
    private val CONTROL_TAG_REGEX = Regex(
        "\\[(FORCE|OBLIGATORIA|OBLIGATORIO|MANDATORY|OPTIONAL|SKIPPABLE)]",
        RegexOption.IGNORE_CASE
    )
    private val SEMVER_REGEX = Regex("(\\d+(?:\\.\\d+)*)")

    private val candidateBranches = listOf("main", "master")
    private val candidateRepoApkPaths = listOf(
        "Luma.apk",
        "releases/Luma.apk",
        "release/Luma.apk",
        "apk/Luma.apk",
        "dist/Luma.apk",
        "app/build/outputs/apk/release/Luma.apk",
        "app/build/outputs/apk/debug/Luma.apk"
    )

    private val allowedDownloadHosts = setOf(
        "github.com",
        "www.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "raw.githubusercontent.com"
    )

    private val httpClient: OkHttpClient by lazy {
        LumaHttpClient.shared.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _activeUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val activeUpdate: StateFlow<AppUpdateInfo?> = _activeUpdate.asStateFlow()

    private val _checkOutcome = MutableStateFlow<UpdateCheckOutcome>(UpdateCheckOutcome.Idle)
    val checkOutcome: StateFlow<UpdateCheckOutcome> = _checkOutcome.asStateFlow()

    private var dismissedOptionalThisSessionVersion: String? = null

    /**
     * Immediately restores any locally cached mandatory update lock if the installed APK has not yet
     * reached the required version (prevents Airplane Mode bypass). Automatically clears the lock
     * once the user installs the matching or newer APK.
     */
    fun enforceCachedMandatoryLockIfNeeded(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockedVer = prefs.getString(KEY_LOCKED_VERSION_NAME, null)?.trim().orEmpty()
        val lockedCode = prefs.getInt(KEY_LOCKED_VERSION_CODE, 0)
        if (lockedVer.isEmpty() && lockedCode <= 0) return false

        val satisfiedByCode = lockedCode > 0 && BuildConfig.VERSION_CODE >= lockedCode
        val satisfiedByName = lockedVer.isNotEmpty() &&
            !isVersionStringDifferent(lockedVer, BuildConfig.VERSION_NAME)
        val installedNewerBySemver = lockedVer.isNotEmpty() &&
            cleanVersionString(lockedVer).isNotEmpty() &&
            compareSemanticVersions(BuildConfig.VERSION_NAME, lockedVer) >= 0

        if (satisfiedByCode || satisfiedByName || (lockedCode <= 0 && installedNewerBySemver)) {
            clearMandatoryLock(context)
            return false
        }

        val fallbackRelease = "https://github.com/$GITHUB_REPO/releases"
        val lockedInfo = AppUpdateInfo(
            versionName = lockedVer.ifEmpty { "v$lockedCode" },
            versionCode = lockedCode,
            title = "Actualización obligatoria",
            changelog = prefs.getString(KEY_LOCKED_CHANGELOG, null).orEmpty()
                .ifBlank { "Esta versión de Luma requiere actualizar al Luma.apk más reciente publicado en GitHub." },
            apkDownloadUrl = prefs.getString(KEY_LOCKED_APK_URL, null).orEmpty().ifBlank { fallbackRelease },
            releasePageUrl = prefs.getString(KEY_LOCKED_RELEASE_URL, null).orEmpty().ifBlank { fallbackRelease },
            apkSizeBytes = prefs.getLong(KEY_LOCKED_APK_SIZE, 0L),
            mode = UpdateEnforcementMode.MANDATORY_BLOCKING,
            blockMessage = prefs.getString(KEY_LOCKED_BLOCK_MESSAGE, null).orEmpty()
        )
        _activeUpdate.value = lockedInfo
        _checkOutcome.value = UpdateCheckOutcome.UpdateFound(lockedInfo)
        return true
    }

    /**
     * Checks `G0uthat/LUNA` for `Luma.apk` updates across Releases (including pre-releases),
     * direct repo `Luma.apk` files, embedded APK `AndroidManifest.xml` version metadata,
     * `update-policy.json`, `app/build.gradle.kts`, and GitHub Actions `Luma-APK` runs.
     */
    suspend fun checkForUpdates(
        context: Context,
        manualCheck: Boolean = false,
        bypassThrottle: Boolean = false
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Always re-verify any cached mandatory lock first (blocks offline bypass)
        val hasCachedLock = enforceCachedMandatoryLockIfNeeded(appCtx)

        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (!manualCheck && !bypassThrottle && !hasCachedLock && (now - lastCheck) < AUTO_CHECK_INTERVAL_MS) {
            return@withContext _checkOutcome.value
        }
        // Skip silent background checks while device is offline to avoid socket timeouts
        if (!manualCheck && !hasCachedLock && !DynamicIslandEventManager.isOnline.value) {
            return@withContext _checkOutcome.value
        }

        _checkOutcome.value = UpdateCheckOutcome.Checking
        val repo = GITHUB_REPO
        val diagnostics = FetchDiagnostics()

        // 1. GitHub Releases (conditional GET with ETag / If-None-Match -> 304 = 0 bytes & 0 rate limit)
        val releaseData = fetchBestGitHubRelease(repo, prefs, diagnostics)

        // 2. Remote update-policy.json (conditional GET with ETag / If-None-Match)
        val policyData = fetchRemoteUpdatePolicy(repo, prefs)

        // 3. Direct Luma.apk committed inside the repository (if no Release APK found)
        val directApkData = if (releaseData?.hasDirectApk == true) {
            null
        } else {
            fetchDirectRepoApk(repo, prefs, diagnostics)
        }

        // 4. Remote app/build.gradle.kts versionCode / versionName (conditional GET with ETag)
        val gradleVersion = fetchRemoteGradleVersion(repo, prefs)

        // 5. GitHub Actions `Build APK` (`Luma-APK`) fallback when no Release or repo APK exists
        val actionsBuild = if (releaseData?.hasDirectApk == true || directApkData != null) {
            null
        } else {
            fetchLatestActionsApkRun(repo, diagnostics)
        }

        // 6. Inspect Luma.apk binary AndroidManifest.xml ONLY when ETag/URL changed (0 Range bytes on cache hit)
        val candidateApkUrl = sanitizeGitHubUrl(policyData?.apkUrl.orEmpty())
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: releaseData?.takeIf { it.hasDirectApk }?.apkDownloadUrl
            ?: directApkData?.downloadUrl
        val knownApkEtag = if (candidateApkUrl == directApkData?.downloadUrl) directApkData?.sha.orEmpty() else ""
        val inspectedApkVersion = candidateApkUrl?.let {
            inspectRemoteApkManifest(apkUrl = it, knownEtag = knownApkEtag, prefs = prefs)
        }

        val anySourceFound = releaseData != null ||
            policyData != null ||
            directApkData != null ||
            gradleVersion != null ||
            actionsBuild != null ||
            inspectedApkVersion != null

        if (!anySourceFound) {
            val outcome = if (hasCachedLock) {
                _activeUpdate.value?.let { UpdateCheckOutcome.UpdateFound(it) }
                    ?: UpdateCheckOutcome.Error("Sin conexión para verificar la actualización obligatoria.")
            } else {
                val reason = when {
                    diagnostics.sawNotFound && !diagnostics.sawRateLimit ->
                        "No se encontró $repo (verifica que el repositorio sea público y tenga Luma.apk)."
                    diagnostics.sawRateLimit ->
                        "Límite temporal de GitHub alcanzado. Intenta de nuevo en unos minutos."
                    else ->
                        "No se pudo conectar con GitHub ($repo). Verifica tu conexión a internet."
                }
                UpdateCheckOutcome.Error(reason)
            }
            _checkOutcome.value = outcome
            return@withContext outcome
        }

        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

        val resolved = mergeAllSources(
            repo = repo,
            release = releaseData,
            directApk = directApkData,
            policy = policyData,
            gradleVersion = gradleVersion,
            actionsBuild = actionsBuild,
            inspectedApk = inspectedApkVersion
        )

        val isDifferentVersion = isVersionDifferentFromInstalled(
            remoteVersionName = resolved.versionName,
            remoteVersionCode = resolved.versionCode
        )
        val isMandatory = resolved.mode == UpdateEnforcementMode.MANDATORY_BLOCKING

        if (!isDifferentVersion) {
            clearMandatoryLock(appCtx)
            _activeUpdate.value = null
            val ok = UpdateCheckOutcome.UpToDate(BuildConfig.VERSION_NAME)
            _checkOutcome.value = ok
            return@withContext ok
        }

        if (isMandatory) {
            saveMandatoryLock(appCtx, resolved)
            _activeUpdate.value = resolved
            val found = UpdateCheckOutcome.UpdateFound(resolved)
            _checkOutcome.value = found
            return@withContext found
        }

        // Optional / Skippable update: clear stale mandatory lock if remote relaxed policy
        clearMandatoryLock(appCtx)
        val skippedVersion = prefs.getString(KEY_SKIPPED_VERSION, null)
        val alreadySkipped = !manualCheck && (
            skippedVersion.equals(resolved.versionName, ignoreCase = true) ||
                dismissedOptionalThisSessionVersion.equals(resolved.versionName, ignoreCase = true)
            )

        if (!alreadySkipped) {
            _activeUpdate.value = resolved
        }
        val found = UpdateCheckOutcome.UpdateFound(resolved)
        _checkOutcome.value = found
        found
    }

    /** Dismisses an optional update for the current session only ("Más tarde"). Never dismisses mandatory updates. */
    fun dismissOptionalForSession() {
        val current = _activeUpdate.value ?: return
        if (current.mode == UpdateEnforcementMode.MANDATORY_BLOCKING) return
        dismissedOptionalThisSessionVersion = current.versionName
        _activeUpdate.value = null
    }

    /** Persists skip for a specific optional version ("Omitir versión"). Never skips mandatory updates. */
    fun skipOptionalVersion(context: Context) {
        val current = _activeUpdate.value ?: return
        if (current.mode == UpdateEnforcementMode.MANDATORY_BLOCKING) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SKIPPED_VERSION, current.versionName)
            .apply()
        dismissedOptionalThisSessionVersion = current.versionName
        _activeUpdate.value = null
    }

    /**
     * Opens the verified HTTPS GitHub APK download URL via Android's standard ACTION_VIEW intent
     * (compliant with Google Play Policy — zero REQUEST_INSTALL_PACKAGES required).
     */
    fun openApkDownload(context: Context, updateInfo: AppUpdateInfo): Boolean {
        val targetUrl = sanitizeGitHubUrl(updateInfo.apkDownloadUrl)
            ?: sanitizeGitHubUrl(updateInfo.releasePageUrl)
            ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun sanitizeGitHubUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        val isAllowed = host in allowedDownloadHosts || host.endsWith(".githubusercontent.com")
        return if (isAllowed) trimmed else null
    }

    private class FetchDiagnostics {
        var sawNotFound: Boolean = false
        var sawRateLimit: Boolean = false
    }

    private data class GitHubReleasePayload(
        val tagName: String,
        val title: String,
        val body: String,
        val htmlUrl: String,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long,
        val hasDirectApk: Boolean
    )

    private data class DirectRepoApkPayload(
        val path: String,
        val downloadUrl: String,
        val htmlUrl: String,
        val sizeBytes: Long,
        val sha: String
    )

    private data class RemoteGradleVersion(
        val versionName: String,
        val versionCode: Int
    )

    private data class ActionsApkPayload(
        val runHtmlUrl: String,
        val headShaShort: String,
        val displayTitle: String,
        val artifactSizeBytes: Long
    )

    private data class ApkManifestVersion(
        val versionName: String,
        val versionCode: Int
    )

    private data class RemotePolicyPayload(
        val latestVersion: String,
        val latestVersionCode: Int,
        val minRequiredVersion: String,
        val minRequiredVersionCode: Int,
        val forceUpdate: Boolean,
        val apkUrl: String,
        val releaseNotes: String,
        val blockMessage: String
    )

    private fun fetchBestGitHubRelease(
        repo: String,
        prefs: SharedPreferences,
        diag: FetchDiagnostics
    ): GitHubReleasePayload? {
        // First try /releases/latest with ETag / If-None-Match (304 = 0 bytes, 0 rate-limit cost)
        val latest = runCatching {
            val savedEtag = prefs.getString(KEY_ETAG_RELEASES_LATEST, null)?.trim().orEmpty()
            val cachedJson = prefs.getString(KEY_CACHE_RELEASES_LATEST, null)
            val reqBuilder = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
            if (savedEtag.isNotEmpty() && !cachedJson.isNullOrBlank()) {
                reqBuilder.header("If-None-Match", savedEtag)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 304 && !cachedJson.isNullOrBlank()) {
                    return@runCatching parseReleaseJson(repo, JSONObject(cachedJson))
                }
                when (response.code) {
                    404 -> diag.sawNotFound = true
                    403, 429 -> diag.sawRateLimit = true
                }
                if (!response.isSuccessful) return@runCatching null
                val bodyStr = response.body?.string() ?: return@runCatching null
                val newEtag = response.header("ETag")?.trim().orEmpty()
                if (newEtag.isNotEmpty()) {
                    prefs.edit()
                        .putString(KEY_ETAG_RELEASES_LATEST, newEtag)
                        .putString(KEY_CACHE_RELEASES_LATEST, bodyStr)
                        .apply()
                }
                parseReleaseJson(repo, JSONObject(bodyStr))
            }
        }.getOrNull()

        if (latest != null && latest.hasDirectApk) return latest

        // Fallback to /releases?per_page=10 with ETag / If-None-Match
        val listCandidate = runCatching {
            val savedEtag = prefs.getString(KEY_ETAG_RELEASES_LIST, null)?.trim().orEmpty()
            val cachedJson = prefs.getString(KEY_CACHE_RELEASES_LIST, null)
            val reqBuilder = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
            if (savedEtag.isNotEmpty() && !cachedJson.isNullOrBlank()) {
                reqBuilder.header("If-None-Match", savedEtag)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val effectiveBody = if (response.code == 304 && !cachedJson.isNullOrBlank()) {
                    cachedJson
                } else {
                    when (response.code) {
                        404 -> diag.sawNotFound = true
                        403, 429 -> diag.sawRateLimit = true
                    }
                    if (!response.isSuccessful) return@runCatching null
                    val bodyStr = response.body?.string() ?: return@runCatching null
                    val newEtag = response.header("ETag")?.trim().orEmpty()
                    if (newEtag.isNotEmpty()) {
                        prefs.edit()
                            .putString(KEY_ETAG_RELEASES_LIST, newEtag)
                            .putString(KEY_CACHE_RELEASES_LIST, bodyStr)
                            .apply()
                    }
                    bodyStr
                }
                val array = JSONArray(effectiveBody)
                var firstNonDraft: GitHubReleasePayload? = null
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false)) continue
                    val parsed = parseReleaseJson(repo, obj)
                    if (firstNonDraft == null) firstNonDraft = parsed
                    if (parsed.hasDirectApk) return@runCatching parsed
                }
                firstNonDraft
            }
        }.getOrNull()

        return listCandidate ?: latest
    }

    private fun parseReleaseJson(repo: String, json: JSONObject): GitHubReleasePayload {
        val tagName = json.optString("tag_name", "").trim()
        val title = json.optString("name", "").trim().ifEmpty { "Luma $tagName".trim() }
        val body = json.optString("body", "").trim()
        val htmlUrl = json.optString("html_url", "https://github.com/$repo/releases").trim()

        var bestApkUrl = ""
        var bestApkSize = 0L
        var matchedExactLuma = false
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    val isExact = name.equals("Luma.apk", ignoreCase = true)
                    val containsLuma = name.contains("luma", ignoreCase = true)
                    if (bestApkUrl.isEmpty() || isExact || (!matchedExactLuma && containsLuma)) {
                        bestApkUrl = downloadUrl
                        bestApkSize = asset.optLong("size", 0L)
                        if (isExact) {
                            matchedExactLuma = true
                            break
                        }
                    }
                }
            }
        }
        return GitHubReleasePayload(
            tagName = tagName,
            title = title,
            body = body,
            htmlUrl = htmlUrl,
            apkDownloadUrl = bestApkUrl.ifBlank { htmlUrl },
            apkSizeBytes = bestApkSize,
            hasDirectApk = bestApkUrl.isNotBlank()
        )
    }

    /**
     * Checks whether `Luma.apk` exists directly inside the repository (via Contents API first,
     * then direct raw HTTPS HEAD probe immune to GitHub API rate limits).
     */
    private fun fetchDirectRepoApk(
        repo: String,
        prefs: SharedPreferences,
        diag: FetchDiagnostics
    ): DirectRepoApkPayload? {
        // Fast path: probe cached (branch, path) first with If-None-Match so unchanged checks use 1 HEAD request
        val cachedBranch = prefs.getString(KEY_DIRECT_APK_BRANCH, null)
        val cachedPath = prefs.getString(KEY_DIRECT_APK_PATH, null)
        val cachedEtag = prefs.getString(KEY_DIRECT_APK_ETAG, null)?.trim().orEmpty()
        val cachedSize = prefs.getLong(KEY_DIRECT_APK_SIZE, 0L)

        val orderedPairs = buildList {
            if (!cachedBranch.isNullOrBlank() && !cachedPath.isNullOrBlank()) {
                add(cachedBranch to cachedPath)
            }
            for (branch in candidateBranches) {
                for (path in candidateRepoApkPaths) {
                    if (branch != cachedBranch || path != cachedPath) {
                        add(branch to path)
                    }
                }
            }
        }

        for ((branch, path) in orderedPairs) {
            val rawUrl = "https://raw.githubusercontent.com/$repo/$branch/$path"
            val isCachedTarget = branch == cachedBranch && path == cachedPath && cachedEtag.isNotEmpty()
            val headResult = runCatching {
                val reqBuilder = Request.Builder()
                    .url(rawUrl)
                    .head()
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                if (isCachedTarget) {
                    reqBuilder.header("If-None-Match", cachedEtag)
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.code == 304 && isCachedTarget) {
                        return@runCatching DirectRepoApkPayload(
                            path = path,
                            downloadUrl = rawUrl,
                            htmlUrl = "https://github.com/$repo/blob/$branch/$path",
                            sizeBytes = cachedSize,
                            sha = cachedEtag
                        )
                    }
                    if (!resp.isSuccessful) return@runCatching null
                    val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                    val etag = resp.header("ETag")?.trim().orEmpty()
                    prefs.edit()
                        .putString(KEY_DIRECT_APK_BRANCH, branch)
                        .putString(KEY_DIRECT_APK_PATH, path)
                        .putString(KEY_DIRECT_APK_ETAG, etag)
                        .putLong(KEY_DIRECT_APK_SIZE, contentLength)
                        .apply()
                    DirectRepoApkPayload(
                        path = path,
                        downloadUrl = rawUrl,
                        htmlUrl = "https://github.com/$repo/blob/$branch/$path",
                        sizeBytes = contentLength,
                        sha = etag
                    )
                }
            }.getOrNull()
            if (headResult != null) return headResult
        }

        // Secondary path: GitHub Contents API (supports custom default branch names)
        for (path in listOf("Luma.apk", "releases/Luma.apk", "apk/Luma.apk")) {
            val apiResult = runCatching {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$repo/contents/$path")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    when (resp.code) {
                        404 -> diag.sawNotFound = true
                        403, 429 -> diag.sawRateLimit = true
                    }
                    if (!resp.isSuccessful) return@runCatching null
                    val bodyStr = resp.body?.string() ?: return@runCatching null
                    val json = JSONObject(bodyStr)
                    val dl = json.optString("download_url", "").trim()
                    if (dl.isEmpty()) return@runCatching null
                    DirectRepoApkPayload(
                        path = path,
                        downloadUrl = dl,
                        htmlUrl = json.optString("html_url", "https://github.com/$repo"),
                        sizeBytes = json.optLong("size", 0L),
                        sha = json.optString("sha", "")
                    )
                }
            }.getOrNull()
            if (apiResult != null) return apiResult
        }
        return null
    }

    private fun fetchRemoteUpdatePolicy(repo: String, prefs: SharedPreferences): RemotePolicyPayload? {
        val cachedBranch = prefs.getString(KEY_POLICY_BRANCH, null)
        val savedEtag = prefs.getString(KEY_ETAG_POLICY, null)?.trim().orEmpty()
        val cachedJson = prefs.getString(KEY_CACHE_POLICY, null)
        val branches = if (!cachedBranch.isNullOrBlank()) {
            listOf(cachedBranch) + candidateBranches.filterNot { it == cachedBranch }
        } else {
            candidateBranches
        }

        for (branch in branches) {
            val canUseCache = branch == cachedBranch && savedEtag.isNotEmpty() && !cachedJson.isNullOrBlank()
            val result = runCatching {
                val reqBuilder = Request.Builder()
                    .url("https://raw.githubusercontent.com/$repo/$branch/update-policy.json")
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                if (canUseCache) {
                    reqBuilder.header("If-None-Match", savedEtag)
                }
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val bodyStr = if (response.code == 304 && canUseCache) {
                        cachedJson!!
                    } else {
                        if (!response.isSuccessful) return@runCatching null
                        val freshBody = response.body?.string() ?: return@runCatching null
                        val newEtag = response.header("ETag")?.trim().orEmpty()
                        if (newEtag.isNotEmpty()) {
                            prefs.edit()
                                .putString(KEY_POLICY_BRANCH, branch)
                                .putString(KEY_ETAG_POLICY, newEtag)
                                .putString(KEY_CACHE_POLICY, freshBody)
                                .apply()
                        }
                        freshBody
                    }
                    val json = JSONObject(bodyStr)
                    RemotePolicyPayload(
                        latestVersion = json.optString("latestVersion", "").trim(),
                        latestVersionCode = json.optInt("latestVersionCode", 0),
                        minRequiredVersion = json.optString("minRequiredVersion", "").trim(),
                        minRequiredVersionCode = json.optInt("minRequiredVersionCode", 0),
                        forceUpdate = json.optBoolean("forceUpdate", false),
                        apkUrl = json.optString("apkUrl", "").trim(),
                        releaseNotes = json.optString("releaseNotes", "").trim(),
                        blockMessage = json.optString("blockMessage", "").trim()
                    )
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    /**
     * Reads `versionCode` and `versionName` directly from the repository's `app/build.gradle.kts`
     * using conditional GET (`If-None-Match`) so unchanged checks transfer 0 bytes.
     */
    private fun fetchRemoteGradleVersion(repo: String, prefs: SharedPreferences): RemoteGradleVersion? {
        val cachedBranch = prefs.getString(KEY_GRADLE_BRANCH, null)
        val savedEtag = prefs.getString(KEY_ETAG_GRADLE, null)?.trim().orEmpty()
        val cachedName = prefs.getString(KEY_CACHE_GRADLE_NAME, null).orEmpty()
        val cachedCode = prefs.getInt(KEY_CACHE_GRADLE_CODE, 0)
        val hasCachedValue = cachedName.isNotEmpty() || cachedCode > 0

        val branches = if (!cachedBranch.isNullOrBlank()) {
            listOf(cachedBranch) + candidateBranches.filterNot { it == cachedBranch }
        } else {
            candidateBranches
        }

        for (branch in branches) {
            val canUseCache = branch == cachedBranch && savedEtag.isNotEmpty() && hasCachedValue
            val result = runCatching {
                val reqBuilder = Request.Builder()
                    .url("https://raw.githubusercontent.com/$repo/$branch/app/build.gradle.kts")
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                if (canUseCache) {
                    reqBuilder.header("If-None-Match", savedEtag)
                }
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.code == 304 && canUseCache) {
                        return@runCatching RemoteGradleVersion(versionName = cachedName, versionCode = cachedCode)
                    }
                    if (!response.isSuccessful) return@runCatching null
                    val text = response.body?.string() ?: return@runCatching null
                    val vCode = VERSION_CODE_REGEX
                        .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val vName = VERSION_NAME_REGEX
                        .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    if (vName.isEmpty() && vCode <= 0) return@runCatching null
                    val newEtag = response.header("ETag")?.trim().orEmpty()
                    if (newEtag.isNotEmpty()) {
                        prefs.edit()
                            .putString(KEY_GRADLE_BRANCH, branch)
                            .putString(KEY_ETAG_GRADLE, newEtag)
                            .putString(KEY_CACHE_GRADLE_NAME, vName)
                            .putInt(KEY_CACHE_GRADLE_CODE, vCode)
                            .apply()
                    }
                    RemoteGradleVersion(versionName = vName, versionCode = vCode)
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    /**
     * Checks the latest successful GitHub Actions workflow run (e.g. `Build APK` -> `Luma-APK`)
     * so users can be directed right to the freshly built `Luma.apk` artifact when no Release exists.
     */
    private fun fetchLatestActionsApkRun(repo: String, diag: FetchDiagnostics): ActionsApkPayload? {
        return runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$repo/actions/runs?status=success&per_page=5")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                .build()
            val runsJson = httpClient.newCall(req).execute().use { resp ->
                when (resp.code) {
                    404 -> diag.sawNotFound = true
                    403, 429 -> diag.sawRateLimit = true
                }
                if (!resp.isSuccessful) return@runCatching null
                JSONObject(resp.body?.string() ?: return@runCatching null)
            }
            val runs = runsJson.optJSONArray("workflow_runs") ?: return@runCatching null
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i) ?: continue
                val runId = run.optLong("id", 0L)
                if (runId <= 0L) continue
                val runHtmlUrl = run.optString("html_url", "").trim()
                val sha = run.optString("head_sha", "").take(7)
                val title = run.optString("display_title", "").trim()

                val artReq = Request.Builder()
                    .url("https://api.github.com/repos/$repo/actions/runs/$runId/artifacts")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                    .build()
                val artifactSize = httpClient.newCall(artReq).execute().use { artResp ->
                    if (!artResp.isSuccessful) return@use -1L
                    val artJson = JSONObject(artResp.body?.string() ?: return@use -1L)
                    val artifacts = artJson.optJSONArray("artifacts") ?: return@use -1L
                    for (j in 0 until artifacts.length()) {
                        val art = artifacts.optJSONObject(j) ?: continue
                        if (art.optBoolean("expired", false)) continue
                        val name = art.optString("name", "")
                        if (name.contains("luma", ignoreCase = true) || name.contains("apk", ignoreCase = true)) {
                            return@use art.optLong("size_in_bytes", 0L)
                        }
                    }
                    -1L
                }
                if (artifactSize >= 0L && runHtmlUrl.isNotBlank()) {
                    return@runCatching ActionsApkPayload(
                        runHtmlUrl = runHtmlUrl,
                        headShaShort = sha,
                        displayTitle = title,
                        artifactSizeBytes = artifactSize
                    )
                }
            }
            null
        }.getOrNull()
    }

    // ---------------------------------------------------------------------------------------------
    // Lightweight Remote APK Inspection (HTTP Range -> ZIP Central Directory -> Binary AXML Parser)
    // Reads the true `android:versionName` and `android:versionCode` embedded inside `Luma.apk`
    // using only ~30–100 KB of network data instead of downloading the full APK.
    // ---------------------------------------------------------------------------------------------

    private fun inspectRemoteApkManifest(
        apkUrl: String,
        knownEtag: String,
        prefs: SharedPreferences
    ): ApkManifestVersion? {
        return runCatching {
            val cachedUrl = prefs.getString(KEY_CACHE_APK_URL, null)
            val cachedEtag = prefs.getString(KEY_CACHE_APK_ETAG, null)?.trim().orEmpty()
            val cachedVerName = prefs.getString(KEY_CACHE_APK_VER_NAME, null).orEmpty()
            val cachedVerCode = prefs.getInt(KEY_CACHE_APK_VER_CODE, 0)
            val hasCachedVersion = cachedUrl == apkUrl &&
                cachedEtag.isNotEmpty() &&
                (cachedVerName.isNotEmpty() || cachedVerCode > 0)

            // 1. Short-circuit with 0 Range downloads if ETag already known and unchanged
            var effectiveEtag = knownEtag.trim()
            if (hasCachedVersion && effectiveEtag.isNotEmpty() && effectiveEtag == cachedEtag) {
                return@runCatching ApkManifestVersion(cachedVerName, cachedVerCode)
            }

            // 2. If no ETag was provided yet, probe via lightweight HEAD + If-None-Match (0 APK bytes)
            if (effectiveEtag.isEmpty()) {
                val headReqBuilder = Request.Builder()
                    .url(apkUrl)
                    .head()
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                if (hasCachedVersion) {
                    headReqBuilder.header("If-None-Match", cachedEtag)
                }
                val headOutcome = runCatching {
                    httpClient.newCall(headReqBuilder.build()).execute().use { resp ->
                        if (resp.code == 304 && hasCachedVersion) {
                            return@runCatching "NOT_MODIFIED"
                        }
                        resp.header("ETag")?.trim().orEmpty()
                    }
                }.getOrNull()
                if (headOutcome == "NOT_MODIFIED" && hasCachedVersion) {
                    return@runCatching ApkManifestVersion(cachedVerName, cachedVerCode)
                }
                if (!headOutcome.isNullOrEmpty()) {
                    effectiveEtag = headOutcome
                    if (hasCachedVersion && effectiveEtag == cachedEtag) {
                        return@runCatching ApkManifestVersion(cachedVerName, cachedVerCode)
                    }
                }
            }

            // 3. Only when APK ETag/URL changed: fetch lightweight ZIP tail + AndroidManifest.xml Range
            val tailFetch = fetchRangeBytes(apkUrl, "bytes=-65536") ?: return@runCatching null
            val tailBytes = tailFetch.bytes
            val totalSize = tailFetch.totalLength
            val tailStartOffset = tailFetch.rangeStart

            // Locate ZIP End of Central Directory signature: 0x06054b50 ("PK\x05\x06")
            var eocdPos = -1
            for (i in (tailBytes.size - 22) downTo 0) {
                if (tailBytes[i] == 0x50.toByte() &&
                    tailBytes[i + 1] == 0x4B.toByte() &&
                    tailBytes[i + 2] == 0x05.toByte() &&
                    tailBytes[i + 3] == 0x06.toByte()
                ) {
                    eocdPos = i
                    break
                }
            }
            if (eocdPos < 0) return@runCatching null

            val eocdBuf = ByteBuffer.wrap(tailBytes, eocdPos, tailBytes.size - eocdPos)
                .order(ByteOrder.LITTLE_ENDIAN)
            val cdSize = eocdBuf.getInt(12).toLong() and 0xFFFFFFFFL
            val cdOffset = eocdBuf.getInt(16).toLong() and 0xFFFFFFFFL
            if (cdSize <= 0L || cdSize > 4_000_000L || cdOffset < 0L) return@runCatching null
            if (totalSize > 0L && cdOffset + cdSize > totalSize) return@runCatching null

            val cdBytes = if (cdOffset >= tailStartOffset &&
                (cdOffset - tailStartOffset + cdSize) <= tailBytes.size
            ) {
                val relStart = (cdOffset - tailStartOffset).toInt()
                tailBytes.copyOfRange(relStart, relStart + cdSize.toInt())
            } else {
                fetchRangeBytes(apkUrl, "bytes=$cdOffset-${cdOffset + cdSize - 1}")?.bytes
                    ?: return@runCatching null
            }

            // Walk Central Directory entries to locate "AndroidManifest.xml"
            val manifestEntry = findManifestEntryInCentralDirectory(cdBytes) ?: return@runCatching null
            if (manifestEntry.compressedSize <= 0L || manifestEntry.compressedSize > 2_000_000L) {
                return@runCatching null
            }

            // Fetch Local File Header + compressed AndroidManifest.xml payload
            val localFetchEnd = manifestEntry.localHeaderOffset + 30L + 512L + manifestEntry.compressedSize
            val clampedEnd = if (totalSize > 0L) minOf(localFetchEnd, totalSize - 1L) else localFetchEnd
            val entryBytes = fetchRangeBytes(
                apkUrl,
                "bytes=${manifestEntry.localHeaderOffset}-$clampedEnd"
            )?.bytes ?: return@runCatching null

            if (entryBytes.size < 30) return@runCatching null
            val lfh = ByteBuffer.wrap(entryBytes).order(ByteOrder.LITTLE_ENDIAN)
            if (lfh.getInt(0) != 0x04034b50) return@runCatching null
            val lfhNameLen = lfh.getShort(26).toInt() and 0xFFFF
            val lfhExtraLen = lfh.getShort(28).toInt() and 0xFFFF
            val dataOffset = 30 + lfhNameLen + lfhExtraLen
            val compLen = manifestEntry.compressedSize.toInt()
            if (dataOffset + compLen > entryBytes.size) return@runCatching null

            val rawManifestBytes = when (manifestEntry.compressionMethod) {
                0 -> entryBytes.copyOfRange(dataOffset, dataOffset + compLen)
                8 -> inflateRawDeflate(entryBytes, dataOffset, compLen)
                else -> return@runCatching null
            }

            val parsed = parseBinaryAndroidManifest(rawManifestBytes)
            if (parsed != null && effectiveEtag.isNotEmpty()) {
                prefs.edit()
                    .putString(KEY_CACHE_APK_URL, apkUrl)
                    .putString(KEY_CACHE_APK_ETAG, effectiveEtag)
                    .putString(KEY_CACHE_APK_VER_NAME, parsed.versionName)
                    .putInt(KEY_CACHE_APK_VER_CODE, parsed.versionCode)
                    .apply()
            }
            parsed
        }.getOrNull()
    }

    private data class RangeFetchResult(
        val bytes: ByteArray,
        val rangeStart: Long,
        val totalLength: Long
    )

    private data class ZipManifestEntry(
        val compressionMethod: Int,
        val compressedSize: Long,
        val localHeaderOffset: Long
    )

    private fun fetchRangeBytes(url: String, rangeHeader: String): RangeFetchResult? {
        val request = Request.Builder()
            .url(url)
            .header("Range", rangeHeader)
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (response.code != 206 && response.code != 200) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            val contentRange = response.header("Content-Range").orEmpty()
            var start = 0L
            var total = bytes.size.toLong()
            val match = CONTENT_RANGE_REGEX.find(contentRange)
            if (match != null) {
                start = match.groupValues[1].toLongOrNull() ?: 0L
                total = match.groupValues[3].toLongOrNull() ?: -1L
            } else if (response.code == 200) {
                start = 0L
                total = bytes.size.toLong()
            }
            RangeFetchResult(bytes = bytes, rangeStart = start, totalLength = total)
        }
    }

    private fun findManifestEntryInCentralDirectory(cdBytes: ByteArray): ZipManifestEntry? {
        val buf = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0
        while (pos + 46 <= cdBytes.size) {
            if (buf.getInt(pos) != 0x02014b50) break
            val method = buf.getShort(pos + 10).toInt() and 0xFFFF
            val compSize = buf.getInt(pos + 20).toLong() and 0xFFFFFFFFL
            val nameLen = buf.getShort(pos + 28).toInt() and 0xFFFF
            val extraLen = buf.getShort(pos + 30).toInt() and 0xFFFF
            val commentLen = buf.getShort(pos + 32).toInt() and 0xFFFF
            val localOffset = buf.getInt(pos + 42).toLong() and 0xFFFFFFFFL
            if (pos + 46 + nameLen > cdBytes.size) break
            val name = String(cdBytes, pos + 46, nameLen, Charsets.UTF_8)
            if (name == "AndroidManifest.xml") {
                return ZipManifestEntry(
                    compressionMethod = method,
                    compressedSize = compSize,
                    localHeaderOffset = localOffset
                )
            }
            pos += 46 + nameLen + extraLen + commentLen
        }
        return null
    }

    private fun inflateRawDeflate(src: ByteArray, offset: Int, length: Int): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(src, offset, length)
            val out = ByteArrayOutputStream(length * 2)
            val chunk = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(chunk)
                if (count > 0) {
                    out.write(chunk, 0, count)
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    break
                }
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun parseBinaryAndroidManifest(data: ByteArray): ApkManifestVersion? {
        if (data.size < 8) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val rootType = buf.getShort(0).toInt() and 0xFFFF
        if (rootType != 0x0003) return null // RES_XML_TYPE
        val rootHeaderSize = buf.getShort(2).toInt() and 0xFFFF

        var strings = emptyList<String>()
        var resourceIds = IntArray(0)
        var pos = rootHeaderSize

        while (pos + 8 <= data.size) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val headerSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > data.size) break

            when (chunkType) {
                0x0001 -> { // RES_STRING_POOL_TYPE
                    strings = parseAxmlStringPool(data, buf, pos, chunkSize)
                }
                0x0180 -> { // RES_XML_RESOURCE_MAP_TYPE
                    val count = (chunkSize - headerSize) / 4
                    if (count > 0) {
                        resourceIds = IntArray(count) { idx ->
                            buf.getInt(pos + headerSize + idx * 4)
                        }
                    }
                }
                0x0102 -> { // RES_XML_START_ELEMENT_TYPE
                    if (pos + headerSize + 20 <= data.size) {
                        val nameIdx = buf.getInt(pos + headerSize + 4)
                        val elemName = strings.getOrNull(nameIdx).orEmpty()
                        if (elemName == "manifest") {
                            val attrStart = buf.getShort(pos + headerSize + 8).toInt() and 0xFFFF
                            val attrSize = buf.getShort(pos + headerSize + 10).toInt() and 0xFFFF
                            val attrCount = buf.getShort(pos + headerSize + 12).toInt() and 0xFFFF
                            var versionCode = 0
                            var versionName = ""
                            val attrBase = pos + headerSize + attrStart
                            for (i in 0 until attrCount) {
                                val aPos = attrBase + i * attrSize
                                if (aPos + 20 > data.size) break
                                val aNameIdx = buf.getInt(aPos + 4)
                                val rawValueIdx = buf.getInt(aPos + 8)
                                val dataType = data[aPos + 15].toInt() and 0xFF
                                val attrData = buf.getInt(aPos + 16)
                                val resId = resourceIds.getOrNull(aNameIdx) ?: 0
                                val attrName = strings.getOrNull(aNameIdx).orEmpty()

                                if (resId == 0x0101021b || attrName == "versionCode") {
                                    versionCode = when (dataType) {
                                        0x10, 0x11 -> attrData
                                        0x03 -> strings.getOrNull(attrData)?.toIntOrNull() ?: 0
                                        else -> strings.getOrNull(rawValueIdx)?.toIntOrNull() ?: attrData
                                    }
                                } else if (resId == 0x0101021c || attrName == "versionName") {
                                    versionName = when {
                                        rawValueIdx >= 0 -> strings.getOrNull(rawValueIdx).orEmpty()
                                        dataType == 0x03 -> strings.getOrNull(attrData).orEmpty()
                                        else -> ""
                                    }.trim()
                                }
                            }
                            if (versionName.isNotEmpty() || versionCode > 0) {
                                return ApkManifestVersion(
                                    versionName = versionName,
                                    versionCode = versionCode
                                )
                            }
                        }
                    }
                }
            }
            pos += chunkSize
        }
        return null
    }

    private fun parseAxmlStringPool(
        data: ByteArray,
        buf: ByteBuffer,
        chunkStart: Int,
        chunkSize: Int
    ): List<String> {
        if (chunkStart + 28 > data.size) return emptyList()
        val stringCount = buf.getInt(chunkStart + 8)
        val flags = buf.getInt(chunkStart + 16)
        val stringsStart = buf.getInt(chunkStart + 20)
        val isUtf8 = (flags and (1 shl 8)) != 0
        if (stringCount <= 0 || stringCount > 50_000) return emptyList()

        val offsetsBase = chunkStart + 28
        val dataBase = chunkStart + stringsStart
        val chunkEnd = chunkStart + chunkSize
        val result = ArrayList<String>(stringCount)

        for (i in 0 until stringCount) {
            val offsetPos = offsetsBase + i * 4
            if (offsetPos + 4 > chunkEnd) break
            val relOffset = buf.getInt(offsetPos)
            var p = dataBase + relOffset
            if (p < dataBase || p >= chunkEnd) {
                result.add("")
                continue
            }
            if (isUtf8) {
                // Skip UTF-16 character length varint
                val c1 = data[p++].toInt() and 0xFF
                if ((c1 and 0x80) != 0 && p < chunkEnd) p++
                // Read UTF-8 byte length varint
                if (p >= chunkEnd) {
                    result.add("")
                    continue
                }
                val b1 = data[p++].toInt() and 0xFF
                val byteLen = if ((b1 and 0x80) != 0 && p < chunkEnd) {
                    ((b1 and 0x7F) shl 8) or (data[p++].toInt() and 0xFF)
                } else {
                    b1
                }
                if (p + byteLen <= chunkEnd && byteLen >= 0) {
                    result.add(String(data, p, byteLen, Charsets.UTF_8))
                } else {
                    result.add("")
                }
            } else {
                if (p + 2 > chunkEnd) {
                    result.add("")
                    continue
                }
                val u1 = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                val charLen = if ((u1 and 0x8000) != 0 && p + 2 <= chunkEnd) {
                    val u2 = buf.getShort(p).toInt() and 0xFFFF
                    p += 2
                    ((u1 and 0x7FFF) shl 16) or u2
                } else {
                    u1
                }
                val byteLen = charLen * 2
                if (p + byteLen <= chunkEnd && byteLen >= 0) {
                    result.add(String(data, p, byteLen, Charsets.UTF_16LE))
                } else {
                    result.add("")
                }
            }
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------
    // Source Resolution & Version Difference Logic
    // ---------------------------------------------------------------------------------------------

    private fun mergeAllSources(
        repo: String,
        release: GitHubReleasePayload?,
        directApk: DirectRepoApkPayload?,
        policy: RemotePolicyPayload?,
        gradleVersion: RemoteGradleVersion?,
        actionsBuild: ActionsApkPayload?,
        inspectedApk: ApkManifestVersion?
    ): AppUpdateInfo {
        val fallbackPage = "https://github.com/$repo/releases"
        val apkVersionName = inspectedApk?.versionName?.trim().orEmpty()
        val releaseTagRaw = release?.tagName?.trim().orEmpty()
        val releaseVersion = cleanVersionString(releaseTagRaw).ifEmpty {
            releaseTagRaw.removePrefix("v").removePrefix("V")
        }
        val policyVersion = policy?.latestVersion?.trim().orEmpty()
            .let { cleanVersionString(it).ifEmpty { it.removePrefix("v").removePrefix("V") } }
        val gradleVersionName = gradleVersion?.versionName?.trim().orEmpty()

        // Priority: actual version inside Luma.apk > update-policy.json / release tag > app/build.gradle.kts
        val chosenVersion = when {
            apkVersionName.isNotEmpty() -> apkVersionName
            policyVersion.isNotEmpty() && releaseVersion.isNotEmpty() -> {
                if (compareSemanticVersions(policyVersion, releaseVersion) > 0) policyVersion else releaseVersion
            }
            releaseVersion.isNotEmpty() -> releaseVersion
            policyVersion.isNotEmpty() -> policyVersion
            gradleVersionName.isNotEmpty() -> gradleVersionName
            else -> BuildConfig.VERSION_NAME
        }

        val bodyText = release?.body.orEmpty()
        val titleText = release?.title.orEmpty()
        val combinedText = "$titleText\n$bodyText"

        // Parse optional markers in GitHub Release body (e.g. version_code: 3, min_version: 1.2, [FORCE])
        val bodyVersionCode = extractIntMarker(combinedText, "version_code")
        val bodyMinVersionCode = extractIntMarker(combinedText, "min_version_code")
        val bodyMinVersion = extractStringMarker(combinedText, "min_version")

        val chosenVersionCode = when {
            (inspectedApk?.versionCode ?: 0) > 0 -> inspectedApk!!.versionCode
            else -> maxOf(
                policy?.latestVersionCode ?: 0,
                bodyVersionCode,
                gradleVersion?.versionCode ?: 0
            )
        }

        val hasForceMarkerInRelease = containsForceMarker(combinedText)
        val belowPolicyMinVersion = !policy?.minRequiredVersion.isNullOrBlank() &&
            compareSemanticVersions(BuildConfig.VERSION_NAME, policy!!.minRequiredVersion) < 0
        val belowPolicyMinCode = (policy?.minRequiredVersionCode ?: 0) > BuildConfig.VERSION_CODE
        val belowBodyMinVersion = !bodyMinVersion.isNullOrBlank() &&
            compareSemanticVersions(BuildConfig.VERSION_NAME, bodyMinVersion) < 0
        val belowBodyMinCode = bodyMinVersionCode > BuildConfig.VERSION_CODE

        val isMandatory = (policy?.forceUpdate == true) ||
            hasForceMarkerInRelease ||
            belowPolicyMinVersion ||
            belowPolicyMinCode ||
            belowBodyMinVersion ||
            belowBodyMinCode

        val cleanChangelog = stripControlMarkers(
            when {
                bodyText.isNotBlank() -> bodyText
                !policy?.releaseNotes.isNullOrBlank() -> policy!!.releaseNotes
                actionsBuild != null && actionsBuild.displayTitle.isNotBlank() ->
                    "Compilación reciente de Luma.apk (${actionsBuild.displayTitle})."
                directApk != null ->
                    "Se detectó una versión distinta de Luma.apk en $repo (${directApk.path})."
                else ->
                    "Nueva versión de Luma.apk disponible en $repo."
            }
        )

        val chosenApkUrl = sanitizeGitHubUrl(policy?.apkUrl.orEmpty())
            ?: sanitizeGitHubUrl(release?.takeIf { it.hasDirectApk }?.apkDownloadUrl.orEmpty())
            ?: sanitizeGitHubUrl(directApk?.downloadUrl.orEmpty())
            ?: sanitizeGitHubUrl(actionsBuild?.runHtmlUrl.orEmpty())
            ?: sanitizeGitHubUrl(release?.htmlUrl.orEmpty())
            ?: fallbackPage

        val releasePageUrl = sanitizeGitHubUrl(release?.htmlUrl.orEmpty())
            ?: sanitizeGitHubUrl(directApk?.htmlUrl.orEmpty())
            ?: sanitizeGitHubUrl(actionsBuild?.runHtmlUrl.orEmpty())
            ?: fallbackPage

        val apkSize = when {
            (release?.apkSizeBytes ?: 0L) > 0L -> release!!.apkSizeBytes
            (directApk?.sizeBytes ?: 0L) > 0L -> directApk!!.sizeBytes
            (actionsBuild?.artifactSizeBytes ?: 0L) > 0L -> actionsBuild!!.artifactSizeBytes
            else -> 0L
        }

        return AppUpdateInfo(
            versionName = chosenVersion,
            versionCode = chosenVersionCode,
            title = if (isMandatory) {
                "Actualización obligatoria"
            } else {
                titleText.ifBlank { "Luma v$chosenVersion" }
            },
            changelog = cleanChangelog,
            apkDownloadUrl = chosenApkUrl,
            releasePageUrl = releasePageUrl,
            apkSizeBytes = apkSize,
            mode = if (isMandatory) UpdateEnforcementMode.MANDATORY_BLOCKING else UpdateEnforcementMode.OPTIONAL_SKIPPABLE,
            blockMessage = policy?.blockMessage.orEmpty()
        )
    }

    private fun containsForceMarker(text: String): Boolean {
        val upper = text.uppercase()
        return upper.contains("[FORCE]") ||
            upper.contains("[OBLIGATORIA]") ||
            upper.contains("[OBLIGATORIO]") ||
            upper.contains("[MANDATORY]") ||
            FORCE_UPDATE_REGEX.containsMatchIn(text)
    }

    private fun extractIntMarker(text: String, key: String): Int {
        val match = Regex("$key\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun extractStringMarker(text: String, key: String): String? {
        val match = Regex("$key\\s*[:=]\\s*([vV]?\\d+(?:\\.\\d+)*)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.let { cleanVersionString(it) }
    }

    private fun stripControlMarkers(raw: String): String {
        return raw.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("force_update", ignoreCase = true) ||
                    trimmed.startsWith("min_version", ignoreCase = true) ||
                    trimmed.startsWith("version_code", ignoreCase = true)
            }
            .joinToString("\n")
            .replace(CONTROL_TAG_REGEX, "")
            .trim()
    }

    /**
     * Checks whether the remote `Luma.apk` version differs from the currently installed APK:
     * - If `remoteVersionCode > 0` and differs from `BuildConfig.VERSION_CODE` -> update available!
     * - If `remoteVersionName` differs from `BuildConfig.VERSION_NAME` -> update available!
     * - If both match -> up to date.
     */
    fun isVersionDifferentFromInstalled(remoteVersionName: String, remoteVersionCode: Int): Boolean {
        if (remoteVersionCode > 0 && remoteVersionCode != BuildConfig.VERSION_CODE) {
            return true
        }
        return isVersionStringDifferent(remoteVersionName, BuildConfig.VERSION_NAME)
    }

    fun isCurrentAppOutdated(remoteVersionName: String, remoteVersionCode: Int): Boolean =
        isVersionDifferentFromInstalled(remoteVersionName, remoteVersionCode)

    private fun isVersionStringDifferent(remoteRaw: String, installedRaw: String): Boolean {
        val remoteNorm = remoteRaw.trim().removePrefix("v").removePrefix("V").trim()
        val installedNorm = installedRaw.trim().removePrefix("v").removePrefix("V").trim()
        if (remoteNorm.isEmpty()) return false
        if (remoteNorm.equals(installedNorm, ignoreCase = true)) return false

        val remoteClean = cleanVersionString(remoteNorm)
        val installedClean = cleanVersionString(installedNorm)
        // If both are pure numeric semantic versions (e.g., "1.0" vs "1.0.0"), compare numerically
        val remoteIsPureSemver = remoteClean.isNotEmpty() && remoteClean == remoteNorm
        val installedIsPureSemver = installedClean.isNotEmpty() && installedClean == installedNorm
        if (remoteIsPureSemver && installedIsPureSemver) {
            return compareSemanticVersions(remoteClean, installedClean) != 0
        }
        return true
    }

    fun cleanVersionString(raw: String): String {
        val match = SEMVER_REGEX.find(raw.trim())
        return match?.groupValues?.getOrNull(1) ?: ""
    }

    fun compareSemanticVersions(a: String, b: String): Int {
        val partsA = cleanVersionString(a).split('.').mapNotNull { it.toIntOrNull() }
        val partsB = cleanVersionString(b).split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    private fun saveMandatoryLock(context: Context, info: AppUpdateInfo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCKED_VERSION_NAME, info.versionName)
            .putInt(KEY_LOCKED_VERSION_CODE, info.versionCode)
            .putString(KEY_LOCKED_APK_URL, info.apkDownloadUrl)
            .putString(KEY_LOCKED_RELEASE_URL, info.releasePageUrl)
            .putString(KEY_LOCKED_CHANGELOG, info.changelog)
            .putString(KEY_LOCKED_BLOCK_MESSAGE, info.blockMessage)
            .putLong(KEY_LOCKED_APK_SIZE, info.apkSizeBytes)
            .apply()
    }

    private fun clearMandatoryLock(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOCKED_VERSION_NAME)
            .remove(KEY_LOCKED_VERSION_CODE)
            .remove(KEY_LOCKED_APK_URL)
            .remove(KEY_LOCKED_RELEASE_URL)
            .remove(KEY_LOCKED_CHANGELOG)
            .remove(KEY_LOCKED_BLOCK_MESSAGE)
            .remove(KEY_LOCKED_APK_SIZE)
            .apply()
    }
}
