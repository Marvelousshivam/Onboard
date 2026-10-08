package com.boardsprep.onboard.core.pdf

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory + file-backed error logger for the OnBOARD Reader.
 *
 * Every significant error in the reader (rendering failures, text extraction
 * failures, download errors, unexpected exceptions) is logged here so the user
 * can view it via the "View error log" option in the reader's overflow menu.
 *
 * A global [Thread.UncaughtExceptionHandler] installed in [com.boardsprep.onboard.OnboardApplication]
 * also routes uncaught crashes through here, so even a hard crash leaves a
 * trail the user can read on next launch.
 *
 * The log is capped at [MAX_ENTRIES] in-memory entries and [MAX_FILE_BYTES]
 * on disk to avoid unbounded growth.
 */
object PdfErrorLog {

    private const val TAG = "PdfErrorLog"
    private const val MAX_ENTRIES = 300
    private const val MAX_FILE_BYTES = 1_000_000 // 1 MB
    private const val LOG_FILENAME = "onboard_reader_errors.log"

    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwableSummary: String? = null
    )

    enum class Level(val label: String) {
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR")
    }

    private val entries = mutableListOf<LogEntry>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Must be called once from Application.onCreate(). */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILENAME)
        // Load any persisted entries from a previous session so the user can
        // view crash logs even after the app was killed.
        try {
            val f = logFile ?: return
            if (!f.exists()) return
            val lines = f.readText().split("\n\n").filter { it.isNotBlank() }
            for (line in lines) {
                val entry = parseEntry(line) ?: continue
                entries.add(entry)
                if (entries.size > MAX_ENTRIES) entries.removeAt(0)
            }
        } catch (_: Exception) {
            // Loading must never throw.
        }
    }

    private fun parseEntry(text: String): LogEntry? {
        try {
            val tsMatch = Regex("\\[(.+?)\\]").find(text) ?: return null
            val timestamp = dateFormat.parse(tsMatch.groupValues[1])?.time ?: return null
            val rest = text.substringAfter("] ").trim()
            val levelMatch = Regex("\\[(.+?)\\]").find(rest) ?: return null
            val level = runCatching { Level.valueOf(levelMatch.groupValues[1]) }.getOrNull() ?: return null
            val rest2 = rest.substringAfter("] ").trim()
            val tagMatch = Regex("\\[(.+?)\\]").find(rest2) ?: return null
            val tag = tagMatch.groupValues[1]
            val rest3 = rest2.substringAfter("] ").trim()
            val parts = rest3.split("\n", limit = 2)
            val message = parts[0].trim()
            val throwableSummary = if (parts.size > 1) parts[1].trim() else null
            return LogEntry(timestamp, level, tag, message, throwableSummary)
        } catch (_: Exception) {
            return null
        }
    }

    @Synchronized
    fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val summary = throwable?.let { t ->
            buildString {
                append("${t.javaClass.name}: ${t.message}")
                val sw = StringWriter()
                t.printStackTrace(PrintWriter(sw))
                // Keep stack traces concise — first 25 lines.
                sw.toString().lines().let { lines ->
                    append("\n")
                    append(lines.take(25).joinToString("\n"))
                    if (lines.size > 25) append("\n... (${lines.size - 25} more lines)")
                }
            }
        }

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwableSummary = summary
        )

        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }

        // Mirror to logcat.
        when (level) {
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message, throwable)
            Level.ERROR -> Log.e(tag, message, throwable)
        }

        // Append to file.
        try {
            val f = logFile ?: return
            f.appendText(formatEntry(entry) + "\n")
            if (f.length() > MAX_FILE_BYTES) {
                // Trim to the second half of the file.
                val lines = f.readLines()
                f.writeText(lines.drop(lines.size / 2).joinToString("\n"))
            }
        } catch (_: Exception) {
            // Logging must never throw.
        }
    }

    fun info(tag: String, message: String) = log(Level.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    @Synchronized
    fun getEntries(): List<LogEntry> = entries.toList()

    @Synchronized
    fun getEntriesReversed(): List<LogEntry> = entries.reversed()

    @Synchronized
    fun getLogText(): String {
        return entries.joinToString("\n\n") { formatEntry(it) }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        try { logFile?.delete() } catch (_: Exception) {}
    }

    private fun formatEntry(entry: LogEntry): String {
        val sb = StringBuilder()
        sb.append("[${dateFormat.format(Date(entry.timestamp))}] ")
        sb.append("[${entry.level.label}] ")
        sb.append("[${entry.tag}] ")
        sb.append(entry.message)
        entry.throwableSummary?.let { sb.append("\n  ").append(it) }
        return sb.toString()
    }
}
