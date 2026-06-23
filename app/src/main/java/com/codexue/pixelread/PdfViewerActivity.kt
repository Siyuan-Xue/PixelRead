package com.codexue.pixelread

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PdfViewerActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var statusPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var openBookButton: ImageButton
    private lateinit var themeToggleButton: ImageButton
    private lateinit var viewerContainer: FrameLayout
    private lateinit var creditLabel: TextView

    private val prefs by lazy { getSharedPreferences(ReaderPrefsName, Context.MODE_PRIVATE) }
    private var themeMode: ReaderThemeMode = ReaderThemeMode.DARK
    private val textViews = mutableListOf<TextView>()
    private val dividers = mutableListOf<View>()

    private val openBookLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openSelectedBook(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        themeMode = enumValueOrDefault(prefs.getString("themeMode", null), ReaderThemeMode.DARK)
        buildLayout()
        applyTheme()

        val uri = intent?.data
        if (uri == null) {
            showError("CAN'T OPEN PDF")
        } else {
            open(uri)
        }
    }

    private fun buildLayout() {
        val padding = screenPadding()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(32), padding, padding)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("PIXELREAD", 18f, bold = true), LinearLayout.LayoutParams(0, dp(34), 1f))
        creditLabel = label("CODEX & XUE", 13f, bold = true, alignEnd = true)
        header.addView(creditLabel, LinearLayout.LayoutParams(dp(180), dp(34)))
        root.addView(header)

        statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        statusText = label("OPENING PDF", 12f, bold = true)
        statusBadge = badge("LOADING")
        openBookButton = iconButton { openBookLauncher.launch(BOOK_OPEN_MIME_TYPES) }
        themeToggleButton = iconButton { toggleThemeMode() }
        statusPanel.addView(statusText, LinearLayout.LayoutParams(0, dp(44), 1f))
        statusPanel.addView(statusBadge, LinearLayout.LayoutParams(statusBadgeWidth(), dp(44)).withLeftMargin(dp(6)))
        statusPanel.addView(divider(), LinearLayout.LayoutParams(dp(1), dp(32)).withLeftMargin(dp(6)))
        statusPanel.addView(openBookButton, LinearLayout.LayoutParams(dp(44), dp(44)).withLeftMargin(dp(6)))
        statusPanel.addView(themeToggleButton, LinearLayout.LayoutParams(dp(44), dp(44)).withLeftMargin(dp(6)))
        root.addView(statusPanel, LinearLayout.LayoutParams(-1, dp(58)).withTopMargin(dp(8)))

        viewerContainer = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(viewerContainer, LinearLayout.LayoutParams(-1, 0, 1f).withTopMargin(dp(8)))

        setContentView(root)
    }

    private fun openSelectedBook(uri: Uri) {
        val fileName = contentResolver.displayName(uri)
        val documentType = detectDocumentType(fileName, contentResolver.getType(uri))
        when (documentType) {
            ReaderDocumentType.PDF -> {
                persistReadPermission(uri)
                open(uri)
            }
            ReaderDocumentType.EPUB -> {
                persistReadPermission(uri)
                startActivity(Intent(this, EpubReadiumActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                finish()
            }
            null -> showError("UNSUPPORTED FILE")
        }
    }

    private fun open(uri: Uri) {
        statusText.text = contentResolver.displayName(uri) ?: "SELECTED.PDF"
        statusBadge.text = "LOADING"
        attachViewer(uri)
        lifecycleScope.launch {
            try {
                val document = PixelPdfDocument.open(this@PdfViewerActivity, uri)
                statusText.text = document.fileName
                statusBadge.text = pdfViewerStatusText(document.pageCount)
            } catch (throwable: Throwable) {
                Log.e(PdfLogTag, "Failed to inspect PDF", throwable)
                statusBadge.text = "PDF"
            }
        }
    }

    private fun attachViewer(uri: Uri) {
        val fragment = PixelPdfViewerFragment().apply {
            onLoadError = { throwable ->
                Log.e(PdfLogTag, "AndroidX PDF viewer failed to load", throwable)
                showError(userFacingPdfError(throwable))
            }
        }
        supportFragmentManager.beginTransaction()
            .replace(viewerContainer.id, fragment, PdfTag)
            .commitNowAllowingStateLoss()
        fragment.documentUri = uri
    }

    private fun toggleThemeMode() {
        themeMode = if (themeMode == ReaderThemeMode.DARK) ReaderThemeMode.LIGHT else ReaderThemeMode.DARK
        prefs.edit().putString("themeMode", themeMode.name).apply()
        applyTheme()
    }

    private fun applyTheme() {
        val tokens = readerThemeTokens(themeMode)
        root.setBackgroundColor(tokens.background.toInt())
        textViews.forEach { it.setTextColor(tokens.text.toInt()) }
        creditLabel.setTextColor(tokens.primary.toInt())
        statusPanel.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        dividers.forEach { it.setBackgroundColor(tokens.outline.toInt()) }
        viewerContainer.setBackgroundColor(tokens.panel.toInt())
        viewerContainer.foreground = pixelBorder(tokens.outline.toInt(), strokeDp = 2)
        updateControls()
    }

    private fun updateControls() {
        val tokens = readerThemeTokens(themeMode)
        openBookButton.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        openBookButton.setImageResource(R.drawable.ic_reader_open_book)
        openBookButton.setColorFilter(tokens.primary.toInt())
        openBookButton.contentDescription = "OPEN BOOK"
        themeToggleButton.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        themeToggleButton.setImageResource(
            if (themeMode == ReaderThemeMode.DARK) R.drawable.ic_reader_moon else R.drawable.ic_reader_sun,
        )
        themeToggleButton.setColorFilter(tokens.primary.toInt())
        themeToggleButton.contentDescription = "TOGGLE THEME"
    }

    private fun showError(message: String) {
        statusText.text = message
        statusBadge.text = "ERROR"
    }

    private fun label(text: String, sp: Float, bold: Boolean = false, alignEnd: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            typeface = Typeface.MONOSPACE
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = if (alignEnd) Gravity.CENTER_VERTICAL or Gravity.END else Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textViews += this
        }

    private fun divider(): View =
        View(this).apply {
            dividers += this
        }

    private fun badge(text: String): TextView =
        label(text, 11f, bold = true).apply {
            gravity = Gravity.CENTER
        }

    private fun iconButton(onClick: () -> Unit): ImageButton =
        ImageButton(this).apply {
            isClickable = true
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }

    private fun pixelBackground(fillColor: Int, strokeColor: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(strokeDp), strokeColor)
        }

    private fun pixelBorder(strokeColor: Int, strokeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke(dp(strokeDp), strokeColor)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun screenPadding(): Int =
        if (resources.configuration.screenWidthDp >= TabletWidthDp) dp(24) else dp(16)

    private fun statusBadgeWidth(): Int =
        if (resources.configuration.screenWidthDp < CompactTopBarWidthDp) dp(104) else dp(140)

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun LinearLayout.LayoutParams.withTopMargin(value: Int) = apply { topMargin = value }
    private fun LinearLayout.LayoutParams.withLeftMargin(value: Int) = apply { leftMargin = value }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private fun pdfViewerStatusText(pageCount: Int): String =
        if (pageCount > 0) "$pageCount PAGES" else "PDF VIEW"

    private companion object {
        const val ReaderPrefsName = "pixelread_reader"
        const val PdfTag = "pixelread-pdf-reader"
        const val PdfLogTag = "PixelRead"
        const val TabletWidthDp = 900
        const val CompactTopBarWidthDp = 600
    }
}
