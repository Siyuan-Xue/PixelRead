package com.milesxue.pixelread

import android.graphics.RectF
import android.util.SparseArray
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment

@OptIn(ExperimentalPdfApi::class)
class PixelPdfViewerFragment : PdfViewerFragment() {
    var initialPageIndex: Int = 0
    var onLoadError: ((Throwable) -> Unit)? = null
    var onVisiblePageChanged: ((Int) -> Unit)? = null

    private var activePdfView: PdfView? = null
    private var documentLoaded = false
    private var restoredInitialPage = false

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hidePdfChrome()
        view.post { hidePdfChrome() }
    }

    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        activePdfView = pdfView
        pdfView.addOnViewportChangedListener(
            object : PdfView.OnViewportChangedListener {
                override fun onViewportChanged(
                    firstVisiblePage: Int,
                    visiblePagesCount: Int,
                    pageLocations: SparseArray<RectF>,
                    zoomLevel: Float,
                ) {
                    onVisiblePageChanged?.invoke(firstVisiblePage.coerceAtLeast(0))
                }
            },
        )
        restoreInitialPageIfNeeded()
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        documentLoaded = true
        hidePdfChrome()
        view?.postDelayed({ hidePdfChrome() }, 100L)
        restoreInitialPageIfNeeded()
    }

    private fun hidePdfChrome() {
        runCatching {
            isTextSearchActive = false
            isToolboxVisible = false
        }
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        onLoadError?.invoke(error)
    }

    private fun restoreInitialPageIfNeeded() {
        if (!documentLoaded || restoredInitialPage || initialPageIndex <= 0) return
        activePdfView?.post {
            activePdfView?.scrollToPage(initialPageIndex.coerceAtLeast(0))
            restoredInitialPage = true
        }
    }
}
