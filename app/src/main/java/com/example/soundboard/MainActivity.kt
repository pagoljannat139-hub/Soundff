package com.example.soundboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.UUID

private const val RC_FILE = 1
private const val RC_IMG = 2
private const val RC_ALL = 10
private const val RC_ONE = 11
private const val RC_REC = 12
private const val RC_SCAN = 13

private const val P_MIC = "android.permission.RECORD_AUDIO"
private const val P_NOTIF = "android.permission.POST_NOTIFICATIONS"
private const val P_AUDIO33 = "android.permission.READ_MEDIA_AUDIO"
private const val P_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var tabs: List<TextView>
    private var tab = 0

    private var pendingAudio: String? = null
    private var pendingName = "Voice"
    private var pendingImage: String? = null
    private var formImageBtn: TextView? = null
    private var recorder: MediaRecorder? = null
    private var recFile: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Col.BG
        window.navigationBarColor = Col.SURFACE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Col.BG)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(12))
        }
        header.addView(label("VoxDeck", 28f, Col.TEXT, true))
        header.addView(label("ভয়েস সাউন্ডবোর্ড · গেম ওভারলে", 13f, Col.MUTED))
        root.addView(header)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Col.SURFACE)
        }
        val names = listOf("🎵  লাইব্রেরি", "🛡️  সেটআপ")
        tabs = names.mapIndexed { i, n ->
            label(n, 15f, Col.MUTED, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(16))
                setOnClickListener { tab = i; render() }
            }
        }
        tabs.forEach { bar.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(bar)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        try { recorder?.release() } catch (e: Exception) { }
        recorder = null
        super.onDestroy()
    }

    private fun render() {
        tabs.forEachIndexed { i, t -> t.setTextColor(if (i == tab) Col.ACCENT2 else Col.MUTED) }
        content.removeAllViews()
        content.addView(if (tab == 0) libraryView() else setupView())
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun page(): Pair<ScrollView, LinearLayout> {
        val sv = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
        sv.addView(col)
        return sv to col
    }

    // ================= লাইব্রেরি ট্যাব =================
    private fun libraryView(): View {
        val (sv, col) = page()

        val launch = card()
        launch.addView(label("গেম", 12f, Col.MUTED))
        launch.addView(
            label(Store.gameName(this) ?: "এখনো বাছা হয়নি (সেটআপ ট্যাবে বাছুন)", 17f, Col.TEXT, true)
        )
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(
            pill("🚀 গেম + বাবল চালু", Col.ACCENT).apply { setOnClickListener { startAll() } },
            LinearLayout.LayoutParams(0, -2, 2f)
        )
        btnRow.addView(
            pill("⏹ বন্ধ", Col.SURFACE3, Col.TEXT).apply {
                setOnClickListener {
                    stopService(Intent(this@MainActivity, OverlayService::class.java))
                }
            },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) }
        )
        launch.addView(btnRow, gap(12))
        col.addView(launch, gap(8))

        col.addView(label("ভয়েস যোগ করুন", 14f, Col.MUTED, true), gap(20))
        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val b1 = pill("📁 ফাইল", Col.SURFACE3, Col.TEXT, 13f).apply {
            setOnClickListener { pickFile() }
        }
        val b2 = pill("🎧 অডিও", Col.SURFACE3, Col.TEXT, 13f).apply {
            setOnClickListener { scanAudio() }
        }
        val b3 = pill("🎙 রেকর্ড", Col.SURFACE3, Col.TEXT, 13f).apply {
            setOnClickListener { startRecordFlow() }
        }
        listOf(b1, b2, b3).forEachIndexed { i, b ->
            addRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (i > 0) leftMargin = dp(8)
            })
        }
        col.addView(addRow, gap(8))

        val clips = Store.load(this)
        col.addView(label("লাইব্রেরি (${clips.size})", 14f, Col.MUTED, true), gap(20))
        if (clips.isEmpty()) {
            col.addView(label("এখনো কোনো ভয়েস নেই। উপরের বাটন দিয়ে যোগ করুন।", 14f, Col.MUTED), gap(8))
        }
        clips.forEach { c -> col.addView(clipCard(c), gap(8)) }
        return sv
    }

    private fun iconBtn(t: String, act: () -> Unit): TextView =
        label(t, 20f).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { act() }
        }

    private fun clipCard(c: Clip): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(Col.SURFACE, 16)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        row.addView(thumbView(c.image, 52))
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        mid.addView(label(c.name, 16f, Col.TEXT, true).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val range = if (c.endMs > 0) "${sec(c.startMs)}s – ${sec(c.endMs)}s"
        else "${sec(c.startMs)}s – শেষ"
        mid.addView(label("${fxOf(c.fx).label} · $range", 12f, Col.MUTED))
        row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(iconBtn("▶️") { Player.play(this, c, false) })
        row.addView(iconBtn("✏️") { showForm(c.audio, c) })
        row.addView(iconBtn("🗑") { confirmDelete(c) })
        return row
    }

    private fun confirmDelete(c: Clip) {
        AlertDialog.Builder(this)
            .setTitle("মুছবেন?")
            .setMessage(c.name)
            .setPositiveButton("মুছুন") { _, _ ->
                Player.stop()
                File(c.audio).delete()
                c.image?.let { File(it).delete() }
                Store.save(this, Store.load(this).filter { it.id != c.id })
                render()
            }
            .setNegativeButton("না", null)
            .show()
    }

    // ================= সেটআপ ট্যাব =================
    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun hasMic() = granted(P_MIC)
    private fun audioPerm() = if (Build.VERSION.SDK_INT >= 33) P_AUDIO33 else P_STORAGE
    private fun hasAudio() = granted(audioPerm())
    private fun hasNotif() = Build.VERSION.SDK_INT < 33 || granted(P_NOTIF)
    private fun batteryOk() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun openOverlaySettings() {
        toast("অনুমতি দিয়ে ফিরে আসুন")
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun permCard(icon: String, title: String, desc: String, ok: Boolean, act: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(Col.SURFACE, 16)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        row.addView(label(icon, 26f))
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        mid.addView(label(title, 15f, Col.TEXT, true))
        mid.addView(label(desc, 12f, Col.MUTED))
        row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(
            if (ok) label("✓ চালু", 13f, Col.OK, true)
            else pill("দিন", Col.ACCENT, Color.WHITE, 13f).apply { setOnClickListener { act() } }
        )
        return row
    }

    private fun grantAll() {
        val need = mutableListOf<String>()
        if (!hasMic()) need.add(P_MIC)
        if (!hasAudio()) need.add(audioPerm())
        if (!hasNotif()) need.add(P_NOTIF)
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), RC_ALL)
        } else if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
        } else {
            toast("সব অনুমতি দেওয়া আছে ✅")
        }
    }

    private fun setupView(): View {
        val (sv, col) = page()
        col.addView(label("অনুমতি", 14f, Col.MUTED, true), gap(8))
        col.addView(
            permCard("🎙", "মাইক্রোফোন", "নিজের গলা রেকর্ড করে ক্লিপ বানাতে লাগে", hasMic()) {
                requestPermissions(arrayOf(P_MIC), RC_ONE)
            }, gap(8)
        )
        col.addView(
            permCard("🪟", "স্ক্রিনের উপরে দেখানো", "গেমের উপরে বাবল ভাসাতে লাগে",
                Settings.canDrawOverlays(this)) { openOverlaySettings() }, gap(8)
        )
        col.addView(
            permCard("📂", "অডিও ফাইল", "ফোনের ভয়েস ফাইল খুঁজে আনতে লাগে", hasAudio()) {
                requestPermissions(arrayOf(audioPerm()), RC_ONE)
            }, gap(8)
        )
        if (Build.VERSION.SDK_INT >= 33) {
            col.addView(
                permCard("🔔", "নোটিফিকেশন", "বাবল চালু থাকার নোটিফিকেশন দেখাতে লাগে", hasNotif()) {
                    requestPermissions(arrayOf(P_NOTIF), RC_ONE)
                }, gap(8)
            )
        }
        col.addView(
            permCard("🔋", "ব্যাটারি", "ব্যাকগ্রাউন্ডে বাবল যেন বন্ধ না হয়", batteryOk()) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }, gap(8)
        )
        col.addView(
            pill("✅ সব অনুমতি একবারে দিন", Col.ACCENT).apply { setOnClickListener { grantAll() } },
            gap(14)
        )

        col.addView(label("গেম", 14f, Col.MUTED, true), gap(24))
        val gcard = card()
        gcard.addView(label("গেম বাছুন", 15f, Col.TEXT, true))
        gcard.addView(
            label(Store.gameName(this)?.let { "নির্বাচিত: $it ✅" } ?: "এখনো বাছা হয়নি", 13f, Col.MUTED)
        )
        gcard.addView(
            pill("➕ ফ্রি ফায়ার যোগ করুন", Col.SURFACE3, Col.TEXT).apply {
                setOnClickListener { chooseGame() }
            }, gap(10)
        )
        col.addView(gcard, gap(8))

        val tips = card()
        tips.addView(label("টিপস", 15f, Col.TEXT, true))
        tips.addView(
            label(
                "• গেমে মাইক অন রাখুন, হেডফোন খুলে রাখুন\n" +
                    "• ▶️ চাপলে ফোনের স্পিকারে বাজে, মাইক সেটা ধরে\n" +
                    "• গেমের ইকো ফিল্টার কিছু ক্লিপ কেটে দিতে পারে\n" +
                    "• আমার জানা মতে ফ্রি ফায়ারে সাধারণ ম্যাচে ভয়েস শুধু স্কোয়াড শোনে",
                13f, Col.MUTED
            )
        )
        col.addView(tips, gap(12))
        return sv
    }

    private fun installed(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) { false }

    private fun chooseGame() {
        val games = listOf("Free Fire" to "com.dts.freefireth", "Free Fire MAX" to "com.dts.freefiremax")
            .filter { installed(it.second) }
        if (games.isEmpty()) {
            toast("ফ্রি ফায়ার ইন্সটল করা নেই")
            return
        }
        AlertDialog.Builder(this).setTitle("গেম বাছুন")
            .setItems(games.map { it.first }.toTypedArray()) { _, i ->
                Store.setGame(this, games[i].second, games[i].first)
                render()
            }.show()
    }

    private fun startAll() {
        val pkg = Store.game(this)
        if (pkg == null) {
            toast("আগে সেটআপ ট্যাবে গেম বাছুন")
            tab = 1; render(); return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("আগে 'স্ক্রিনের উপরে দেখানো' অনুমতি দিন")
            tab = 1; render(); return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RC_ALL -> {
                render()
                if (!Settings.canDrawOverlays(this)) openOverlaySettings()
            }
            RC_REC -> if (hasMic()) startRecordFlow() else toast("মাইকের অনুমতি লাগবে")
            RC_SCAN -> if (hasAudio()) scanAudio() else toast("অডিও ফাইলের অনুমতি লাগবে")
            else -> render()
        }
    }

    // ================= ভয়েস যোগ করা =================
    private fun pickFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, RC_FILE)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK || data == null) return
        try {
            if (req == RC_FILE) {
                val cd = data.clipData
                if (cd != null && cd.itemCount > 1) {
                    val uris = (0 until cd.itemCount).map { cd.getItemAt(it).uri }
                    importMany(uris.map { it to fileName(it) })
                } else {
                    val uri = data.data ?: return
                    val p = copyIn(uri, ".mp3")
                    pendingAudio = p
                    pendingName = fileName(uri)
                    pendingImage = null
                    showForm(p, null)
                }
            } else if (req == RC_IMG) {
                val uri = data.data ?: return
                pendingImage = copyIn(uri, ".img")
                formImageBtn?.text = "✅ ছবি বাছা হয়েছে"
            }
        } catch (e: Exception) {
            toast("ফাইল নেওয়া যায়নি")
        }
    }

    private fun importMany(list: List<Pair<Uri, String>>) {
        toast("${list.size}টি যোগ হচ্ছে...")
        Thread {
            val added = mutableListOf<Clip>()
            list.forEach { (u, n) ->
                try {
                    val p = copyIn(u, ".mp3")
                    added.add(Clip(UUID.randomUUID().toString(), n, p, null, 0, 0, "normal"))
                } catch (e: Exception) { }
            }
            val all = Store.load(this)
            all.addAll(added)
            Store.save(this, all)
            runOnUiThread {
                toast("${added.size}টি যোগ হয়েছে")
                render()
            }
        }.start()
    }

    private fun scanAudio() {
        if (!hasAudio()) {
            requestPermissions(arrayOf(audioPerm()), RC_SCAN)
            return
        }
        val items = mutableListOf<Triple<Uri, String, Int>>()
        try {
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.query(
                base,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.DURATION
                ),
                "${MediaStore.Audio.Media.DURATION} > 0 AND ${MediaStore.Audio.Media.DURATION} < 180000",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext() && items.size < 150) {
                    items.add(
                        Triple(
                            ContentUris.withAppendedId(base, c.getLong(0)),
                            c.getString(1) ?: "Audio",
                            c.getInt(2)
                        )
                    )
                }
            }
        } catch (e: Exception) { }
        if (items.isEmpty()) {
            toast("কোনো অডিও পাওয়া যায়নি")
            return
        }
        val names = items.map { "${it.second} (${sec(it.third)}s)" }.toTypedArray()
        val checked = BooleanArray(items.size)
        AlertDialog.Builder(this)
            .setTitle("যেগুলো আনবেন বাছুন")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("যোগ করুন") { _, _ ->
                val sel = items.filterIndexed { i, _ -> checked[i] }.map { it.first to it.second }
                if (sel.isNotEmpty()) importMany(sel)
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    private fun startRecordFlow() {
        if (!hasMic()) {
            requestPermissions(arrayOf(P_MIC), RC_REC)
            return
        }
        val f = File(filesDir, UUID.randomUUID().toString() + ".m4a")
        try {
            val r = newRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recFile = f.absolutePath
        } catch (e: Exception) {
            toast("রেকর্ড শুরু করা যায়নি (অন্য অ্যাপ মাইক ব্যবহার করছে?)")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("🔴 রেকর্ড হচ্ছে...")
            .setMessage("কথা বলুন, শেষ হলে থামান চাপুন")
            .setCancelable(false)
            .setPositiveButton("⏹ থামান") { _, _ -> finishRecord() }
            .show()
    }

    private fun finishRecord() {
        val r = recorder
        val path = recFile
        recorder = null
        try {
            r?.stop()
        } catch (e: Exception) {
            toast("রেকর্ড খুব ছোট ছিল, আবার চেষ্টা করুন")
            try { r?.release() } catch (x: Exception) { }
            return
        }
        try { r?.release() } catch (e: Exception) { }
        if (path != null) {
            pendingAudio = path
            pendingName = "Recording"
            pendingImage = null
            showForm(path, null)
        }
    }

    // ================= নতুন/এডিট ফর্ম =================
    private fun showForm(path: String, existing: Clip?) {
        val total = audioDurationMs(path)
        pendingImage = existing?.image
        var fxSel = existing?.fx ?: "normal"
        var saved = false

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val nameEt = EditText(this).apply {
            hint = "নাম"
            setText(existing?.name ?: pendingName)
        }
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val endInit = if (existing != null && existing.endMs > 0) existing.endMs else total
        val startEt = EditText(this).apply {
            hint = "শুরু (সেকেন্ড)"
            inputType = dec
            setText(sec(existing?.startMs ?: 0))
        }
        val endEt = EditText(this).apply {
            hint = "শেষ (সেকেন্ড)"
            inputType = dec
            setText(sec(endInit))
        }
        box.addView(label("নাম", 12f, Col.MUTED))
        box.addView(nameEt)
        box.addView(label("কত সেকেন্ড থেকে (শুরু)", 12f, Col.MUTED), gap(8))
        box.addView(startEt)
        box.addView(label("কত সেকেন্ড পর্যন্ত (শেষ)", 12f, Col.MUTED), gap(8))
        box.addView(endEt)

        fun readTrim(): Pair<Int, Int> {
            val s = (startEt.text.toString().toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            val e = endEt.text.toString().toDoubleOrNull() ?: 0.0
            return (s * 1000).toInt() to (if (e > s) (e * 1000).toInt() else 0)
        }

        box.addView(label("ইফেক্ট", 12f, Col.MUTED), gap(12))
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<Pair<Fx, TextView>>()
        fun paint() {
            chips.forEach { (f, v) -> v.background = shape(if (f.id == fxSel) Col.ACCENT else Col.SURFACE3, 10) }
        }
        FX_LIST.forEach { f ->
            val v = label(f.label, 13f, Color.WHITE, true).apply {
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            v.setOnClickListener { fxSel = f.id; paint() }
            chips.add(f to v)
            chipRow.addView(v, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
        paint()
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        })

        formImageBtn = pill(
            if (pendingImage != null) "✅ ছবি বাছা হয়েছে" else "🖼️ ছবি বাছুন (ঐচ্ছিক)",
            Col.SURFACE3, Col.TEXT, 13f
        ).apply {
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }, RC_IMG)
            }
        }
        val prev = pill("▶️ শুনে দেখুন", Col.SURFACE3, Col.TEXT, 13f).apply {
            setOnClickListener {
                val (s, e) = readTrim()
                Player.play(this@MainActivity, Clip("t", "t", path, null, s, e, fxSel), false)
            }
        }
        val actRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actRow.addView(formImageBtn, LinearLayout.LayoutParams(0, -2, 1f))
        actRow.addView(prev, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        box.addView(actRow, gap(14))

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "নতুন ভয়েস · ${sec(total)}s" else "এডিট")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("সেভ") { _, _ ->
                val (s, e) = readTrim()
                val clip = Clip(
                    existing?.id ?: UUID.randomUUID().toString(),
                    nameEt.text.toString().ifBlank { "Voice" },
                    path, pendingImage, s, e, fxSel
                )
                val list = Store.load(this)
                val idx = list.indexOfFirst { it.id == clip.id }
                if (idx >= 0) list[idx] = clip else list.add(clip)
                Store.save(this, list)
                saved = true
                render()
            }
            .setNegativeButton("বাতিল", null)
            .setOnDismissListener {
                Player.stop()
                if (!saved && existing == null) File(path).delete()
            }
            .show()
    }
}
