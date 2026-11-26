package com.socialflow.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

private data class Prospect(
    val name: String,
    val url: String,
    val source: String,
    var reviewed: Boolean = false
)

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 16, 16)
    private val panel = Color.rgb(29, 29, 29)
    private val panel2 = Color.rgb(39, 39, 39)
    private val red = Color.rgb(181, 18, 27)
    private val white = Color.WHITE
    private val muted = Color.rgb(160, 160, 160)
    private val prospects = mutableListOf<Prospect>()
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var page = "Dashboard"
    private var intervalSeconds = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        intervalSeconds = getPreferences(0).getInt("interval", 5)
        buildShell()
        showPage("Dashboard")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bg) }
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(18), dp(8), dp(12))
            setBackgroundColor(Color.rgb(20, 20, 20))
        }
        val navParams = LinearLayout.LayoutParams(dp(116), -1)
        root.addView(nav, navParams)
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = TextView(this).apply {
            text = "SOCIALFLOW"
            setTextColor(white); textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .06f; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(12), dp(14))
        }
        right.addView(header)
        val line = View(this).apply { setBackgroundColor(Color.rgb(48, 48, 48)) }
        right.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        right.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)
        buildNav()
    }

    private fun buildNav() {
        nav.removeAllViews()
        val brand = TextView(this).apply {
            text = "SF"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(white); setBackgroundColor(red)
        }
        nav.addView(brand, LinearLayout.LayoutParams(dp(42), dp(42)).apply { bottomMargin = dp(28) })
        listOf("Dashboard", "Audiences", "Queue", "History", "Settings").forEach { item ->
            val b = TextView(this).apply {
                text = item
                textSize = 12f
                typeface = if (page == item) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (page == item) white else muted)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(13), dp(4), dp(13))
                if (page == item) setBackgroundColor(Color.rgb(65, 25, 27))
                setOnClickListener { showPage(item) }
            }
            val lp = LinearLayout.LayoutParams(-1, dp(46)); lp.bottomMargin = dp(5)
            nav.addView(b, lp)
        }
        val spacer = View(this); nav.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val foot = TextView(this).apply { text = "v1.0"; textSize = 10f; setTextColor(muted); gravity = Gravity.CENTER }
        nav.addView(foot)
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
        }
    }

    private fun title(t: String, sub: String? = null) {
        val tv = TextView(this).apply { text = t; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white) }
        content.addView(tv)
        if (sub != null) {
            val st = TextView(this).apply { text = sub; textSize = 13f; setTextColor(muted); setPadding(0, dp(5), 0, dp(18)) }
            content.addView(st)
        } else content.addView(space(14))
    }

    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); setBackgroundColor(panel)
    }

    private fun label(text: String, size: Float = 14f, color: Int = white, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun addCard(v: View) {
        val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(12); content.addView(v, lp)
    }

    private fun action(text: String, primary: Boolean = false, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(white); setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(if (primary) red else panel2); setOnClickListener { onClick() }
    }

    private fun dashboard() {
        title("Dashboard", "Your audience workspace")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val saved = metric("${prospects.size}", "Profiles saved")
        val pending = metric("${prospects.count { !it.reviewed }}", "Awaiting review")
        row.addView(saved, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        row.addView(pending, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        content.addView(row); content.addView(space(14))
        val speed = card()
        speed.addView(label("REVIEW INTERVAL", 11f, muted, true)); speed.addView(space(8))
        speed.addView(label("${intervalSeconds} seconds", 27f, Color.rgb(255, 75, 75), true))
        speed.addView(label("Delay between profile review prompts", 12f, muted))
        speed.addView(space(12)); speed.addView(action("CHANGE SPEED", false) { showPage("Settings") })
        addCard(speed)
        val quick = card(); quick.addView(label("QUICK ACTIONS", 11f, muted, true)); quick.addView(space(10))
        quick.addView(action("+  Add a profile", true) { addProspectDialog() }); quick.addView(space(8))
        quick.addView(action("Open review queue", false) { showPage("Queue") })
        addCard(quick)
        val note = card(); note.addView(label("Designed for deliberate outreach", 15f, white, true)); note.addView(space(6))
        note.addView(label("Add profile links yourself, review each person, and open Facebook only when you choose. SocialFlow does not scrape Facebook or send friend requests automatically.", 12f, muted))
        addCard(note)
    }

    private fun metric(value: String, caption: String): LinearLayout {
        val c = card(); c.addView(label(value, 26f, white, true)); c.addView(space(4)); c.addView(label(caption, 11f, muted)); return c
    }

    private fun audiences() {
        title("Audiences", "Organize prospects by where you found them")
        addCard(action("+  Add profile manually", true) { addProspectDialog() })
        val sources = prospects.map { it.source }.distinct()
        if (sources.isEmpty()) {
            val c = card(); c.addView(label("No profiles yet", 16f, white, true)); c.addView(space(6)); c.addView(label("Add a profile name and Facebook profile URL to start building your review list.", 12f, muted)); addCard(c)
        } else sources.forEach { source ->
            val c = card(); c.addView(label(source, 16f, white, true)); c.addView(space(5)); c.addView(label("${prospects.count { it.source == source }} profiles", 12f, muted)); addCard(c)
        }
    }

    private fun queue() {
        title("Review queue", "Review profiles individually before taking action")
        addCard(action("+  Add profile", true) { addProspectDialog() })
        val pending = prospects.filter { !it.reviewed }
        if (pending.isEmpty()) {
            val c = card(); c.addView(label("Your queue is clear", 16f, white, true)); c.addView(space(6)); c.addView(label("Profiles you add will appear here.", 12f, muted)); addCard(c)
        }
        pending.forEach { p ->
            val c = card(); c.addView(label(p.name, 16f, white, true)); c.addView(space(4)); c.addView(label(p.source, 11f, muted)); c.addView(space(12))
            c.addView(action("OPEN PROFILE", true) { openProfile(p.url) }); c.addView(space(7))
            c.addView(action("MARK REVIEWED", false) { p.reviewed = true; saveData(); showPage("Queue") })
            addCard(c)
        }
    }

    private fun history() {
        title("History", "Profiles you have marked as reviewed")
        val done = prospects.filter { it.reviewed }
        if (done.isEmpty()) { val c = card(); c.addView(label("No review history", 15f, white, true)); addCard(c) }
        done.forEach { p -> val c = card(); c.addView(label(p.name, 15f, white, true)); c.addView(space(4)); c.addView(label(p.source, 11f, muted)); addCard(c) }
    }

    private fun settings() {
        title("Settings", "Control the pace and behavior of your workspace")
        val c = card(); c.addView(label("REVIEW SPEED", 11f, muted, true)); c.addView(space(8))
        val value = label("${intervalSeconds} seconds", 24f, Color.rgb(255, 75, 75), true); c.addView(value)
        val seek = SeekBar(this).apply { max = 29; progress = intervalSeconds - 1; progressTintList = android.content.res.ColorStateList.valueOf(red); thumbTintList = android.content.res.ColorStateList.valueOf(red) }
        c.addView(seek)
        val range = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; addView(label("1 sec", 11f, muted), LinearLayout.LayoutParams(0, -2, 1f)); addView(label("30 sec", 11f, muted)) }
        c.addView(range)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) { intervalSeconds = progress + 1; value.text = "$intervalSeconds seconds"; getPreferences(0).edit().putInt("interval", intervalSeconds).apply() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        c.addView(space(12)); c.addView(label("This interval is a personal pacing preference for reviewing your queue. It does not control Facebook or trigger automatic actions.", 12f, muted)); addCard(c)
        val privacy = card(); privacy.addView(label("PRIVACY", 11f, muted, true)); privacy.addView(space(8)); privacy.addView(label("Profile names and links are stored locally on this device. SocialFlow does not ask for or store your Facebook password.", 12f, muted)); addCard(privacy)
        addCard(action("Clear all local profiles", false) { confirmClear() })
    }

    private fun addProspectDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(this).apply { hint = "Profile name"; setSingleLine(); setTextColor(white); setHintTextColor(muted) }
        val url = EditText(this).apply { hint = "Facebook profile URL"; inputType = InputType.TYPE_TEXT_VARIATION_URI; setSingleLine(); setTextColor(white); setHintTextColor(muted) }
        val source = EditText(this).apply { hint = "Source (group / post / other)"; setSingleLine(); setTextColor(white); setHintTextColor(muted) }
        listOf(name, url, source).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("Add profile").setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim(); val u = url.text.toString().trim(); val s = source.text.toString().trim().ifBlank { "Manually added" }
                if (n.isNotBlank() && u.startsWith("https://")) { prospects.add(Prospect(n, u, s)); saveData(); showPage(page) }
                else Toast.makeText(this, "Enter a name and a valid HTTPS profile link", Toast.LENGTH_LONG).show()
            }.show()
    }

    private fun openProfile(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Could not open this link", Toast.LENGTH_SHORT).show() }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setTitle("Clear local profiles?").setMessage("This removes all saved names and links from this device.")
            .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> prospects.clear(); saveData(); showPage("Settings") }.show()
    }

    private fun saveData() {
        val arr = JSONArray()
        prospects.forEach { p -> arr.put(JSONObject().put("name", p.name).put("url", p.url).put("source", p.source).put("reviewed", p.reviewed)) }
        getPreferences(0).edit().putString("prospects", arr.toString()).apply()
    }

    private fun loadData() {
        val raw = getPreferences(0).getString("prospects", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                prospects.add(Prospect(o.optString("name"), o.optString("url"), o.optString("source"), o.optBoolean("reviewed")))
            }
        } catch (_: Exception) { }
    }
}
