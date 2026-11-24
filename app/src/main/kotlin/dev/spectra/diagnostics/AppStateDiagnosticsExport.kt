package dev.spectra.diagnostics

import android.content.pm.PackageManager
import android.os.Build
import dev.spectra.app.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.spectra_core.DiagnosticsPlatformInfo
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone

// Core assembles the bundle from what it recorded; this side supplies what
// only the platform knows about itself and owns the file.

/** The app's version and build, and the device's system, language and time zone. */
private val AppState.diagnosticsPlatformInfo: DiagnosticsPlatformInfo
    get() {
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return DiagnosticsPlatformInfo(
            appVersion = info?.versionName ?: "unknown",
            buildNumber = info?.longVersionCode?.toString() ?: "unknown",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            localeIdentifier = Locale.getDefault().toLanguageTag(),
            timeZoneIdentifier = TimeZone.getDefault().id,
        )
    }

/**
 * The bundle, written to the app's cache where the share sheet reads it
 * from. The system may clear the cache; the app keeps only the newest
 * export, replacing the one before.
 */
suspend fun AppState.exportDiagnosticsBundle(): File {
    val json = bridge.ready().diagnosticsBundle(diagnosticsPlatformInfo)
    val stamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-")
    val directory = File(context.cacheDir, "diagnostics")
    return withContext(Dispatchers.IO) {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }
        File(directory, "spectra-diagnostics-$stamp.json").apply { writeText(json) }
    }
}
