package com.codexue.pixelread

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
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
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl

@OptIn(ExperimentalReadiumApi::class)
class EpubReadiumActivity :
    FragmentActivity(),
    EpubNavigatorFragment.Listener,
    EpubNavigatorFragment.PaginationListener {

    private lateinit var root: LinearLayout
    private lateinit var statusPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusBadge: TextView
    private lateinit var drawerToggleButton: ImageButton
    private lateinit var toolsDrawer: LinearLayout
    private lateinit var openBookButton: ImageButton
    private lateinit var themeToggleButton: ImageButton
    private lateinit var fontIcon: ImageView
    private lateinit var fontSlider: SeekBar
    private lateinit var readerShell: FrameLayout
    private lateinit var navigatorContainer: FrameLayout
    private lateinit var creditLabel: TextView

    private val prefs by lazy { getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE) }
    private var document: PixelReadiumEpubDocument? = null
    private var openedUri: Uri? = null
    private var themeMode: ReaderThemeMode = ReaderThemeMode.DARK
    private var fontIndex: Int = DEFAULT_EPUB_FONT_INDEX
    private var pageIndex: Int = 0
    private var pageCount: Int = 1
    private var chapterIndex: Int = 0
    private var drawerExpanded: Boolean = false
    private var currentTitle: String = "SELECTED EPUB"
    private var latestLocator: Locator? = null
    private var pendingInitialLocator: Locator? = null
    private var systemBarsTopInset: Int = 0
    private var systemBarsBottomInset: Int = 0
    private val textViews = mutableListOf<TextView>()
    private val dividers = mutableListOf<View>()
    private val readerBounds = Rect()

    private val openBookLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openSelectedBook(uri)
        }

    private val pageTapDetector by lazy {
        GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    handleReaderSideTap(event.rawX, event.rawY)
                    return false
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPrefs()
        pendingInitialLocator = savedInstanceState?.getString(StateEpubLocatorJson)?.let(::locatorFromJson)
            ?: intent.getStringExtra(EXTRA_INITIAL_EPUB_LOCATOR_JSON)?.let(::locatorFromJson)
        buildLayout()
        applyTheme()
        val uri = savedInstanceState?.getString(StateOpenedUri)?.let(Uri::parse) ?: intent?.data
        if (uri == null) {
            showError("CAN'T OPEN EPUB")
        } else {
            open(uri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        openedUri?.let { outState.putString(StateOpenedUri, it.toString()) }
        latestLocator?.toJSON()?.toString()?.let { outState.putString(StateEpubLocatorJson, it) }
    }

    override fun onDestroy() {
        document?.close()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pageTapDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun loadPrefs() {
        themeMode = enumValueOrDefault(prefs.getString("themeMode", null), ReaderThemeMode.DARK)
        val savedFontScaleVersion = prefs.getInt("epubFontScaleVersion", 0)
        fontIndex = if (savedFontScaleVersion < EPUB_FONT_SCALE_VERSION) {
            DEFAULT_EPUB_FONT_INDEX
        } else {
            clampEpubFontIndex(prefs.getInt("epubFontIndex", DEFAULT_EPUB_FONT_INDEX))
        }
        if (savedFontScaleVersion < EPUB_FONT_SCALE_VERSION) {
            persist()
        }
    }

    private fun persist() {
        prefs.edit()
            .putString("themeMode", themeMode.name)
            .putInt("epubFontIndex", fontIndex)
            .putInt("epubFontScaleVersion", EPUB_FONT_SCALE_VERSION)
            .apply()
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
        statusText = label("OPENING EPUB", 14f, bold = true)
        statusText.setPadding(dp(4), 0, 0, 0)
        statusBadge = badge("LOADING")
        drawerToggleButton = iconButton { toggleDrawer() }
        openBookButton = iconButton { openBookLauncher.launch(BOOK_OPEN_MIME_TYPES) }
        themeToggleButton = iconButton { toggleThemeMode() }
        fontIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = "FONT SIZE"
        }
        fontSlider = SeekBar(this).apply {
            max = EPUB_FONT_SIZES_SP.lastIndex
            progress = fontIndex
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setFontIndex(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
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

        readerShell = FrameLayout(this)
        navigatorContainer = FrameLayout(this).apply { id = View.generateViewId() }
        readerShell.addView(
            navigatorContainer,
            FrameLayout.LayoutParams(readerContentWidth(), -1, Gravity.CENTER),
        )
        root.addView(readerShell, LinearLayout.LayoutParams(-1, 0, 1f).withTopMargin(dp(8)))

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
            ReaderDocumentType.EPUB -> {
                persistReadPermission(uri)
                pageIndex = 0
                pageCount = 1
                chapterIndex = 0
                latestLocator = null
                pendingInitialLocator = null
                open(uri)
            }
            ReaderDocumentType.PDF -> {
                persistReadPermission(uri)
                startActivity(Intent(this, PdfViewerActivity::class.java).apply {
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
        lifecycleScope.launch {
            try {
                currentTitle = bookTitleWithoutExtension(contentResolver.displayName(uri) ?: "SELECTED.EPUB")
                statusText.text = currentTitle
                statusBadge.text = "LOADING"
                document?.close()
                document = null
                val opened = PixelReadiumEpubDocument.open(this@EpubReadiumActivity, uri)
                document = opened
                currentTitle = bookTitleWithoutExtension(opened.fileName)
                statusText.text = currentTitle
                attachNavigator()
                statusBadge.text = chapterStatusText(0, opened.chapterCount)
                saveRecentBook()
                updateNavLabel()
            } catch (throwable: Throwable) {
                showError(userFacingEpubError(throwable))
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

    private fun toggleThemeMode() {
        themeMode = if (themeMode == ReaderThemeMode.DARK) ReaderThemeMode.LIGHT else ReaderThemeMode.DARK
        persist()
        applyTheme()
        applyReaderSettings()
    }

    private fun setFontIndex(index: Int) {
        fontIndex = clampEpubFontIndex(index)
        if (fontSlider.progress != fontIndex) {
            fontSlider.progress = fontIndex
        }
        persist()
        applyReaderSettings()
    }

    private fun applyReaderSettings() {
        updateControls()
        navigator()?.submitPreferences(readiumPreferences())
    }

    private fun toggleDrawer() {
        drawerExpanded = !drawerExpanded
        toolsDrawer.visibility = if (drawerExpanded) View.VISIBLE else View.GONE
        updateControls()
    }

    private fun attachNavigator() {
        val opened = document ?: return
        val initialLocator = pendingInitialLocator
        pendingInitialLocator = null
        supportFragmentManager.fragmentFactory = opened.navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = readiumPreferences(),
            listener = this@EpubReadiumActivity,
            paginationListener = this@EpubReadiumActivity,
        )
        supportFragmentManager.beginTransaction()
            .replace(navigatorContainer.id, EpubNavigatorFragment::class.java, null, ReadiumTag)
            .commitNowAllowingStateLoss()
    }

    private fun navigator(): EpubNavigatorFragment? =
        supportFragmentManager.findFragmentByTag(ReadiumTag) as? EpubNavigatorFragment

    private fun readiumPreferences(): EpubPreferences {
        val tokens = readerThemeTokens(themeMode)
        return EpubPreferences(
            backgroundColor = ReadiumColor(tokens.pageBackground.toInt()),
            columnCount = ColumnCount.ONE,
            fontSize = readiumFontScaleForEpubIndex(fontIndex),
            pageMargins = 1.0,
            publisherStyles = true,
            scroll = false,
            spread = Spread.NEVER,
            textColor = ReadiumColor(tokens.text.toInt()),
            theme = if (themeMode == ReaderThemeMode.DARK) ReadiumTheme.DARK else ReadiumTheme.LIGHT,
        )
    }

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        this.pageIndex = pageIndex.coerceAtLeast(0)
        pageCount = totalPages.coerceAtLeast(1)
        chapterIndex = document?.chapterIndexFor(locator) ?: 0
        latestLocator = locator
        updateNavLabel()
        saveRecentBook(locator)
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit

    private fun updateNavLabel() {
        val chapterCount = document?.chapterCount ?: 1
        statusBadge.text = epubPageStatusText(chapterIndex, chapterCount, pageIndex, pageCount)
    }

    private fun applyTheme() {
        applyPixelReadSystemBars(themeMode)
        val tokens = readerThemeTokens(themeMode)
        val background = tokens.background.toInt()
        val panel = tokens.panel.toInt()
        root.setBackgroundColor(background)
        textViews.forEach { it.setTextColor(tokens.text.toInt()) }
        creditLabel.setTextColor(tokens.primary.toInt())
        statusPanel.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        dividers.forEach { it.setBackgroundColor(tokens.outline.toInt()) }
        readerShell.setBackgroundColor(background)
        navigatorContainer.setBackgroundColor(panel)
        navigatorContainer.foreground = pixelBorder(tokens.outline.toInt(), strokeDp = 2)
        updateControls()
    }

    private fun updateControls() {
        val tokens = readerThemeTokens(themeMode)
        drawerToggleButton.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        drawerToggleButton.setImageResource(R.drawable.ic_reader_drawer_toggle)
        drawerToggleButton.setColorFilter(tokens.primary.toInt())
        drawerToggleButton.rotation = if (drawerExpanded) 180f else 0f
        drawerToggleButton.contentDescription = if (drawerExpanded) "HIDE TOOLS" else "SHOW TOOLS"
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
        fontIcon.setImageResource(R.drawable.ic_reader_font_size)
        fontIcon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        fontIcon.setColorFilter(tokens.text.toInt())
        fontIcon.alpha = 1f
        val progressTint = ColorStateList.valueOf(tokens.primary.toInt())
        val trackTint = ColorStateList.valueOf(tokens.outline.toInt())
        fontSlider.progressTintList = progressTint
        fontSlider.thumbTintList = progressTint
        fontSlider.progressBackgroundTintList = trackTint
    }

    private fun showError(message: String) {
        statusText.text = message
        statusBadge.text = "ERROR"
    }

    private fun saveRecentBook(locator: Locator? = latestLocator) {
        val uri = openedUri ?: return
        if (currentTitle.isBlank()) return
        prefs.upsertRecentBook(
            RecentBookEntry(
                uri = uri.toString(),
                title = currentTitle,
                documentType = ReaderDocumentType.EPUB,
                lastOpenedAt = System.currentTimeMillis(),
                progressLabel = epubPageStatusText(chapterIndex, document?.chapterCount ?: 1, pageIndex, pageCount),
                epubLocatorJson = locator?.toJSON()?.toString(),
            ),
        )
    }

    private fun locatorFromJson(json: String): Locator? =
        runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()

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
            addView(View(this@EpubReadiumActivity), LinearLayout.LayoutParams(dp(12), dp(1)))
            creditLabel = label("CODEX & XUE", 8f, bold = true)
            addView(creditLabel, LinearLayout.LayoutParams(-2, dp(14)))
        }

    private fun badge(text: String): TextView =
        label(text, 11f, bold = true).apply {
            gravity = Gravity.CENTER
        }

    private fun divider(): View =
        View(this).apply {
            dividers += this
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

    private fun readerContentWidth(): Int =
        ViewGroup.LayoutParams.MATCH_PARENT

    private fun handleReaderSideTap(rawX: Float, rawY: Float) {
        if (!navigatorContainer.getGlobalVisibleRect(readerBounds)) return
        if (rawY < readerBounds.top || rawY > readerBounds.bottom) return
        val localX = rawX - readerBounds.left
        val leftLimit = readerBounds.width() * SideTapRatio
        val rightLimit = readerBounds.width() * (1f - SideTapRatio)
        when {
            localX <= leftLimit -> navigator()?.goBackward(animated = true)
            localX >= rightLimit -> navigator()?.goForward(animated = true)
        }
    }

    private fun statusBadgeWidth(): Int =
        if (resources.configuration.screenWidthDp < CompactTopBarWidthDp) dp(112) else dp(220)

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
        const val ReadiumTag = "pixelread-epub-reader"
        const val StateOpenedUri = "pixelread.opened_uri"
        const val StateEpubLocatorJson = "pixelread.epub_locator_json"
        const val CompactTopBarWidthDp = 720
        const val SideTapRatio = 0.28f
        const val DisabledControlAlpha = 0.45f
    }
}
