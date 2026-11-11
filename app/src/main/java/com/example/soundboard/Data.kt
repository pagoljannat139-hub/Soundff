package com.example.soundboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

// ---------- রং ----------
object Col {
    val BG = 0xFF0E0F14.toInt()
    val SURFACE = 0xFF171923.toInt()
    val SURFACE3 = 0xFF2A2E42.toInt()
    val ACCENT = 0xFF7C5CFF.toInt()
    val ACCENT2 = 0xFF22D3EE.toInt()
    val OK = 0xFF34D399.toInt()
    val TEXT = 0xFFF2F4F8.toInt()
    val MUTED = 0xFF8B92A5.toInt()
}

// ---------- ভয়েস ইফেক্ট ----------
data class Fx(val id: String, val label: String, val pitch: Float, val speed: Float)

val FX_LIST = listOf(
    Fx("normal", "Normal", 1.0f, 1.0f),
    Fx("deep", "Deep", 0.75f, 1.0f),
    Fx("giant", "Giant", 0.55f, 0.85f),
    Fx("kid", "Kid", 1.35f, 1.05f),
    Fx("chipmunk", "Chipmunk", 1.7f, 1.15f),
    Fx("fast", "Fast", 1.0f, 1.4f),
    Fx("slow", "Slow", 1.0f, 0.75f)
)

fun fxOf(id: String): Fx = FX_LIST.firstOrNull { it.id == id } ?: FX_LIST[0]

// ---------- ডাটা ----------
data class Clip(
    val id: String,
    val name: String,
    val audio: String,
    val image: String?,
    val startMs: Int,
    val endMs: Int,
    val fx: String
)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("sb", 0)

    fun load(c: Context): MutableList<Clip> {
        val out = mutableListOf<Clip>()
        try {
            val arr = JSONArray(sp(c).getString("clips", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Clip(
                        o.getString("id"), o.getString("name"), o.getString("audio"),
                        o.optString("image", "").ifEmpty { null },
                        o.optInt("s", 0), o.optInt("e", 0), o.optString("fx", "normal")
                    )
                )
            }
        } catch (e: Exception) { }
        return out
    }

    fun save(c: Context, list: List<Clip>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("audio", it.audio)
                    .put("image", it.image ?: "").put("s", it.startMs).put("e", it.endMs)
                    .put("fx", it.fx)
            )
        }
        sp(c).edit().putString("clips", arr.toString()).apply()
    }

    fun game(c: Context): String? = sp(c).getString("game", null)
    fun gameName(c: Context): String? = sp(c).getString("gname", null)
    fun setGame(c: Context, pkg: String, name: String) {
        sp(c).edit().putString("game", pkg).putString("gname", name).apply()
    }
}

// ---------- প্লেয়ার (অ্যাপ ও বাবল দুটোই ব্যবহার করে) ----------
object Player {
    private val handler = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var task: Runnable? = null

    fun stop() {
        task?.let { handler.removeCallbacks(it) }
        task = null
        try { mp?.release() } catch (e: Exception) { }
        mp = null
    }

    // ear = true হলে কানের স্পিকারে আস্তে বাজে (নিজে শোনার জন্য)
    fun play(ctx: Context, c: Clip, ear: Boolean) {
        stop()
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            val p = MediaPlayer()
            p.setDataSource(c.audio)
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            val fx = fxOf(c.fx)
            if (ear) {
                p.setVolume(0.5f, 0.5f)
                if (Build.VERSION.SDK_INT >= 28) {
                    am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?.let { p.setPreferredDevice(it) }
                }
            } else {
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0
                )
            }
            try {
                p.setPlaybackParams(PlaybackParams().setPitch(fx.pitch).setSpeed(fx.speed))
            } catch (e: Exception) { }
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepare()
            mp = p
            val begin = c.startMs
            fun go() {
                try {
                    p.start()
                    if (c.endMs > begin) {
                        val t = Runnable { stop() }
                        task = t
                        handler.postDelayed(t, ((c.endMs - begin) / fx.speed).toLong())
                    }
                } catch (e: Exception) { }
            }
            if (begin > 0) {
                p.setOnSeekCompleteListener { go() }
                p.seekTo(begin)
            } else go()
        } catch (e: Exception) { }
    }
}

// ---------- ছোট সাহায্যকারী ----------
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.shape(col: Int, r: Int = 14) =
    GradientDrawable().apply { setColor(col); cornerRadius = dp(r).toFloat() }

fun Context.label(t: String, size: Float = 14f, tc: Int = Col.TEXT, bold: Boolean = false) =
    TextView(this).apply {
        text = t
        textSize = size
        setTextColor(tc)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.pill(t: String, fill: Int, tc: Int = Color.WHITE, size: Float = 14f) =
    TextView(this).apply {
        text = t
        textSize = size
        setTextColor(tc)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = shape(fill, 12)
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = shape(Col.SURFACE, 16)
    setPadding(dp(16), dp(16), dp(16), dp(16))
}

fun Context.gap(h: Int = 12) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(h) }

fun Context.copyIn(uri: Uri, ext: String): String {
    val f = File(filesDir, UUID.randomUUID().toString() + ext)
    contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    return f.absolutePath
}

fun Context.fileName(uri: Uri): String {
    try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i).substringBeforeLast('.')
        }
    } catch (e: Exception) { }
    return "Voice"
}

fun audioDurationMs(path: String): Int = try {
    val r = MediaMetadataRetriever()
    r.setDataSource(path)
    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
    r.release()
    d
} catch (e: Exception) { 0 }

fun sec(ms: Int): String = String.format(Locale.US, "%.1f", ms / 1000.0)

fun thumb(path: String?): Bitmap? {
    if (path == null) return null
    return try {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 8 })
    } catch (e: Exception) { null }
}

fun Context.thumbView(path: String?, size: Int): View {
    val bm = thumb(path)
    return if (bm != null) {
        ImageView(this).apply {
            setImageBitmap(bm)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = shape(Col.SURFACE3, 12)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    } else {
        TextView(this).apply {
            text = "🎵"
            textSize = 20f
            gravity = Gravity.CENTER
            background = shape(Col.SURFACE3, 12)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    }
}
