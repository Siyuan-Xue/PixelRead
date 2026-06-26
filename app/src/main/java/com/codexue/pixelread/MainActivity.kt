package com.codexue.pixelread

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codexue.pixelread.ui.theme.ClaudeClay
import com.codexue.pixelread.ui.theme.ClaudeClayInteractive
import com.codexue.pixelread.ui.theme.ClaudeGray050
import com.codexue.pixelread.ui.theme.ClaudeGray100
import com.codexue.pixelread.ui.theme.ClaudeGray300
import com.codexue.pixelread.ui.theme.ClaudeGray600
import com.codexue.pixelread.ui.theme.ClaudeGray800
import com.codexue.pixelread.ui.theme.ClaudeGray850
import com.codexue.pixelread.ui.theme.ClaudeGray950
import com.codexue.pixelread.ui.theme.ClaudeIvory
import com.codexue.pixelread.ui.theme.ClaudeOat
import com.codexue.pixelread.ui.theme.PixelError
import com.codexue.pixelread.ui.theme.PixelReadTheme

private const val DeveloperCredit = "CODEX & XUE"
private val TopBarFrameInset = 8.dp
private val TopBarToggleSize = 36.dp

private data class HomePalette(
    val background: Color,
    val surface: Color,
    val panel: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val primary: Color,
)

class MainActivity : ComponentActivity() {
    private var resumeVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPixelReadSystemBars(readReaderThemeMode())
        setContent {
            val refreshKey = resumeVersion
            PixelReadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PixelReadHome(refreshKey = refreshKey)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeVersion += 1
        applyPixelReadSystemBars(readReaderThemeMode())
    }
}

@Composable
private fun PixelReadHome(
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE) }
    var statusText by remember { mutableStateOf("SELECT OR RESUME") }
    var recentBooks by remember(refreshKey) { mutableStateOf(prefs.loadRecentBooks()) }
    val themeMode = context.readReaderThemeMode()
    val palette = homePalette(themeMode)
    fun canOpen(uri: Uri): Boolean =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)

    fun startReader(uri: Uri, documentType: ReaderDocumentType, recentBook: RecentBookEntry? = null) {
        val intentClass = when (documentType) {
            ReaderDocumentType.PDF -> PdfViewerActivity::class.java
            ReaderDocumentType.EPUB -> EpubReadiumActivity::class.java
        }
        context.startActivity(
            Intent(context, intentClass).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (documentType == ReaderDocumentType.PDF && recentBook != null) {
                    putExtra(EXTRA_INITIAL_PDF_PAGE_INDEX, recentBook.pdfPageIndex)
                }
                if (documentType == ReaderDocumentType.EPUB && recentBook?.epubLocatorJson != null) {
                    putExtra(EXTRA_INITIAL_EPUB_LOCATOR_JSON, recentBook.epubLocatorJson)
                }
            },
        )
    }

    val latestOpenDocument by rememberUpdatedState<(Uri) -> Unit> { uri ->
        val resolver = context.contentResolver
        val displayName = resolver.displayName(uri)
        when (detectDocumentType(displayName ?: uri.lastPathSegment, resolver.getType(uri))) {
            ReaderDocumentType.PDF -> {
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startReader(uri, ReaderDocumentType.PDF)
            }

            ReaderDocumentType.EPUB -> {
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startReader(uri, ReaderDocumentType.EPUB)
            }

            null -> {
                statusText = "UNSUPPORTED FILE"
            }
        }
    }

    val openBookLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            latestOpenDocument(uri)
        }
    }

    val openBook = {
        openBookLauncher.launch(
            BOOK_OPEN_MIME_TYPES,
        )
    }

    val openRecentBook: (RecentBookEntry) -> Unit = { entry ->
        val uri = Uri.parse(entry.uri)
        if (canOpen(uri)) {
            startReader(uri, entry.documentType, entry)
        } else {
            statusText = "BOOK UNAVAILABLE"
            recentBooks = prefs.loadRecentBooks()
        }
    }

    PixelReadHomeScreen(
        statusText = statusText,
        recentBooks = recentBooks,
        palette = palette,
        onOpenBook = openBook,
        onOpenRecentBook = openRecentBook,
        modifier = modifier,
    )
}

@Composable
private fun PixelReadHomeScreen(
    statusText: String,
    recentBooks: List<RecentBookEntry>,
    palette: HomePalette,
    onOpenBook: () -> Unit,
    onOpenRecentBook: (RecentBookEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReaderStatusBar(
            statusText = statusText,
            palette = palette,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(palette.panel, RectangleShape)
                .border(BorderStroke(2.dp, palette.outline), RectangleShape)
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "SELECT A BOOK",
                    color = palette.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
                Text(
                    text = "Open a new book or continue recent reading.",
                    color = palette.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                PixelButton(
                    label = "OPEN BOOK",
                    onClick = onOpenBook,
                    palette = palette,
                    modifier = Modifier.sizeIn(minWidth = 184.dp),
                    large = true,
                )
                if (recentBooks.isNotEmpty()) {
                    RecentBooksList(
                        recentBooks = recentBooks,
                        palette = palette,
                        onOpenRecentBook = onOpenRecentBook,
                    )
                }
            }
        }
        PixelReadFooter(palette = palette)
    }
}

@Composable
private fun PixelReadFooter(
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PIXELREAD",
            color = palette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = DeveloperCredit,
            color = palette.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReaderStatusBar(
    statusText: String,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface, RectangleShape)
            .border(BorderStroke(2.dp, palette.outline), RectangleShape)
            .padding(TopBarFrameInset),
        horizontalArrangement = Arrangement.spacedBy(TopBarFrameInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusText,
            color = if (statusText == "UNSUPPORTED FILE" || statusText == "BOOK UNAVAILABLE") {
                PixelError
            } else {
                palette.text
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PixelDrawerToggleButton(
            expanded = false,
            enabled = false,
            palette = palette,
            onClick = {},
        )
    }
}

@Composable
private fun RecentBooksList(
    recentBooks: List<RecentBookEntry>,
    palette: HomePalette,
    modifier: Modifier = Modifier,
    onOpenRecentBook: (RecentBookEntry) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "RECENT",
            color = palette.muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        recentBooks.forEach { recentBook ->
            RecentBookRow(
                recentBook = recentBook,
                palette = palette,
                onClick = { onOpenRecentBook(recentBook) },
            )
        }
    }
}

@Composable
private fun RecentBookRow(
    recentBook: RecentBookEntry,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (pressed) palette.surface else palette.panel, RectangleShape)
            .border(BorderStroke(1.dp, palette.outline), RectangleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recentBook.title,
                color = palette.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = recentBook.progressLabel.ifBlank { recentBook.documentType.label },
                color = palette.muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = recentBook.documentType.label,
            color = palette.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PixelDrawerToggleButton(
    expanded: Boolean,
    enabled: Boolean,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(TopBarToggleSize)
            .alpha(if (enabled) 1f else 0.48f)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_reader_drawer_toggle),
            contentDescription = if (expanded) "HIDE TOOLS" else "SHOW TOOLS",
            tint = if (pressed && enabled) ClaudeClayInteractive else palette.primary,
            modifier = Modifier
                .size(28.dp)
                .rotate(if (expanded) 180f else 0f),
        )
    }
}

@Composable
private fun PixelButton(
    label: String,
    onClick: () -> Unit,
    palette: HomePalette,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(if (large) 48.dp else 36.dp)
            .sizeIn(minWidth = 104.dp)
            .background(if (pressed) ClaudeClayInteractive else ClaudeClay, RectangleShape)
            .border(BorderStroke(2.dp, ClaudeGray300), RectangleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ClaudeGray950,
            fontFamily = FontFamily.Monospace,
            fontSize = if (large) 16.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun homePalette(themeMode: ReaderThemeMode): HomePalette =
    when (themeMode) {
        ReaderThemeMode.DARK -> HomePalette(
            background = ClaudeGray950,
            surface = ClaudeGray850,
            panel = ClaudeGray800,
            text = ClaudeGray050,
            muted = ClaudeGray300,
            outline = ClaudeGray600,
            primary = ClaudeClay,
        )

        ReaderThemeMode.LIGHT -> HomePalette(
            background = ClaudeIvory,
            surface = ClaudeGray100,
            panel = ClaudeOat,
            text = ClaudeGray950,
            muted = ClaudeGray600,
            outline = ClaudeGray300,
            primary = ClaudeClay,
        )
    }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PixelReadHomePreview() {
    PixelReadTheme {
        PixelReadHomeScreen(
            statusText = "SELECT OR RESUME",
            recentBooks = listOf(
                RecentBookEntry(
                    uri = "content://preview/book",
                    title = "Design Notes",
                    documentType = ReaderDocumentType.EPUB,
                    lastOpenedAt = 1L,
                    progressLabel = "CH 2/4 P 3/8",
                ),
            ),
            palette = homePalette(ReaderThemeMode.DARK),
            onOpenBook = {},
            onOpenRecentBook = {},
        )
    }
}
