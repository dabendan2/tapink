package com.dabendan.tapink

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ShareNfcActivity : Activity() {

    enum class UiState {
        PREPARING,
        NFC_DISABLED,
        WAITING_NFC,
        TRANSFERRING,
        SUCCESS,
        ERROR
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerExecutor = Executors.newSingleThreadExecutor()
    private val isTransferring = AtomicBoolean(false)

    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null
    private var isReaderModeActive = false
    private var isActivityResumed = false
    private var allowMockNfc = false
    private var detectTimeoutMs = 25000L
    @Volatile private var pendingDiscoveredTag: Tag? = null

    private val readerModeReassertDelaysMs = longArrayOf(250L, 500L, 750L, 1050L, 1500L, 2200L)
    private val readerModeReassertRunnables = mutableListOf<Runnable>()
    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag: Tag ->
        onPhysicalNfcTagDiscovered(tag)
    }

    private var rawSourceBitmap: Bitmap? = null
    private var orientedSourceBitmap: Bitmap? = null
    private var isLandscapePreferred: Boolean = false
    private var cachedDitherResult: FloydSteinbergDitherer.DitherResult? = null
    private var currentState: UiState = UiState.PREPARING
    private var currentProgress: Int = 0

    // Root Backdrop & Modal Bottom Sheet
    private lateinit var scrimRoot: FrameLayout
    private lateinit var bottomSheetCard: LinearLayout

    // Zone 1: Centered Header Zone
    private lateinit var headerTitleView: TextView
    private lateinit var headerSubtitleView: TextView

    // Zone 2: Central Hero Visual Zone (256dp fixed height)
    private lateinit var heroZoneContainer: FrameLayout
    private lateinit var rippleCanvasView: RippleAndPerimeterView
    private lateinit var epaperCardWrapper: FrameLayout
    private lateinit var epaperBezelFrame: LinearLayout
    private lateinit var previewImageView: ImageView
    private lateinit var errorOverlayPill: TextView
    private lateinit var successCheckBadge: TextView
    private lateinit var hardwareSpecPill: TextView

    // Zone 3: Action Footer Zone
    private lateinit var footerZone: FrameLayout
    private lateinit var readyFooterRow: LinearLayout
    private lateinit var portraitSegmentBtn: TextView
    private lateinit var landscapeSegmentBtn: TextView
    private lateinit var cancelTextBtn: Button
    private lateinit var errorFooterRow: LinearLayout
    private lateinit var closeOutlinedBtn: Button
    private lateinit var retryTonalBtn: Button

    private var rippleAnimator: ValueAnimator? = null

    private val detectTimeoutRunnable = Runnable {
        if (currentState == UiState.WAITING_NFC && !isTransferring.get()) {
            handleRecoverableError(
                rawError = "逾時未偵測到 NFC 電子紙 (${detectTimeoutMs / 1000}秒)",
                userHeadline = getString(R.string.ui_err_timeout_headline),
                userSubtitle = getString(R.string.ui_err_timeout_subtitle)
            )
        }
    }

    /**
     * Supports Zero-Click Auto-Retry: accepts NFC scan in both WAITING_NFC and ERROR states!
     */
    private val simulateNfcReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_SIMULATE_NFC_TAG) return
            val mode = intent.getStringExtra("mode") ?: SimulatedIsoDepTransceiver.MODE_SUCCESS_420_BWRY
            val busyPolls = intent.getIntExtra("busy_polls", 2)
            LogManager.i(TAG, "收到模擬 NFC 感應廣播: mode=$mode (currentState=$currentState)")

            if (currentState != UiState.WAITING_NFC && currentState != UiState.ERROR) {
                LogManager.w(TAG, "忽略模擬感應廣播：目前狀態為 $currentState")
                return
            }

            if (mode == "NON_ISODEP") {
                handleRecoverableError(
                    rawError = "不支援的 NFC 標籤類型 (Tag 不支援 IsoDep / ISO 14443-4)",
                    userHeadline = getString(R.string.ui_err_unsupported_headline),
                    userSubtitle = getString(R.string.ui_err_unsupported_subtitle)
                )
                return
            }

            val simTransceiver = SimulatedIsoDepTransceiver(mode = mode, busyPollCountBeforeIdle = busyPolls)
            startTransferWithTransceiver(simTransceiver, isSimulated = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 21) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.parseColor(COLOR_SURFACE_CONTAINER_LOW)
        }

        allowMockNfc = intent?.getBooleanExtra("allow_mock_nfc", false) ?: false
        detectTimeoutMs = intent?.getLongExtra("nfc_detect_timeout_ms", 25000L) ?: 25000L

        buildModalBottomSheetUi()
        startRippleAnimation()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) 0x02000000 else 0)
        nfcPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            piFlags
        )
        registerSimulationReceiver()

        LogManager.i(
            TAG,
            "ShareNfcActivity onCreate: action=${intent?.action}, type=${intent?.type}, allowMockNfc=$allowMockNfc"
        )
        syncServiceState("PREPARING", "NONE")

        prepareSharedImage(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        val action = intent.action
        val nfcTag: Tag? = @Suppress("DEPRECATION") intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (nfcTag != null ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            LogManager.i(TAG, "onNewIntent 收到 NFC Tag 派發 (action=$action, tag=$nfcTag)")
            if (nfcTag != null) {
                onPhysicalNfcTagDiscovered(nfcTag)
            }
            return
        }

        LogManager.i(TAG, "onNewIntent 收到新分享 Intent: action=$action, type=${intent.type}")
        setIntent(intent)
        prepareSharedImage(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE) {
            val pickedUri = data?.data
            if (resultCode == RESULT_OK && pickedUri != null) {
                val pickIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, pickedUri)
                }
                prepareSharedImage(pickIntent)
            } else if (rawSourceBitmap == null) {
                closeActivityNow()
            }
        }
    }

    private fun buildModalBottomSheetUi() {
        // Translucent Window Scrim (36% Black backdrop, tap outside to dismiss when idle)
        scrimRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#5C000000"))
            setOnClickListener {
                if (currentState == UiState.WAITING_NFC || currentState == UiState.ERROR || currentState == UiState.NFC_DISABLED) {
                    LogManager.i(TAG, "使用者點擊面板外部遮罩關閉視窗")
                    closeActivityNow()
                }
            }
        }

        // Modal Bottom Sheet Container (Top 28dp rounded corners, SurfaceContainerLow #191C20)
        bottomSheetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            background = topRoundedSheetBackground(dp(28), Color.parseColor(COLOR_SURFACE_CONTAINER_LOW))
            setPadding(dp(24), dp(12), dp(24), dp(24))
            if (Build.VERSION.SDK_INT >= 21) {
                elevation = dp(16).toFloat()
            }
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            layoutParams = lp
        }

        // Top Drag Handle (32dp x 4dp, onSurfaceVariant 40% alpha)
        val dragHandle = View(this).apply {
            background = roundedRect(dp(2), Color.parseColor("#66C4C6D0"))
            val lp = LinearLayout.LayoutParams(dp(32), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(18)
            }
            layoutParams = lp
        }
        bottomSheetCard.addView(dragHandle)

        // =====================================================================
        // ZONE 1: Header Zone (Centered Headline + Centered Supporting Text)
        // =====================================================================
        headerTitleView = TextView(this).apply {
            text = getString(R.string.ui_ready_headline)
            contentDescription = "status_text"
            setTextColor(Color.parseColor(COLOR_ON_SURFACE))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        bottomSheetCard.addView(headerTitleView)

        headerSubtitleView = TextView(this).apply {
            text = getString(R.string.ui_ready_subtitle)
            contentDescription = "status_subtitle"
            setTextColor(Color.parseColor(COLOR_ON_SURFACE_VARIANT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1.0f)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }
        bottomSheetCard.addView(headerSubtitleView)

        // =====================================================================
        // ZONE 2: Central Hero Visual Zone (Fixed 256dp height)
        // =====================================================================
        heroZoneContainer = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(256)
            ).apply {
                bottomMargin = dp(16)
            }
            layoutParams = lp
        }

        // Background Breathing Ripples + Perimeter Progress Canvas
        rippleCanvasView = RippleAndPerimeterView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        heroZoneContainer.addView(rippleCanvasView)

        // Center Column holding the E-Paper Card + Hardware Spec Pill
        val heroCenterCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        epaperCardWrapper = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Physical E-Paper Casing (SurfaceContainerHighest / Matte Bezel with 1dp outlineVariant)
        // Long-press on the E-Paper preview card triggers Demo/Simulation Preview Mode for reviewers without physical NFC hardware
        epaperBezelFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedRect(
                radiusPx = dp(16),
                fillColor = Color.parseColor(COLOR_SURFACE_CONTAINER_HIGHEST),
                strokeWidthPx = dp(1),
                strokeColor = Color.parseColor(COLOR_OUTLINE_VARIANT)
            )
            setPadding(dp(10), dp(10), dp(10), dp(10))
            if (Build.VERSION.SDK_INT >= 21) {
                elevation = dp(4).toFloat()
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setOnLongClickListener {
                if (currentState == UiState.WAITING_NFC || currentState == UiState.ERROR || currentState == UiState.NFC_DISABLED) {
                    LogManager.i(TAG, "使用者長按預覽卡片，啟動無硬體展示預覽模式 (Demo 4.2\" BWRY)")
                    triggerHapticFeedback()
                    val simTransceiver = SimulatedIsoDepTransceiver(
                        mode = SimulatedIsoDepTransceiver.MODE_SUCCESS_420_BWRY,
                        busyPollCountBeforeIdle = 4
                    )
                    startTransferWithTransceiver(simTransceiver, isSimulated = true)
                    true
                } else {
                    false
                }
            }
        }

        previewImageView = ImageView(this).apply {
            contentDescription = "dither_preview"
            scaleType = ImageView.ScaleType.FIT_XY
            background = roundedRect(dp(6), Color.parseColor("#22242A"))
            layoutParams = LinearLayout.LayoutParams(dp(135), dp(180))
        }
        epaperBezelFrame.addView(previewImageView)
        epaperCardWrapper.addView(epaperBezelFrame)

        // Error Overlay Pill centered on the E-Paper card: "[ ! 請重新貼靠電子紙 ]"
        errorOverlayPill = TextView(this).apply {
            text = getString(R.string.ui_error_overlay_pill)
            contentDescription = "error_overlay_pill"
            setTextColor(Color.parseColor(COLOR_ON_ERROR_CONTAINER))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = roundedRect(
                radiusPx = dp(20),
                fillColor = Color.parseColor(COLOR_ERROR_CONTAINER),
                strokeWidthPx = dp(1),
                strokeColor = Color.parseColor(COLOR_ERROR)
            )
            setPadding(dp(14), dp(8), dp(14), dp(8))
            if (Build.VERSION.SDK_INT >= 21) {
                elevation = dp(8).toFloat()
            }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        epaperCardWrapper.addView(errorOverlayPill)

        // Success Checkmark Badge on bottom-right corner of the E-Paper card
        successCheckBadge = TextView(this).apply {
            text = "✓"
            contentDescription = "success_check_badge"
            setTextColor(Color.parseColor(COLOR_ON_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedRect(dp(20), Color.parseColor(COLOR_PRIMARY))
            if (Build.VERSION.SDK_INT >= 21) {
                elevation = dp(8).toFloat()
            }
            visibility = View.GONE
            val lp = FrameLayout.LayoutParams(dp(38), dp(38), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(-8)
                bottomMargin = dp(-8)
            }
            layoutParams = lp
        }
        epaperCardWrapper.addView(successCheckBadge)

        heroCenterCol.addView(epaperCardWrapper)

        // Hardware Spec Pill (Appears upon NFC detection, e.g. "2.9" 黑白雙色")
        hardwareSpecPill = TextView(this).apply {
            text = ""
            contentDescription = "spec_badge"
            setTextColor(Color.parseColor(COLOR_PRIMARY))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = roundedRect(dp(12), Color.parseColor("#1E293B"))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            visibility = View.INVISIBLE
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
            layoutParams = lp
        }
        heroCenterCol.addView(hardwareSpecPill)
        heroZoneContainer.addView(heroCenterCol)
        bottomSheetCard.addView(heroZoneContainer)

        // =====================================================================
        // ZONE 3: Action Footer Zone (56dp height)
        // =====================================================================
        footerZone = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            )
        }

        // 3A. Ready State Footer: [ 直向 | 橫向 ] Segmented Button on left + "取消" Text Button on right
        readyFooterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val segmentedGroup = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(
                radiusPx = dp(20),
                fillColor = Color.parseColor("#22252C"),
                strokeWidthPx = dp(1),
                strokeColor = Color.parseColor(COLOR_OUTLINE_VARIANT)
            )
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            )
        }

        portraitSegmentBtn = TextView(this).apply {
            text = getString(R.string.ui_btn_portrait)
            contentDescription = "orientation_portrait_btn"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { setOrientationPreference(landscape = false) }
        }

        landscapeSegmentBtn = TextView(this).apply {
            text = getString(R.string.ui_btn_landscape)
            contentDescription = "orientation_landscape_btn"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), 0, dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { setOrientationPreference(landscape = true) }
        }

        segmentedGroup.addView(portraitSegmentBtn)
        segmentedGroup.addView(landscapeSegmentBtn)
        readyFooterRow.addView(segmentedGroup)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        readyFooterRow.addView(spacer)

        cancelTextBtn = Button(this).apply {
            text = getString(R.string.ui_btn_cancel)
            contentDescription = "cancel_button"
            isAllCaps = false
            setTextColor(Color.parseColor(COLOR_ON_SURFACE_VARIANT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = roundedRect(dp(20), Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= 21) {
                stateListAnimator = null
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
            )
            setOnClickListener {
                LogManager.i(TAG, "使用者點擊「取消」按鈕")
                closeActivityNow()
            }
        }
        readyFooterRow.addView(cancelTextBtn)
        footerZone.addView(readyFooterRow)

        // 3B. Error / NFC Disabled Footer: Outlined "關閉" + Filled Tonal "重新感應"
        errorFooterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        closeOutlinedBtn = Button(this).apply {
            text = getString(R.string.ui_btn_close)
            contentDescription = "close_button"
            isAllCaps = false
            setTextColor(Color.parseColor(COLOR_ON_SURFACE_VARIANT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = roundedRect(
                radiusPx = dp(24),
                fillColor = Color.TRANSPARENT,
                strokeWidthPx = dp(1),
                strokeColor = Color.parseColor(COLOR_OUTLINE_VARIANT)
            )
            if (Build.VERSION.SDK_INT >= 21) {
                stateListAnimator = null
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener {
                LogManager.i(TAG, "使用者點擊「關閉」按鈕")
                closeActivityNow()
            }
        }

        retryTonalBtn = Button(this).apply {
            text = getString(R.string.ui_btn_retry)
            contentDescription = "retry_button"
            isAllCaps = false
            setTextColor(Color.parseColor(COLOR_ON_SECONDARY_CONTAINER))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = roundedRect(dp(24), Color.parseColor(COLOR_SECONDARY_CONTAINER))
            if (Build.VERSION.SDK_INT >= 21) {
                stateListAnimator = null
            }
            val lp = LinearLayout.LayoutParams(0, dp(48), 1.3f).apply {
                leftMargin = dp(12)
            }
            layoutParams = lp
            setOnClickListener {
                onRetryOrOpenNfcClicked()
            }
        }

        errorFooterRow.addView(closeOutlinedBtn)
        errorFooterRow.addView(retryTonalBtn)
        footerZone.addView(errorFooterRow)

        bottomSheetCard.addView(footerZone)
        scrimRoot.addView(bottomSheetCard)
        setContentView(scrimRoot)
    }

    private fun updateSegmentButtonsVisual() {
        if (!isLandscapePreferred) {
            portraitSegmentBtn.setTextColor(Color.parseColor(COLOR_ON_SECONDARY_CONTAINER))
            portraitSegmentBtn.background = roundedRect(dp(17), Color.parseColor(COLOR_SECONDARY_CONTAINER))
            landscapeSegmentBtn.setTextColor(Color.parseColor(COLOR_ON_SURFACE_VARIANT))
            landscapeSegmentBtn.background = null
        } else {
            landscapeSegmentBtn.setTextColor(Color.parseColor(COLOR_ON_SECONDARY_CONTAINER))
            landscapeSegmentBtn.background = roundedRect(dp(17), Color.parseColor(COLOR_SECONDARY_CONTAINER))
            portraitSegmentBtn.setTextColor(Color.parseColor(COLOR_ON_SURFACE_VARIANT))
            portraitSegmentBtn.background = null
        }
    }

    private fun setOrientationPreference(landscape: Boolean) {
        if (currentState != UiState.WAITING_NFC) return
        val raw = rawSourceBitmap ?: return
        if (isLandscapePreferred == landscape) return
        isLandscapePreferred = landscape
        updateSegmentButtonsVisual()
        cachedDitherResult = null

        val currentlyLandscape = raw.width > raw.height
        orientedSourceBitmap = if (currentlyLandscape == landscape) {
            raw
        } else {
            val m = Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        }
        orientedSourceBitmap?.let { bmp ->
            val previewBmp = createPreScanPreviewBitmap(bmp)
            updateHeroCardAspectAndBitmap(previewBmp, applyDesaturation = true)
        }
        enableNfcReaderModeIfAvailable()
        scheduleReaderModeReassertions()
    }

    private fun createPreScanPreviewBitmap(src: Bitmap): Bitmap {
        val isPortrait = src.height >= src.width
        val targetW = if (isPortrait) 300 else 400
        val targetH = if (isPortrait) 400 else 300
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = maxOf(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat())
        val dx = (targetW - src.width * scale) * 0.5f
        val dy = (targetH - src.height * scale) * 0.5f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun updateHeroCardAspectAndBitmap(bmp: Bitmap, applyDesaturation: Boolean) {
        beginSmoothSheetTransition()
        val maxSide = dp(164)
        val scale = minOf(
            maxSide.toFloat() / bmp.width.toFloat(),
            maxSide.toFloat() / bmp.height.toFloat()
        )
        val viewW = (bmp.width * scale).toInt().coerceAtLeast(dp(68))
        val viewH = (bmp.height * scale).toInt().coerceAtLeast(dp(68))

        previewImageView.layoutParams = LinearLayout.LayoutParams(viewW, viewH)
        previewImageView.setImageBitmap(bmp)

        if (applyDesaturation) {
            // 85% saturation before NFC scan to subtly hint pending E-Ink conversion
            val cm = ColorMatrix().apply { setSaturation(0.85f) }
            previewImageView.colorFilter = ColorMatrixColorFilter(cm)
        } else {
            previewImageView.colorFilter = null
        }

        epaperCardWrapper.post {
            rippleCanvasView.updateTargetCardBounds(epaperBezelFrame, heroZoneContainer)
        }
    }

    private fun prepareSharedImage(incomingIntent: Intent?) {
        if (incomingIntent?.action == Intent.ACTION_MAIN &&
            incomingIntent.getStringExtra("image_path").isNullOrBlank() &&
            @Suppress("DEPRECATION") incomingIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) == null &&
            incomingIntent.data == null
        ) {
            LogManager.i(TAG, "從桌面圖示啟動 (ACTION_MAIN)，自動開啟系統圖片選擇器")
            headerTitleView.text = getString(R.string.ui_pick_image_headline)
            headerSubtitleView.text = getString(R.string.ui_pick_image_subtitle)
            try {
                val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(Intent.createChooser(pickIntent, getString(R.string.ui_picker_chooser_title)), REQ_PICK_IMAGE)
                return
            } catch (t: Throwable) {
                LogManager.e(TAG, "無法開啟圖片選擇器: ${t.message}", t)
            }
        }

        applySheetState(
            state = UiState.PREPARING,
            headline = getString(R.string.ui_ready_headline),
            subtitle = getString(R.string.ui_ready_subtitle)
        )

        workerExecutor.execute {
            try {
                val bitmap = loadBitmapFromIntent(incomingIntent)
                    ?: throw IllegalArgumentException("未收到有效的分享圖片")

                rawSourceBitmap = bitmap
                isLandscapePreferred = bitmap.width > bitmap.height
                orientedSourceBitmap = bitmap

                LogManager.i(TAG, "成功解碼分享圖片: ${bitmap.width}x${bitmap.height}")
                val preScanBmp = createPreScanPreviewBitmap(bitmap)

                mainHandler.post {
                    updateSegmentButtonsVisual()
                    updateHeroCardAspectAndBitmap(preScanBmp, applyDesaturation = true)
                    enterReadyToScanState()
                }
            } catch (t: Throwable) {
                mainHandler.post {
                    handleRecoverableError(
                        rawError = "圖片讀取失敗: ${t.message}",
                        userHeadline = getString(R.string.ui_err_image_headline),
                        userSubtitle = getString(R.string.ui_err_image_subtitle),
                        throwable = t
                    )
                }
            }
        }
    }

    private fun loadBitmapFromIntent(incomingIntent: Intent?): Bitmap? {
        if (incomingIntent == null) return null

        val filePath = incomingIntent.getStringExtra("image_path")
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("指定的圖片檔案不存在: $filePath")
            }
            return BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IllegalArgumentException("無法解碼圖片檔案: $filePath")
        }

        var uri: Uri? = @Suppress("DEPRECATION") incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null && incomingIntent.clipData != null && incomingIntent.clipData!!.itemCount > 0) {
            uri = incomingIntent.clipData!!.getItemAt(0).uri
        }
        if (uri == null) {
            uri = incomingIntent.data
        }
        if (uri == null) return null

        LogManager.i(TAG, "讀取分享圖片 URI: $uri")
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IllegalArgumentException("空白的 file:// URI 路徑")
            return BitmapFactory.decodeFile(path)
                ?: throw IllegalArgumentException("無法解碼圖片檔案: $path")
        }

        var inputStream: InputStream? = null
        try {
            inputStream = contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("無法開啟圖片資料流: $uri")
            return BitmapFactory.decodeStream(inputStream)
                ?: throw IllegalArgumentException("無法解碼圖片資料流: $uri")
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun enterReadyToScanState() {
        val adapter = nfcAdapter
        if ((adapter == null || !adapter.isEnabled) && !allowMockNfc) {
            LogManager.e(TAG, "NFC 未開啟或無 NFC 硬體 (adapter=$adapter)")
            syncServiceState("ERROR", "需要開啟 NFC 功能")
            applySheetState(
                state = UiState.NFC_DISABLED,
                headline = getString(R.string.ui_nfc_disabled_headline),
                subtitle = getString(R.string.ui_nfc_disabled_subtitle)
            )
            return
        }

        enableNfcReaderModeIfAvailable()
        scheduleReaderModeReassertions()
        orientedSourceBitmap?.let { bmp ->
            val preScanBmp = createPreScanPreviewBitmap(bmp)
            updateHeroCardAspectAndBitmap(preScanBmp, applyDesaturation = true)
        }
        hardwareSpecPill.visibility = View.INVISIBLE

        applySheetState(
            state = UiState.WAITING_NFC,
            headline = getString(R.string.ui_ready_headline),
            subtitle = getString(R.string.ui_ready_subtitle)
        )
        LogManager.i(TAG, "進入 WAITING_NFC 狀態，等待感應電子紙 (timeout=${detectTimeoutMs}ms)")

        mainHandler.removeCallbacks(detectTimeoutRunnable)
        mainHandler.postDelayed(detectTimeoutRunnable, detectTimeoutMs)

        val queuedTag = pendingDiscoveredTag
        if (queuedTag != null) {
            pendingDiscoveredTag = null
            LogManager.i(TAG, "處理圖片準備期間預先感應到的 NFC Tag")
            onPhysicalNfcTagDiscovered(queuedTag)
        }
    }

    private fun cancelReaderModeReassertions() {
        for (r in readerModeReassertRunnables) {
            mainHandler.removeCallbacks(r)
        }
        readerModeReassertRunnables.clear()
    }

    private fun shouldHoldNfcReaderMode(): Boolean {
        if (!isActivityResumed || isTransferring.get()) return false
        if (currentState == UiState.WAITING_NFC || currentState == UiState.ERROR) return true
        if (currentState == UiState.PREPARING && intent?.action == Intent.ACTION_SEND) return true
        return false
    }

    /**
     * Android 16 System Share Sheet (`ChooserActivityLauncher` + `com.google.android.gms` Quick Share)
     * holds NFC ReaderMode (`flags=387`) while open and calls `NfcAdapter.disableReaderMode(null)` (`flags=0`)
     * when `ChooserActivityLauncher` finishes its exit transition (`onStop`/`onDestroy`, ~400-800ms after
     * `ShareNfcActivity` starts). Because `NfcService.setReaderMode(flags=0)` from privileged UID `gms`
     * unconditionally resets `NfcService.mReaderModeParams = null`, we re-assert ReaderMode across the
     * Share Sheet teardown window to ensure `ShareNfcActivity` retains NFC ReaderMode ownership.
     */
    private fun scheduleReaderModeReassertions() {
        cancelReaderModeReassertions()
        if (!isActivityResumed) return
        for (delayMs in readerModeReassertDelaysMs) {
            val r = Runnable {
                if (shouldHoldNfcReaderMode()) {
                    enableNfcReaderModeIfAvailable(quietLog = true)
                }
            }
            readerModeReassertRunnables.add(r)
            mainHandler.postDelayed(r, delayMs)
        }
    }

    private fun enableNfcReaderModeIfAvailable(quietLog: Boolean = false) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        try {
            val options = Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            }
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            adapter.enableReaderMode(
                this,
                nfcReaderCallback,
                flags,
                options
            )
            if (isActivityResumed) {
                nfcPendingIntent?.let { pi ->
                    try {
                        adapter.enableForegroundDispatch(this, pi, null, null)
                    } catch (_: Throwable) {
                    }
                }
            }
            isReaderModeActive = true
            if (!quietLog) {
                LogManager.d(TAG, "NfcAdapter.enableReaderMode 已啟用")
            }
        } catch (t: Throwable) {
            if (!quietLog) {
                handleRecoverableError(
                    rawError = "啟動 NFC ReaderMode 失敗: ${t.message}",
                    userHeadline = getString(R.string.ui_err_reader_headline),
                    userSubtitle = getString(R.string.ui_err_reader_subtitle),
                    throwable = t
                )
            }
        }
    }

    private fun disableNfcReaderMode() {
        cancelReaderModeReassertions()
        val adapter = nfcAdapter ?: return
        try {
            adapter.disableForegroundDispatch(this)
        } catch (_: Throwable) {
        }
        if (!isReaderModeActive) return
        try {
            adapter.disableReaderMode(this)
            isReaderModeActive = false
        } catch (_: Throwable) {
        }
    }

    private fun onPhysicalNfcTagDiscovered(tag: Tag) {
        val techList = tag.techList.joinToString(", ")
        LogManager.i(TAG, "偵測到實體 NFC Tag: techs=[$techList]")

        if (currentState == UiState.PREPARING) {
            LogManager.i(TAG, "圖片尚在解碼準備中，暫存 NFC Tag 待準備完成後立即寫入")
            pendingDiscoveredTag = tag
            return
        }

        // Zero-Click Auto-Retry: allow direct scan in both WAITING_NFC and ERROR states
        if (currentState != UiState.WAITING_NFC && currentState != UiState.ERROR) {
            LogManager.w(TAG, "忽略 NFC Tag：目前狀態為 $currentState")
            return
        }

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            mainHandler.post {
                handleRecoverableError(
                    rawError = "不支援的 NFC 標籤類型 (僅支援 [$techList]，缺少 IsoDep)",
                    userHeadline = getString(R.string.ui_err_unsupported_headline),
                    userSubtitle = getString(R.string.ui_err_unsupported_subtitle)
                )
            }
            return
        }

        startTransferWithTransceiver(RealIsoDepTransceiver(isoDep), isSimulated = false)
    }

    private fun formatHardwarePillLabel(cfg: TapInkProtocol.HardwareConfig): String {
        val inchLabel = when {
            cfg.normWidth == 400 && cfg.normHeight == 300 -> "4.2\""
            cfg.normWidth == 296 && cfg.normHeight == 128 -> "2.9\""
            cfg.normWidth == 250 && cfg.normHeight == 122 -> "2.13\""
            cfg.normWidth == 416 && cfg.normHeight == 240 -> "3.7\""
            cfg.normWidth == 200 && cfg.normHeight == 200 -> "1.54\""
            cfg.normWidth == 600 && cfg.normHeight == 400 -> "3.6\""
            else -> "${cfg.normWidth}×${cfg.normHeight}"
        }
        val colorLabel = when (cfg.colorMode) {
            FloydSteinbergDitherer.ColorMode.BW_2COLOR -> getString(R.string.ui_color_bw)
            FloydSteinbergDitherer.ColorMode.BWR_3COLOR -> getString(R.string.ui_color_bwr)
            FloydSteinbergDitherer.ColorMode.BWRY_4COLOR -> getString(R.string.ui_color_bwry)
            FloydSteinbergDitherer.ColorMode.SPECTRA_6COLOR -> getString(R.string.ui_color_spectra6)
        }
        return "$inchLabel $colorLabel"
    }

    private fun startTransferWithTransceiver(transceiver: IsoDepTransceiver, isSimulated: Boolean) {
        val srcBitmap = orientedSourceBitmap ?: rawSourceBitmap ?: return
        if (!isTransferring.compareAndSet(false, true)) {
            LogManager.w(TAG, "已有傳輸正在進行中，忽略重複感應")
            return
        }

        mainHandler.removeCallbacks(detectTimeoutRunnable)
        mainHandler.post {
            triggerHapticFeedback()
            announceAccessibility(getString(R.string.ui_a11y_transferring))
            currentProgress = 1
            rippleCanvasView.setProgress(1)
            applySheetState(
                state = UiState.TRANSFERRING,
                headline = getString(R.string.ui_transferring_headline),
                subtitle = getString(R.string.ui_transferring_subtitle)
            )
        }

        workerExecutor.execute {
            val refreshWaitMs = if (isSimulated) 150L else 2000L
            val refreshPollMs = if (isSimulated) 150L else 500L
            try {
                val detectedCfg = TapInkProtocol.executeDynamicUpdate(
                    transceiver = transceiver,
                    refreshInitialWaitMs = refreshWaitMs,
                    refreshPollIntervalMs = refreshPollMs,
                    refreshTimeoutMs = 28000L,
                    imageProvider = { cfg ->
                        LogManager.i(TAG, "動態重算 ${cfg.modelSummary} Floyd-Steinberg 抖動影像...")
                        val (adaptedBmp, adaptedRes) = FloydSteinbergDitherer.processBitmapForSpec(
                            source = srcBitmap,
                            hwWidth = cfg.normWidth,
                            hwHeight = cfg.normHeight,
                            colorMode = cfg.colorMode
                        )
                        cachedDitherResult = adaptedRes
                        LogManager.i(
                            TAG,
                            "動態轉換完成 (${cfg.modelSummary}): 輸出=${adaptedRes.width}x${adaptedRes.height}, 封包=${adaptedRes.packedHardwareBytes.size} bytes"
                        )
                        mainHandler.post {
                            hardwareSpecPill.text = formatHardwarePillLabel(cfg)
                            hardwareSpecPill.visibility = View.VISIBLE
                            updateHeroCardAspectAndBitmap(adaptedBmp, applyDesaturation = false)
                        }
                        adaptedRes.packedHardwareBytes
                    }
                ) { _, _, pct, rawMsg ->
                    LogManager.i(TAG, "進度 ($pct%): $rawMsg")
                    mainHandler.post {
                        if (pct > currentProgress) {
                            currentProgress = pct
                            rippleCanvasView.setProgress(pct)
                        }
                    }
                }

                LogManager.i(TAG, "${detectedCfg.modelSummary} NFC 電子紙更新成功！即將自動關閉視窗")

                mainHandler.post {
                    triggerHapticFeedback()
                    announceAccessibility(getString(R.string.ui_a11y_success))
                    currentProgress = 100
                    rippleCanvasView.setProgress(100)
                    syncServiceState("SUCCESS", "NONE")
                    applySheetState(
                        state = UiState.SUCCESS,
                        headline = getString(R.string.ui_success_headline),
                        subtitle = ""
                    )
                    val dismissDelay = if (isSimulated) 450L else 1200L
                    mainHandler.postDelayed({ closeActivityNow() }, dismissDelay)
                }
            } catch (t: Throwable) {
                val rawErr = t.message ?: "NFC 寫入發生未知錯誤 (${t.javaClass.simpleName})"
                val (headline, subtitle) = translateErrorForConsumer(rawErr)
                mainHandler.post {
                    handleRecoverableError(
                        rawError = rawErr,
                        userHeadline = headline,
                        userSubtitle = subtitle,
                        throwable = t
                    )
                }
            } finally {
                transceiver.close()
                isTransferring.set(false)
            }
        }
    }

    private fun translateErrorForConsumer(rawError: String): Pair<String, String> {
        return when {
            rawError.contains("感應斷開") || rawError.contains("Tag was lost") || rawError.contains("通訊中斷") || rawError.contains("刷新期間") ->
                Pair(
                    getString(R.string.ui_err_disconnected_headline),
                    getString(R.string.ui_err_disconnected_subtitle)
                )
            rawError.contains("PIN") || rawError.contains("6982") || rawError.contains("色碼") || rawError.contains("規格") || rawError.contains("TLV") ->
                Pair(
                    getString(R.string.ui_err_unsupported_headline),
                    getString(R.string.ui_err_unsupported_subtitle)
                )
            else ->
                Pair(
                    getString(R.string.ui_err_disconnected_headline),
                    getString(R.string.ui_err_disconnected_subtitle)
                )
        }
    }

    private fun onRetryOrOpenNfcClicked() {
        if (currentState == UiState.NFC_DISABLED) {
            try {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } catch (_: Throwable) {
                enterReadyToScanState()
            }
            return
        }
        LogManager.i(TAG, "使用者點擊「重新感應」按鈕")
        enterReadyToScanState()
    }

    private fun handleRecoverableError(
        rawError: String,
        userHeadline: String,
        userSubtitle: String,
        throwable: Throwable? = null
    ) {
        mainHandler.removeCallbacks(detectTimeoutRunnable)
        LogManager.e(TAG, rawError, throwable)
        syncServiceState("ERROR", rawError)
        triggerHapticFeedback()

        applySheetState(
            state = UiState.ERROR,
            headline = userHeadline,
            subtitle = userSubtitle
        )
        enableNfcReaderModeIfAvailable(quietLog = true)
        shakeHeroCard()
    }

    private fun shakeHeroCard() {
        val delta = dp(6).toFloat()
        ObjectAnimator.ofFloat(
            epaperCardWrapper,
            "translationX",
            0f, -delta, delta, -delta * 0.7f, delta * 0.7f, -delta * 0.3f, delta * 0.3f, 0f
        ).apply {
            duration = 320
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun applySheetState(
        state: UiState,
        headline: String,
        subtitle: String
    ) {
        beginSmoothSheetTransition()
        currentState = state
        headerTitleView.text = headline
        headerSubtitleView.text = subtitle
        headerSubtitleView.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE

        rippleCanvasView.setMode(state)
        errorOverlayPill.visibility = if (state == UiState.ERROR) View.VISIBLE else View.GONE
        successCheckBadge.visibility = if (state == UiState.SUCCESS) View.VISIBLE else View.GONE

        // Update E-Paper card border color
        val strokeColor = when (state) {
            UiState.ERROR -> Color.parseColor(COLOR_ERROR)
            UiState.SUCCESS -> Color.parseColor(COLOR_PRIMARY)
            else -> Color.parseColor(COLOR_OUTLINE_VARIANT)
        }
        epaperBezelFrame.background = roundedRect(
            radiusPx = dp(16),
            fillColor = Color.parseColor(COLOR_SURFACE_CONTAINER_HIGHEST),
            strokeWidthPx = dp(if (state == UiState.ERROR) 2 else 1),
            strokeColor = strokeColor
        )

        // Update Footer visibility per state (hide footer completely during TRANSFERRING / SUCCESS)
        val showReadyFooter = (state == UiState.PREPARING || state == UiState.WAITING_NFC)
        val showErrorFooter = (state == UiState.ERROR || state == UiState.NFC_DISABLED)
        readyFooterRow.visibility = if (showReadyFooter) View.VISIBLE else View.GONE
        errorFooterRow.visibility = if (showErrorFooter) View.VISIBLE else View.GONE
        footerZone.visibility = if (showReadyFooter || showErrorFooter) View.VISIBLE else View.GONE

        if (state == UiState.NFC_DISABLED) {
            retryTonalBtn.text = getString(R.string.ui_btn_enable_nfc)
        } else {
            retryTonalBtn.text = getString(R.string.ui_btn_retry)
        }

        epaperCardWrapper.post {
            rippleCanvasView.updateTargetCardBounds(epaperBezelFrame, heroZoneContainer)
        }

        if (state != UiState.ERROR && state != UiState.SUCCESS && state != UiState.NFC_DISABLED) {
            syncServiceState(state.name, "NONE")
        }
    }

    private fun startRippleAnimation() {
        rippleAnimator?.cancel()
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                rippleCanvasView.setRipplePhase(anim.animatedValue as Float)
            }
            start()
        }
    }

    private fun beginSmoothSheetTransition() {
        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds())
            addTransition(Fade())
            duration = 260L
            interpolator = DecelerateInterpolator()
        }
        TransitionManager.beginDelayedTransition(bottomSheetCard, transition)
    }

    private fun triggerHapticFeedback() {
        try {
            bottomSheetCard.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Throwable) {
        }
    }

    private fun announceAccessibility(message: String) {
        try {
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null && am.isEnabled) {
                @Suppress("DEPRECATION")
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.text.add(message)
                event.className = javaClass.name
                event.packageName = packageName
                am.sendAccessibilityEvent(event)
            }
        } catch (_: Throwable) {
        }
    }

    private fun topRoundedSheetBackground(radiusPx: Int, fillColor: Int): GradientDrawable {
        val r = radiusPx.toFloat()
        return GradientDrawable().apply {
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(fillColor)
        }
    }

    private fun roundedRect(
        radiusPx: Int,
        fillColor: Int,
        strokeWidthPx: Int = 0,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radiusPx.toFloat()
            setColor(fillColor)
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    private fun syncServiceState(state: String, error: String) {
        try {
            TapInkLogService.lastState = state
            TapInkLogService.lastError = error
            startService(Intent(this, TapInkLogService::class.java).apply {
                putExtra("state", state)
                putExtra("error", error)
            })
        } catch (_: Throwable) {
        }
    }

    private fun registerSimulationReceiver() {
        if (!allowMockNfc) return
        val filter = IntentFilter(ACTION_SIMULATE_NFC_TAG)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(simulateNfcReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(simulateNfcReceiver, filter)
            }
        } catch (_: Throwable) {
            registerReceiver(simulateNfcReceiver, filter)
        }
    }

    private fun closeActivityNow() {
        mainHandler.removeCallbacks(detectTimeoutRunnable)
        cancelReaderModeReassertions()
        rippleAnimator?.cancel()
        disableNfcReaderMode()
        if (isTaskRoot && Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        if (shouldHoldNfcReaderMode()) {
            enableNfcReaderModeIfAvailable()
            scheduleReaderModeReassertions()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && shouldHoldNfcReaderMode()) {
            enableNfcReaderModeIfAvailable(quietLog = true)
            scheduleReaderModeReassertions()
        }
    }

    override fun onPause() {
        isActivityResumed = false
        disableNfcReaderMode()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(detectTimeoutRunnable)
        cancelReaderModeReassertions()
        rippleAnimator?.cancel()
        try {
            unregisterReceiver(simulateNfcReceiver)
        } catch (_: Throwable) {
        }
        workerExecutor.shutdownNow()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    /**
     * Custom Hero Canvas View rendering:
     * - Concentric breathing NFC ripples during WAITING_NFC
     * - Morphing perimeter progress ring hugging the E-Paper card during TRANSFERRING / SUCCESS
     */
    private inner class RippleAndPerimeterView(context: Context) : View(context) {
        private var uiMode: UiState = UiState.WAITING_NFC
        private var phase: Float = 0f
        private var progressPct: Int = 0
        private val cardRect = RectF()

        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor(COLOR_PRIMARY)
        }
        private val rippleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.parseColor(COLOR_PRIMARY)
        }
        private val perimeterTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
            color = Color.parseColor("#3344474F")
        }
        private val perimeterProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor(COLOR_PRIMARY)
        }
        private val perimeterPath = Path()
        private val progressSegmentPath = Path()

        fun setMode(mode: UiState) {
            uiMode = mode
            invalidate()
        }

        fun setRipplePhase(p: Float) {
            phase = p
            if (uiMode == UiState.WAITING_NFC || uiMode == UiState.PREPARING) {
                invalidate()
            }
        }

        fun setProgress(pct: Int) {
            progressPct = pct.coerceIn(0, 100)
            invalidate()
        }

        fun updateTargetCardBounds(cardView: View, parentContainer: View) {
            updateTargetCardBoundsNow(cardView, parentContainer)
            invalidate()
        }

        private fun updateTargetCardBoundsNow(cardView: View, parentContainer: View) {
            var x = 0f
            var y = 0f
            var v: View? = cardView
            while (v != null && v !== parentContainer) {
                x += v.left + v.translationX
                y += v.top + v.translationY
                v = v.parent as? View
            }
            if (cardView.width > 0 && cardView.height > 0) {
                val pad = dp(6).toFloat()
                cardRect.set(
                    x - pad,
                    y - pad,
                    x + cardView.width + pad,
                    y + cardView.height + pad
                )
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            updateTargetCardBoundsNow(epaperBezelFrame, heroZoneContainer)
            val cx = if (!cardRect.isEmpty) cardRect.centerX() else width * 0.5f
            val cy = if (!cardRect.isEmpty) cardRect.centerY() else height * 0.45f

            if (uiMode == UiState.WAITING_NFC || uiMode == UiState.PREPARING) {
                val minR = dp(65).toFloat()
                val maxR = dp(126).toFloat()
                for (i in 0..1) {
                    val p = (phase + i * 0.5f) % 1.0f
                    val r = minR + (maxR - minR) * p
                    val alpha = ((1.0f - p) * 38f).toInt().coerceIn(0, 255)
                    ripplePaint.alpha = alpha
                    canvas.drawCircle(cx, cy, r, ripplePaint)
                    rippleStrokePaint.alpha = (alpha * 1.5f).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cx, cy, r, rippleStrokePaint)
                }
            } else if ((uiMode == UiState.TRANSFERRING || uiMode == UiState.SUCCESS) && !cardRect.isEmpty) {
                val cornerR = dp(20).toFloat()
                canvas.drawRoundRect(cardRect, cornerR, cornerR, perimeterTrackPaint)

                perimeterPath.reset()
                perimeterPath.addRoundRect(cardRect, cornerR, cornerR, Path.Direction.CW)
                val pm = PathMeasure(perimeterPath, false)
                val totalLen = pm.length
                val stopLen = totalLen * (progressPct / 100f)
                progressSegmentPath.reset()
                if (pm.getSegment(0f, stopLen, progressSegmentPath, true)) {
                    canvas.drawPath(progressSegmentPath, perimeterProgressPaint)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ShareNfcActivity"
        private const val REQ_PICK_IMAGE = 1001
        const val ACTION_SIMULATE_NFC_TAG = "com.dabendan.tapink.ACTION_SIMULATE_NFC_TAG"

        // Material Design 3 Dark / Dynamic Color Tokens
        private const val COLOR_SURFACE_CONTAINER_LOW = "#191C20"
        private const val COLOR_SURFACE_CONTAINER_HIGHEST = "#33353A"
        private const val COLOR_OUTLINE_VARIANT = "#44474E"
        private const val COLOR_PRIMARY = "#A8C7FA"
        private const val COLOR_ON_PRIMARY = "#062E6F"
        private const val COLOR_SECONDARY_CONTAINER = "#3E4759"
        private const val COLOR_ON_SECONDARY_CONTAINER = "#D8E3F8"
        private const val COLOR_ERROR = "#FFB4AB"
        private const val COLOR_ERROR_CONTAINER = "#93000A"
        private const val COLOR_ON_ERROR_CONTAINER = "#FFDAD6"
        private const val COLOR_ON_SURFACE = "#E2E2E9"
        private const val COLOR_ON_SURFACE_VARIANT = "#C4C6D0"
    }
}
