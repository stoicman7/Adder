package com.socialflow.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable

class FriendAssistService : AccessibilityService() {
    private val bg = Color.rgb(8, 9, 12)
    private val red = Color.rgb(174, 9, 22)
    private val white = Color.rgb(248, 248, 250)
    private val muted = Color.rgb(160, 164, 174)
    private val border = Color.rgb(103, 18, 29)

    private var windowManager: WindowManager? = null
    private var overlay: LinearLayout? = null
    private var countView: TextView? = null
    private var messageView: TextView? = null
    private var confirmView: TextView? = null
    private var nextView: TextView? = null
    private var dismissView: TextView? = null
    private var lastTapAt = 0L

    companion object {
        @Volatile var armed: Boolean = false
        @Volatile var detectedCount: Int = 0
        @Volatile var lastMessage: String = "Enable the service, then arm it."
        @Volatile var failedAddDialogVisible: Boolean = false
        @Volatile var requestLimitDetected: Boolean = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 120
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        lastMessage = "Service connected. Arm it from the app."
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (!isFacebookPackage(packageName) || !armed) {
            removeOverlay()
            if (!isFacebookPackage(packageName)) {
                detectedCount = 0
                failedAddDialogVisible = false
            }
            return
        }

        val root = activeFacebookRoot()
        if (root == null) {
            detectedCount = 0
            showOverlay()
            updateOverlay()
            return
        }

        val screenText = collectAllText(root).joinToString(" ").lowercase()
        requestLimitDetected = containsRequestLimit(screenText)
        failedAddDialogVisible = !requestLimitDetected && containsFailedAdd(screenText)
        detectedCount = if (requestLimitDetected || failedAddDialogVisible) 0
            else findAddFriendButtons(root).size

        if (requestLimitDetected) {
            lastMessage = "Facebook reports a request limit. Actions are locked. Stop and follow Facebook's guidance."
        } else if (failedAddDialogVisible) {
            lastMessage = "Facebook could not add this person. Dismiss the notice; no next request is sent."
        } else if (detectedCount > 0) {
            lastMessage = "Found visible buttons. Confirm one at a time."
        } else {
            lastMessage = "No supported Add Friend button is visible."
        }

        showOverlay()
        updateOverlay()
    }

    override fun onInterrupt() {
        lastMessage = "Accessibility interrupted."
        removeOverlay()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun isFacebookPackage(packageName: String): Boolean =
        packageName == "com.facebook.katana" || packageName == "com.facebook.lite"

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), border)
        }

    private fun uiText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

    private fun panelButton(
        value: String,
        color: Int,
        callback: () -> Unit
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = 10f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(white)
            setPadding(dp(7), dp(11), dp(7), dp(11))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(9).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { callback() }
        }

    private fun showOverlay() {
        if (overlay != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(bg, 16)
            elevation = dp(12).toFloat()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(uiText("EAGLE ASSIST", 15f, white, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(uiText("LIVE  /  MANUAL", 8f, Color.rgb(255, 71, 83), true))
        root.addView(top)

        countView = uiText("Scanning Facebook…", 11f, white, true).apply {
            setPadding(0, dp(5), 0, dp(2))
        }
        root.addView(countView)

        messageView = uiText("No action happens without your tap.", 9.5f, muted)
        root.addView(messageView)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        confirmView = panelButton("CONFIRM ONE", red) { confirmOne() }
        nextView = panelButton("NEXT  ↓", Color.rgb(42, 44, 52)) { scrollNext() }
        dismissView = panelButton("DISMISS NOTICE", Color.rgb(104, 50, 13)) {
            dismissFailureNotice()
        }.apply { visibility = android.view.View.GONE }

        row.addView(confirmView, LinearLayout.LayoutParams(0, -2, 1.15f).apply {
            rightMargin = dp(5)
        })
        row.addView(nextView, LinearLayout.LayoutParams(0, -2, 0.75f).apply {
            rightMargin = dp(5)
        })
        row.addView(dismissView, LinearLayout.LayoutParams(0, -2, 1.15f))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9)
        })

        val stop = panelButton("STOP ASSIST", Color.rgb(69, 23, 30)) {
            armed = false
            failedAddDialogVisible = false
            requestLimitDetected = false
            lastMessage = "Assistant stopped."
            detectedCount = 0
            removeOverlay()
        }
        root.addView(stop, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(7)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(18)
            width = resources.displayMetrics.widthPixels - dp(16)
        }

        try {
            windowManager?.addView(root, params)
            overlay = root
        } catch (_: Exception) {
            overlay = null
            lastMessage = "Could not display floating controls."
        }
    }

    private fun updateOverlay() {
        if (overlay == null) return

        countView?.text = when {
            requestLimitDetected -> "FACEBOOK REQUEST LIMIT DETECTED"
            failedAddDialogVisible -> "FACEBOOK DECLINED THIS REQUEST"
            detectedCount == 1 -> "1 matching Add Friend button visible"
            else -> detectedCount.toString() + " matching Add Friend buttons visible"
        }
        messageView?.text = lastMessage

        dismissView?.visibility = if (failedAddDialogVisible && !requestLimitDetected)
            android.view.View.VISIBLE else android.view.View.GONE

        val locked = requestLimitDetected || failedAddDialogVisible
        confirmView?.isEnabled = !locked
        nextView?.isEnabled = !locked
        confirmView?.alpha = if (locked) 0.35f else 1f
        nextView?.alpha = if (locked) 0.35f else 1f
    }

    private fun removeOverlay() {
        val current = overlay ?: return
        try { windowManager?.removeView(current) } catch (_: Exception) {}
        overlay = null
        countView = null
        messageView = null
        confirmView = null
        nextView = null
        dismissView = null
    }

    private fun activeFacebookRoot(): AccessibilityNodeInfo? {
        val activeWindow = windows?.firstOrNull {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive
        }
        val root = activeWindow?.root ?: rootInActiveWindow
        val packageName = root?.packageName?.toString()
        return if (packageName != null && isFacebookPackage(packageName)) root else null
    }

    private fun collectAllText(root: AccessibilityNodeInfo): List<String> {
        val values = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser) return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { values.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { values.add(it) }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child)
            }
        }
        walk(root)
        return values
    }

    private fun containsRequestLimit(allText: String): Boolean {
        val patterns = listOf(
            "temporarily blocked",
            "try again later",
            "too many friend requests",
            "you are temporarily blocked",
            "you're temporarily blocked",
            "we limit how often",
            "slow down",
            "restricted from sending friend requests",
            "bloqué temporairement",
            "trop de demandes d’amitié",
            "trop de demandes d'amis",
            "réessayez plus tard",
            "حاول مرة أخرى لاحقا",
            "تم حظرك مؤقتا",
            "عدد كبير من طلبات الصداقة"
        )
        return patterns.any { allText.contains(it) }
    }

    private fun containsFailedAdd(allText: String): Boolean {
        val patterns = listOf(
            "couldn't add this person",
            "could not add this person",
            "can't add this person",
            "cannot add this person",
            "unable to add this person",
            "we couldn't add",
            "we could not add",
            "couldn't send friend request",
            "impossible d'ajouter cette personne",
            "impossible d’ajouter cette personne",
            "vous ne pouvez pas ajouter cette personne",
            "تعذر إضافة هذا الشخص",
            "يتعذر إضافة هذا الشخص",
            "لا يمكن إضافة هذا الشخص"
        )
        return patterns.any { allText.contains(it) }
    }

    private fun isAddFriendLabel(raw: String): Boolean {
        val value = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        return value in setOf(
            "add friend",
            "add as friend",
            "ajouter un ami",
            "ajouter une amie",
            "ajouter comme ami",
            "ajouter comme amie",
            "إضافة صديق",
            "اضافة صديق",
            "أضف صديق",
            "اضف صديق",
            "إضافة كصديق",
            "اضافة كصديق"
        )
    }

    private data class Match(
        val node: AccessibilityNodeInfo,
        val top: Int,
        val left: Int
    )

    private fun findAddFriendButtons(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val matches = LinkedHashMap<String, Match>()

        fun walk(node: AccessibilityNodeInfo) {
            if (!node.isVisibleToUser) return
            val labels = listOf(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).filterNotNull()

            if (labels.any { isAddFriendLabel(it) }) {
                val target = clickableAncestor(node)
                if (target != null) {
                    val rect = Rect()
                    target.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        val key = rect.left.toString() + ":" + rect.top + ":" +
                            rect.right + ":" + rect.bottom
                        if (!matches.containsKey(key)) {
                            matches[key] = Match(target, rect.top, rect.left)
                        }
                    }
                }
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child)
            }
        }

        walk(root)
        return matches.values.sortedWith(
            compareBy<Match> { it.top }.thenBy { it.left }
        ).map { it.node }
    }

    private fun clickableAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = start
        repeat(6) {
            val node = current ?: return null
            if (node.isClickable && node.isEnabled) return node
            current = node.parent
        }
        return if (start.isClickable && start.isEnabled) start else null
    }

    private fun confirmOne() {
        if (!armed || requestLimitDetected || failedAddDialogVisible) return
        val now = System.currentTimeMillis()
        if (now - lastTapAt < 900) {
            lastMessage = "Wait briefly before confirming another action."
            updateOverlay()
            return
        }

        val root = activeFacebookRoot()
        if (root == null) {
            lastMessage = "Facebook list not available. Return to the member or reactions list."
            updateOverlay()
            return
        }

        val buttons = findAddFriendButtons(root)
        detectedCount = buttons.size
        val target = buttons.firstOrNull()
        if (target == null) {
            lastMessage = "No supported Add Friend button is visible."
            updateOverlay()
            return
        }

        lastTapAt = now
        val clicked = try {
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (_: Exception) {
            false
        }

        lastMessage = if (clicked)
            "One button tapped. Check Facebook's response."
        else
            "Facebook did not accept the tap. Try the control manually."
        updateOverlay()
    }

    private fun scrollNext() {
        if (!armed || requestLimitDetected || failedAddDialogVisible) return
        val root = activeFacebookRoot()
        if (root == null) {
            lastMessage = "Facebook list not available."
            updateOverlay()
            return
        }

        val scrollable = findFirstScrollable(root)
        if (scrollable == null) {
            lastMessage = "No scrollable list found. Swipe Facebook manually."
            updateOverlay()
            return
        }

        val moved = try {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (_: Exception) {
            false
        }
        lastMessage = if (moved)
            "Scrolled once. Check the newly visible profiles."
        else
            "Facebook did not scroll. Swipe the list manually."
        updateOverlay()
    }

    private fun findFirstScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable && root.isVisibleToUser) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            val result = findFirstScrollable(child)
            if (result != null) return result
        }
        return null
    }

    private fun isOkLabel(raw: String): Boolean {
        val value = raw.trim().lowercase()
        return value in setOf("ok", "okay", "got it", "d'accord", "d’accord",
            "compris", "حسنا", "حسنًا", "موافق")
    }

    private fun findOkButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null

        fun walk(node: AccessibilityNodeInfo) {
            if (result != null || !node.isVisibleToUser) return
            val values = listOf(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).filterNotNull()

            if (values.any { isOkLabel(it) }) {
                result = clickableAncestor(node)
                if (result != null) return
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child)
                if (result != null) return
            }
        }

        walk(root)
        return result
    }

    private fun dismissFailureNotice() {
        if (!armed || requestLimitDetected || !failedAddDialogVisible) return
        val root = activeFacebookRoot()
        if (root == null) {
            lastMessage = "Facebook notice not available. Close it manually."
            updateOverlay()
            return
        }

        val ok = findOkButton(root)
        if (ok == null) {
            lastMessage = "Notice detected, but OK was not found. Tap OK manually."
            updateOverlay()
            return
        }

        val clicked = try {
            ok.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (_: Exception) {
            false
        }

        if (clicked) {
            failedAddDialogVisible = false
            detectedCount = 0
            lastMessage = "Notice acknowledged. No next request was sent; review the list before continuing."
        } else {
            lastMessage = "Could not dismiss the notice. Tap OK manually."
        }
        updateOverlay()
    }
}
