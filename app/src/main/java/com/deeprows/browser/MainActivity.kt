package com.deeprows.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.webkit.CookieManager
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.net.VpnService
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.ProgressBar
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject

data class HomeSite(
    val name: String,
    val url: String
)

data class HomeSubCategory(
    val title: String,
    val sites: List<HomeSite>
)

data class HomeCategory(
    val title: String,
    val subCategories: List<HomeSubCategory>
)

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var homePage: ScrollView
    private lateinit var settingsPage: ScrollView

    private lateinit var addressBar: android.widget.EditText
    private lateinit var loadingBar: android.widget.ProgressBar

    private lateinit var dataSavingSwitch: Switch
    private lateinit var adBlockingSwitch: Switch

    private lateinit var vpnSwitch: Switch
    private lateinit var vpnLocationText: TextView
    private val vpnCountries by lazy { VpnCountryRepository.allCountries() }

    private val preferences by lazy {
        getSharedPreferences(
            "deeprows_browser",
            MODE_PRIVATE
        )
    }

    private val newsRepository =
        NewsRepository()

    // Google Trends translation state
    private val trendTitleViews =
        mutableListOf<Pair<TextView, NewsArticle>>()

    private var trendsTranslated = false

    // Downloads state
    private val uiHandler = Handler(Looper.getMainLooper())
    private var downloadReceiver: BroadcastReceiver? = null
    private var pendingAfterPermission: (() -> Unit)? = null

    // Current WebView mode (mobile / desktop)
    private var desktopMode = false

    // =========================================================
    // OPEN TABS
    // =========================================================

    data class BrowserTab(
        val id: Int,
        var title: String,
        var url: String
    )

    private val openTabs =
        mutableListOf<BrowserTab>()

    private var activeTabId = 0

    private var nextTabId = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webView =
            findViewById(R.id.webView)

        homePage =
            findViewById(R.id.homePage)

        settingsPage =
            findViewById(R.id.settingsPage)

        addressBar =
            findViewById(R.id.addressBar)

        loadingBar =
            findViewById(R.id.loadingBar)

        dataSavingSwitch =
            findViewById(R.id.dataSavingSwitch)

        adBlockingSwitch =
            findViewById(R.id.adBlockingSwitch)

        vpnSwitch = findViewById(R.id.vpnSwitch)
        vpnLocationText = findViewById(R.id.vpnLocationText)

        setupWebView()
        setupDownloads()
        setupControls()
        setupDynamicHomepage()
        setupSettings()

        setupTrendNotifications()
        setupUpdateEnquiryButton()

        applyAppTheme()

        showHomePage()

        loadLatestNews()
        loadSportNews()
        loadGoogleTrends()
        setupTrendsTranslate()

        hideSystemNavigationBar()

        handleNotificationIntent(intent)
    }

    override fun onDestroy() {
        downloadReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        downloadReceiver = null
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun setupDynamicHomepage() {

        val container =
            findViewById<LinearLayout>(R.id.siteCategoriesContainer)

        container.removeAllViews()

        val categories = listOf(

            HomeCategory(
                "🤖 AI TOOLS",
                listOf(

                    HomeSubCategory(
                        "🎬 AI VIDEO",
                        listOf(
                            HomeSite("Kling AI", "https://klingai.com/"),
                            HomeSite("Hailuo AI", "https://hailuoai.video/"),
                            HomeSite("Pika", "https://pika.art/"),
                            HomeSite("Runway", "https://runwayml.com/"),
                            HomeSite("Luma Dream Machine", "https://lumalabs.ai/dream-machine"),
                            HomeSite("PixVerse", "https://pixverse.ai/"),
                            HomeSite("Vidu", "https://www.vidu.com/"),
                            HomeSite("CapCut", "https://www.capcut.com/"),
                            HomeSite("Canva", "https://www.canva.com/"),
                            HomeSite("Krea", "https://www.krea.ai/")
                        )
                    ),

                    HomeSubCategory(
                        "🖼️ AI IMAGE",
                        listOf(
                            HomeSite("Microsoft Designer", "https://designer.microsoft.com/"),
                            HomeSite("Leonardo AI", "https://leonardo.ai/"),
                            HomeSite("Ideogram", "https://ideogram.ai/"),
                            HomeSite("Adobe Firefly", "https://firefly.adobe.com/"),
                            HomeSite("Google Gemini", "https://gemini.google.com/"),
                            HomeSite("Canva AI", "https://www.canva.com/ai-image-generator/"),
                            HomeSite("Playground AI", "https://playground.com/"),
                            HomeSite("Krea AI", "https://www.krea.ai/"),
                            HomeSite("Freepik AI", "https://www.freepik.com/ai/image-generator"),
                            HomeSite("Craiyon", "https://www.craiyon.com/")
                        )
                    ),

                    HomeSubCategory(
                        "🎵 AI AUDIO / MUSIC / VOICE",
                        listOf(
                            HomeSite("ElevenLabs", "https://elevenlabs.io/"),
                            HomeSite("Suno", "https://suno.com/"),
                            HomeSite("Udio", "https://udio.com/"),
                            HomeSite("Murf AI", "https://murf.ai/"),
                            HomeSite("PlayHT", "https://play.ht/"),
                            HomeSite("Speechify", "https://speechify.com/"),
                            HomeSite("AIVA", "https://www.aiva.ai/"),
                            HomeSite("Soundraw", "https://soundraw.io/"),
                            HomeSite("Adobe Podcast", "https://podcast.adobe.com/"),
                            HomeSite("TTSMaker", "https://ttsmaker.com/")
                        )
                    )
                )
            ),

            HomeCategory(
                "🎓 EDUCATION",
                listOf(

                    HomeSubCategory(
                        "📚 FREE ONLINE COURSES",
                        listOf(
                            HomeSite("MIT OpenCourseWare", "https://ocw.mit.edu/"),
                            HomeSite("OpenLearn", "https://www.open.edu/openlearn/"),
                            HomeSite("edX", "https://www.edx.org/"),
                            HomeSite("Coursera", "https://www.coursera.org/"),
                            HomeSite("Open Yale Courses", "https://oyc.yale.edu/"),
                            HomeSite("NPTEL", "https://nptel.ac.in/")
                        )
                    ),

                    HomeSubCategory(
                        "🎓 SCHOLARSHIPS & SPONSORSHIPS",
                        listOf(
                            HomeSite("Chevening", "https://www.chevening.org/"),
                            HomeSite("Erasmus+", "https://erasmus-plus.ec.europa.eu/"),
                            HomeSite("DAAD", "https://www.daad.de/en/studying-in-germany/scholarships/"),
                            HomeSite("Commonwealth", "https://cscuk.fcdo.gov.uk/scholarships-filter-search/"),
                            HomeSite("Mastercard Foundation", "https://mastercardfdn.org/all/scholars/"),
                            HomeSite("Swedish Institute", "https://si.se/en/apply/scholarships/"),
                            HomeSite("Opportunity Desk", "https://opportunitydesk.org/"),
                            HomeSite("Scholarship Positions", "https://www.scholarshippositions.com/"),
                            HomeSite("Studyportals", "https://www.mastersportal.com/scholarships/"),
                            HomeSite("African Union", "https://au.int/")
                        )
                    )
                )
            ),

            HomeCategory(
                "JOBS & CAREERS",
                listOf(
                    HomeSubCategory(
                        "💼",
                        listOf(
                            HomeSite("LinkedIn Jobs", "https://www.linkedin.com/jobs/"),
                            HomeSite("Indeed", "https://www.indeed.com/"),
                            HomeSite("Glassdoor", "https://www.glassdoor.com/"),
                            HomeSite("ZipRecruiter", "https://www.ziprecruiter.com/"),
                            HomeSite("Bayt", "https://www.bayt.com/"),
                            HomeSite("Jooble", "https://jooble.org/"),
                            HomeSite("Monster", "https://www.monster.com/"),
                            HomeSite("JobStreet", "https://www.jobstreet.com/"),
                            HomeSite("Wellfound", "https://wellfound.com/jobs")
                        )
                    )
                )
            ),

            HomeCategory(
                "🎬 ENTERTAINMENT",
                listOf(

                    HomeSubCategory(
                        "🎥 MOVIE",
                        listOf(
                            HomeSite("Deeprowss Movies", "https://deeprowss.com/"),
                            HomeSite("Netflix", "https://www.netflix.com/"),
                            HomeSite("TMDB", "https://www.themoviedb.org/"),
                            HomeSite("IMDb", "https://www.imdb.com/")
                        )
                    ),

                    HomeSubCategory(
                        "🎌 ANIME",
                        listOf(
                            HomeSite("Miruro", "https://www.miruro.tv/"),
                            HomeSite("AnimePahe", "https://animepahe.ru/"),
                            HomeSite("KickAssAnime", "https://kaa.to/")
                        )
                    ),

                    HomeSubCategory(
                        "🎨 CARTOONS",
                        listOf(
                            HomeSite("WatchCartoonOnline", "https://www.wco.tv/"),
                            HomeSite("SuperCartoons", "https://www.supercartoons.net/"),
                            HomeSite("Japanese Animated Film Classics", "https://animation.filmarchives.jp/")
                        )
                    ),

                    HomeSubCategory(
                        "🇰🇷 ASIAN / K-DRAMA",
                        listOf(
                            HomeSite("AsianCrush", "https://www.asiancrush.com/"),
                            HomeSite("OnDemandChina", "https://www.ondemandchina.com/"),
                            HomeSite("Einthusan", "https://einthusan.tv/")
                        )
                    ),

                    HomeSubCategory(
                        "🎞️ CLASSICS",
                        listOf(
                            HomeSite("Internet Archive", "https://archive.org/"),
                            HomeSite("WikiFlix", "https://wikiflix.toolforge.org/"),
                            HomeSite("NASA+", "https://plus.nasa.gov/")
                        )
                    )
                )
            ),

            HomeCategory(
                "SPORTS",
                listOf(

                    HomeSubCategory(
                        "⚽",
                        listOf(
                            HomeSite("ESPN", "https://www.espn.com/"),
                            HomeSite("BBC Sport", "https://www.bbc.com/sport"),
                            HomeSite("Sky Sports", "https://www.skysports.com/"),
                            HomeSite("Goal", "https://www.goal.com/"),
                            HomeSite("The Athletic", "https://www.nytimes.com/athletic/"),
                            HomeSite("CBS Sports", "https://www.cbssports.com/"),
                            HomeSite("FOX Sports", "https://www.foxsports.com/"),
                            HomeSite("Sporting News", "https://www.sportingnews.com/"),
                            HomeSite("Eurosport", "https://www.eurosport.com/"),
                            HomeSite("Sports Illustrated", "https://www.si.com/")
                        )
                    ),

                    HomeSubCategory(
                        "📼 LIVE FOOTBALL & REPLAYS",
                        listOf(
                            HomeSite("Deeprowss Sports", "https://deeprowss.com"),
                            HomeSite("Footballia", "https://footballia.online/"),
                            HomeSite("FullRaces", "https://fullraces.com/")
                        )
                    )
                )
            ),

            HomeCategory(
                "NEWS",
                listOf(
                    HomeSubCategory(
                        "📰",
                        listOf(
                            HomeSite("BBC News", "https://www.bbc.com/news"),
                            HomeSite("Reuters", "https://www.reuters.com/"),
                            HomeSite("AP News", "https://apnews.com/"),
                            HomeSite("CNN", "https://www.cnn.com/"),
                            HomeSite("Al Jazeera", "https://www.aljazeera.com/"),
                            HomeSite("The Guardian", "https://www.theguardian.com/international"),
                            HomeSite("New York Times", "https://www.nytimes.com/"),
                            HomeSite("Sky News", "https://news.sky.com/"),
                            HomeSite("France 24", "https://www.france24.com/en/"),
                            HomeSite("DW", "https://www.dw.com/en/")
                        )
                    )
                )
            ),

            HomeCategory(
                "✈️ TRAVEL, CARGO, & TOURS",
                listOf(
                    HomeSubCategory(
                        "🧳",
                        listOf(
                            HomeSite("Ballowconnect", "https://ballowconnect.com"),
                            HomeSite("Booking.com", "https://www.booking.com"),
                            HomeSite("Skyscanner", "https://www.skyscanner.net/"),
                            HomeSite("Kayak", "https://www.kayak.com"),
                            HomeSite("Hotels.com", "https://www.hotels.com"),
                            HomeSite("Wakanow", "https://www.wakanow.com/en-ng"),
                            HomeSite("Google Flights", "https://www.google.com/travel/flights")
                        )
                    )
                )
            ),

            HomeCategory(
                "📺 IPTV",
                listOf(

                    HomeSubCategory(
                        "🛠️ IPTV TOOLS",
                        listOf(
                            HomeSite("Awesome IPTV", "https://github.com/iptv-org/awesome-iptv"),
                            HomeSite("IPTV Playlists", "https://iptv-org.github.io/"),
                            HomeSite("M3Unator", "https://m3unator.com/"),
                            HomeSite("M3U4U", "https://m3u4u.com/"),
                            HomeSite("M3U8DL-RE", "https://github.com/nilaoda/N_m3u8DL-RE")
                        )
                    ),

                    HomeSubCategory(
                        "▶️ IPTV PLAYERS",
                        listOf(
                            HomeSite("IPTVnator", "https://github.com/4gray/iptvnator"),
                            HomeSite("ynoTV", "https://ynotv.com/"),
                            HomeSite("Open TV", "https://opentv.app/"),
                            HomeSite("LivePush", "https://livepush.io/"),
                            HomeSite("Jellyfin", "https://jellyfin.org/")
                        )
                    )
                )
            ),

            HomeCategory(
                "ANDROID TV APPS",
                listOf(
                    HomeSubCategory(
                        "📺",
                        listOf(
                            HomeSite("SmartTube", "https://github.com/yuliskov/SmartTube"),
                            HomeSite("TiviMate", "https://tivimate.com/"),
                            HomeSite("Downloader", "https://www.aftvnews.com/downloader/"),
                            HomeSite("CloudStream", "https://cloudstream3.com/"),
                            HomeSite("Nova Video Player", "https://github.com/nova-video-player/aos-AVP")
                        )
                    )
                )
            ),

            HomeCategory(
                "SOCIAL MEDIA",
                listOf(
                    HomeSubCategory(
                        "📲",
                        listOf(
                            HomeSite("Facebook", "https://www.facebook.com/"),
                            HomeSite("TikTok", "https://www.tiktok.com/"),
                            HomeSite("YouTube", "https://www.youtube.com/"),
                            HomeSite("X", "https://x.com/"),
                            HomeSite("Dailymotion", "https://www.dailymotion.com/"),
                            HomeSite("Nairaland", "https://www.nairaland.com/")
                        )
                    )
                )
            ),
            HomeCategory(
                "MESSAGING",
                listOf(
                    HomeSubCategory(
                        "💬",
                        listOf(
                            HomeSite("WhatsApp", "https://web.whatsapp.com/"),
                            HomeSite("Snapchat", "https://www.snapchat.com/"),
                            HomeSite("Telegram", "https://web.telegram.org/")
                        )
                    )
                )
            )
        )

        categories.forEach { category ->

            addMainCategoryHeader(
                container,
                category.title
            )

            category.subCategories.forEach { subCategory ->

                addSubCategoryHeader(
                    container,
                    subCategory.title
                )

                addSiteGrid(
                    container,
                    subCategory.sites
                )
            }
        }
    }

    private fun addMainCategoryHeader(
        container: LinearLayout,
        title: String
    ) {
        val titleView = TextView(this)

        titleView.text = title
        titleView.tag = "main_header"
        titleView.textSize = 12f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.letterSpacing = 0.04f
        titleView.includeFontPadding = false
        titleView.setTextColor(getThemeTextColor())

        titleView.setPadding(
            dp(6),
            dp(10),
            dp(6),
            dp(3)
        )

        container.addView(titleView)
    }

    private fun addSubCategoryHeader(
        container: LinearLayout,
        title: String
    ) {
        val titleView = TextView(this)

        titleView.text = title
        titleView.tag = "sub_header"
        titleView.textSize = 10f
        titleView.setTypeface(null, Typeface.BOLD)
        titleView.includeFontPadding = false
        titleView.setTextColor(getThemeMutedColor())

        titleView.setPadding(
            dp(6),
            dp(3),
            dp(6),
            dp(3)
        )

        container.addView(titleView)
    }

    // Compact website cards in a 4-column grid that fits the screen.
    private fun addSiteGrid(
        container: LinearLayout,
        sites: List<HomeSite>
    ) {

        val grid = GridLayout(this).apply {
            columnCount = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, dp(2))
        }

        sites.forEach { site ->

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(6), dp(3), dp(5))

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(getThemeSurfaceColor())
                    setStroke(dp(1), getThemeBorderColor())
                }

                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply {
                    width = 0
                    height = dp(62)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }

                tag = "site_card"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    openWebsite(site.url)
                }
            }

            val logo = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(24),
                    dp(24)
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "${site.name} logo"
            }

            val name = TextView(this).apply {
                tag = "site_name"
                text = site.name
                textSize = 9.5f
                setTextColor(getThemeTextColor())
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setPadding(dp(1), dp(4), dp(1), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            card.addView(logo)
            card.addView(name)
            grid.addView(card)

            loadSiteLogo(site.url, logo)
        }

        container.addView(grid)
    }

    private fun loadSiteLogo(
        siteUrl: String,
        imageView: ImageView
    ) {
        val host = try {
            java.net.URL(siteUrl).host
        } catch (e: Exception) {
            return
        }

        // "web.whatsapp.com" and similar sub-domains often have no
        // favicon of their own, so the main domain is tried as well.
        val mainHost = host
            .removePrefix("www.")
            .removePrefix("web.")

        // WhatsApp always has a built-in logo, so it can never be blank.
        val builtInLogo =
            if (mainHost.contains("whatsapp")) R.drawable.ic_whatsapp else 0

        if (builtInLogo != 0) {
            imageView.setImageResource(builtInLogo)
        }

        val candidates = listOf(
            "https://www.google.com/s2/favicons?domain=$mainHost&sz=128",
            "https://www.google.com/s2/favicons?domain=$host&sz=128",
            "https://icons.duckduckgo.com/ip3/$mainHost.ico",
            "https://$mainHost/favicon.ico"
        ).distinct()

        val tagKey = "logo:$mainHost"
        imageView.tag = tagKey

        Thread {
            for (logoUrl in candidates) {
                try {
                    val connection =
                        java.net.URL(logoUrl).openConnection()
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000

                    val bitmap = connection.getInputStream().use {
                        BitmapFactory.decodeStream(it)
                    }

                    // Very small images are generic placeholders.
                    if (bitmap != null && bitmap.width >= 24) {
                        runOnUiThread {
                            if (imageView.tag == tagKey) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                        return@Thread
                    }
                } catch (_: Exception) {
                    // Try the next logo source.
                }
            }
            // If nothing loads the card stays usable (and WhatsApp keeps
            // its built-in logo).
        }.start()
    }

    private fun roundedBg(
        fill: Int,
        radiusDp: Int,
        withBorder: Boolean = true
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (withBorder) {
                setStroke(dp(1), getThemeBorderColor())
            }
        }
    }

    private fun addEmptyMessage(
        container: LinearLayout,
        message: String
    ) {
        container.addView(
            TextView(this).apply {
                tag = "muted"
                text = message
                textSize = 12f
                setTextColor(getThemeMutedColor())
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }
        )
    }

    private fun isDarkTheme(): Boolean {
        return when (
            preferences.getString("app_theme", "Chrome Light")
        ) {
            "Midnight", "Graphite" -> true
            else -> false
        }
    }

    // Dialogs follow the selected app theme (light / dark).
    private fun themedDialog(): AlertDialog.Builder {
        return AlertDialog.Builder(
            this,
            if (isDarkTheme())
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            else
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        )
    }

    private fun styleDialogWindow(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(getThemeSurfaceColor())
                setStroke(dp(1), getThemeBorderColor())
            }
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    // =========================================================
    // SYSTEM NAVIGATION BAR
    // =========================================================

    private fun hideSystemNavigationBar() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.R
        ) {

            window.insetsController?.let { controller ->

                controller.hide(
                    WindowInsets.Type.navigationBars()
                )

                controller.systemBarsBehavior =
                    WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

        } else {

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    // =========================================================
    // WEBVIEW
    // =========================================================

    private fun setupWebView() {

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            loadWithOverviewMode = false

            useWideViewPort = false

            mediaPlaybackRequiresUserGesture = false

            databaseEnabled = true

            javaScriptCanOpenWindowsAutomatically = true

            userAgentString = MOBILE_UA

            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            allowFileAccess = true

            allowContentAccess = true

            blockNetworkImage =
                preferences.getBoolean(
                    "data_saving",
                    false
                )
        }

        CookieManager
            .getInstance()
            .setAcceptCookie(true)

        CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(
                webView,
                true
            )

        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?
                ) {

                    loadingBar.visibility =
                        View.VISIBLE

                    if (
                        !url.isNullOrBlank() &&
                        (
                            url.startsWith("http://") ||
                                    url.startsWith("https://")
                            )
                    ) {

                        // Back / forward can land on a site that needs
                        // the other view mode: switch and reload once.
                        if (needsDesktopMode(url) != desktopMode) {

                            applyViewMode(url)

                            view?.reload()

                            return
                        }

                        addressBar.setText(url)

                        saveHistory(url)
                    }
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    loadingBar.visibility =
                        View.GONE

                    if (
                        url != null &&
                        (
                            url.startsWith("http://") ||
                            url.startsWith("https://")
                        )
                    ) {

                        addressBar.setText(url)
                        // Update active tab information
                        val activeTab =
                            openTabs.find {
                                it.id == activeTabId
                            }

                        if (activeTab != null) {

                            activeTab.url =
                                url

                            activeTab.title =
                                view?.title
                                    ?.trim()
                                    ?.ifBlank {
                                        "New Tab"
                                    }
                                    ?: "New Tab"
                        }

                        // Hide search/address bar after website finishes loading
                        addressBar.visibility =
                            View.GONE

                        findViewById<View>(
                            R.id.goButton
                        ).visibility =
                            View.GONE

                        applyDesktopViewport(view)
                    }
                }

                override fun onPageCommitVisible(
                    view: WebView?,
                    url: String?
                ) {
                    applyDesktopViewport(view)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {

                    if (request == null) {
                        return false
                    }

                    val target =
                        request.url.toString()

                    val scheme =
                        request.url.scheme?.lowercase()
                            ?: return false

                    // Web pages: switch between mobile / desktop view
                    // when the target site needs a different mode.
                    if (
                        scheme == "http" ||
                        scheme == "https"
                    ) {

                        if (
                            request.isForMainFrame &&
                            needsDesktopMode(target) != desktopMode
                        ) {

                            loadUrlInWebView(target)

                            return true
                        }

                        return false
                    }

                    // Let the system handle simple actions.
                    if (
                        scheme == "mailto" ||
                        scheme == "tel"
                    ) {

                        try {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    request.url
                                )
                            )
                        } catch (_: Exception) {
                        }

                        return true
                    }

                    // Internal / local schemes stay in the WebView.
                    if (
                        scheme == "about" ||
                        scheme == "data" ||
                        scheme == "blob" ||
                        scheme == "file" ||
                        scheme == "javascript"
                    ) {
                        return false
                    }

                    // App deep links (fb://, snssdk://, intent://...)
                    // cannot run inside the browser - ignore them so
                    // the page does not show an error.
                    return true
                }
            }

        webView.setOnScrollChangeListener {
                _, _, _, _, _ ->
        }
    }

    // =========================================================
    // MOBILE / DESKTOP VIEW MODE
    // =========================================================
    //
    // WhatsApp Web, TikTok and Facebook refuse (or break) when
    // they see a phone browser, so those sites are opened with a
    // desktop ("Windows") identity. The page is then laid out at
    // DESKTOP_VIEWPORT_WIDTH and scaled to fit the phone screen,
    // so it stays small and fits instead of being oversized.
    // Every other website keeps the normal mobile view.
    //
    // =========================================================

    private fun needsDesktopMode(url: String?): Boolean {

        if (url.isNullOrBlank()) {
            return false
        }

        val host = try {
            Uri.parse(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false

        return DESKTOP_HOSTS.any { site ->
            host == site || host.endsWith(".$site")
        }
    }

    private fun applyViewMode(url: String?) {

        val desktop = needsDesktopMode(url)

        desktopMode = desktop

        webView.settings.apply {
            userAgentString =
                if (desktop) DESKTOP_UA else MOBILE_UA

            useWideViewPort = desktop
            loadWithOverviewMode = desktop
        }
    }

    private fun loadUrlInWebView(url: String) {

        applyViewMode(url)

        webView.loadUrl(url)
    }

    private fun applyDesktopViewport(view: WebView?) {

        if (!desktopMode || view == null) {
            return
        }

        view.evaluateJavascript(
            "(function(){" +
                "var m=document.querySelector('meta[name=\"viewport\"]');" +
                "if(!m){m=document.createElement('meta');" +
                "m.setAttribute('name','viewport');" +
                "(document.head||document.documentElement).appendChild(m);}" +
                "m.setAttribute('content','width=$DESKTOP_VIEWPORT_WIDTH," +
                "minimum-scale=0.25,maximum-scale=5,user-scalable=yes');" +
                "})();",
            null
        )
    }

    // =========================================================
    // DOWNLOADS
    // =========================================================

    private data class DownloadEntry(
        val dmId: Long,
        val name: String,
        val url: String,
        val time: Long,
        val localUri: String?,
        val mime: String?
    )

    private data class DownloadState(
        val status: Int,
        val downloaded: Long,
        val total: Long
    )

    private val downloadLock = Any()

    private val blobTokens = mutableSetOf<String>()

    private var downloadsChanged: (() -> Unit)? = null

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun downloadManager(): DownloadManager =
        getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun setupDownloads() {

        webView.addJavascriptInterface(BlobBridge(), "DeeprowssBridge")

        // Any download started by any website goes through here.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            requestStorageThen {
                startDownload(url, userAgent, contentDisposition, mimeType)
            }
        }

        setupImageLongPress()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {

                val id = intent?.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID,
                    -1L
                ) ?: -1L

                val entry = loadDownloads().firstOrNull { it.dmId == id }
                    ?: return

                when (queryDownload(id)?.status) {
                    DownloadManager.STATUS_SUCCESSFUL ->
                        toast("Download complete: ${entry.name}")
                    DownloadManager.STATUS_FAILED ->
                        toast("Download failed: ${entry.name}")
                }

                downloadsChanged?.invoke()
            }
        }

        downloadReceiver = receiver

        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    // Press and hold any image on any website to save it.
    private fun setupImageLongPress() {

        webView.setOnLongClickListener {

            val hit = webView.hitTestResult

            val isImage =
                hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

            val imageUrl = hit.extra

            if (!isImage || imageUrl.isNullOrBlank()) {
                // Not an image: keep the normal WebView behaviour
                // (text selection, link menu...).
                return@setOnLongClickListener false
            }

            showImageMenu(imageUrl)

            true
        }
    }

    private fun showImageMenu(imageUrl: String) {

        val options = arrayOf(
            "\u2B07\uFE0F  Save image",
            "\uD83D\uDD17  Open image in new tab",
            "\uD83D\uDCCB  Copy image link"
        )

        val dialog = themedDialog()
            .setTitle("Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveImage(imageUrl)
                    1 -> openWebsite(imageUrl)
                    2 -> {
                        val clipboard = getSystemService(
                            Context.CLIPBOARD_SERVICE
                        ) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Image link",
                                imageUrl
                            )
                        )
                        toast("Image link copied")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        styleDialogWindow(dialog)
    }

    private fun saveImage(imageUrl: String) {

        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {

            // Work out the image type from the file extension so the
            // saved file gets a proper name (photo.jpg, logo.png...).
            val ext = MimeTypeMap.getFileExtensionFromUrl(imageUrl)

            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext.lowercase())
                ?.takeIf { it.startsWith("image/") }
                ?: "image/jpeg"

            requestStorageThen {
                startDownload(
                    imageUrl,
                    webView.settings.userAgentString,
                    null,
                    mime
                )
            }

        } else {

            // data: and blob: images are handled by the download code.
            requestStorageThen {
                startDownload(imageUrl, null, null, null)
            }
        }
    }

    // Android 9 and below need a storage permission to save files.
    private fun requestStorageThen(action: () -> Unit) {

        if (
            android.os.Build.VERSION.SDK_INT >
            android.os.Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
            return
        }

        pendingAfterPermission = action

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == STORAGE_PERMISSION_REQUEST) {

            val action = pendingAfterPermission
            pendingAfterPermission = null

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                action?.invoke()
            } else {
                toast("Storage permission is needed to save downloads")
            }
        }
    }

    private fun startDownload(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {

        if (url.isNullOrBlank()) {
            return
        }

        if (url.startsWith("blob:")) {
            downloadBlob(url, mimeType)
            return
        }

        if (url.startsWith("data:")) {
            downloadDataUri(url)
            return
        }

        try {

            val fileName =
                URLUtil.guessFileName(url, contentDisposition, mimeType)

            val request = DownloadManager.Request(Uri.parse(url)).apply {

                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }

                CookieManager.getInstance().getCookie(url)?.let {
                    addRequestHeader("Cookie", it)
                }

                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }

                webView.url?.let {
                    addRequestHeader("Referer", it)
                }

                setTitle(fileName)
                setDescription("Deeprowss Browser")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )

                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            }

            val id = downloadManager().enqueue(request)

            addDownloadEntry(
                DownloadEntry(
                    dmId = id,
                    name = fileName,
                    url = url,
                    time = System.currentTimeMillis(),
                    localUri = null,
                    mime = mimeType
                )
            )

            toast("Downloading $fileName")

            downloadsChanged?.invoke()

        } catch (e: Exception) {

            toast("Unable to download this file")
        }
    }

    // ---------- blob: and data: downloads ----------

    private fun downloadBlob(url: String, mimeType: String?) {

        val token = java.util.UUID.randomUUID().toString()

        synchronized(blobTokens) {
            blobTokens.add(token)
        }

        val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
        val mime = (mimeType ?: "").replace("'", "")

        val script =
            "(function(){" +
                "var x=new XMLHttpRequest();" +
                "x.open('GET','$safeUrl',true);" +
                "x.responseType='blob';" +
                "x.onload=function(){" +
                "var r=new FileReader();" +
                "r.onloadend=function(){" +
                "var d=String(r.result);var i=d.indexOf(',');" +
                "DeeprowssBridge.saveBase64('$token',d.substring(i+1)," +
                "(x.response&&x.response.type)||'$mime');};" +
                "r.readAsDataURL(x.response);};" +
                "x.onerror=function(){DeeprowssBridge.fail('$token');};" +
                "x.send();})();"

        toast("Preparing download...")

        webView.evaluateJavascript(script, null)
    }

    private fun downloadDataUri(uri: String) {

        Thread {
            try {
                val comma = uri.indexOf(',')
                val header = uri.substring(5, comma)
                val payload = uri.substring(comma + 1)

                val mime = header.substringBefore(';')
                    .ifBlank { "application/octet-stream" }

                val bytes =
                    if (header.contains(";base64")) {
                        Base64.decode(payload, Base64.DEFAULT)
                    } else {
                        Uri.decode(payload).toByteArray()
                    }

                saveBytesAsDownload(bytes, guessFileNameForMime(mime), mime, "data:")

            } catch (e: Exception) {
                runOnUiThread { toast("Unable to download this file") }
            }
        }.start()
    }

    // Only accepts data from a download this app itself started
    // (one-time token), so websites cannot write files by themselves.
    inner class BlobBridge {

        @JavascriptInterface
        fun saveBase64(token: String, base64: String, mime: String) {

            val valid = synchronized(blobTokens) {
                blobTokens.remove(token)
            }

            if (!valid) {
                return
            }

            try {
                val type = mime.ifBlank { "application/octet-stream" }
                val bytes = Base64.decode(base64, Base64.DEFAULT)

                saveBytesAsDownload(bytes, guessFileNameForMime(type), type, "blob")

            } catch (e: Exception) {
                runOnUiThread { toast("Unable to download this file") }
            }
        }

        @JavascriptInterface
        fun fail(token: String) {

            synchronized(blobTokens) {
                blobTokens.remove(token)
            }

            runOnUiThread { toast("Unable to download this file") }
        }
    }

    private fun guessFileNameForMime(mime: String): String {

        val extension =
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)

        val stamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        return if (extension.isNullOrBlank()) {
            "download_$stamp"
        } else {
            "download_$stamp.$extension"
        }
    }

    private fun uniqueFile(directory: File, name: String): File {

        var file = File(directory, name)
        var counter = 1

        val base = name.substringBeforeLast('.', name)
        val ext =
            if (name.contains('.')) "." + name.substringAfterLast('.') else ""

        while (file.exists()) {
            file = File(directory, "$base-$counter$ext")
            counter++
        }

        return file
    }

    @Suppress("DEPRECATION")
    private fun saveBytesAsDownload(
        bytes: ByteArray,
        name: String,
        mime: String,
        sourceUrl: String
    ) {

        var dmId = -1L
        var localUri: String? = null

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.Q
        ) {

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
            }

            val uri = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw java.io.IOException("Unable to create file")

            contentResolver.openOutputStream(uri)?.use {
                it.write(bytes)
            }

            localUri = uri.toString()

        } else {

            val directory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            directory.mkdirs()

            val file = uniqueFile(directory, name)

            file.writeBytes(bytes)

            dmId = downloadManager().addCompletedDownload(
                file.name,
                "Deeprowss Browser",
                true,
                mime,
                file.absolutePath,
                file.length(),
                true
            )
        }

        addDownloadEntry(
            DownloadEntry(
                dmId = dmId,
                name = name,
                url = sourceUrl,
                time = System.currentTimeMillis(),
                localUri = localUri,
                mime = mime
            )
        )

        runOnUiThread {
            toast("Saved to Downloads: $name")
            downloadsChanged?.invoke()
        }
    }

    // ---------- download history storage ----------

    private fun loadDownloads(): MutableList<DownloadEntry> {

        val list = mutableListOf<DownloadEntry>()

        synchronized(downloadLock) {
            try {
                val array = JSONArray(
                    preferences.getString("downloads_json", "[]") ?: "[]"
                )

                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)

                    list.add(
                        DownloadEntry(
                            dmId = o.optLong("dm", -1L),
                            name = o.optString("name"),
                            url = o.optString("url"),
                            time = o.optLong("time"),
                            localUri = o.optString("local").ifBlank { null },
                            mime = o.optString("mime").ifBlank { null }
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        return list
    }

    private fun saveDownloads(list: List<DownloadEntry>) {

        synchronized(downloadLock) {
            val array = JSONArray()

            list.take(100).forEach { e ->
                array.put(
                    JSONObject()
                        .put("dm", e.dmId)
                        .put("name", e.name)
                        .put("url", e.url)
                        .put("time", e.time)
                        .put("local", e.localUri ?: "")
                        .put("mime", e.mime ?: "")
                )
            }

            preferences.edit()
                .putString("downloads_json", array.toString())
                .apply()
        }
    }

    private fun addDownloadEntry(entry: DownloadEntry) {

        synchronized(downloadLock) {
            val list = loadDownloads()
            list.add(0, entry)
            saveDownloads(list)
        }
    }

    private fun removeDownloadEntry(entry: DownloadEntry) {

        synchronized(downloadLock) {
            val list = loadDownloads()
            list.remove(entry)
            saveDownloads(list)
        }
    }

    private fun queryDownload(id: Long): DownloadState? {

        return try {
            downloadManager()
                .query(DownloadManager.Query().setFilterById(id))
                ?.use { c ->
                    if (!c.moveToFirst()) {
                        null
                    } else {
                        DownloadState(
                            status = c.getInt(
                                c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                            ),
                            downloaded = c.getLong(
                                c.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            ),
                            total = c.getLong(
                                c.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                )
                            )
                        )
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatSize(bytes: Long): String {

        if (bytes <= 0) {
            return "0 KB"
        }

        val kb = bytes / 1024.0

        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb)
        }

        val mb = kb / 1024.0

        if (mb < 1024) {
            return String.format(Locale.US, "%.1f MB", mb)
        }

        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    private fun openDownloadedFile(entry: DownloadEntry) {

        try {

            val uri: Uri?
            val mime: String?

            if (entry.dmId >= 0) {
                val dm = downloadManager()
                uri = dm.getUriForDownloadedFile(entry.dmId)
                mime = dm.getMimeTypeForDownloadedFile(entry.dmId) ?: entry.mime
            } else {
                uri = entry.localUri?.let { Uri.parse(it) }
                mime = entry.mime
            }

            if (uri == null) {
                toast("File not found")
                return
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: "*/*")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

            startActivity(intent)

        } catch (e: android.content.ActivityNotFoundException) {
            toast("No app found to open this file")
        } catch (e: Exception) {
            toast("Unable to open file")
        }
    }

    private fun deleteDownload(entry: DownloadEntry) {

        try {
            if (entry.dmId >= 0) {
                downloadManager().remove(entry.dmId)
            } else if (!entry.localUri.isNullOrBlank()) {
                contentResolver.delete(Uri.parse(entry.localUri), null, null)
            }
        } catch (_: Exception) {
        }

        removeDownloadEntry(entry)
    }

    // ---------- Downloads screen (Settings > Downloads) ----------

    private fun showDownloads() {

        val textColor = getThemeTextColor()
        val mutedColor = getThemeMutedColor()
        val accentColor = getThemeAccentColor()

        val scroll = ScrollView(this)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }

        scroll.addView(list)

        val dialog = themedDialog()
            .setTitle("Downloads")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Files app") { _, _ ->
                try {
                    startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                } catch (_: Exception) {
                    toast("Unable to open Downloads")
                }
            }
            .create()

        var hasActive = false

        fun actionButton(label: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
                setPadding(0, dp(8), dp(18), dp(2))
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        fun render() {

            list.removeAllViews()
            hasActive = false

            val entries = loadDownloads()

            if (entries.isEmpty()) {
                list.addView(
                    TextView(this).apply {
                        text = "No downloads yet.\nFiles you download from websites will appear here."
                        setTextColor(mutedColor)
                        textSize = 12f
                        setPadding(0, dp(16), 0, dp(16))
                    }
                )
                return
            }

            entries.forEach { entry ->

                val state =
                    if (entry.dmId >= 0) queryDownload(entry.dmId) else null

                val removed = entry.dmId >= 0 && state == null

                val complete =
                    !removed && (
                        entry.dmId < 0 ||
                            state?.status == DownloadManager.STATUS_SUCCESSFUL
                        )

                val failed = state?.status == DownloadManager.STATUS_FAILED

                val running = !removed && !complete && !failed

                if (running) {
                    hasActive = true
                }

                val percent =
                    if (state != null && state.total > 0) {
                        ((state.downloaded * 100) / state.total).toInt()
                    } else {
                        0
                    }

                val statusText = when {
                    removed -> "File removed"
                    failed -> "Failed"
                    complete && state != null ->
                        "Completed  •  ${formatSize(state.total.coerceAtLeast(state.downloaded))}"
                    complete -> "Saved to Downloads"
                    state?.status == DownloadManager.STATUS_PAUSED ->
                        "Paused  •  waiting for network"
                    state?.status == DownloadManager.STATUS_PENDING ->
                        "Waiting to start..."
                    state != null && state.total > 0 ->
                        "Downloading $percent%  •  ${formatSize(state.downloaded)} of ${formatSize(state.total)}"
                    else ->
                        "Downloading  •  ${formatSize(state?.downloaded ?: 0)}"
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(8))

                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12).toFloat()
                        setColor(getThemeSurface2Color())
                        setStroke(dp(1), getThemeBorderColor())
                    }

                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dp(4), 0, dp(4))
                    }
                }

                row.addView(
                    TextView(this).apply {
                        text = entry.name
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(textColor)
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    }
                )

                row.addView(
                    TextView(this).apply {
                        text = statusText
                        textSize = 11f
                        setTextColor(if (failed || removed) Color.parseColor("#D93025") else mutedColor)
                        setPadding(0, dp(3), 0, 0)
                    }
                )

                if (running) {
                    row.addView(
                        ProgressBar(
                            this@MainActivity,
                            null,
                            android.R.attr.progressBarStyleHorizontal
                        ).apply {
                            max = 100
                            progress = percent
                            isIndeterminate = (state == null || state.total <= 0)
                            progressTintList = ColorStateList.valueOf(accentColor)
                            indeterminateTintList = ColorStateList.valueOf(accentColor)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(6)
                            ).apply {
                                setMargins(0, dp(6), 0, 0)
                            }
                        }
                    )
                }

                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                if (complete) {
                    actions.addView(actionButton("Open") {
                        openDownloadedFile(entry)
                    })
                }

                actions.addView(
                    actionButton(if (running) "Cancel" else "Delete") {
                        deleteDownload(entry)
                        render()
                    }
                )

                row.addView(actions)

                if (complete) {
                    row.setOnClickListener {
                        openDownloadedFile(entry)
                    }
                }

                list.addView(row)
            }
        }

        val ticker = object : Runnable {
            override fun run() {
                if (dialog.isShowing) {
                    if (hasActive) {
                        render()
                    }
                    uiHandler.postDelayed(this, 1000)
                }
            }
        }

        downloadsChanged = { render() }

        dialog.setOnDismissListener {
            downloadsChanged = null
            uiHandler.removeCallbacks(ticker)
        }

        styleDialogWindow(dialog)

        dialog.show()

        render()

        uiHandler.postDelayed(ticker, 1000)
    }


    // =========================================================
    // CONTROLS
    // =========================================================

    private fun setupControls() {

        findViewById<View>(
            R.id.goButton
        ).setOnClickListener {

            openAddress()
        }

        addressBar.setOnEditorActionListener {
                _, _, _ ->

            openAddress()

            true
        }

        findViewById<View>(
            R.id.backButton
        ).setOnClickListener {

            if (
                settingsPage.visibility ==
                View.VISIBLE
            ) {

                showHomePage()

            } else if (
                webView.visibility ==
                View.VISIBLE &&
                webView.canGoBack()
            ) {

                webView.goBack()

            } else {

                showHomePage()
            }
        }

        findViewById<View>(
            R.id.forwardButton
        ).setOnClickListener {

            if (
                webView.visibility ==
                View.VISIBLE &&
                webView.canGoForward()
            ) {

                webView.goForward()
            }
        }

        findViewById<View>(
            R.id.moreTrendsButton
        ).setOnClickListener {

            openWebsite(
                "https://trends.google.com/trending"
            )
        }

        // =====================================================
        // REFRESH
        // =====================================================

        findViewById<View>(
            R.id.refreshPageButton
        ).setOnClickListener {

            if (
                webView.visibility ==
                View.VISIBLE
            ) {

                webView.reload()

            } else {

                Toast.makeText(
                    this,
                    "Open a website first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // =====================================================
        // OPEN PAGES
        // =====================================================

        findViewById<View>(
            R.id.refreshButton
        ).setOnClickListener {

            showOpenTabs()
        }

        // =====================================================
        // HOME
        // =====================================================

        findViewById<View>(
            R.id.homeButton
        ).setOnClickListener {

            showHomePage()
        }

        // =====================================================
        // BOOKMARK
        // =====================================================

        findViewById<View>(
            R.id.bookmarkButton
        ).setOnClickListener {

            saveCurrentBookmark()
        }

        // =====================================================
        // MENU
        // =====================================================

        findViewById<View>(
            R.id.menuButton
        ).setOnClickListener {

            showSettings()
        }

        // =====================================================
        // THEME
        // =====================================================

        findViewById<View>(
            R.id.themeButton
        ).setOnClickListener {
            showThemeSelector()
        }

        // =====================================================
        // NEWS BUTTONS
        // =====================================================

        findViewById<View>(
            R.id.moreNewsButton
        ).setOnClickListener {

            openWebsite(
                "https://news.google.com/"
            )
        }

        findViewById<View>(
            R.id.moreSportNewsButton
        ).setOnClickListener {

            openWebsite(
                "https://news.google.com/search?q=football"
            )
        }
    }

    // =========================================================
    // OPEN TABS
    // =========================================================

    private fun showOpenTabs() {

        if (openTabs.isEmpty()) {

            Toast.makeText(
                this,
                "No open tabs",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val container =
            android.widget.LinearLayout(this)

        container.orientation =
            android.widget.LinearLayout.VERTICAL

        container.setPadding(
            dp(16),
            dp(8),
            dp(16),
            dp(8)
        )

        val dialog =
            themedDialog()
                .setTitle(
                    "Open Tabs (${openTabs.size})"
                )
                .setView(container)
                .setNegativeButton(
                    "Close",
                    null
                )
                .create()

        fun refreshTabList() {

            container.removeAllViews()

            dialog.setTitle(
                "Open Tabs (${openTabs.size})"
            )

            openTabs.forEach { tab ->

                val row =
                    android.widget.LinearLayout(this)

                row.orientation =
                    android.widget.LinearLayout.HORIZONTAL

                row.gravity =
                    android.view.Gravity.CENTER_VERTICAL

                row.setPadding(
                    dp(12),
                    dp(8),
                    dp(6),
                    dp(8)
                )

                row.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(getThemeSurface2Color())
                    setStroke(
                        if (tab.id == activeTabId) dp(2) else dp(1),
                        if (tab.id == activeTabId)
                            getThemeAccentColor()
                        else
                            getThemeBorderColor()
                    )
                }

                val rowParams =
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                rowParams.setMargins(
                    0,
                    0,
                    0,
                    dp(6)
                )

                row.layoutParams =
                    rowParams

                val title =
                    android.widget.TextView(this)

                title.text =
                    if (tab.id == activeTabId) {
                        "✓ ${tab.title}"
                    } else {
                        tab.title
                    }

                title.setTextColor(
                    getThemeTextColor()
                )

                title.textSize =
                    14f

                title.maxLines =
                    1

                title.ellipsize =
                    android.text.TextUtils.TruncateAt.END

                title.layoutParams =
                    android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                val closeButton =
                    android.widget.TextView(this)

                closeButton.text =
                    "✕"

                closeButton.gravity =
                    android.view.Gravity.CENTER

                closeButton.setTextColor(
                    getThemeMutedColor()
                )

                closeButton.textSize =
                    18f

                closeButton.setPadding(
                    16,
                    8,
                    16,
                    8
                )

                row.addView(
                    title
                )

                row.addView(
                    closeButton
                )

                // Open this tab
                title.setOnClickListener {

                    activeTabId =
                        tab.id

                    homePage.visibility =
                        View.GONE

                    settingsPage.visibility =
                        View.GONE

                    webView.visibility =
                        View.VISIBLE

                    addressBar.visibility =
                        View.GONE

                    findViewById<View>(
                        R.id.goButton
                    ).visibility =
                        View.GONE

                    loadUrlInWebView(
                        tab.url
                    )

                    dialog.dismiss()
                }

                // Close this tab
                closeButton.setOnClickListener {

                    val wasActive =
                        tab.id == activeTabId

                    openTabs.remove(
                        tab
                    )

                    if (openTabs.isEmpty()) {

                        activeTabId =
                            0

                        updateTabsCount()

                        dialog.dismiss()

                        showHomePage()

                        return@setOnClickListener
                    }

                    if (wasActive) {

                        val newActiveTab =
                            openTabs.last()

                        activeTabId =
                            newActiveTab.id

                        loadUrlInWebView(
                            newActiveTab.url
                        )
                    }

                    updateTabsCount()

                    refreshTabList()
                }

                container.addView(
                    row
                )
            }
        }

        refreshTabList()

        styleDialogWindow(dialog)

        dialog.show()
    }

    private fun loadWebsiteLogo(
        textView: android.widget.TextView,
        domain: String
    ) {

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO
        ).launch {

            try {

                val logoUrl =
                    "https://www.google.com/s2/favicons?domain=$domain&sz=128"

                val connection =
                    java.net.URL(
                        logoUrl
                    ).openConnection()

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val input =
                    connection.getInputStream()

                val bitmap =
                    android.graphics.BitmapFactory
                        .decodeStream(input)

                input.close()

                if (bitmap != null) {

                    runOnUiThread {

                        val density =
                            resources.displayMetrics.density

                        val size =
                            (30 * density).toInt()

                        val drawable =
                            android.graphics.drawable.BitmapDrawable(
                                resources,
                                bitmap
                            )

                        drawable.setBounds(
                            0,
                            0,
                            size,
                            size
                        )

                        textView.setCompoundDrawables(
                            null,
                            drawable,
                            null,
                            null
                        )

                        textView.compoundDrawablePadding =
                            (7 * density).toInt()

                        textView.gravity =
                            android.view.Gravity.CENTER

                        textView.includeFontPadding =
                            false

                        textView.setPadding(
                            (4 * density).toInt(),
                            (8 * density).toInt(),
                            (4 * density).toInt(),
                            (8 * density).toInt()
                        )
                    }
                }

            } catch (_: Exception) {

                // Keep the website name visible
            }
        }
    }

    private fun loadWebsiteLogoToImage(
        containerId: Int,
        domain: String
    ) {

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.IO
        ).launch {

            try {

                val logoUrl =
                    "https://www.google.com/s2/favicons?domain=$domain&sz=128"

                val connection =
                    java.net.URL(
                        logoUrl
                    ).openConnection()

                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val input =
                    connection.getInputStream()

                val bitmap =
                    android.graphics.BitmapFactory
                        .decodeStream(input)

                input.close()

                if (bitmap != null) {

                    runOnUiThread {

                        val container =
                            findViewById<android.widget.LinearLayout>(
                                containerId
                            )

                        val imageView =
                            container.getChildAt(0)

                                as? android.widget.ImageView

                        imageView?.setImageBitmap(
                            bitmap
                        )
                    }
                }

            } catch (_: Exception) {

                // Keep the existing icon if logo fails
            }
        }
    }

    // =========================================================
    // NOTIFICATIONS
    // =========================================================

    // Trend alerts are sent by TrendNotifications (a background job
    // that runs every few hours). Here we only ask for permission
    // (Android 13+) and make sure the job is scheduled.
    private fun setupTrendNotifications() {

        TrendNotifications.createChannel(this)

        requestNotificationPermission()

        TrendNotifications.schedule(this)
    }

    private fun requestNotificationPermission() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
                    1001
                )
            }
        }
    }

    // Opens the trend the user tapped in a notification.
    private fun handleNotificationIntent(intent: Intent?) {

        val url = intent
            ?.getStringExtra(TrendNotifications.EXTRA_OPEN_URL)
            ?.takeIf { it.isNotBlank() }
            ?: return

        intent?.removeExtra(TrendNotifications.EXTRA_OPEN_URL)

        openWebsite(url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    // "App Update Enquiry" opens a WhatsApp chat with the developer.
    private fun setupUpdateEnquiryButton() {

        findViewById<View>(R.id.appUpdateButton).setOnClickListener {

            val link =
                "https://wa.me/2348164887683" +
                    "?text=Hi%2C%20can%20I%20get%20the%20latest%20" +
                    "update%20on%20this%20app%2C%20please%3F"

            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(link))
                )
            } catch (_: Exception) {
                toast("Unable to open WhatsApp")
            }
        }
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    private fun setupVpnSettings() {

        // The VPN is not available yet: everything stays off and
        // tapping any part of the card tells the user it is coming.
        preferences.edit().putBoolean("vpn_enabled", false).apply()

        vpnSwitch.isChecked = false

        val comingSoon = {
            Toast.makeText(
                this,
                "VPN is coming soon",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<View>(R.id.vpnCard).setOnClickListener {
            comingSoon()
        }

        findViewById<View>(R.id.vpnLocationButton).apply {
            alpha = 0.55f
            setOnClickListener {
                comingSoon()
            }
        }

        vpnSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled) {
                vpnSwitch.isChecked = false
                comingSoon()
            }
        }
    }

    private fun showVpnCountryPicker() {
        val names = vpnCountries.map { country ->
            val status = if (country.available) "" else "  • Coming soon"
            "${country.flag()}  ${country.name}$status"
        }.toTypedArray()

        themedDialog()
            .setTitle("VPN location")
            .setItems(names) { _, which ->
                val selected = vpnCountries[which]
                preferences.edit().putString("vpn_location", selected.code).apply()
                vpnLocationText.text = "${selected.flag()}  ${selected.name}"
                if (!selected.available) {
                    Toast.makeText(this, "VPN server for ${selected.name} is not deployed yet.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun VpnCountry.flag(): String = code
        .map { char -> String(Character.toChars(0x1F1E6 + (char.code - 'A'.code))) }
        .joinToString("")

    private fun setupSettings() {

        dataSavingSwitch.isChecked =
            preferences.getBoolean(
                "data_saving",
                false
            )

        setupVpnSettings()

        adBlockingSwitch.isChecked =
            preferences.getBoolean(
                "ad_blocking",
                false
            )

        dataSavingSwitch.setOnCheckedChangeListener {
                _, enabled ->

            preferences.edit()
                .putBoolean(
                    "data_saving",
                    enabled
                )
                .apply()

            webView.settings.blockNetworkImage =
                enabled

            Toast.makeText(
                this,
                if (enabled)
                    "Data Saving enabled"
                else
                    "Data Saving disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        adBlockingSwitch.setOnCheckedChangeListener {
                _, enabled ->

            preferences.edit()
                .putBoolean(
                    "ad_blocking",
                    enabled
                )
                .apply()

            Toast.makeText(
                this,
                if (enabled)
                    "Ad Blocking enabled"
                else
                    "Ad Blocking disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<View>(
            R.id.historyButton
        ).setOnClickListener {

            showHistory()
        }

        findViewById<View>(
            R.id.bookmarksButton
        ).setOnClickListener {

            showBookmarks()
        }

        findViewById<View>(
            R.id.offlinePagesButton
        ).setOnClickListener {

            showOfflinePages()
        }

        findViewById<View>(
            R.id.clearCacheButton
        ).setOnClickListener {

            webView.clearCache(true)

            Toast.makeText(
                this,
                "Browser cache cleared",
                Toast.LENGTH_SHORT
            ).show()
        }

        findViewById<View>(
            R.id.downloadsButton
        ).setOnClickListener {

            showDownloads()
        }

        findViewById<View>(
            R.id.shareButton
        ).setOnClickListener {

            val currentUrl =
                webView.url

            if (
                currentUrl.isNullOrBlank()
            ) {

                Toast.makeText(
                    this,
                    "No webpage to share",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val shareIntent =
                android.content.Intent(
                    android.content.Intent.ACTION_SEND
                ).apply {

                    type = "text/plain"

                    putExtra(
                        android.content.Intent.EXTRA_TEXT,
                        currentUrl
                    )

                    putExtra(
                        android.content.Intent.EXTRA_SUBJECT,
                        webView.title
                            ?: "Deeprows Browser"
                    )
                }

            startActivity(
                android.content.Intent.createChooser(
                    shareIntent,
                    "Share page"
                )
            )
        }

        findViewById<View>(
            R.id.translateButton
        ).setOnClickListener {

            val currentUrl =
                webView.url

            if (
                currentUrl.isNullOrBlank()
            ) {

                Toast.makeText(
                    this,
                    "No webpage to translate",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            val translateUrl =
                "https://translate.google.com/translate" +
                        "?sl=auto&tl=en&u=" +
                        android.net.Uri.encode(
                            currentUrl
                        )

            openWebsite(
                translateUrl
            )
        }

        findViewById<View>(
            R.id.settingsBackButton
        ).setOnClickListener {

            showHomePage()
        }
    }

    // =========================================================
    // HOME / SETTINGS / WEB
    // =========================================================

    private fun showHomePage() {

        homePage.visibility =
            View.VISIBLE

        webView.visibility =
            View.GONE

        settingsPage.visibility =
            View.GONE

        loadingBar.visibility =
            View.GONE

        addressBar.visibility =
            View.VISIBLE

        findViewById<View>(
            R.id.goButton
        ).visibility =
            View.VISIBLE
    }

    private fun showSettings() {

        homePage.visibility =
            View.GONE

        webView.visibility =
            View.GONE

        settingsPage.visibility =
            View.VISIBLE

        loadingBar.visibility =
            View.GONE
    }

    // =========================================================
    // OPEN WEBSITE
    // =========================================================

    private fun openWebsite(
        url: String
    ) {

        settingsPage.visibility =
            View.GONE

        homePage.visibility =
            View.GONE

        webView.visibility =
            View.VISIBLE

        addressBar.visibility =
            View.GONE

        findViewById<View>(
            R.id.goButton
        ).visibility =
            View.GONE

        // Create a new tab
        val newTab =
            BrowserTab(
                id = nextTabId++,
                title = "New Tab",
                url = url
            )

        openTabs.add(
            newTab
        )

        activeTabId =
            newTab.id

        updateTabsCount()

        loadUrlInWebView(
            url
        )
    }

    // =========================================================
    // UPDATE TABS COUNT
    // =========================================================

    private fun updateTabsCount() {

        findViewById<android.widget.TextView>(
            R.id.pagesCount
        ).text =
            openTabs.size.toString()
    }

    // =========================================================
    // ADDRESS BAR
    // =========================================================

    private fun openAddress() {

        var text =
            addressBar.text
                .toString()
                .trim()

        if (text.isBlank()) {
            return
        }

        if (
            !text.startsWith("http://") &&
            !text.startsWith("https://")
        ) {

            if (
                text.contains(".") &&
                !text.contains(" ")
            ) {

                text =
                    "https://$text"

            } else {

                text =
                    "https://www.google.com/search?q=" +
                            Uri.encode(text)
            }
        }

        openWebsite(text)
    }

    // =========================================================
    // HISTORY
    // =========================================================

    private fun saveHistory(
        url: String
    ) {

        if (
            url.isBlank() ||
            url == "about:blank" ||
            (
                !url.startsWith("http://") &&
                !url.startsWith("https://")
            )
        ) {
            return
        }

        val history =
            preferences.getStringSet(
                "history",
                emptySet()
            )?.toMutableList()
                ?: mutableListOf()

        history.remove(url)

        history.add(
            0,
            url
        )

        val limited =
            history
                .take(50)
                .toSet()

        preferences.edit()
            .putStringSet(
                "history",
                limited
            )
            .apply()
    }

    private fun showHistory() {

        val history =
            preferences.getStringSet(
                "history",
                emptySet()
            )?.toList()
                ?: emptyList()

        if (history.isEmpty()) {

            themedDialog()
                .setTitle("History")
                .setMessage(
                    "No browsing history yet."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        themedDialog()
            .setTitle("History")
            .setItems(
                history.toTypedArray()
            ) { _, which ->

                openWebsite(
                    history[which]
                )
            }
            .setNegativeButton(
                "Clear History"
            ) { _, _ ->

                preferences.edit()
                    .remove("history")
                    .apply()

                Toast.makeText(
                    this,
                    "History cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton(
                "Close",
                null
            )
            .show()
    }

    // =========================================================
    // BOOKMARKS
    // =========================================================

    private fun saveCurrentBookmark() {

        val url =
            webView.url

        if (
            url.isNullOrBlank() ||
            (
                !url.startsWith("http://") &&
                !url.startsWith("https://")
            )
        ) {

            Toast.makeText(
                this,
                "Open a website first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val bookmarks =
            preferences.getStringSet(
                "bookmarks",
                emptySet()
            )?.toMutableSet()
                ?: mutableSetOf()

        bookmarks.add(url)

        preferences.edit()
            .putStringSet(
                "bookmarks",
                bookmarks
            )
            .apply()

        Toast.makeText(
            this,
            "Page bookmarked",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBookmarks() {

        val bookmarks =
            preferences.getStringSet(
                "bookmarks",
                emptySet()
            )?.toList()
                ?: emptyList()

        if (bookmarks.isEmpty()) {

            themedDialog()
                .setTitle("Bookmarks")
                .setMessage(
                    "No bookmarks saved yet."
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .show()

            return
        }

        themedDialog()
            .setTitle("Bookmarks")
            .setItems(
                bookmarks.toTypedArray()
            ) { _, which ->

                openWebsite(
                    bookmarks[which]
                )
            }
            .setNegativeButton(
                "Clear All"
            ) { _, _ ->

                preferences.edit()
                    .remove("bookmarks")
                    .apply()

                Toast.makeText(
                    this,
                    "Bookmarks cleared",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton(
                "Close",
                null
            )
            .show()
    }

    // =========================================================
    // OFFLINE PAGES
    // =========================================================

    private fun getOfflineDirectory(): File {

        val directory =
            File(
                filesDir,
                "offline_pages"
            )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        return directory
    }

    private fun saveOfflinePage() {

        val url =
            webView.url

        if (
            url.isNullOrBlank() ||
            url == "about:blank" ||
            (
                !url.startsWith("http://") &&
                !url.startsWith("https://")
            )
        ) {

            Toast.makeText(
                this,
                "Open a website first",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (webView.progress < 100) {

            Toast.makeText(
                this,
                "Wait until the page finishes loading",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val title =
            webView.title
                ?.trim()
                ?.ifBlank {
                    "Offline Page"
                }
                ?: "Offline Page"

        val safeTitle =
            title
                .replace(
                    Regex("[^A-Za-z0-9 _-]"),
                    ""
                )
                .replace(
                    Regex("\\s+"),
                    "_"
                )
                .take(50)
                .ifBlank {
                    "Offline_Page"
                }

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val file =
            File(
                getOfflineDirectory(),
                "${safeTitle}_$timestamp.mht"
            )

        Toast.makeText(
            this,
            "Saving offline page...",
            Toast.LENGTH_SHORT
        ).show()

        webView.saveWebArchive(
            file.absolutePath,
            false
        ) { savedPath ->

            runOnUiThread {

                if (
                    !savedPath.isNullOrBlank() &&
                    File(savedPath).exists()
                ) {

                    Toast.makeText(
                        this,
                        "Offline page saved",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    Toast.makeText(
                        this,
                        "Unable to save offline page",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showOfflinePages() {

        val directory =
            getOfflineDirectory()

        val files =
            directory
                .listFiles { file ->

                    file.isFile &&
                            file.extension.equals(
                                "mht",
                                ignoreCase = true
                            )
                }
                ?.sortedByDescending {
                    it.lastModified()
                }
                ?: emptyList()

        val builder =
            themedDialog()
                .setTitle("Offline Pages")

        if (files.isEmpty()) {

            builder.setMessage(
                "No offline pages saved yet."
            )

        } else {

            val names =
                files.map { file ->

                    file.nameWithoutExtension
                        .replace("_", " ")

                }.toTypedArray()

            builder.setItems(
                names
            ) { _, which ->

                openOfflinePage(
                    files[which]
                )
            }
        }

        builder.setPositiveButton(
            "Save Current"
        ) { _, _ ->

            saveOfflinePage()
        }

        if (files.isNotEmpty()) {

            builder.setNeutralButton(
                "Clear All"
            ) { _, _ ->

                clearOfflinePages()
            }
        }

        builder.setNegativeButton(
            "Close",
            null
        )

        builder.show()
    }

    private fun openOfflinePage(
        file: File
    ) {

        if (!file.exists()) {

            Toast.makeText(
                this,
                "Offline page no longer exists",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        settingsPage.visibility =
            View.GONE

        homePage.visibility =
            View.GONE

        webView.visibility =
            View.VISIBLE

        loadingBar.visibility =
            View.GONE

        addressBar.setText(
            "Offline: " +
                    file.nameWithoutExtension
                        .replace("_", " ")
        )

        applyViewMode(null)

        webView.loadUrl(
            Uri.fromFile(file).toString()
        )
    }

    private fun clearOfflinePages() {

        val directory =
            getOfflineDirectory()

        val files =
            directory.listFiles()

        var deleted = 0

        files?.forEach { file ->

            if (
                file.isFile &&
                file.extension.equals(
                    "mht",
                    ignoreCase = true
                )
            ) {

                if (file.delete()) {
                    deleted++
                }
            }
        }

        Toast.makeText(
            this,
            "$deleted offline page(s) deleted",
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================
    // NEWS
    // =========================================================

    private fun loadLatestNews() {

        val newsList =
            findViewById<android.widget.LinearLayout>(
                R.id.newsList
            )

        newsList.removeAllViews()

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main
        ).launch {

            val articles =
                newsRepository.getLatestNews(5)

            if (articles.isEmpty()) {
                addEmptyMessage(newsList, "Unable to load news right now")
            }

            articles.forEach { article ->

                addNewsCard(
                    newsList,
                    article
                )
            }
        }
    }

    private fun loadSportNews() {

        val sportNewsList =
            findViewById<android.widget.LinearLayout>(
                R.id.sportNewsList
            )

        sportNewsList.removeAllViews()

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main
        ).launch {

            val articles =
                newsRepository.getSportNews(5)

            if (articles.isEmpty()) {
                addEmptyMessage(sportNewsList, "Unable to load sport news right now")
            }

            articles.forEach { article ->

                addNewsCard(
                    sportNewsList,
                    article
                )
            }
        }
    }

    private fun loadGoogleTrends() {

        val trendsList =
            findViewById<android.widget.LinearLayout>(
                R.id.trendsList
            )

        trendsList.removeAllViews()

        trendTitleViews.clear()

        trendsTranslated = false

        findViewById<TextView>(R.id.trendsTranslateButton).text =
            "\uD83C\uDF10 Translate"

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Main
        ).launch {

            val trends =
                newsRepository.getGoogleTrends(null, 10)

            if (trends.isEmpty()) {

                addEmptyMessage(trendsList, "Unable to load Google Trends")
            }

            trends.forEachIndexed { index, trend ->
                addTrendRow(trendsList, index, trend)
            }

            // ---------------------------------------------
            // TRENDING MOVIES & SHOWS
            // ---------------------------------------------

            val entertainment =
                newsRepository.getTrendingEntertainment(6)

            if (entertainment.isNotEmpty()) {

                trendsList.addView(
                    android.widget.TextView(this@MainActivity).apply {
                        tag = "muted"
                        text = "\uD83C\uDFAC Trending Movies & Shows"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(getThemeMutedColor())
                        setPadding(dp(4), dp(10), dp(4), dp(6))
                    }
                )

                entertainment.forEachIndexed { index, item ->
                    addTrendRow(trendsList, index, item)
                }
            }

        }
    }

    // One numbered row of the trends list (used for the Google Trends
    // items and for the trending movies & shows).
    private fun addTrendRow(
        trendsList: android.widget.LinearLayout,
        index: Int,
        trend: NewsArticle
    ) {

                val trendRow =
                    android.widget.LinearLayout(
                        this@MainActivity
                    )

                trendRow.orientation =
                    android.widget.LinearLayout.HORIZONTAL

                trendRow.gravity =
                    android.view.Gravity.CENTER_VERTICAL

                trendRow.setPadding(
                    dp(8),
                    dp(8),
                    dp(8),
                    dp(8)
                )

                trendRow.tag = "trend_row"
                trendRow.background =
                    roundedBg(getThemeSurface2Color(), 10, false)

                val number =
                    android.widget.TextView(
                        this@MainActivity
                    )

                number.text =
                    "${index + 1}"

                number.setTextColor(
                    getThemeAccentColor()
                )

                number.tag =
                    "accent"

                number.textSize =
                    13f

                number.gravity =
                    android.view.Gravity.CENTER

                val numberParams =
                    android.widget.LinearLayout.LayoutParams(
                        dp(26),
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                trendRow.addView(
                    number,
                    numberParams
                )

                val textContainer =
                    android.widget.LinearLayout(
                        this@MainActivity
                    )

                textContainer.orientation =
                    android.widget.LinearLayout.VERTICAL

                textContainer.layoutParams =
                    android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                val title =
                    android.widget.TextView(
                        this@MainActivity
                    )

                title.text =
                    trend.title

                title.setTextColor(
                    getThemeTextColor()
                )

                title.textSize =
                    13f

                title.maxLines =
                    2

                title.ellipsize =
                    android.text.TextUtils.TruncateAt.END

                textContainer.addView(
                    title
                )

                trendTitleViews.add(
                    Pair(title, trend)
                )

                val source =
                    android.widget.TextView(
                        applicationContext
                    )

                source.text =
                    if (trend.source.isNotBlank()) {
                        trend.source
                    } else {
                        "Google Trends"
                    }

                source.setTextColor(
                    getThemeAccentColor()
                )

                source.tag =
                    "accent"

                source.textSize =
                    10f

                source.setPadding(
                    0,
                    dp(2),
                    0,
                    0
                )

                textContainer.addView(
                    source
                )

                trendRow.addView(
                    textContainer
                )

                trendRow.setOnClickListener {

                    if (
                        trend.link.isNotBlank()
                    ) {

                        openWebsite(
                            trend.link
                        )
                    }
                }

                val rowParams =
                    android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                rowParams.setMargins(
                    0,
                    0,
                    0,
                    dp(4)
                )

                trendsList.addView(
                    trendRow,
                    rowParams
                )
    }

    // Translates the Google Trends titles to English (tap again to
    // go back to the original language).
    private fun setupTrendsTranslate() {

        val button = findViewById<TextView>(R.id.trendsTranslateButton)

        button.setOnClickListener {

            if (trendTitleViews.isEmpty()) {
                toast("Trends are still loading")
                return@setOnClickListener
            }

            if (trendsTranslated) {

                trendTitleViews.forEach { (view, article) ->
                    view.text = article.title
                }

                trendsTranslated = false
                button.text = "\uD83C\uDF10 Translate"

                return@setOnClickListener
            }

            button.text = "Translating..."
            button.isEnabled = false

            val snapshot = trendTitleViews.toList()

            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.Main
            ).launch {

                val translated =
                    newsRepository.translateToEnglish(
                        snapshot.map { it.second.title }
                    )

                button.isEnabled = true

                if (translated == null) {
                    button.text = "\uD83C\uDF10 Translate"
                    toast("Translation unavailable. Check your connection.")
                    return@launch
                }

                snapshot.forEachIndexed { index, (view, _) ->
                    view.text = translated[index]
                }

                trendsTranslated = true
                button.text = "Show original"
            }
        }
    }

    private fun addNewsCard(
        container: android.widget.LinearLayout,
        article: NewsArticle
    ) {

        val card =
            android.widget.LinearLayout(this)

        card.orientation =
            android.widget.LinearLayout.HORIZONTAL
        card.tag = "news_card"

        card.setPadding(
            dp(10),
            dp(10),
            dp(10),
            dp(10)
        )

        card.background =
            roundedBg(getThemeSurface2Color(), 12, false)

        val cardParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        cardParams.setMargins(
            0,
            0,
            0,
            dp(8)
        )

        card.layoutParams =
            cardParams

        val imageView =
            android.widget.ImageView(this)

        val imageParams =
            android.widget.LinearLayout.LayoutParams(
                dp(84),
                dp(64)
            )

        imageParams.setMargins(
            0,
            0,
            dp(10),
            0
        )

        imageView.layoutParams =
            imageParams

        imageView.scaleType =
            android.widget.ImageView.ScaleType.CENTER_CROP

        imageView.setBackgroundColor(
            getThemeBorderColor()
        )

        val textContainer =
            android.widget.LinearLayout(this)

        textContainer.orientation =
            android.widget.LinearLayout.VERTICAL

        textContainer.layoutParams =
            android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

        val title =
            android.widget.TextView(this)

        title.text =
            article.title

        title.setTextColor(
            getThemeTextColor()
        )

        title.textSize =
            13f

        title.setTypeface(
            null,
            android.graphics.Typeface.BOLD
        )

        title.maxLines =
            3

        title.ellipsize =
            android.text.TextUtils.TruncateAt.END

        val source =
            android.widget.TextView(this)

        source.text =
            if (article.source.isNotBlank()) {
                article.source
            } else {
                "News"
            }

        source.setTextColor(
            getThemeAccentColor()
        )

        source.tag =
            "accent"

        source.textSize =
            11f

        source.setPadding(
            0,
            dp(5),
            0,
            0
        )

        textContainer.addView(
            title
        )

        textContainer.addView(
            source
        )

        card.addView(
            imageView
        )

        card.addView(
            textContainer
        )

        card.setOnClickListener {

            openWebsite(
                article.link
            )
        }

        container.addView(
            card
        )

        if (
            article.imageUrl.isNotBlank()
        ) {

            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.Dispatchers.IO
            ).launch {

                try {

                    val connection =
                        java.net.URL(
                            article.imageUrl
                        ).openConnection()

                    connection.connect()

                    val input =
                        connection.getInputStream()

                    val bitmap =
                        android.graphics.BitmapFactory
                            .decodeStream(input)

                    input.close()

                    runOnUiThread {

                        if (bitmap != null) {

                            imageView.setImageBitmap(
                                bitmap
                            )
                        }
                    }

                } catch (_: Exception) {

                    // Keep placeholder
                }
            }
        }
    }

    // =========================================================
    // THEME SELECTOR
    // =========================================================

    private fun showThemeSelector() {

        val themes = arrayOf(
            "Chrome Light",
            "Midnight",
            "Graphite",
            "Ocean",
            "Rose"
        )

        val currentTheme =
            preferences.getString(
                "app_theme",
                "Chrome Light"
            )

        var selectedIndex =
            themes.indexOf(currentTheme)

        if (selectedIndex < 0) {
            selectedIndex = 0
        }

        themedDialog()
            .setTitle("Choose Theme")
            .setSingleChoiceItems(
                themes,
                selectedIndex
            ) { dialog, which ->

                preferences.edit()
                    .putString(
                        "app_theme",
                        themes[which]
                    )
                    .apply()

                applyAppTheme()

                Toast.makeText(
                    this,
                    "${themes[which]} selected",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
            .setNegativeButton(
                "Cancel",
                null
            )
            .show()
    }

    // =========================================================
    // APPLY THEME
    // =========================================================

    private fun applyAppTheme() {
        val theme = preferences.getString("app_theme", "Chrome Light")

        val backgroundColor: Int
        val surfaceColor: Int
        val surface2Color: Int
        val textColor: Int
        val mutedColor: Int
        val accentColor: Int

        when (theme) {
            "Midnight" -> {
                backgroundColor = Color.parseColor("#0F1115")
                surfaceColor = Color.parseColor("#171A20")
                surface2Color = Color.parseColor("#20252E")
                textColor = Color.parseColor("#F8FAFC")
                mutedColor = Color.parseColor("#AAB4C3")
                accentColor = Color.parseColor("#8AB4F8")
            }

            "Graphite" -> {
                backgroundColor = Color.parseColor("#12161C")
                surfaceColor = Color.parseColor("#1A2028")
                surface2Color = Color.parseColor("#242C36")
                textColor = Color.parseColor("#F1F5F9")
                mutedColor = Color.parseColor("#A7B0BD")
                accentColor = Color.parseColor("#9AA7B8")
            }

            "Ocean" -> {
                backgroundColor = Color.parseColor("#F2F8FB")
                surfaceColor = Color.WHITE
                surface2Color = Color.parseColor("#E7F2F8")
                textColor = Color.parseColor("#18313F")
                mutedColor = Color.parseColor("#607D8B")
                accentColor = Color.parseColor("#0B84C6")
            }

            "Rose" -> {
                backgroundColor = Color.parseColor("#FFF7F8")
                surfaceColor = Color.WHITE
                surface2Color = Color.parseColor("#FBECEF")
                textColor = Color.parseColor("#2D2024")
                mutedColor = Color.parseColor("#8D6D74")
                accentColor = Color.parseColor("#D94F70")
            }

            else -> {
                backgroundColor = Color.parseColor("#F6F7F9")
                surfaceColor = Color.WHITE
                surface2Color = Color.parseColor("#F0F2F5")
                textColor = Color.parseColor("#202124")
                mutedColor = Color.parseColor("#5F6368")
                accentColor = Color.parseColor("#1A73E8")
            }
        }

        // Main backgrounds
        findViewById<View>(android.R.id.content)
            .setBackgroundColor(backgroundColor)

        homePage.setBackgroundColor(backgroundColor)
        settingsPage.setBackgroundColor(backgroundColor)
        webView.setBackgroundColor(backgroundColor)

        // Homepage containers
        homePage.getChildAt(0)
            ?.setBackgroundColor(backgroundColor)

        findViewById<View>(R.id.categoryContainer)
            .setBackgroundColor(backgroundColor)

        findViewById<View>(R.id.siteCategoriesContainer)
            .setBackgroundColor(Color.TRANSPARENT)

        // Homepage content cards
        val categoryRoot = findViewById<GridLayout>(R.id.categoryContainer)
        for (i in 3 until categoryRoot.childCount) {
            val child = categoryRoot.getChildAt(i)
            if (child is LinearLayout) {
                child.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(surfaceColor)
                    setStroke(dp(1), getThemeBorderColor())
                }
            }
        }

        // Address bar
        addressBar.setBackgroundColor(Color.TRANSPARENT)
        addressBar.setTextColor(textColor)
        addressBar.setHintTextColor(mutedColor)

        val bottomBar = findViewById<LinearLayout>(R.id.bottomBar)
        bottomBar.setBackgroundColor(surfaceColor)

        fun tintBottomBar(view: View) {
            if (view is ImageView) view.setColorFilter(mutedColor)
            if (view is TextView && view.id != R.id.pagesCount) {
                view.setTextColor(mutedColor)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    tintBottomBar(view.getChildAt(i))
                }
            }
        }
        tintBottomBar(bottomBar)
        findViewById<TextView>(R.id.pagesCount).setTextColor(Color.WHITE)
        findViewById<ImageView>(R.id.homeButton).setColorFilter(accentColor)
        (findViewById<ImageView>(R.id.homeButton).parent as? ViewGroup)?.let { parent ->
            for (j in 0 until parent.childCount) {
                val v = parent.getChildAt(j)
                if (v is TextView) v.setTextColor(accentColor)
            }
        }

        fun tintHomepageText(view: View) {
            if (view is TextView) {
                when {
                    view.tag == "accent" -> view.setTextColor(accentColor)
                    view.tag == "muted" -> view.setTextColor(mutedColor)
                    view.text.toString().startsWith("More") ||
                        view.text.toString().startsWith("View Google") -> view.setTextColor(accentColor)
                    view.text.toString().startsWith("Loading") ||
                        view.text.toString() == "Worldwide" -> view.setTextColor(mutedColor)
                    else -> view.setTextColor(textColor)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    tintHomepageText(view.getChildAt(i))
                }
            }
        }

        for (i in 3 until categoryRoot.childCount) {
            tintHomepageText(categoryRoot.getChildAt(i))
        }

        fun refreshHomepageComponents(view: View) {
            when (view.tag) {
                "site_card" -> {
                    view.background = roundedBg(surfaceColor, 12)
                }
                "site_name" -> {
                    (view as? TextView)?.setTextColor(textColor)
                }
                "main_header" -> {
                    (view as? TextView)?.setTextColor(textColor)
                }
                "sub_header" -> {
                    (view as? TextView)?.setTextColor(mutedColor)
                }
                "action_button" -> {
                    view.background = roundedBg(surface2Color, 12, false)
                    (view as? TextView)?.setTextColor(accentColor)
                }
                "trend_row" -> {
                    view.background = roundedBg(surface2Color, 10, false)
                }
                "news_card" -> {
                    view.background = roundedBg(surface2Color, 12, false)
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    refreshHomepageComponents(view.getChildAt(i))
                }
            }
        }
        refreshHomepageComponents(categoryRoot)

        val searchBar = addressBar.parent as? View

        searchBar?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(27).toFloat()
            setColor(surfaceColor)
            setStroke(
                dp(1),
                getThemeBorderColor()
            )
        }

        // Send button: round gradient button with a paper-plane icon
        findViewById<View>(R.id.goButton).background =
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    accentColor,
                    Color.parseColor("#7C4DFF")
                )
            ).apply {
                shape = GradientDrawable.OVAL
            }
        findViewById<ImageView>(R.id.goButton).setColorFilter(Color.WHITE)

        // Live clock colours follow the theme
        findViewById<TextView>(R.id.clockTime).setTextColor(textColor)
        findViewById<TextView>(R.id.clockDate).setTextColor(mutedColor)

        // Settings background
        settingsPage.setBackgroundColor(backgroundColor)

        // Settings cards
        val settingsCardIds = listOf(
            R.id.dataSavingSwitch,
            R.id.adBlockingSwitch,
            R.id.vpnCard,
            R.id.historyButton,
            R.id.bookmarksButton,
            R.id.offlinePagesButton,
            R.id.clearCacheButton,
            R.id.themeButton,
            R.id.downloadsButton,
            R.id.shareButton,
            R.id.translateButton,
            R.id.settingsBackButton
        )

        settingsCardIds.forEach { id ->
            val view = findViewById<View>(id)
            val card = if (id == R.id.dataSavingSwitch || id == R.id.adBlockingSwitch) {
                view.parent as? View
            } else {
                view
            }

            card?.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1), getThemeBorderColor())
            }

            if (view is TextView) {
                view.setTextColor(
                    if (id == R.id.settingsBackButton) accentColor else textColor
                )
            }
        }

        // Make every settings label readable in every theme.
        fun tintSettingsText(view: View) {
            if (view is TextView && view.id != R.id.settingsBackButton) {
                view.setTextColor(
                    if (view.text.toString().contains("Customize") ||
                        view.text.toString().contains("Reduce") ||
                        view.text.toString().contains("Block common") ||
                        view.text.toString().contains("Private, device-wide") ||
                        view.text.toString().contains("future update")
                    ) mutedColor else textColor
                )
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    tintSettingsText(view.getChildAt(i))
                }
            }
        }
        tintSettingsText(settingsPage)

        // "Coming soon" badge on the VPN card
        findViewById<TextView>(R.id.vpnComingSoonBadge).apply {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(accentColor)
            }
        }

        // Switches use the theme accent color
        listOf(dataSavingSwitch, adBlockingSwitch, vpnSwitch).forEach { sw ->
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            )
            sw.thumbTintList = ColorStateList(
                states,
                intArrayOf(accentColor, mutedColor)
            )
            sw.trackTintList = ColorStateList(
                states,
                intArrayOf(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(accentColor, 110),
                    androidx.core.graphics.ColorUtils.setAlphaComponent(mutedColor, 90)
                )
            )
        }

        // "Translate" chip in the Google Trends header
        findViewById<TextView>(R.id.trendsTranslateButton).apply {
            setTextColor(accentColor)
            background = roundedBg(surface2Color, 14)
        }

        // System bars (icons stay readable in light and dark themes)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme()
            isAppearanceLightNavigationBars = !isDarkTheme()
        }
    }

    // =========================================================
    // THEME COLORS
    // =========================================================

    private fun getThemeTextColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#F8FAFC")
            "Graphite" -> Color.parseColor("#F1F5F9")
            "Ocean" -> Color.parseColor("#18313F")
            "Rose" -> Color.parseColor("#2D2024")
            else -> Color.parseColor("#202124")
        }
    }

    private fun getThemeMutedColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#AAB4C3")
            "Graphite" -> Color.parseColor("#A7B0BD")
            "Ocean" -> Color.parseColor("#607D8B")
            "Rose" -> Color.parseColor("#8D6D74")
            else -> Color.parseColor("#5F6368")
        }
    }

    private fun getThemeBorderColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#2B3442")
            "Graphite" -> Color.parseColor("#303946")
            "Ocean" -> Color.parseColor("#CDE8F5")
            "Rose" -> Color.parseColor("#F0D9DE")
            else -> Color.parseColor("#E1E5EA")
        }
    }

    private fun getThemeSurfaceColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#171A20")
            "Graphite" -> Color.parseColor("#1A2028")
            "Ocean" -> Color.WHITE
            "Rose" -> Color.WHITE
            else -> Color.WHITE
        }
    }

    private fun getThemeSurface2Color(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#20252E")
            "Graphite" -> Color.parseColor("#242C36")
            "Ocean" -> Color.parseColor("#E7F2F8")
            "Rose" -> Color.parseColor("#FBECEF")
            else -> Color.parseColor("#F0F2F5")
        }
    }

    private fun getThemeAccentColor(): Int {
        return when (preferences.getString("app_theme", "Chrome Light")) {
            "Midnight" -> Color.parseColor("#8AB4F8")
            "Graphite" -> Color.parseColor("#9AA7B8")
            "Ocean" -> Color.parseColor("#0B84C6")
            "Rose" -> Color.parseColor("#D94F70")
            else -> Color.parseColor("#1A73E8")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_PERMISSION_REQUEST) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "VPN permission granted. Connect after a server is configured.", Toast.LENGTH_LONG).show()
            } else {
                vpnSwitch.isChecked = false
            }
        }
    }

    companion object {
        private const val VPN_PERMISSION_REQUEST = 9401
        private const val STORAGE_PERMISSION_REQUEST = 9402

        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 15; Mobile) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/153.0.0.0 Mobile Safari/537.36"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/153.0.0.0 Safari/537.36"

        // Layout width (in CSS px) used for desktop-mode sites.
        // Lower = bigger text, higher = more of the page visible.
        private const val DESKTOP_VIEWPORT_WIDTH = 820

        // Sites opened in desktop ("Windows") mode.
        private val DESKTOP_HOSTS = listOf(
            "facebook.com",
            "fb.com",
            "tiktok.com",
            "whatsapp.com"
        )
    }

}
