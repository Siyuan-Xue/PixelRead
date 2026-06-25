package com.codexue.pixelread

val EPUB_FONT_SIZES_SP = (14..34 step 2).toList()
const val DEFAULT_EPUB_FONT_INDEX = 6
const val EPUB_FONT_SCALE_VERSION = 5
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
    if (pageCount <= 0) return "$chapterText PAGE 0/0"
    val safePage = pageIndex.coerceIn(0, pageCount - 1) + 1
    return "$chapterText PAGE $safePage/$pageCount"
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
