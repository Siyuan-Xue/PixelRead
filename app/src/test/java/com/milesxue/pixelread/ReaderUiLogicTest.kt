package com.milesxue.pixelread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderUiLogicTest {
    @Test
    fun detectDocumentType_usesMimeTypeAndFilenameFallback() {
        assertEquals(ReaderDocumentType.PDF, detectDocumentType("book.bin", "application/pdf"))
        assertEquals(ReaderDocumentType.PDF, detectDocumentType("book.pdf", "application/octet-stream"))
        assertEquals(ReaderDocumentType.EPUB, detectDocumentType("book.bin", "application/epub+zip"))
        assertEquals(ReaderDocumentType.EPUB, detectDocumentType("book.epub", "application/octet-stream"))
        assertEquals(null, detectDocumentType("book.txt", "text/plain"))
    }

    @Test
    fun epubFontSizeSp_usesThirteenToTwentyNineRangeWithTwoSpSteps() {
        assertEquals(listOf(13, 15, 17, 19, 21, 23, 25, 27, 29), EPUB_FONT_SIZES_SP)
        assertEquals(13, epubFontSizeSp(-10))
        assertEquals(27, epubFontSizeSp(7))
        assertEquals(29, epubFontSizeSp(99))
        assertEquals(19, epubFontSizeSp(DEFAULT_EPUB_FONT_INDEX))
    }

    @Test
    fun readiumFontScale_mapsPixelReadStepsToReadiumScale() {
        assertEquals(0.8125, readiumFontScaleForEpubIndex(0), 0.001)
        assertEquals(1.1875, readiumFontScaleForEpubIndex(DEFAULT_EPUB_FONT_INDEX), 0.001)
        assertEquals(1.8125, readiumFontScaleForEpubIndex(EPUB_FONT_SIZES_SP.lastIndex), 0.001)
    }

    @Test
    fun chapterAndEpubPageStatus_clampToValidRanges() {
        assertEquals("CH 1/4", chapterStatusText(-1, 4))
        assertEquals("CH 4/4", chapterStatusText(8, 4))
        assertEquals("CH 2/4 P 3/8", epubPageStatusText(1, 4, 2, 8))
    }

    @Test
    fun bookTitleWithoutExtension_stripsKnownReaderExtensions() {
        assertEquals("Novel", bookTitleWithoutExtension("Novel.epub"))
        assertEquals("Manual", bookTitleWithoutExtension("Manual.PDF"))
        assertEquals("Notes.txt", bookTitleWithoutExtension("Notes.txt"))
        assertEquals("SELECTED BOOK", bookTitleWithoutExtension("  "))
    }

    @Test
    fun pdfPageStatusText_clampsToValidRanges() {
        assertEquals("P 0/0", pdfPageStatusText(8, 0))
        assertEquals("P 1/10", pdfPageStatusText(-4, 10))
        assertEquals("P 10/10", pdfPageStatusText(99, 10))
        assertEquals("P 4/10", pdfPageStatusText(3, 10))
    }

    @Test
    fun recentBooks_roundTripSortAndDeduplicate() {
        val entries = listOf(
            RecentBookEntry(
                uri = "content://old",
                title = "Old",
                documentType = ReaderDocumentType.PDF,
                lastOpenedAt = 10L,
                progressLabel = "P 1/3",
                pdfPageIndex = 0,
            ),
            RecentBookEntry(
                uri = "content://new",
                title = "New",
                documentType = ReaderDocumentType.EPUB,
                lastOpenedAt = 30L,
                progressLabel = "CH 2/4 P 3/8",
                epubLocatorJson = "{\"href\":\"chapter.xhtml\"}",
            ),
            RecentBookEntry(
                uri = "content://old",
                title = "Old Latest",
                documentType = ReaderDocumentType.PDF,
                lastOpenedAt = 40L,
                progressLabel = "P 2/3",
                pdfPageIndex = 1,
            ),
        )

        val decoded = decodeRecentBooks(encodeRecentBooks(entries))

        assertEquals(2, decoded.size)
        assertEquals("Old Latest", decoded[0].title)
        assertEquals("New", decoded[1].title)
        assertEquals("CH 2/4 P 3/8", decoded[1].progressLabel)
        assertEquals("{\"href\":\"chapter.xhtml\"}", decoded[1].epubLocatorJson)
    }

    @Test
    fun readerThemeTokens_doNotUsePureBlackOrPureWhiteReadingSurfaces() {
        val dark = readerThemeTokens(ReaderThemeMode.DARK)
        val light = readerThemeTokens(ReaderThemeMode.LIGHT)

        assertEquals(0xFF141413, dark.background)
        assertEquals(0xFF1F1E1D, dark.pageBackground)
        assertEquals(0xFFFAF9F5, light.background)
        assertTrue(dark.background != 0xFF000000)
        assertTrue(dark.pageBackground != 0xFFFFFFFF)
        assertTrue(light.background != 0xFFFFFFFF)
        assertTrue(light.pageBackground != 0xFFFFFFFF)
    }

    @Test
    fun userFacingPdfError_mapsSecurityFailuresSeparately() {
        assertEquals("ACCESS DENIED", userFacingPdfError(SecurityException()))
        assertEquals("CAN'T OPEN PDF", userFacingPdfError(IllegalStateException()))
    }
}
