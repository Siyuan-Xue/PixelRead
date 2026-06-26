package com.milesxue.pixelread

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PixelPdfDocument(
    val fileName: String,
    val uri: Uri,
    val pageCount: Int = 0,
) : Closeable {
    fun withPageCount(pageCount: Int): PixelPdfDocument =
        copy(pageCount = pageCount.coerceAtLeast(0))

    override fun close() = Unit

    companion object {
        suspend fun open(context: Context, uri: Uri): PixelPdfDocument = withContext(Dispatchers.IO) {
            PixelPdfDocument(
                fileName = context.contentResolver.displayName(uri) ?: "SELECTED.PDF",
                uri = uri,
                pageCount = countPages(context, uri),
            )
        }

        private fun countPages(context: Context, uri: Uri): Int =
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
                } ?: 0
            }.getOrDefault(0)
    }
}
