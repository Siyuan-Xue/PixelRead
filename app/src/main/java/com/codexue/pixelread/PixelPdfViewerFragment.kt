package com.codexue.pixelread

import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment

class PixelPdfViewerFragment : PdfViewerFragment() {
    var onLoadError: ((Throwable) -> Unit)? = null

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hidePdfChrome()
        view.post { hidePdfChrome() }
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        hidePdfChrome()
        view?.postDelayed({ hidePdfChrome() }, 100L)
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
}
