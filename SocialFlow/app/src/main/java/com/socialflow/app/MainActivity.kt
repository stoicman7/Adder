package com.socialflow.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 8, 11)
    private val panel = Color.rgb(17, 19, 24)
    private val red = Color.rgb(174, 9, 22)
    private val hotRed = Color.rgb(232, 35, 48)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(143, 150, 163)
    private val stroke = Color.rgb(55, 25, 32)

    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var page = "OPERATOR"
    private var accessibilityEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildShell()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(color: Int, radius: Int = 16, border: Int = stroke): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (border != Color.TRANSPARENT) setStroke(dp(1), border)
        }

    private fun ripple(color: Int, radius: Int = 12): RippleDrawable =
        RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.argb(42, 255, 255, 255)),
            shape(color, radius, Color.TRANSPARENT),
            null
        )

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

    private fun gap(height: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    private fun eagle(size: Int): ImageView =
        ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Color.BLACK, 12)
            clipToOutline = true
            contentDescription = "Eagle Assist emblem"
        }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
        }

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(7), dp(13), dp(7), dp(12))
            setBackgroundColor(Color.rgb(11, 12, 16))
        }
        root.addView(nav, LinearLayout.LayoutParams(dp(86), -1))

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            setBackgroundColor(Color.rgb(12, 13, 17))
        }
        header.addView(eagle(dp(42)), LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            rightMargin = dp(10)
        })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("EAGLE ASSIST", 17f, white, true))
        brand.addView(text("FIELD CONTROL  /  V3.1", 8.5f, hotRed, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        main.addView(header, LinearLayout.LayoutParams(-1, -2))

        val divider = View(this).apply { setBackgroundColor(stroke) }
        main.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content)
        }
        main.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)
        buildNav()
    }

    private fun buildNav() {
        nav.removeAllViews()
        nav.addView(eagle(dp(58)), LinearLayout.LayoutParams(dp(58), dp(58)).apply {
            bottomMargin = dp(26)
        })

        listOf(
            "OPERATOR" to "RUN",
            "FIELD GUIDE" to "GUIDE",
            "SETTINGS" to "CONFIG"
        ).forEach { item ->
            val selected = page == item.first
            val button = TextView(this).apply {
                text = item.second
                gravity = Gravity.CENTER
                textSize = 8.5f
                letterSpacing = 0.06f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setTextColor(if (selected) white else muted)
                background = ripple(
                    if (selected) Color.rgb(87, 9, 20) else Color.TRANSPARENT
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    page = item.first
                    refresh()
                }
            }
            nav.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(7)
            })
        }

        val spacer = View(this)
        nav.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        nav.addView(text("V3.1", 9f, muted, true))
    }

    private fun refresh() {
        accessibilityEnabled = isServiceEnabled()
        buildNav()
        content.removeAllViews()
        when (page) {
            "OPERATOR" -> operatorPage()
            "FIELD GUIDE" -> guidePage()
            "SETTINGS" -> settingsPage()
        }
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = shape(panel, 17)
        }

    private fun addCard(view: View) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(11)
        })
    }

    private fun action(
        label: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 12f
            letterSpacing = 0.04f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(white)
            setPadding(dp(12), dp(13), dp(12), dp(13))
            minHeight = dp(46)
            background = ripple(if (primary) red else Color.rgb(27, 30, 37), 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun heading(title: String, subtitle: String) {
        content.addView(text(title, 27f, white, true))
        val sub = text(subtitle, 11.5f, muted)
        sub.setPadding(0, dp(4), 0, dp(17))
        content.addView(sub)
    }

    private fun operatorPage() {
        heading("OPERATOR", "FACEBOOK ASSIST  /  ONE ACTION AT A TIME")

        val hero = card().apply {
            background = shape(Color.rgb(18, 15, 19), 18, Color.rgb(105, 13, 27))
        }
        hero.addView(eagle(dp(96)), LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(12)
        })
        hero.addView(text("EAGLE", 34f, white, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        })
        hero.addView(text("ASSIST  /  CONTROLLED MODE", 10f, hotRed, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.09f
        })
        hero.addView(gap(12))
        hero.addView(text(
            "Find visible Add Friend controls in Facebook. You choose when each request is tapped.",
            11.5f, muted
        ).apply { gravity = Gravity.CENTER })
        addCard(hero)

        val status = card()
        status.addView(text("SYSTEM STATUS", 9f, muted, true))
        status.addView(gap(8))
        status.addView(text(
            if (accessibilityEnabled) "ACCESSIBILITY SERVICE  /  ENABLED"
            else "ACCESSIBILITY SERVICE  /  NOT ENABLED",
            13f,
            if (accessibilityEnabled) Color.rgb(95, 210, 135) else hotRed,
            true
        ))
        status.addView(gap(6))
        status.addView(text(
            if (FriendAssistService.armed) "ASSISTANT  /  ARMED"
            else "ASSISTANT  /  DISARMED",
            11f,
            if (FriendAssistService.armed) hotRed else muted,
            true
        ))
        status.addView(gap(7))
        status.addView(text(
            "Visible Add Friend buttons: " + FriendAssistService.detectedCount,
            11f, white
        ))
        status.addView(gap(4))
        status.addView(text(FriendAssistService.lastMessage, 10.5f, muted))
        addCard(status)

        val controls = card()
        controls.addView(text("ENGAGE", 9f, muted, true))
        controls.addView(gap(9))
        if (!accessibilityEnabled) {
            controls.addView(action("01   ENABLE ACCESSIBILITY", true) {
                openAccessibilitySettings()
            })
            controls.addView(gap(8))
            controls.addView(text(
                "Android requires you to enable Eagle Assist in Accessibility settings. The service only watches the Facebook app screen while armed.",
                10.5f, muted
            ))
        } else if (!FriendAssistService.armed) {
            controls.addView(action("ARM ASSIST  +  OPEN FACEBOOK", true) {
                armAndOpenFacebook()
            })
            controls.addView(gap(8))
            controls.addView(text(
                "Open a group member list or post reactions. The floating Eagle control panel appears while Facebook is in front.",
                10.5f, muted
            ))
        } else {
            controls.addView(action("DISARM ASSISTANT", true) {
                FriendAssistService.armed = false
                FriendAssistService.lastMessage = "Assistant disarmed."
                refresh()
            })
            controls.addView(gap(8))
            controls.addView(action("OPEN FACEBOOK", false) { launchFacebook() })
        }
        addCard(controls)

        val warning = card()
        warning.addView(text("ONE REQUEST  /  ONE CONFIRMATION", 12f, white, true))
        warning.addView(gap(5))
        warning.addView(text(
            "CONFIRM ONE taps one visible Add Friend button. NEXT scrolls once. If Facebook rejects a person, dismiss that notice manually before proceeding. Request-limit warnings lock the controls.",
            10.5f, muted
        ))
        addCard(warning)
    }

    private fun guidePage() {
        heading("FIELD GUIDE", "HOW TO USE EAGLE ASSIST")
        val steps = listOf(
            "01  ENABLE THE SERVICE" to "Open Android Accessibility settings and enable Eagle Assist.",
            "02  ARM THE ASSISTANT" to "Return here and press ARM ASSIST + OPEN FACEBOOK.",
            "03  OPEN YOUR LIST" to "In Facebook, open a group member list or a post reactions list.",
            "04  CONFIRM ONE" to "The floating panel counts supported visible Add Friend buttons. Press CONFIRM ONE to tap only the first visible match.",
            "05  NEXT PAGE" to "Press NEXT to scroll the list once. There is no automatic scrolling after a request.",
            "06  HANDLE A FAILED ADD" to "When Facebook says a person could not be added, the panel pauses and offers DISMISS NOTICE. Press it to acknowledge the message and close its OK button. No further request is sent by that action.",
            "07  RESPECT LIMITS" to "If Facebook reports a temporary block, rate limit, or request restriction, Eagle Assist locks request and scroll controls. Stop and follow Facebook's on-screen guidance."
        )
        steps.forEach { step ->
            val box = card()
            box.addView(text(step.first, 12f, hotRed, true))
            box.addView(gap(5))
            box.addView(text(step.second, 11f, muted))
            addCard(box)
        }
        val note = card()
        note.addView(text("SUPPORTED LABELS", 10f, white, true))
        note.addView(gap(5))
        note.addView(text(
            "Detection includes common English, French and Arabic Add Friend labels and common failed-add notices. Facebook can change its interface; if no control is detected, use Facebook normally.",
            10.5f, muted
        ))
        addCard(note)
    }

    private fun settingsPage() {
        heading("CONFIGURATION", "PERMISSIONS  /  PRIVACY  /  SCOPE")
        val permission = card()
        permission.addView(text("ACCESSIBILITY PERMISSION", 10f, muted, true))
        permission.addView(gap(7))
        permission.addView(text(
            if (accessibilityEnabled) "ENABLED" else "DISABLED",
            18f,
            if (accessibilityEnabled) Color.rgb(95, 210, 135) else hotRed,
            true
        ))
        permission.addView(gap(10))
        permission.addView(action("OPEN ANDROID SETTINGS", false) {
            openAccessibilitySettings()
        })
        addCard(permission)

        val privacy = card()
        privacy.addView(text("LOCAL PROCESSING", 12f, white, true))
        privacy.addView(gap(6))
        privacy.addView(text(
            "Eagle Assist does not ask for your Facebook password and does not upload a profile list. Screen text is inspected locally while Facebook is in the foreground and the assistant is armed.",
            10.5f, muted
        ))
        addCard(privacy)

        val limits = card()
        limits.addView(text("ACTION LIMITS", 12f, white, true))
        limits.addView(gap(6))
        limits.addView(text(
            "Every Add Friend tap requires an individual CONFIRM ONE press. Scrolling requires NEXT. Failure notices can be dismissed manually. Facebook rate limits are not bypassed.",
            10.5f, muted
        ))
        addCard(limits)

        addCard(action("DISARM ASSISTANT", true) {
            FriendAssistService.armed = false
            FriendAssistService.lastMessage = "Assistant disarmed."
            refresh()
        })
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(this, FriendAssistService::class.java)
        val full = component.flattenToString()
        val short = component.flattenToShortString()
        return enabled.split(':').any {
            it.equals(full, true) || it.equals(short, true)
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun armAndOpenFacebook() {
        accessibilityEnabled = isServiceEnabled()
        if (!accessibilityEnabled) {
            openAccessibilitySettings()
            return
        }
        FriendAssistService.armed = true
        FriendAssistService.requestLimitDetected = false
        FriendAssistService.failedAddDialogVisible = false
        FriendAssistService.lastMessage = "Armed. Waiting for Facebook."
        if (!launchFacebook()) {
            Toast.makeText(this, "Install Facebook or Facebook Lite first.", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun launchFacebook(): Boolean {
        val packages = listOf("com.facebook.katana", "com.facebook.lite")
        for (packageName in packages) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            }
        }
        return false
    }
}
