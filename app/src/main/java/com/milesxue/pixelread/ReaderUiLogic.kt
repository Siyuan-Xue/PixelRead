package com.milesxue.pixelread

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val READER_PREFS_NAME = "pixelread_reader"
const val RECENT_BOOKS_PREF_KEY = "recentBooks"
const val RECENT_BOOK_LIMIT = 6
const val EXTRA_INITIAL_PDF_PAGE_INDEX = "pixelread.initial_pdf_page_index"
const val EXTRA_INITIAL_EPUB_LOCATOR_JSON = "pixelread.initial_epub_locator_json"

val EPUB_FONT_SIZES_SP = (13..29 step 2).toList()
const val DEFAULT_EPUB_FONT_INDEX = 3
const val EPUB_FONT_SCALE_VERSION = 6
val BOOK_OPEN_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/x-epub+zip",
    "application/octet-stream",
)

enum class ReaderDocumentType(val label: String) {
    PDF("PDF"),
    EPUB("EPUB"),
}

enum class ReaderThemeMode(val label: String) {
    DARK("DARK"),
    LIGHT("LIGHT"),
}

data class ReaderThemeTokens(
    val background: Long,
    val surface: Long,
    val panel: Long,
    val pageBackground: Long,
    val text: Long,
    val muted: Long,
    val outline: Long,
    val primary: Long,
)

data class RecentBookEntry(
    val uri: String,
    val title: String,
    val documentType: ReaderDocumentType,
    val lastOpenedAt: Long,
    val progressLabel: String,
    val pdfPageIndex: Int = 0,
    val epubLocatorJson: String? = null,
)

fun detectDocumentType(fileName: String?, mimeType: String?): ReaderDocumentType? {
    val safeMime = mimeType.orEmpty().lowercase()
    val safeName = fileName.orEmpty().lowercase()
    return when {
        safeMime == "application/pdf" || safeName.endsWith(".pdf") -> ReaderDocumentType.PDF
        safeMime in setOf("application/epub+zip", "application/x-epub+zip") ||
            safeName.endsWith(".epub") -> ReaderDocumentType.EPUB
        else -> null
    }
}

fun bookTitleWithoutExtension(fileName: String?): String {
    val trimmed = fileName.orEmpty().trim()
    if (trimmed.isEmpty()) return "SELECTED BOOK"
    val lowered = trimmed.lowercase()
    val withoutExtension = when {
        lowered.endsWith(".pdf") -> trimmed.dropLast(4)
        lowered.endsWith(".epub") -> trimmed.dropLast(5)
        else -> trimmed
    }.trim()
    return withoutExtension.ifEmpty { "SELECTED BOOK" }
}

fun clampEpubFontIndex(index: Int): Int =
    index.coerceIn(0, EPUB_FONT_SIZES_SP.lastIndex)

fun epubFontSizeSp(index: Int): Int =
    EPUB_FONT_SIZES_SP[clampEpubFontIndex(index)]

fun readiumFontScaleForEpubIndex(index: Int): Double =
    epubFontSizeSp(index) / 16.0

fun chapterStatusText(chapterIndex: Int, chapterCount: Int): String {
    if (chapterCount <= 0) return "CH 0/0"
    val safeChapter = chapterIndex.coerceIn(0, chapterCount - 1) + 1
    return "CH $safeChapter/$chapterCount"
}

fun epubPageStatusText(chapterIndex: Int, chapterCount: Int, pageIndex: Int, pageCount: Int): String {
    val chapterText = chapterStatusText(chapterIndex, chapterCount)
    if (pageCount <= 0) return "$chapterText P 0/0"
    val safePage = pageIndex.coerceIn(0, pageCount - 1) + 1
    return "$chapterText P $safePage/$pageCount"
}

fun pdfPageStatusText(pageIndex: Int, pageCount: Int): String {
    if (pageCount <= 0) return "P 0/0"
    val safePage = pageIndex.coerceIn(0, pageCount - 1) + 1
    return "P $safePage/$pageCount"
}

fun readerThemeTokens(themeMode: ReaderThemeMode): ReaderThemeTokens =
    when (themeMode) {
        ReaderThemeMode.DARK -> ReaderThemeTokens(
            background = 0xFF141413,
            surface = 0xFF1F1E1D,
            panel = 0xFF262624,
            pageBackground = 0xFF1F1E1D,
            text = 0xFFFAF9F5,
            muted = 0xFFD1CFC5,
            outline = 0xFF5E5D59,
            primary = 0xFFD97757,
        )
        ReaderThemeMode.LIGHT -> ReaderThemeTokens(
            background = 0xFFFAF9F5,
            surface = 0xFFF5F4ED,
            panel = 0xFFE3DACC,
            pageBackground = 0xFFF0EEE6,
            text = 0xFF141413,
            muted = 0xFF5E5D59,
            outline = 0xFFD1CFC5,
            primary = 0xFFD97757,
        )
    }

fun userFacingPdfError(throwable: Throwable): String =
    when (throwable) {
        is SecurityException -> "ACCESS DENIED"
        else -> "CAN'T OPEN PDF"
    }

fun userFacingEpubError(throwable: Throwable): String =
    when (throwable) {
        is SecurityException -> "EPUB PATH ERROR"
        else -> "CAN'T OPEN EPUB"
    }

fun encodeRecentBooks(entries: List<RecentBookEntry>, maxEntries: Int = RECENT_BOOK_LIMIT): String =
    normalizeRecentBooks(entries, maxEntries).joinToString("\n") { entry ->
        listOf(
            RecentBookEncodingVersion,
            entry.uri,
            entry.title,
            entry.documentType.name,
            entry.lastOpenedAt.toString(),
            entry.progressLabel,
            entry.pdfPageIndex.coerceAtLeast(0).toString(),
            entry.epubLocatorJson.orEmpty(),
        ).joinToString("\t") { value -> encodeRecentField(value) }
    }

fun decodeRecentBooks(encoded: String?, maxEntries: Int = RECENT_BOOK_LIMIT): List<RecentBookEntry> =
    normalizeRecentBooks(
        encoded.orEmpty()
            .lineSequence()
            .mapNotNull { line -> decodeRecentBookLine(line) }
            .toList(),
        maxEntries,
    )

fun mergeRecentBook(
    entries: List<RecentBookEntry>,
    entry: RecentBookEntry,
    maxEntries: Int = RECENT_BOOK_LIMIT,
): List<RecentBookEntry> =
    normalizeRecentBooks(listOf(entry) + entries.filterNot { it.uri == entry.uri }, maxEntries)

fun normalizeRecentBooks(
    entries: List<RecentBookEntry>,
    maxEntries: Int = RECENT_BOOK_LIMIT,
): List<RecentBookEntry> =
    entries
        .filter { it.uri.isNotBlank() && it.title.isNotBlank() }
        .sortedByDescending { it.lastOpenedAt }
        .distinctBy { it.uri }
        .take(maxEntries.coerceAtLeast(0))

private fun decodeRecentBookLine(line: String): RecentBookEntry? {
    val fields = line.split("\t").map { decodeRecentField(it) }
    if (fields.size < 8 || fields[0] != RecentBookEncodingVersion) return null
    val documentType = enumValues<ReaderDocumentType>().firstOrNull { it.name == fields[3] } ?: return null
    val lastOpenedAt = fields[4].toLongOrNull() ?: return null
    val pdfPageIndex = fields[6].toIntOrNull()?.coerceAtLeast(0) ?: 0
    return RecentBookEntry(
        uri = fields[1],
        title = fields[2],
        documentType = documentType,
        lastOpenedAt = lastOpenedAt,
        progressLabel = fields[5],
        pdfPageIndex = pdfPageIndex,
        epubLocatorJson = fields[7].ifBlank { null },
    )
}

private fun encodeRecentField(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun decodeRecentField(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault("")

private const val RecentBookEncodingVersion = "1"
