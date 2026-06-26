package com.milesxue.pixelread

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PdfViewerActivity : FragmentActivity() {
    private lateinit var root: LinearLayout
    private lateinit var statusPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var drawerToggleButton: ImageButton
    private lateinit var toolsDrawer: LinearLayout
    private lateinit var fontIcon: ImageView
    private lateinit var fontSlider: SeekBar
    private lateinit var openBookButton: ImageButton
    private lateinit var themeToggleButton: ImageButton
    private lateinit var viewerContainer: FrameLayout
    private lateinit var creditLabel: TextView

    private val prefs by lazy { getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE) }
    private var themeMode: ReaderThemeMode = ReaderThemeMode.DARK
    private var openedUri: Uri? = null
    private var drawerExpanded: Boolean = false
    private var currentTitle: String = "SELECTED PDF"
    private var currentPageIndex: Int = 0
    private var pageCount: Int = 0
    private var systemBarsTopInset: Int = 0
    private var systemBarsBottomInset: Int = 0
    private val textViews = mutableListOf<TextView>()
    private val dividers = mutableListOf<View>()

    private val openBookLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openSelectedBook(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = enumValueOrDefault(prefs.getString("themeMode", null), ReaderThemeMode.DARK)
        currentPageIndex = savedInstanceState?.getInt(StatePdfPageIndex)
            ?: intent.getIntExtra(EXTRA_INITIAL_PDF_PAGE_INDEX, 0)
        currentPageIndex = currentPageIndex.coerceAtLeast(0)
        buildLayout()
        applyTheme()

        val uri = savedInstanceState?.getString(StateOpenedUri)?.let(Uri::parse) ?: intent?.data
        if (uri == null) {
            showError("CAN'T OPEN PDF")
        } else {
            open(uri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        openedUri?.let { outState.putString(StateOpenedUri, it.toString()) }
        outState.putInt(StatePdfPageIndex, currentPageIndex)
    }

    private fun buildLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        updateRootPadding()

        statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val statusInfoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusText = label("OPENING PDF", 14f, bold = true)
        statusText.setPadding(dp(4), 0, 0, 0)
        statusBadge = badge("LOADING")
        drawerToggleButton = iconButton { toggleDrawer() }
        fontIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = "FONT SIZE"
        }
        fontSlider = SeekBar(this).apply {
            max = EPUB_FONT_SIZES_SP.lastIndex
            progress = DEFAULT_EPUB_FONT_INDEX
            isEnabled = false
            alpha = DisabledControlAlpha
        }
        openBookButton = iconButton { openBookLauncher.launch(BOOK_OPEN_MIME_TYPES) }
        themeToggleButton = iconButton { toggleThemeMode() }
        statusInfoRow.addView(statusText, LinearLayout.LayoutParams(0, dp(44), 1f))
        statusInfoRow.addView(statusBadge, LinearLayout.LayoutParams(statusBadgeWidth(), dp(44)).withLeftMargin(dp(6)))
        statusInfoRow.addView(drawerToggleButton, LinearLayout.LayoutParams(dp(44), dp(44)).withLeftMargin(dp(6)))
        statusPanel.addView(statusInfoRow, LinearLayout.LayoutParams(-1, dp(44)))

        toolsDrawer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        toolsDrawer.addView(fontIcon, LinearLayout.LayoutParams(dp(44), dp(44)))
        toolsDrawer.addView(fontSlider, LinearLayout.LayoutParams(0, dp(44), 1f).withLeftMargin(dp(8)))
        toolsDrawer.addView(divider(), LinearLayout.LayoutParams(dp(1), dp(32)).withLeftMargin(dp(8)))
        toolsDrawer.addView(themeToggleButton, LinearLayout.LayoutParams(dp(44), dp(44)).withLeftMargin(dp(8)))
        toolsDrawer.addView(openBookButton, LinearLayout.LayoutParams(dp(44), dp(44)).withLeftMargin(dp(8)))
        statusPanel.addView(toolsDrawer, LinearLayout.LayoutParams(-1, dp(44)).withTopMargin(dp(4)))

        root.addView(statusPanel, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        viewerContainer = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(viewerContainer, LinearLayout.LayoutParams(-1, 0, 1f).withTopMargin(dp(8)))

        root.addView(brandFooter(), LinearLayout.LayoutParams(-1, dp(14)).withTopMargin(dp(8)))

        setContentView(root)
        installRootInsets()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateRootPadding()
        ViewCompat.requestApplyInsets(root)
        statusBadge.layoutParams = (statusBadge.layoutParams as LinearLayout.LayoutParams).apply {
            width = statusBadgeWidth()
        }
        statusBadge.requestLayout()
    }

    private fun openSelectedBook(uri: Uri) {
        val fileName = contentResolver.displayName(uri)
        val documentType = detectDocumentType(fileName, contentResolver.getType(uri))
        when (documentType) {
            ReaderDocumentType.PDF -> {
                persistReadPermission(uri)
                currentPageIndex = 0
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
        rememberOpenedUri(uri)
        currentTitle = bookTitleWithoutExtension(contentResolver.displayName(uri) ?: "SELECTED.PDF")
        statusText.text = currentTitle
        statusBadge.text = "LOADING"
        pageCount = 0
        attachViewer(uri)
        lifecycleScope.launch {
            try {
                val document = PixelPdfDocument.open(this@PdfViewerActivity, uri)
                currentTitle = bookTitleWithoutExtension(document.fileName)
                pageCount = document.pageCount
                currentPageIndex = currentPageIndex.coerceAtMost((pageCount - 1).coerceAtLeast(0))
                statusText.text = currentTitle
                updatePdfStatus()
                saveRecentBook()
            } catch (throwable: Throwable) {
                Log.e(PdfLogTag, "Failed to inspect PDF", throwable)
                statusBadge.text = "PDF"
            }
        }
    }

    private fun rememberOpenedUri(uri: Uri) {
        openedUri = uri
        setIntent(Intent(intent).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            removeExtra(EXTRA_INITIAL_PDF_PAGE_INDEX)
            removeExtra(EXTRA_INITIAL_EPUB_LOCATOR_JSON)
        })
    }

    private fun attachViewer(uri: Uri) {
        val fragment = PixelPdfViewerFragment().apply {
            initialPageIndex = currentPageIndex
            onVisiblePageChanged = { pageIndex ->
                if (currentPageIndex != pageIndex) {
                    currentPageIndex = pageIndex
                    updatePdfStatus()
                    saveRecentBook()
                }
            }
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

    private fun toggleDrawer() {
        drawerExpanded = !drawerExpanded
        toolsDrawer.visibility = if (drawerExpanded) View.VISIBLE else View.GONE
        updateControls()
    }

    private fun toggleThemeMode() {
        themeMode = if (themeMode == ReaderThemeMode.DARK) ReaderThemeMode.LIGHT else ReaderThemeMode.DARK
        prefs.edit().putString("themeMode", themeMode.name).apply()
        applyTheme()
    }

    private fun applyTheme() {
        applyPixelReadSystemBars(themeMode)
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
        drawerToggleButton.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        drawerToggleButton.setImageResource(R.drawable.ic_reader_drawer_toggle)
        drawerToggleButton.setColorFilter(tokens.primary.toInt())
        drawerToggleButton.rotation = if (drawerExpanded) 180f else 0f
        drawerToggleButton.contentDescription = if (drawerExpanded) "HIDE TOOLS" else "SHOW TOOLS"
        fontIcon.setImageResource(R.drawable.ic_reader_font_size)
        fontIcon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        fontIcon.setColorFilter(tokens.text.toInt())
        fontIcon.alpha = 1f
        val disabledTint = ColorStateList.valueOf(tokens.outline.toInt())
        fontSlider.progressTintList = disabledTint
        fontSlider.thumbTintList = disabledTint
        fontSlider.progressBackgroundTintList = disabledTint
        openBookButton.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        openBookButton.setImageResource(R.drawable.ic_reader_open_book)
        openBookButton.setColorFilter(tokens.primary.toInt())
        openBookButton.alpha = 1f
        openBookButton.contentDescription = "OPEN BOOK"
        themeToggleButton.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        themeToggleButton.setImageResource(
            if (themeMode == ReaderThemeMode.DARK) R.drawable.ic_reader_moon else R.drawable.ic_reader_sun,
        )
        themeToggleButton.setColorFilter(tokens.primary.toInt())
        themeToggleButton.alpha = 1f
        themeToggleButton.contentDescription = "TOGGLE THEME"
    }

    private fun showError(message: String) {
        statusText.text = message
        statusBadge.text = "ERROR"
    }

    private fun updatePdfStatus() {
        if (pageCount > 0) {
            statusBadge.text = pdfPageStatusText(currentPageIndex, pageCount)
        }
    }

    private fun saveRecentBook() {
        val uri = openedUri ?: return
        if (currentTitle.isBlank()) return
        prefs.upsertRecentBook(
            RecentBookEntry(
                uri = uri.toString(),
                title = currentTitle,
                documentType = ReaderDocumentType.PDF,
                lastOpenedAt = System.currentTimeMillis(),
                progressLabel = if (pageCount > 0) pdfPageStatusText(currentPageIndex, pageCount) else "PDF",
                pdfPageIndex = currentPageIndex.coerceAtLeast(0),
            ),
        )
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

    private fun brandFooter(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(label("PIXELREAD", 10f, bold = true), LinearLayout.LayoutParams(-2, dp(14)))
            addView(View(this@PdfViewerActivity), LinearLayout.LayoutParams(dp(12), dp(1)))
            creditLabel = label("CODEX & XUE", 8f, bold = true)
            addView(creditLabel, LinearLayout.LayoutParams(-2, dp(14)))
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
            setPadding(0, 0, 0, 0)
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

    private fun installRootInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarsTopInset = systemBars.top
            systemBarsBottomInset = systemBars.bottom
            updateRootPadding()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun updateRootPadding() {
        val horizontalPadding = screenPadding()
        root.setPadding(
            horizontalPadding,
            systemBarsTopInset + contentTopPadding(),
            horizontalPadding,
            systemBarsBottomInset + contentBottomPadding(),
        )
    }

    private fun screenPadding(): Int =
        dp(16)

    private fun contentTopPadding(): Int =
        dp(10)

    private fun contentBottomPadding(): Int =
        dp(0)

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

    private companion object {
        const val PdfTag = "pixelread-pdf-reader"
        const val PdfLogTag = "PixelRead"
        const val StateOpenedUri = "pixelread.opened_uri"
        const val StatePdfPageIndex = "pixelread.pdf_page_index"
        const val CompactTopBarWidthDp = 600
        const val DisabledControlAlpha = 0.45f
    }
}
