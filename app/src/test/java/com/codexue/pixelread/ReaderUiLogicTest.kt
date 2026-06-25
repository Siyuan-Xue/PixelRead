package com.codexue.pixelread

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
    fun epubFontSizeSp_usesTwoSpStepsWithLowerMinimum() {
        assertEquals(14, epubFontSizeSp(-10))
        assertEquals(28, epubFontSizeSp(7))
        assertEquals(34, epubFontSizeSp(99))
        assertEquals(26, epubFontSizeSp(DEFAULT_EPUB_FONT_INDEX))
    }

    @Test
    fun readiumFontScale_mapsPixelReadStepsToReadiumScale() {
        assertEquals(0.875, readiumFontScaleForEpubIndex(0), 0.001)
        assertEquals(1.625, readiumFontScaleForEpubIndex(DEFAULT_EPUB_FONT_INDEX), 0.001)
        assertEquals(2.125, readiumFontScaleForEpubIndex(EPUB_FONT_SIZES_SP.lastIndex), 0.001)
    }

    @Test
    fun chapterAndEpubPageStatus_clampToValidRanges() {
        assertEquals("CH 1/4", chapterStatusText(-1, 4))
        assertEquals("CH 4/4", chapterStatusText(8, 4))
        assertEquals("CH 2/4 PAGE 3/8", epubPageStatusText(1, 4, 2, 8))
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
