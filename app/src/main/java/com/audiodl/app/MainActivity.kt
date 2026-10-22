package com.audiodl.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    lateinit var player: AudioPlayer
        private set

    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    private val tabs = mutableListOf<LinearLayout>()
    private lateinit var downloadScreen: DownloadScreen
    private lateinit var libraryScreen: LibraryScreen
    private var editor: EditorScreen? = null

    private val urlRegex = Regex("""https?://\S+""")

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (::libraryScreen.isInitialized) libraryScreen.refresh()
        }

    private val editorBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeEditor()
    }

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT

        player = AudioPlayer(this)
        downloadScreen = DownloadScreen(this)
        libraryScreen = LibraryScreen(this)

        root = FrameLayout(this).apply { background = appBackground() }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = FrameLayout(this)
        column.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        tabBar = buildTabBar()
        column.addView(tabBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            setMargins(dp(20), dp(6), dp(20), dp(14))
        })
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        // Android 15 dibuja de borde a borde: respetamos barras, notch y teclado.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onBackPressedDispatcher.addCallback(this, editorBack)

        showTab(0)
        if (!App.initOk) downloadScreen.showInitError()
        handleShareIntent(intent)
        if (savedInstanceState == null) showIntro()
    }

    // ------------------------------------------------------------------
    // Presentación al abrir la app

    private var intro: FrameLayout? = null

    private fun showIntro() {
        val overlay = FrameLayout(this).apply {
            background = appBackground()
            isClickable = true
            setOnClickListener { dismissIntro() }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
        }
        val logoWidth = (resources.displayMetrics.widthPixels * 0.84f).toInt()
        box.addView(logo, LinearLayout.LayoutParams(logoWidth, WRAP_CONTENT))
        val credit = TextView(this).apply {
            text = "App desarrollada por un tío orgulloso"
            textSize = 15f
            setTextColor(Palette.TEXT_SOFT)
            gravity = Gravity.CENTER
            letterSpacing = 0.02f
            alpha = 0f
            translationY = dpf(12f)
        }
        box.addView(credit, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(28) })
        overlay.addView(box, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
        root.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        intro = overlay

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(800).setInterpolator(DecelerateInterpolator()).start()
        credit.animate().alpha(1f).translationY(0f)
            .setStartDelay(550).setDuration(600).start()
        overlay.postDelayed({ dismissIntro() }, 2800)
    }

    private fun dismissIntro() {
        val o = intro ?: return
        intro = null
        o.animate().alpha(0f).setDuration(400).withEndAction { root.removeView(o) }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        libraryScreen.onHidden()
        editor?.release()
        player.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------

    private fun buildTabBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(Palette.SURFACE, 100, Palette.STROKE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        listOf("Descargar" to Glyph.DOWNLOAD, "Biblioteca" to Glyph.MUSIC).forEachIndexed { i, (label, glyph) ->
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = dp(50)
                isClickable = true
                setOnClickListener { showTab(i) }
            }
            tab.addView(ImageView(this).apply {
                setImageDrawable(GlyphDrawable(glyph, Palette.TEXT))
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            tab.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Palette.TEXT)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            bar.addView(tab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            tabs += tab
        }
        return bar
    }

    fun showTab(index: Int) {
        tabs.forEachIndexed { i, t ->
            t.background = if (i == index) gradient() else null
            t.alpha = if (i == index) 1f else 0.65f
        }
        content.removeAllViews()
        val v = if (index == 0) downloadScreen.view else libraryScreen.view
        content.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (index == 1) libraryScreen.onShown() else libraryScreen.onHidden()
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = urlRegex.find(text)?.value ?: return
        if (editor != null) closeEditor()
        showTab(0)
        downloadScreen.setUrl(url)
    }

    // ------------------------------------------------------------------

    fun playTrack(track: Track) {
        showTab(1)
        libraryScreen.play(track)
    }

    fun openEditor(track: Track) {
        player.pause()
        editor?.let { closeEditor() }
        val e = EditorScreen(this, track)
        editor = e
        root.addView(e.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        tabBar.visibility = View.INVISIBLE
        editorBack.isEnabled = true
    }

    fun closeEditor() {
        val e = editor ?: return
        e.release()
        root.removeView(e.view)
        editor = null
        tabBar.visibility = View.VISIBLE
        editorBack.isEnabled = false
        libraryScreen.refresh()
    }

    fun onTrackSaved() {
        libraryScreen.refresh()
    }

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    fun requestAudioPermission() {
        permissionLauncher.launch(audioPermission)
    }
}
