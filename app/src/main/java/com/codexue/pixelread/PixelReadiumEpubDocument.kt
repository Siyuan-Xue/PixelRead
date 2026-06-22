package com.codexue.pixelread

import android.content.Context
import android.net.Uri
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@OptIn(ExperimentalReadiumApi::class)
class PixelReadiumEpubDocument private constructor(
    val fileName: String,
    private val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
) : Closeable {
    val sessionKey: String = "${fileName}-${System.identityHashCode(publication)}"
    val chapterCount: Int = publication.readingOrder.size.coerceAtLeast(1)

    fun chapterIndexFor(locator: Locator): Int {
        val locatorHref = locator.href.toString().substringBefore("#")
        val index = publication.readingOrder.indexOfFirst { link ->
            link.href.toString().substringBefore("#") == locatorHref
        }
        return index.coerceAtLeast(0).coerceAtMost(chapterCount - 1)
    }

    override fun close() {
        publication.close()
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PixelReadiumEpubDocument = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            val fileName = resolver.displayName(uri) ?: "SELECTED.EPUB"
            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(resolver, httpClient)
            val parser = DefaultPublicationParser(
                context = appContext,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null,
            )
            val opener = PublicationOpener(publicationParser = parser)
            val absoluteUrl = uri.toAbsoluteUrl() ?: error("Selected EPUB URI is not an absolute URL.")
            val asset = assetRetriever
                .retrieve(absoluteUrl, MediaType.EPUB)
                .getOrNull()
                ?: error("Readium could not retrieve the selected EPUB.")
            val publication = opener
                .open(asset, allowUserInteraction = false)
                .getOrNull()
                ?: error("Readium could not open the selected EPUB.")

            require(publication.conformsTo(Publication.Profile.EPUB)) {
                "Selected publication is not an EPUB."
            }

            PixelReadiumEpubDocument(
                fileName = fileName,
                publication = publication,
                navigatorFactory = EpubNavigatorFactory(publication),
            )
        }
    }
}
