package com.codexue.pixelread

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
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
private const val ReaderPrefsName = "pixelread_reader"

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelReadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PixelReadHome()
                }
            }
        }
    }
}

@Composable
private fun PixelReadHome(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ReaderPrefsName, Context.MODE_PRIVATE) }
    var statusText by remember { mutableStateOf("SELECT A PDF OR EPUB") }
    var statusBadge by remember { mutableStateOf("NO FILE") }
    val themeMode = enumValueOrDefault(
        prefs.getString("themeMode", null),
        ReaderThemeMode.DARK,
    )
    val palette = homePalette(themeMode)
    val latestOpenDocument by rememberUpdatedState<(Uri) -> Unit> { uri ->
        val resolver = context.contentResolver
        val displayName = resolver.displayName(uri)
        when (detectDocumentType(displayName ?: uri.lastPathSegment, resolver.getType(uri))) {
            ReaderDocumentType.PDF -> {
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent(context, PdfViewerActivity::class.java).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            }

            ReaderDocumentType.EPUB -> {
                runCatching {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent(context, EpubReadiumActivity::class.java).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            }

            null -> {
                statusText = "UNSUPPORTED FILE"
                statusBadge = "ERROR"
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
            arrayOf(
                "application/pdf",
                "application/epub+zip",
                "application/x-epub+zip",
                "application/octet-stream",
            ),
        )
    }

    PixelReadHomeScreen(
        statusText = statusText,
        statusBadge = statusBadge,
        palette = palette,
        onOpenBook = openBook,
        modifier = modifier,
    )
}

@Composable
private fun PixelReadHomeScreen(
    statusText: String,
    statusBadge: String,
    palette: HomePalette,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelReadHeader(palette = palette)
        ReaderStatusBar(
            statusText = statusText,
            statusBadge = statusBadge,
            palette = palette,
            onOpenBook = onOpenBook,
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
                    text = "Open a local PDF or EPUB.",
                    color = palette.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
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
            }
        }
    }
}

@Composable
private fun PixelReadHeader(
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PIXELREAD",
            color = palette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
        )
        Text(
            text = DeveloperCredit,
            color = palette.primary,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
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
    statusBadge: String,
    palette: HomePalette,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surface, RectangleShape)
            .border(BorderStroke(2.dp, palette.outline), RectangleShape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusText,
            color = if (statusBadge == "ERROR") PixelError else palette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PixelBadge(label = statusBadge, palette = palette)
        PixelButton(label = "OPEN BOOK", onClick = onOpenBook, palette = palette)
    }
}

@Composable
private fun PixelBadge(
    label: String,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .sizeIn(minWidth = 92.dp)
            .background(palette.panel, RectangleShape)
            .border(BorderStroke(1.dp, palette.outline), RectangleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = palette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
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

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PixelReadHomePreview() {
    PixelReadTheme {
        PixelReadHomeScreen(
            statusText = "SELECT A PDF OR EPUB",
            statusBadge = "NO FILE",
            palette = homePalette(ReaderThemeMode.DARK),
            onOpenBook = {},
        )
    }
}
