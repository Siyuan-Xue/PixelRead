package com.codexue.pixelread

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    private lateinit var readerTools: LinearLayout
    private lateinit var themeToggleButton: ImageButton
    private lateinit var fontLabel: TextView
    private lateinit var fontSlider: SeekBar
    private lateinit var navLabel: TextView
    private lateinit var readerShell: FrameLayout
    private lateinit var navigatorContainer: FrameLayout
    private lateinit var creditLabel: TextView

    private val prefs by lazy { getSharedPreferences(ReaderPrefsName, Context.MODE_PRIVATE) }
    private var document: PixelReadiumEpubDocument? = null
    private var themeMode: ReaderThemeMode = ReaderThemeMode.DARK
    private var fontIndex: Int = 2
    private var pageIndex: Int = 0
    private var pageCount: Int = 1
    private var chapterIndex: Int = 0
    private val textViews = mutableListOf<TextView>()
    private val buttons = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        loadPrefs()
        buildLayout()
        applyTheme()
        val uri = intent?.data
        if (uri == null) {
            showError("CAN'T OPEN EPUB")
        } else {
            open(uri)
        }
    }

    override fun onDestroy() {
        document?.close()
        super.onDestroy()
    }

    private fun loadPrefs() {
        themeMode = enumValueOrDefault(prefs.getString("themeMode", null), ReaderThemeMode.DARK)
        fontIndex = clampEpubFontIndex(prefs.getInt("epubFontIndex", 2))
    }

    private fun persist() {
        prefs.edit()
            .putString("themeMode", themeMode.name)
            .putInt("epubFontIndex", fontIndex)
            .apply()
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
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        statusText = label("OPENING EPUB", 12f, bold = true)
        statusBadge = badge("LOADING")
        statusPanel.addView(statusText, LinearLayout.LayoutParams(0, dp(36), 1f))
        statusPanel.addView(statusBadge, LinearLayout.LayoutParams(dp(220), dp(36)))
        statusPanel.addView(button("BACK") { finish() }, LinearLayout.LayoutParams(dp(120), dp(36)).withLeftMargin(dp(6)))
        root.addView(statusPanel, LinearLayout.LayoutParams(-1, dp(58)).withTopMargin(dp(8)))

        readerTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        themeToggleButton = iconButton { toggleThemeMode() }
        readerTools.addView(themeToggleButton, LinearLayout.LayoutParams(dp(44), dp(36)))
        fontLabel = label("FONT ${epubFontSizeSp(fontIndex)}SP", 11f, bold = true)
        readerTools.addView(fontLabel, LinearLayout.LayoutParams(dp(96), dp(36)).withLeftMargin(dp(10)))
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
        readerTools.addView(fontSlider, LinearLayout.LayoutParams(dp(220), dp(36)).withLeftMargin(dp(4)))
        root.addView(readerTools, LinearLayout.LayoutParams(-1, dp(46)).withTopMargin(dp(8)))

        readerShell = FrameLayout(this)
        navigatorContainer = FrameLayout(this).apply { id = View.generateViewId() }
        readerShell.addView(
            navigatorContainer,
            FrameLayout.LayoutParams(readerContentWidth(), -1, Gravity.CENTER),
        )
        root.addView(readerShell, LinearLayout.LayoutParams(-1, 0, 1f).withTopMargin(dp(8)))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        nav.addView(button("PREV") { navigator()?.goBackward(animated = true) }, LinearLayout.LayoutParams(0, dp(40), 1f))
        navLabel = badge("EPUB")
        nav.addView(navLabel, LinearLayout.LayoutParams(0, dp(40), 1.6f).withLeftMargin(dp(6)))
        nav.addView(button("NEXT") { navigator()?.goForward(animated = true) }, LinearLayout.LayoutParams(0, dp(40), 1f).withLeftMargin(dp(6)))
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(56)).withTopMargin(dp(8)))

        setContentView(root)
    }

    private fun open(uri: Uri) {
        lifecycleScope.launch {
            try {
                val opened = PixelReadiumEpubDocument.open(this@EpubReadiumActivity, uri)
                document = opened
                statusText.text = opened.fileName
                attachNavigator()
                statusBadge.text = chapterStatusText(0, opened.chapterCount)
                updateNavLabel()
            } catch (throwable: Throwable) {
                showError(userFacingEpubError(throwable))
            }
        }
    }

    private fun toggleThemeMode() {
        themeMode = if (themeMode == ReaderThemeMode.DARK) ReaderThemeMode.LIGHT else ReaderThemeMode.DARK
        persist()
        applyTheme()
        applyReaderSettings()
    }

    private fun setFontIndex(index: Int) {
        fontIndex = clampEpubFontIndex(index)
        fontLabel.text = "FONT ${epubFontSizeSp(fontIndex)}SP"
        persist()
        applyReaderSettings()
    }

    private fun applyReaderSettings() {
        updateControls()
        navigator()?.submitPreferences(readiumPreferences())
    }

    private fun attachNavigator() {
        val opened = document ?: return
        supportFragmentManager.fragmentFactory = opened.navigatorFactory.createFragmentFactory(
            initialLocator = null,
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
        updateNavLabel()
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit

    private fun updateNavLabel() {
        val chapterCount = document?.chapterCount ?: 1
        navLabel.text = epubPageStatusText(chapterIndex, chapterCount, pageIndex, pageCount)
        statusBadge.text = navLabel.text
    }

    private fun applyTheme() {
        val tokens = readerThemeTokens(themeMode)
        val background = tokens.background.toInt()
        val panel = tokens.panel.toInt()
        root.setBackgroundColor(background)
        textViews.forEach { it.setTextColor(tokens.text.toInt()) }
        creditLabel.setTextColor(tokens.primary.toInt())
        statusPanel.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        readerShell.setBackgroundColor(background)
        navigatorContainer.setBackgroundColor(panel)
        navigatorContainer.foreground = pixelBorder(tokens.outline.toInt(), strokeDp = 2)
        readerTools.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        updateControls()
    }

    private fun updateControls() {
        val tokens = readerThemeTokens(themeMode)
        buttons.forEach { button ->
            button.setTextColor(tokens.text.toInt())
            button.background = pixelBackground(tokens.surface.toInt(), tokens.outline.toInt(), strokeDp = 2)
        }
        themeToggleButton.background = pixelBackground(tokens.panel.toInt(), tokens.outline.toInt(), strokeDp = 2)
        themeToggleButton.setImageResource(
            if (themeMode == ReaderThemeMode.DARK) R.drawable.ic_reader_moon else R.drawable.ic_reader_sun,
        )
        themeToggleButton.setColorFilter(tokens.primary.toInt())
        themeToggleButton.contentDescription = "TOGGLE THEME"
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

    private fun label(text: String, sp: Float, bold: Boolean = false, alignEnd: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            typeface = Typeface.MONOSPACE
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = if (alignEnd) Gravity.CENTER_VERTICAL or Gravity.END else Gravity.CENTER_VERTICAL
            maxLines = 1
            textViews += this
        }

    private fun badge(text: String): TextView =
        label(text, 11f, bold = true).apply {
            gravity = Gravity.CENTER
        }

    private fun button(text: String, onClick: () -> Unit): TextView =
        badge(text).apply {
            isClickable = true
            buttons += this
            setOnClickListener { onClick() }
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

    private fun readerContentWidth(): Int =
        if (resources.configuration.screenWidthDp >= TabletWidthDp) {
            dp(TabletReaderMaxWidthDp)
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }

    private fun LinearLayout.LayoutParams.withTopMargin(value: Int) = apply { topMargin = value }
    private fun LinearLayout.LayoutParams.withLeftMargin(value: Int) = apply { leftMargin = value }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default

    private companion object {
        const val ReaderPrefsName = "pixelread_reader"
        const val ReadiumTag = "pixelread-epub-reader"
        const val TabletWidthDp = 900
        const val TabletReaderMaxWidthDp = 860
    }
}
