package com.socialflow.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

private data class Prospect(
    var name: String,
    var url: String,
    var source: String,
    var reviewed: Boolean = false
)

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(13, 14, 17)
    private val panelColor = Color.rgb(23, 25, 30)
    private val raisedColor = Color.rgb(31, 34, 41)
    private val red = Color.rgb(181, 18, 27)
    private val redBright = Color.rgb(232, 49, 58)
    private val white = Color.rgb(246, 247, 249)
    private val muted = Color.rgb(151, 157, 168)
    private val lineColor = Color.rgb(48, 51, 59)

    private val prospects = mutableListOf<Prospect>()
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var page = "Dashboard"
    private var intervalSeconds = 5
    private var selectedSourceFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(true)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = 0

        loadData()
        intervalSeconds = getPreferences(0).getInt("interval", 5).coerceIn(1, 30)
        buildShell()
        showPage("Dashboard")
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun shape(color: Int, radius: Int = 16, stroke: Int = lineColor): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun ripple(color: Int, radius: Int = 13): RippleDrawable {
        return RippleDrawable(
            ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
            shape(color, radius, Color.TRANSPARENT),
            null
        )
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(backgroundColor)
        }

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(15), dp(8), dp(12))
            setBackgroundColor(Color.rgb(17, 18, 22))
        }
        root.addView(nav, LinearLayout.LayoutParams(dp(96), -1))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(12), dp(13))
            setBackgroundColor(Color.rgb(17, 18, 22))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Color.BLACK, 11, lineColor)
            clipToOutline = true
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            rightMargin = dp(11)
        })
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(label("SOCIALFLOW", 15f, white, true))
        brandText.addView(label("AUDIENCE WORKSPACE", 9f, muted, true))
        header.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        right.addView(header, LinearLayout.LayoutParams(-1, -2))

        val divider = View(this).apply { setBackgroundColor(lineColor) }
        right.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(content, ScrollView.LayoutParams(-1, -2))
        }

        // A vertical LinearLayout needs MATCH_PARENT width and a weighted height.
        right.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)
        buildNav()
    }

    private fun buildNav() {
        nav.removeAllViews()

        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Color.BLACK, 13, lineColor)
            clipToOutline = true
        }
        nav.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)).apply {
            bottomMargin = dp(24)
        })

        val items = listOf(
            Pair("Dashboard", "HOME"),
            Pair("Audiences", "PEOPLE"),
            Pair("Queue", "QUEUE"),
            Pair("History", "HISTORY"),
            Pair("Settings", "SETTINGS")
        )

        items.forEach { item ->
            val selected = page == item.first
            val button = TextView(this).apply {
                text = item.second
                textSize = 9.5f
                letterSpacing = 0.04f
                gravity = Gravity.CENTER
                typeface = if (selected) Typeface.create("sans-serif-bold", Typeface.NORMAL)
                    else Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (selected) white else muted)
                background = ripple(if (selected) Color.rgb(91, 20, 27) else Color.TRANSPARENT, 12)
                isClickable = true
                isFocusable = true
                setOnClickListener { showPage(item.first) }
            }
            nav.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(6)
            })
        }

        val spacer = View(this)
        nav.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        nav.addView(label("V2", 9f, muted, true))
    }

    private fun showPage(which: String) {
        page = which
        buildNav()
        content.removeAllViews()
        when (which) {
            "Dashboard" -> dashboard()
            "Audiences" -> audiences()
            "Queue" -> queue()
            "History" -> history()
            "Settings" -> settings()
            else -> dashboard()
        }
    }

    private fun label(
        value: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun title(value: String, subtitle: String) {
        content.addView(label(value, 25f, white, true))
        val sub = label(subtitle, 12f, muted)
        sub.setPadding(0, dp(5), 0, dp(18))
        content.addView(sub)
    }

    private fun space(height: Int): View =
        View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = shape(panelColor, 17, lineColor)
        }
    }

    private fun addCard(view: View) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(11)
        })
    }

    private fun button(
        value: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(white)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minHeight = dp(43)
            background = ripple(if (primary) red else raisedColor, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun actionRow(
        parent: LinearLayout,
        actions: List<Pair<String, () -> Unit>>
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.forEachIndexed { index, action ->
            row.addView(button(action.first, index == 0, action.second),
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index > 0) leftMargin = dp(7)
                })
        }
        parent.addView(row)
    }

    private fun metric(value: String, caption: String): LinearLayout {
        val box = card()
        box.addView(label(value, 25f, white, true))
        box.addView(space(4))
        box.addView(label(caption, 10.5f, muted))
        return box
    }

    private fun dashboard() {
        title("Dashboard", "Your audience workspace at a glance")

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(metric(prospects.size.toString(), "PROFILES SAVED"),
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        row.addView(metric(prospects.count { !it.reviewed }.toString(), "TO REVIEW"),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        addCard(row)

        val pace = card()
        pace.addView(label("YOUR REVIEW PACE", 10f, muted, true))
        pace.addView(space(6))
        pace.addView(label(intervalSeconds.toString() + " seconds", 26f, redBright, true))
        pace.addView(space(3))
        pace.addView(label("A personal reminder interval. It never sends actions automatically.", 11.5f, muted))
        pace.addView(space(11))
        pace.addView(button("ADJUST PACE", false) { showPage("Settings") })
        addCard(pace)

        val quick = card()
        quick.addView(label("QUICK ACTIONS", 10f, muted, true))
        quick.addView(space(10))
        quick.addView(button("+  ADD A PROFILE", true) { addProspectDialog() })
        quick.addView(space(8))
        quick.addView(button("OPEN REVIEW QUEUE", false) {
            selectedSourceFilter = null
            showPage("Queue")
        })
        addCard(quick)

        val guide = card()
        guide.addView(label("BUILT FOR CONTROLLED OUTREACH", 14f, white, true))
        guide.addView(space(6))
        guide.addView(label(
            "Save profile links yourself, organize them by source, and review each person individually. You stay in control of every Facebook action.",
            11.5f, muted
        ))
        addCard(guide)
    }

    private fun audiences() {
        title("Audiences", "Groups and sources for the profiles you save")
        addCard(button("+  ADD PROFILE", true) { addProspectDialog() })

        val groups = prospects.groupBy { it.source.ifBlank { "Manually added" } }
        if (groups.isEmpty()) {
            addEmptyState("No audiences yet", "Add a profile and give it a source such as a group, page, or campaign.")
            return
        }

        groups.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { entry ->
            val box = card()
            box.addView(label(entry.key, 15f, white, true))
            box.addView(space(5))
            box.addView(label(
                entry.value.size.toString() + " profiles  ·  " +
                    entry.value.count { !it.reviewed }.toString() + " waiting for review",
                11f, muted
            ))
            box.addView(space(10))
            box.addView(button("VIEW PENDING PROFILES", false) {
                selectedSourceFilter = entry.key
                showPage("Queue")
            })
            addCard(box)
        }
    }

    private fun queue() {
        title("Review queue", "Open and review profiles one at a time")
        addCard(button("+  ADD PROFILE", true) { addProspectDialog() })

        selectedSourceFilter?.let { source ->
            addCard(button("FILTER: " + source + "   × CLEAR", false) {
                selectedSourceFilter = null
                showPage("Queue")
            })
        }

        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = searchField("Search names, sources or links") { query ->
            renderQueueRows(results, query)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply {
            bottomMargin = dp(12)
        })
        content.addView(results, LinearLayout.LayoutParams(-1, -2))
        renderQueueRows(results, "")
    }

    private fun renderQueueRows(container: LinearLayout, query: String) {
        container.removeAllViews()
        val source = selectedSourceFilter
        val matches = prospects.filter { prospect ->
            !prospect.reviewed &&
                (source == null || prospect.source.equals(source, ignoreCase = true)) &&
                matchesQuery(prospect, query)
        }

        if (matches.isEmpty()) {
            val message = if (prospects.none { !it.reviewed }) "Your queue is clear"
                else "No profiles match this search"
            container.addView(emptyCard(message, "Add a profile or change your search to see it here."))
            return
        }

        matches.forEach { prospect ->
            container.addView(profileCard(prospect, true),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun history() {
        title("History", "Profiles you have marked as reviewed")
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val search = searchField("Search reviewed profiles") { query ->
            renderHistoryRows(results, query)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply {
            bottomMargin = dp(12)
        })
        content.addView(results, LinearLayout.LayoutParams(-1, -2))
        renderHistoryRows(results, "")
    }

    private fun renderHistoryRows(container: LinearLayout, query: String) {
        container.removeAllViews()
        val matches = prospects.filter { it.reviewed && matchesQuery(it, query) }
        if (matches.isEmpty()) {
            container.addView(emptyCard("No review history", "Profiles you mark as reviewed will appear here."))
            return
        }
        matches.forEach { prospect ->
            container.addView(profileCard(prospect, false),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun matchesQuery(prospect: Prospect, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isBlank()) return true
        return prospect.name.lowercase().contains(q) ||
            prospect.source.lowercase().contains(q) ||
            prospect.url.lowercase().contains(q)
    }

    private fun searchField(hintText: String, onChanged: (String) -> Unit): EditText {
        val input = EditText(this).apply {
            hint = hintText
            textSize = 12f
            setTextColor(white)
            setHintTextColor(muted)
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(13), 0, dp(13), 0)
            background = shape(raisedColor, 13, lineColor)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onChanged(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        return input
    }

    private fun profileCard(prospect: Prospect, pending: Boolean): LinearLayout {
        val box = card()
        box.addView(label(prospect.name, 15f, white, true))
        box.addView(space(4))
        box.addView(label(prospect.source, 10.5f, muted))
        box.addView(space(3))
        val link = label(prospect.url, 10f, Color.rgb(180, 185, 195))
        link.maxLines = 1
        link.ellipsize = android.text.TextUtils.TruncateAt.END
        box.addView(link)
        box.addView(space(12))

        actionRow(box, listOf(
            Pair("OPEN PROFILE") { openProfile(prospect.url) },
            Pair(if (pending) "MARK REVIEWED" else "RETURN TO QUEUE") {
                prospect.reviewed = pending
                saveData()
                showPage(page)
                Toast.makeText(this,
                    if (pending) "Marked as reviewed" else "Returned to the queue",
                    Toast.LENGTH_SHORT).show()
            }
        ))
        box.addView(space(7))
        actionRow(box, listOf(
            Pair("EDIT") { addProspectDialog(prospect) },
            Pair("DELETE") { confirmDelete(prospect) }
        ))
        return box
    }

    private fun emptyCard(heading: String, description: String): LinearLayout {
        val box = card()
        box.addView(label(heading, 15f, white, true))
        box.addView(space(5))
        box.addView(label(description, 11.5f, muted))
        return box
    }

    private fun addEmptyState(heading: String, description: String) {
        addCard(emptyCard(heading, description))
    }

    private fun settings() {
        title("Settings", "Choose how you want to organize your work")

        val pace = card()
        pace.addView(label("REVIEW INTERVAL", 10f, muted, true))
        pace.addView(space(7))
        val value = label(intervalSeconds.toString() + " seconds", 25f, redBright, true)
        pace.addView(value)
        val seek = SeekBar(this).apply {
            max = 29
            progress = intervalSeconds - 1
            progressTintList = ColorStateList.valueOf(red)
            thumbTintList = ColorStateList.valueOf(redBright)
        }
        pace.addView(seek)
        pace.addView(space(2))
        val scale = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scale.addView(label("1 SEC", 9f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        scale.addView(label("30 SEC", 9f, muted))
        pace.addView(scale)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                intervalSeconds = progress + 1
                value.text = intervalSeconds.toString() + " seconds"
                getPreferences(0).edit().putInt("interval", intervalSeconds).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        pace.addView(space(8))
        pace.addView(label(
            "This is a personal pacing preference only. It does not control Facebook or run background actions.",
            11f, muted
        ))
        addCard(pace)

        val data = card()
        data.addView(label("LOCAL DATA", 10f, muted, true))
        data.addView(space(6))
        data.addView(label(
            "Your profile names, links, sources, and review status stay on this device. No Facebook password is requested or stored.",
            11.5f, muted
        ))
        data.addView(space(11))
        data.addView(button("EXPORT MY PROFILES", false) { exportData() })
        addCard(data)

        addCard(button("CLEAR ALL LOCAL PROFILES", false) { confirmClear() })
        addCard(button("ABOUT SOCIALFLOW", false) {
            AlertDialog.Builder(this)
                .setTitle("SocialFlow")
                .setMessage("Version 2.0\n\nA private, manual workspace for organizing audience profiles. Facebook browsing and outreach decisions remain under your control.")
                .setPositiveButton("CLOSE", null)
                .show()
        })
    }

    private fun addProspectDialog(existing: Prospect? = null) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        fun field(hintText: String, type: Int, value: String): EditText {
            return EditText(this).apply {
                hint = hintText
                inputType = type
                setSingleLine(true)
                textSize = 14f
                setTextColor(white)
                setHintTextColor(muted)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = shape(raisedColor, 12, lineColor)
                setText(value)
            }
        }

        val name = field("Profile name", InputType.TYPE_CLASS_TEXT, existing?.name ?: "")
        val url = field(
            "Facebook profile URL",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            existing?.url ?: ""
        )
        val source = field("Source (group, page, campaign)", InputType.TYPE_CLASS_TEXT,
            existing?.source ?: "Manually added")

        form.addView(name)
        form.addView(space(9))
        form.addView(url)
        form.addView(space(9))
        form.addView(source)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add profile" else "Edit profile")
            .setView(form)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton(if (existing == null) "SAVE PROFILE" else "SAVE CHANGES", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = name.text.toString().trim()
                val newUrl = url.text.toString().trim()
                val newSource = source.text.toString().trim().ifBlank { "Manually added" }

                if (newName.isBlank()) {
                    name.error = "Enter a profile name"
                    name.requestFocus()
                    return@setOnClickListener
                }
                if (!isValidFacebookUrl(newUrl)) {
                    url.error = "Enter a valid HTTPS Facebook profile link"
                    url.requestFocus()
                    return@setOnClickListener
                }

                val normalized = normalizeUrl(newUrl)
                val duplicate = prospects.firstOrNull {
                    it !== existing && normalizeUrl(it.url) == normalized
                }
                if (duplicate != null) {
                    url.error = "This profile link is already saved"
                    url.requestFocus()
                    return@setOnClickListener
                }

                if (existing == null) {
                    prospects.add(Prospect(newName, newUrl, newSource))
                } else {
                    existing.name = newName
                    existing.url = newUrl
                    existing.source = newSource
                }
                saveData()
                dialog.dismiss()
                showPage(page)
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun isValidFacebookUrl(value: String): Boolean {
        return try {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase() ?: return false
            val validHost = host == "facebook.com" || host.endsWith(".facebook.com") ||
                host == "fb.com" || host.endsWith(".fb.com") || host == "fb.me"
            uri.scheme.equals("https", true) && validHost
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeUrl(value: String): String =
        value.trim().trimEnd('/').lowercase()

    private fun openProfile(value: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No browser is available to open this link", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open this profile link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(prospect: Prospect) {
        AlertDialog.Builder(this)
            .setTitle("Delete profile?")
            .setMessage("Remove " + prospect.name + " from this device?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                prospects.remove(prospect)
                saveData()
                showPage(page)
                Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear all profiles?")
            .setMessage("This permanently removes every saved profile from this device.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CLEAR ALL") { _, _ ->
                prospects.clear()
                selectedSourceFilter = null
                saveData()
                showPage("Settings")
                Toast.makeText(this, "Local profiles cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun exportData() {
        val array = JSONArray()
        prospects.forEach { prospect ->
            array.put(JSONObject()
                .put("name", prospect.name)
                .put("url", prospect.url)
                .put("source", prospect.source)
                .put("reviewed", prospect.reviewed))
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "SocialFlow profile backup")
            putExtra(Intent.EXTRA_TEXT, array.toString(2))
        }
        try {
            startActivity(Intent.createChooser(send, "Export profile backup"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app is available to export the backup", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveData() {
        val array = JSONArray()
        prospects.forEach { prospect ->
            array.put(JSONObject()
                .put("name", prospect.name)
                .put("url", prospect.url)
                .put("source", prospect.source)
                .put("reviewed", prospect.reviewed))
        }
        getPreferences(0).edit().putString("prospects", array.toString()).apply()
    }

    private fun loadData() {
        val raw = getPreferences(0).getString("prospects", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                if (name.isNotBlank() && isValidFacebookUrl(url)) {
                    prospects.add(Prospect(
                        name,
                        url,
                        item.optString("source", "Manually added"),
                        item.optBoolean("reviewed", false)
                    ))
                }
            }
        } catch (_: Exception) {
            prospects.clear()
        }
    }
}
