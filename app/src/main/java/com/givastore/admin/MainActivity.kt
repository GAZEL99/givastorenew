package com.givastore.admin

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/* ============================================================
   KONFIGURASI
   ============================================================ */
private const val SUPABASE_URL = "https://rblktttasrxemtkhknvt.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJibGt0dHRhc3J4ZW10a2hrbnZ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NDQ5MzMsImV4cCI6MjEwMTAyMDkzM30.l4mPqAlPhZk-Z73_sKNARc3qTxAfUsKZyNl9u6N90Lw"
private const val STORE_URL = "https://givastore.biz.id"

private const val REQ_PICK_IMAGE = 4101
private const val REQ_NOTIF = 1001
private const val CH_ORDERS = "givastore_admin_orders"
private const val CH_MONITOR = "givastore_monitor"
private const val NOTIF_MONITOR_ID = 7001

/* ============================================================
   PALET WARNA (sama dengan CSS :root versi HTML)
   ============================================================ */
val BgBase = Color(0xFF0A0A0A)
val BgSurface = Color(0xFF121214)
val BgCard = Color(0xFF18181C)
val FieldBg = Color(0xFF0F0F11)
val Ink = Color(0xFFF7F7F5)
val InkDim = Color(0xFF9B9BA3)
val InkFaint = Color(0xFF5A5A64)
val LineC = Color(0x1AFFFFFF)
val LineStrong = Color(0x3DFFFFFF)
val WaGreen = Color(0xFF25D366)
val WaInk = Color(0xFF06210F)
val RedC = Color(0xFFFF4757)
val YellowC = Color(0xFFF59E0B)
val GreenC = Color(0xFF10B981)

val GivaColors = darkColorScheme(
    primary = Ink, onPrimary = BgBase,
    background = BgBase, onBackground = Ink,
    surface = BgSurface, onSurface = Ink,
    surfaceVariant = BgCard, onSurfaceVariant = InkDim,
    outline = LineC
)

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
private val ID_LOCALE: Locale = Locale.forLanguageTag("id-ID")

/* ============================================================
   SUPABASE API (Auth + REST + Storage)
   ============================================================ */
class SupabaseApi(
    private val baseUrl: String,
    private val anonKey: String,
    val client: OkHttpClient,
    val store: SharedPreferences
) {
    @Volatile private var accessToken: String? = null
    @Volatile private var memRefresh: String? = null
    private val json = "application/json".toMediaType()

    fun isBioEnabled() = store.getBoolean("bio_enabled", false)
    fun hasSavedSession() = isBioEnabled() && !store.getString("refresh", null).isNullOrEmpty()
    fun ensureSession(): Boolean = accessToken != null || refreshSession()

    fun setBiometric(enabled: Boolean): Boolean {
        if (enabled) {
            if (memRefresh.isNullOrEmpty() && store.getString("refresh", null).isNullOrEmpty()) return false
            store.edit().putBoolean("bio_enabled", true).apply()
            return true
        }
        store.edit().putBoolean("bio_enabled", false).apply()
        return true
    }

    fun signIn(email: String, password: String) {
        authCall("password", JSONObject().put("email", email).put("password", password).toString())
    }

    fun refreshSession(): Boolean {
        val rt = memRefresh ?: store.getString("refresh", null) ?: return false
        if (rt.isEmpty()) return false
        return try {
            authCall("refresh_token", JSONObject().put("refresh_token", rt).toString()); true
        } catch (e: Exception) { false }
    }

    fun logout() {
        accessToken = null
        memRefresh = null
        if (!isBioEnabled()) store.edit().remove("refresh").apply()
    }

    private fun authCall(grant: String, body: String) {
        val req = Request.Builder().url("$baseUrl/auth/v1/token?grant_type=$grant")
            .addHeader("apikey", anonKey).post(body.toRequestBody(json)).build()
        val (code, text) = client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        if (code !in 200..299) throw IOException(parseError(text, code))
        val obj = JSONObject(text)
        accessToken = obj.getString("access_token")
        val rt = obj.optString("refresh_token")
        memRefresh = rt
        if (rt.isNotEmpty()) store.edit().putString("refresh", rt).apply()
    }

    private fun call(method: String, path: String, body: String? = null, prefer: String? = null): String {
        for (attempt in 0..1) {
            val token = accessToken ?: throw IOException("Belum login")
            val b = Request.Builder().url("$baseUrl/rest/v1/$path")
                .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $token")
            if (prefer != null) b.addHeader("Prefer", prefer)
            val rb = (body ?: "").toRequestBody(json)
            when (method) {
                "GET" -> b.get()
                "POST" -> b.post(rb)
                "PATCH" -> b.patch(rb)
                "DELETE" -> b.delete()
                else -> error("Method tidak didukung")
            }
            val (code, text) = client.newCall(b.build()).execute().use { it.code to it.body?.string().orEmpty() }
            if (code == 401 && attempt == 0 && refreshSession()) continue
            if (code !in 200..299) throw IOException(parseError(text, code))
            return text
        }
        throw IOException("Sesi berakhir, silakan login ulang")
    }

    fun get(path: String) = JSONArray(call("GET", path).ifBlank { "[]" })
    fun post(path: String, body: Any, prefer: String? = null): String = call("POST", path, body.toString(), prefer)
    fun patch(path: String, body: JSONObject) { call("PATCH", path, body.toString()) }
    fun delete(path: String) { call("DELETE", path) }

    /** Upload foto ke bucket Supabase Storage "product-images", mengembalikan public URL. */
    fun uploadImage(bytes: ByteArray, mime: String, ext: String, folder: String): String {
        val rand = (1..5).map { ('a'..'z').random() }.joinToString("")
        val path = "$folder/${System.currentTimeMillis()}_$rand.$ext"
        for (attempt in 0..1) {
            val token = accessToken ?: throw IOException("Belum login")
            val req = Request.Builder().url("$baseUrl/storage/v1/object/product-images/$path")
                .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $token")
                .addHeader("x-upsert", "true")
                .post(bytes.toRequestBody(mime.toMediaType())).build()
            val (code, text) = client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
            if (code == 401 && attempt == 0 && refreshSession()) continue
            if (code !in 200..299) throw IOException(storageError(code, text))
            return "$baseUrl/storage/v1/object/public/product-images/$path"
        }
        throw IOException("Sesi berakhir, silakan login ulang")
    }

    private fun storageError(code: Int, text: String): String {
        val t = text.lowercase()
        return when {
            code == 404 || t.contains("bucket not found") -> "Bucket 'product-images' belum dibuat di Supabase Storage."
            code == 401 || code == 403 || t.contains("row-level security") || t.contains("unauthorized") ->
                "Upload ditolak: kebijakan Storage belum mengizinkan user login."
            code == 413 -> "Ukuran foto terlalu besar."
            else -> parseError(text, code)
        }
    }

    private fun parseError(text: String, code: Int): String = try {
        val o = JSONObject(text)
        listOf("error_description", "msg", "message", "error").map { o.optString(it) }.firstOrNull { it.isNotEmpty() } ?: "HTTP $code"
    } catch (e: Exception) { "HTTP $code" }
}

fun createPrefs(ctx: Context): SharedPreferences = try {
    val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    EncryptedSharedPreferences.create(
        ctx, "gs_secure", mk,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (e: Exception) {
    ctx.getSharedPreferences("gs_plain", Context.MODE_PRIVATE)
}

/** Satu instance API dipakai bersama oleh aplikasi dan service pemantau (hindari konflik refresh token). */
object ApiHolder {
    @Volatile private var inst: SupabaseApi? = null
    fun get(ctx: Context): SupabaseApi = inst ?: synchronized(this) {
        inst ?: SupabaseApi(
            SUPABASE_URL, SUPABASE_ANON_KEY,
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build(),
            createPrefs(ctx.applicationContext)
        ).also { inst = it }
    }
}

/* ============================================================
   MODEL DATA & HELPER
   ============================================================ */
data class NativeOrder(
    val id: String, val orderId: String, val productName: String, val packageName: String,
    val total: Long, val whatsapp: String, val status: String,
    val createdMillis: Long, val paidMillis: Long, val accountData: String
) {
    val isPaid get() = status.equals("paid", true)
    val eventMillis get() = if (paidMillis > 0) paidMillis else createdMillis
}

data class NativeProduct(
    val id: String, val name: String, val slug: String, val categoryId: String, val imageUrl: String,
    val basePrice: Long, val originalPrice: Long, val stock: Int, val discount: Int,
    val description: String, val isFlash: Boolean
)

data class NativeExpense(val id: String, val description: String, val category: String, val amount: Long, val dateMillis: Long)
data class NativeCategory(val id: String, val name: String)
data class NativePromo(val id: String, val code: String, val type: String, val value: Double, val minOrder: Long)
data class NativePopup(val id: String, val title: String, val message: String, val isActive: Boolean)

enum class Period(val pill: String, val full: String) {
    TODAY("Hari Ini", "Hari Ini"), WEEK("Minggu", "Minggu Ini"), MONTH("Bulan", "Bulan Ini"), YEAR("Tahun", "Tahun Ini")
}

sealed interface SheetType {
    data class OrderDetail(val id: String) : SheetType
    data class ProductEdit(val id: String?) : SheetType
    object Category : SheetType
    object Promo : SheetType
    object Customer : SheetType
    object NewPopup : SheetType
}

class ConfirmReq(val message: String, val action: () -> Unit)

class PkgRow(n: String = "", p: String = "", d: String = "1 BULAN") {
    var name by mutableStateOf(n)
    var price by mutableStateOf(p)
    var duration by mutableStateOf(d)
}

class InpRow(l: String = "", t: String = "text") {
    var label by mutableStateOf(l)
    var type by mutableStateOf(t)
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
fun JSONObject.str(key: String, fb: String = ""): String = if (isNull(key)) fb else optString(key, fb)

fun JSONObject.optSafeLong(key: String, fallback: Long = 0L): Long {
    if (isNull(key)) return fallback
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toDoubleOrNull()?.toLong() ?: fallback
        else -> fallback
    }
}

fun parseIsoMillis(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    return try {
        val s = raw.trim().replace(' ', 'T')
        if (s.length < 19) {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = UTC }
            return f.parse(s.take(10))?.time ?: 0L
        }
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = UTC }
        var millis = f.parse(s.substring(0, 19))?.time ?: return 0L
        var rest = s.substring(19)
        if (rest.startsWith(".")) {
            var i = 1
            while (i < rest.length && rest[i].isDigit()) i++
            millis += rest.substring(1, i).padEnd(3, '0').take(3).toLong()
            rest = rest.substring(i)
        }
        if (rest.length >= 3 && (rest[0] == '+' || rest[0] == '-')) {
            val sign = if (rest[0] == '-') -1 else 1
            val h = rest.substring(1, 3).toInt()
            val m = if (rest.length >= 6) rest.substring(4, 6).toInt() else 0
            millis -= sign * (h * 60 + m) * 60_000L
        }
        millis
    } catch (e: Exception) { 0L }
}

fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = UTC }.format(Date())

fun dayStartMillis(daysAgo: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_MONTH, -daysAgo)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun periodStart(p: Period): Long {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    when (p) {
        Period.TODAY -> {}
        Period.WEEK -> c.add(Calendar.DAY_OF_MONTH, -(c.get(Calendar.DAY_OF_WEEK) - 1))
        Period.MONTH -> c.set(Calendar.DAY_OF_MONTH, 1)
        Period.YEAR -> { c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1) }
    }
    return c.timeInMillis
}

private val idNumberFmt: NumberFormat = NumberFormat.getInstance(ID_LOCALE).apply { maximumFractionDigits = 0 }
fun fmt(n: Number?): String = "Rp " + idNumberFmt.format(Math.round((n ?: 0).toDouble()))
fun fmtShort(n: Long): String = when {
    n >= 1_000_000_000L -> "Rp " + String.format(Locale.US, "%.1f", n / 1e9) + " M"
    n >= 1_000_000L -> "Rp " + String.format(Locale.US, "%.1f", n / 1e6) + " Jt"
    else -> fmt(n)
}

fun fmtDateShort(ms: Long): String = SimpleDateFormat("d MMM, HH.mm", ID_LOCALE).format(Date(ms))
fun fmtDateFull(ms: Long): String = SimpleDateFormat("d/M/yyyy HH.mm.ss", ID_LOCALE).format(Date(ms))
fun fmtDay(ms: Long): String = SimpleDateFormat("d/M/yyyy", ID_LOCALE).format(Date(ms))

fun timeAgo(ms: Long): String {
    if (ms <= 0) return "-"
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "Baru saja"
        d < 3_600_000 -> "${d / 60_000} mnt lalu"
        d < 86_400_000 -> "${d / 3_600_000} jam lalu"
        else -> fmtDateShort(ms)
    }
}

fun slugify(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

fun resolveImg(url: String?): String {
    var s = (url ?: "").trim()
    if (s.isEmpty()) return ""
    if (s.startsWith("http://") || s.startsWith("https://")) return s
    while (s.startsWith("../")) s = s.substring(3)
    if (s.startsWith("./")) s = s.substring(2)
    if (STORE_URL.isBlank()) return ""
    return STORE_URL.trimEnd('/') + "/" + s.trimStart('/')
}

fun readOrientation(bytes: ByteArray): Int = try {
    when (android.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
        .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
        6 -> 90; 3 -> 180; 8 -> 270; else -> 0
    }
} catch (e: Exception) { 0 }

fun JSONObject.toOrder(): NativeOrder {
    val acc = opt("account_data")
    val accStr = when {
        acc == null || isNull("account_data") -> ""
        acc is JSONObject -> acc.toString(2)
        acc is JSONArray -> acc.toString(2)
        else -> acc.toString()
    }
    val created = parseIsoMillis(str("created_at"))
    val paid = parseIsoMillis(str("paid_at"))
    val total = optSafeLong("total_payment").let { if (it != 0L) it else optSafeLong("total_amount") }
    return NativeOrder(
        id = str("id"),
        orderId = str("order_id").ifEmpty { str("id") },
        productName = str("product_name"),
        packageName = str("package_name"),
        total = total,
        whatsapp = str("customer_whatsapp"),
        status = str("status", "Pending"),
        createdMillis = created, paidMillis = paid, accountData = accStr
    )
}

fun JSONObject.toProduct() = NativeProduct(
    id = str("id"), name = str("name"), slug = str("slug"), categoryId = str("category_id"),
    imageUrl = str("image_url"), basePrice = optSafeLong("base_price"), originalPrice = optSafeLong("original_price"),
    stock = optInt("stock", 0), discount = optInt("discount_percentage", 0),
    description = str("description"), isFlash = optBoolean("is_flash_sale", false)
)

/** Data grafik penjualan: 14 hari atau 8 minggu (sama dengan versi web). */
fun salesSeries(orders: List<NativeOrder>, weekly: Boolean): Pair<List<String>, List<Long>> {
    val paid = orders.filter { it.isPaid }
    val labels = mutableListOf<String>()
    val data = mutableListOf<Long>()
    val dFmt = SimpleDateFormat("d MMM", ID_LOCALE)
    if (!weekly) {
        for (i in 13 downTo 0) {
            val s = dayStartMillis(i); val e = dayStartMillis(i - 1)
            labels.add(dFmt.format(Date(s)))
            data.add(paid.filter { it.eventMillis in s until e }.sumOf { it.total })
        }
    } else {
        for (w in 7 downTo 0) {
            val s = dayStartMillis(w * 7 + 6); val e = dayStartMillis(w * 7 - 1)
            val startDay = Calendar.getInstance().apply { timeInMillis = s }.get(Calendar.DAY_OF_MONTH)
            labels.add("$startDay-" + dFmt.format(Date(dayStartMillis(w * 7))))
            data.add(paid.filter { it.eventMillis in s until e }.sumOf { it.total })
        }
    }
    return labels to data
}

/* ============================================================
   NOTIFIKASI (channel, notifikasi pesanan, service pemantau)
   ============================================================ */
fun ensureChannels(ctx: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(
        NotificationChannel(CH_ORDERS, "Pesanan Baru GivaStore", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Notifikasi transaksi toko"; enableVibration(true) }
    )
    nm.createNotificationChannel(
        NotificationChannel(CH_MONITOR, "Pemantau Pesanan", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Menjaga notifikasi pesanan tetap aktif di latar belakang" }
    )
}

fun notifyNewOrder(ctx: Context, o: NativeOrder) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    ensureChannels(ctx)
    val open = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }
    val pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val pkg = if (o.packageName.isNotEmpty()) " · ${o.packageName}" else ""
    val n = NotificationCompat.Builder(ctx, CH_ORDERS)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Pesanan Baru Masuk! ⚡")
        .setContentText("${o.productName} • ${fmt(o.total)}")
        .setStyle(NotificationCompat.BigTextStyle().bigText("#${o.orderId}\n${o.productName}$pkg\n${fmt(o.total)} • +${o.whatsapp}"))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true).setContentIntent(pi).build()
    ctx.getSystemService(NotificationManager::class.java)?.notify(o.id.hashCode(), n)
}

/** Service latar depan: mengecek pesanan baru tiap beberapa detik walau aplikasi ditutup. */
class OrderMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val pi = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GivaStore Admin aktif")
            .setContentText("Memantau pesanan baru...")
            .setOngoing(true).setContentIntent(pi).setPriority(NotificationCompat.PRIORITY_LOW).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_MONITOR_ID, n, 0x40000000) // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else startForeground(NOTIF_MONITOR_ID, n)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        if (job == null) job = scope.launch { monitorLoop() }
        return START_STICKY
    }

    private suspend fun monitorLoop() = coroutineScope {
        val api = ApiHolder.get(applicationContext)
        while (isActive) {
            var wait = 12_000L
            try {
                if (!api.ensureSession()) { stopSelf(); return@coroutineScope }
                val list = api.get("orders?select=*&order=created_at.desc&limit=20").objects().map { it.toOrder() }
                val last = api.store.getLong("last_order_ms", 0L)
                val newest = list.maxOfOrNull { it.createdMillis } ?: 0L
                if (last == 0L) {
                    api.store.edit().putLong("last_order_ms", if (newest > 0) newest else System.currentTimeMillis()).apply()
                } else {
                    list.filter { it.createdMillis > last }.sortedBy { it.createdMillis }.forEach { notifyNewOrder(applicationContext, it) }
                    if (newest > last) api.store.edit().putLong("last_order_ms", newest).apply()
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { wait = 25_000L }
            delay(wait)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/* ============================================================
   VIEWMODEL (state bertahan saat layar diputar)
   ============================================================ */
class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Context = app.applicationContext
    val api = ApiHolder.get(app)
    val httpClient: OkHttpClient get() = api.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollJob: Job? = null
    private var toastJob: Job? = null
    private var initialized = false

    var isAuthenticated by mutableStateOf(false)
    var loginLoading by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var tab by mutableIntStateOf(0)
    var toastMsg by mutableStateOf<String?>(null)
    var sheet by mutableStateOf<SheetType?>(null)
    var confirm by mutableStateOf<ConfirmReq?>(null)
    var bioEnabled by mutableStateOf(api.isBioEnabled())
    var monitorEnabled by mutableStateOf(api.store.getBoolean("monitor_enabled", true))
    var refreshing by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var uploadTarget by mutableStateOf<String?>(null)

    var orders by mutableStateOf<List<NativeOrder>>(emptyList())
    var ordersLoaded by mutableStateOf(false)
    var ordersError by mutableStateOf<String?>(null)
    var products by mutableStateOf<List<NativeProduct>>(emptyList())
    var productsLoaded by mutableStateOf(false)
    var categories by mutableStateOf<List<NativeCategory>>(emptyList())
    var expenses by mutableStateOf<List<NativeExpense>>(emptyList())
    var expensesLoaded by mutableStateOf(false)
    var popups by mutableStateOf<List<NativePopup>>(emptyList())
    var popupsLoaded by mutableStateOf(false)
    var promos by mutableStateOf<List<NativePromo>>(emptyList())

    var logoUrl by mutableStateOf("")
    var banner1 by mutableStateOf("")
    var banner2 by mutableStateOf("")
    var banner3 by mutableStateOf("")

    var omzetPeriod by mutableStateOf(Period.TODAY)
    var finPeriod by mutableStateOf(Period.TODAY)
    var chartWeekly by mutableStateOf(false)

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private suspend fun <R> io(block: () -> R): R = withContext(Dispatchers.IO) { block() }
    private fun enc(s: String) = Uri.encode(s)

    fun toast(msg: String) {
        toastMsg = msg
        toastJob?.cancel()
        toastJob = scope.launch { delay(2400); toastMsg = null }
    }

    /* ---------- Notifikasi & service ---------- */
    fun syncMonitor() {
        val intent = Intent(appCtx, OrderMonitorService::class.java)
        if (monitorEnabled && isAuthenticated) {
            try { ContextCompat.startForegroundService(appCtx, intent) } catch (e: Exception) { toast("Gagal menyalakan pemantau: ${e.message}") }
        } else {
            appCtx.stopService(intent)
        }
    }

    fun setMonitor(enabled: Boolean) {
        monitorEnabled = enabled
        api.store.edit().putBoolean("monitor_enabled", enabled).apply()
        syncMonitor()
        toast(if (enabled) "Notifikasi latar belakang aktif" else "Notifikasi latar belakang dimatikan")
    }

    fun testNotification() {
        notifyNewOrder(appCtx, NativeOrder("test", "TEST-001", "Netflix Premium", "1 Bulan", 25000L, "628123456789", "Pending", 0L, 0L, ""))
        toast("Notifikasi tes dikirim")
    }

    /* ---------- Auth ---------- */
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) { loginError = "Email dan password wajib diisi."; return }
        loginLoading = true; loginError = null
        scope.launch {
            try {
                io { api.signIn(email.trim(), password) }
                isAuthenticated = true
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { loginError = e.message ?: "Login gagal" }
            loginLoading = false
        }
    }

    fun biometricLogin() {
        scope.launch {
            val ok = io { api.refreshSession() }
            if (ok) isAuthenticated = true else toast("Sesi habis, masuk dengan password.")
        }
    }

    fun enableBiometric() {
        if (api.setBiometric(true)) { bioEnabled = true; toast("Kunci sidik jari diaktifkan") }
        else toast("Gagal, silakan login ulang dengan password")
    }

    fun disableBiometric() {
        api.setBiometric(false); bioEnabled = false; toast("Kunci sidik jari dimatikan")
    }

    fun logout() {
        pollJob?.cancel(); initialized = false
        appCtx.stopService(Intent(appCtx, OrderMonitorService::class.java))
        api.logout()
        isAuthenticated = false
        orders = emptyList(); products = emptyList(); categories = emptyList(); expenses = emptyList()
        popups = emptyList(); promos = emptyList()
        ordersLoaded = false; productsLoaded = false; expensesLoaded = false; popupsLoaded = false
        sheet = null; tab = 0
        toast("Berhasil keluar")
    }

    /* ---------- Loader ---------- */
    fun initData() {
        if (initialized) return
        initialized = true
        loadOrders(); loadProducts(); loadCategories(); loadExpenses(); loadPopups(); loadPromos(); loadContent()
        pollJob = scope.launch {
            while (true) { delay(15_000); loadOrders(silent = true) }
        }
    }

    fun refreshAll() {
        scope.launch {
            refreshing = true
            toast("Menyinkronkan data Supabase...")
            loadOrders().join(); loadProducts().join(); loadExpenses().join()
            refreshing = false
            toast("Data terbaru dimuat")
        }
    }

    fun loadOrders(silent: Boolean = false): Job = scope.launch {
        try {
            val list = io { api.get("orders?select=*&order=created_at.desc&limit=120").objects().map { it.toOrder() } }
            val oldIds = orders.map { it.id }.toSet()
            if (ordersLoaded && list.any { it.id !in oldIds }) toast("Pesanan Baru Masuk! ⚡")
            orders = list; ordersLoaded = true; ordersError = null
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { if (!silent) { ordersError = e.message ?: "Gagal memuat pesanan."; ordersLoaded = true } }
    }

    fun loadProducts(): Job = scope.launch {
        try {
            products = io { api.get("products?select=*&order=name.asc").objects().map { it.toProduct() } }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { toast("Gagal memuat produk: ${e.message}") }
        productsLoaded = true
    }

    fun loadCategories(): Job = scope.launch {
        try {
            categories = io { api.get("categories?select=*&order=id.asc").objects().map { NativeCategory(it.str("id"), it.str("name")) } }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { }
    }

    fun loadExpenses(): Job = scope.launch {
        try {
            expenses = io {
                api.get("expenses?select=*&order=expense_date.desc&limit=60").objects().map {
                    NativeExpense(it.str("id"), it.str("description"), it.str("category"), it.optSafeLong("amount"), parseIsoMillis(it.str("expense_date")))
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { toast("Gagal memuat pengeluaran") }
        expensesLoaded = true
    }

    fun loadPopups(): Job = scope.launch {
        try {
            popups = io {
                api.get("site_popups?select=*&order=created_at.desc").objects().map {
                    NativePopup(it.str("id"), it.str("title", "Tanpa Judul"), it.str("message"), it.optBoolean("is_active", false))
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { }
        popupsLoaded = true
    }

    fun loadPromos(): Job = scope.launch {
        try {
            promos = io {
                api.get("promo_codes?select=*&order=created_at.desc").objects().map {
                    NativePromo(it.str("id"), it.str("code"), it.str("discount_type", "percentage"),
                        it.optDouble("discount_value", 0.0), it.optSafeLong("min_order_amount"))
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { }
    }

    fun loadContent(): Job = scope.launch {
        try {
            val rows = io { api.get("site_settings?select=*").objects() }
            rows.forEach {
                when (it.str("key")) {
                    "logo_url" -> logoUrl = it.str("value")
                    "carousel_banner_1" -> banner1 = it.str("value")
                    "carousel_banner_2" -> banner2 = it.str("value")
                    "carousel_banner_3" -> banner3 = it.str("value")
                }
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { }
    }

    /* ---------- Aksi tulis (aman: error ditangkap, tidak crash) ---------- */
    private fun act(okMsg: String? = null, onDone: () -> Unit = {}, block: () -> Unit) {
        scope.launch {
            try {
                io(block)
                okMsg?.let { toast(it) }
                onDone()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { toast("Gagal: ${e.message}") }
        }
    }

    fun markOrderPaid(id: String) {
        val paidAt = isoNow()
        act("Pesanan berhasil ditandai Lunas", onDone = {
            orders = orders.map { if (it.id == id) it.copy(status = "Paid", paidMillis = System.currentTimeMillis()) else it }
            sheet = null
        }) { api.patch("orders?id=eq.${enc(id)}", JSONObject().put("status", "Paid").put("paid_at", paidAt)) }
    }

    fun toggleStock(id: String, ready: Boolean) {
        val newStock = if (ready) 50 else 0
        act(if (ready) "Stok diubah menjadi READY" else "Stok ditandai HABIS", onDone = {
            products = products.map { if (it.id == id) it.copy(stock = newStock) else it }
        }) { api.patch("products?id=eq.${enc(id)}", JSONObject().put("stock", newStock)) }
    }

    suspend fun fetchProductExtras(id: String): Pair<List<PkgRow>, List<InpRow>> = try {
        io {
            val pk = api.get("product_packages?select=*&product_id=eq.${enc(id)}").objects()
                .map { PkgRow(it.str("name"), if (it.isNull("price")) "" else it.optSafeLong("price").toString(), it.str("duration", "1 BULAN")) }
            val ip = api.get("product_inputs?select=*&product_id=eq.${enc(id)}").objects()
                .map { InpRow(it.str("label"), it.str("type", "text")) }
            pk to ip
        }
    } catch (e: CancellationException) { throw e
    } catch (e: Exception) { toast("Gagal memuat paket produk"); emptyList<PkgRow>() to emptyList<InpRow>() }

    fun saveProduct(editId: String?, payload: JSONObject, pkgs: List<Triple<String, Int, String>>, inps: List<Pair<String, String>>) {
        saving = true
        scope.launch {
            try {
                io {
                    var prodId = editId ?: ""
                    if (!editId.isNullOrEmpty()) {
                        api.patch("products?id=eq.${enc(editId)}", payload)
                        api.delete("product_packages?product_id=eq.${enc(editId)}")
                        api.delete("product_inputs?product_id=eq.${enc(editId)}")
                    } else {
                        val res = api.post("products", payload, "return=representation")
                        prodId = JSONArray(res).getJSONObject(0).str("id")
                    }
                    if (pkgs.isNotEmpty()) {
                        val arr = JSONArray()
                        pkgs.forEach { (n, p, d) ->
                            arr.put(JSONObject().put("product_id", prodId).put("name", n).put("price", p).put("duration", d.ifBlank { "1 BULAN" }))
                        }
                        api.post("product_packages", arr)
                    }
                    if (inps.isNotEmpty()) {
                        val arr = JSONArray()
                        inps.forEach { (l, t) ->
                            arr.put(JSONObject().put("product_id", prodId).put("label", l).put("type", t).put("name", t)
                                .put("placeholder", "Masukkan $l").put("is_required", true))
                        }
                        api.post("product_inputs", arr)
                    }
                }
                toast(if (editId == null) "Produk berhasil ditambahkan" else "Produk berhasil diperbarui")
                sheet = null; loadProducts()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                val m = e.message.orEmpty()
                toast(if (m.contains("duplicate", true) || m.contains("unique", true)) "Slug sudah dipakai produk lain, ganti slug." else "Gagal menyimpan: $m")
            }
            saving = false
        }
    }

    fun deleteProduct(id: String) {
        act("Produk dihapus", onDone = { sheet = null; loadProducts() }) {
            api.delete("product_packages?product_id=eq.${enc(id)}")
            api.delete("product_inputs?product_id=eq.${enc(id)}")
            api.delete("products?id=eq.${enc(id)}")
        }
    }

    /** Baca foto dari galeri, perbaiki rotasi, kompres bila besar, lalu upload ke Supabase Storage. */
    fun uploadImage(uri: Uri, folder: String, target: String, onUrl: (String) -> Unit) {
        scope.launch {
            uploadTarget = target
            try {
                val url = io {
                    val mime = appCtx.contentResolver.getType(uri) ?: "image/jpeg"
                    val raw = appCtx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("File tidak terbaca")
                    val orient = readOrientation(raw)
                    val isPng = mime.contains("png")
                    val common = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
                    var bytes = raw; var outMime = mime
                    if (raw.size > 900_000 || (orient != 0 && !isPng) || mime !in common) {
                        val maxDim = when (folder) { "banner" -> 1600; "logo" -> 800; else -> 1000 }
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
                        var sample = 1
                        while (o.outWidth / (sample * 2) >= maxDim || o.outHeight / (sample * 2) >= maxDim) sample *= 2
                        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
                        if (decoded != null) {
                            var bmp: Bitmap = decoded
                            val longest = maxOf(bmp.width, bmp.height)
                            if (longest > maxDim) {
                                val sc = maxDim.toFloat() / longest
                                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt().coerceAtLeast(1), (bmp.height * sc).toInt().coerceAtLeast(1), true)
                            }
                            if (orient != 0 && !isPng) {
                                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(orient.toFloat()) }, true)
                            }
                            val out = java.io.ByteArrayOutputStream()
                            bmp.compress(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, out)
                            bytes = out.toByteArray(); outMime = if (isPng) "image/png" else "image/jpeg"
                        }
                    }
                    val ext = when { outMime.contains("png") -> "png"; outMime.contains("webp") -> "webp"; outMime.contains("gif") -> "gif"; else -> "jpg" }
                    api.uploadImage(bytes, outMime, ext, folder)
                }
                onUrl(url)
                toast("Foto berhasil diunggah!")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { toast("Gagal unggah foto: ${e.message}") }
            uploadTarget = null
        }
    }

    fun addExpense(desc: String, cat: String, amount: Double, onOk: () -> Unit) {
        act("Pengeluaran berhasil dicatat", onDone = { onOk(); loadExpenses() }) {
            api.post("expenses", JSONObject().put("description", desc).put("category", cat)
                .put("amount", if (amount % 1.0 == 0.0) amount.toLong() else amount))
        }
    }

    fun deleteExpense(id: String) = act("Pengeluaran dihapus", onDone = { loadExpenses() }) { api.delete("expenses?id=eq.${enc(id)}") }

    fun saveLogo() = act("Logo berhasil disimpan") {
        api.post("site_settings?on_conflict=key", JSONObject().put("key", "logo_url").put("value", logoUrl.trim()), "resolution=merge-duplicates")
    }

    fun setBanner(n: Int, v: String) { when (n) { 1 -> banner1 = v; 2 -> banner2 = v; else -> banner3 = v } }

    fun saveBannerSlot(n: Int, v: String) = act("Banner $n diperbarui") {
        api.post("site_settings?on_conflict=key", JSONObject().put("key", "carousel_banner_$n").put("value", v.trim()), "resolution=merge-duplicates")
    }

    fun saveBanners() {
        val arr = JSONArray()
        listOf(1 to banner1, 2 to banner2, 3 to banner3).forEach { (n, v) ->
            if (v.trim().isNotEmpty()) arr.put(JSONObject().put("key", "carousel_banner_$n").put("value", v.trim()))
        }
        if (arr.length() == 0) return
        act("Banner carousel diperbarui") { api.post("site_settings?on_conflict=key", arr, "resolution=merge-duplicates") }
    }

    fun togglePopup(id: String, active: Boolean) {
        popups = popups.map { if (it.id == id) it.copy(isActive = active) else it }
        act(if (active) "Popup diaktifkan" else "Popup dimatikan") { api.patch("site_popups?id=eq.${enc(id)}", JSONObject().put("is_active", active)) }
    }

    fun createPopup(title: String, message: String) {
        act("Popup baru dibuat", onDone = { loadPopups() }) {
            api.post("site_popups", JSONObject().put("title", title).put("message", message)
                .put("is_active", true).put("position", "center").put("show_on_page", "all"))
        }
    }

    fun addCategory(name: String, onOk: () -> Unit) =
        act("Kategori ditambahkan", onDone = { onOk(); loadCategories() }) { api.post("categories", JSONObject().put("name", name)) }

    fun deleteCategory(id: String) = act("Kategori dihapus", onDone = { loadCategories() }) { api.delete("categories?id=eq.${enc(id)}") }

    fun savePromo(code: String, type: String, value: Double, min: Double, max: Double?, onOk: () -> Unit) {
        act("Kode promo berhasil dibuat", onDone = { onOk(); loadPromos() }) {
            val o = JSONObject().put("code", code).put("discount_type", type).put("discount_value", value)
                .put("min_order_amount", min).put("is_active", true)
            o.put("max_discount_amount", max ?: JSONObject.NULL)
            api.post("promo_codes", o)
        }
    }

    fun deletePromo(id: String) = act("Promo dihapus", onDone = { loadPromos() }) { api.delete("promo_codes?id=eq.${enc(id)}") }

    /* ---------- Perhitungan keuangan (sama dengan versi web) ---------- */
    fun income(p: Period): Long { val s = periodStart(p); return orders.filter { it.isPaid && it.eventMillis >= s }.sumOf { it.total } }
    fun expense(p: Period): Long { val s = periodStart(p); return expenses.filter { it.dateMillis >= s }.sumOf { it.amount } }
}

/* ============================================================
   ACTIVITY
   ============================================================ */
class MainActivity : FragmentActivity() {

    private val vm: AdminViewModel by viewModels()
    private var pickCallback: ((Uri) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        window.decorView.setBackgroundColor(0xFF0A0A0A.toInt())
        WindowCompat.getInsetsController(window, window.decorView).let {
            it.isAppearanceLightStatusBars = false
            it.isAppearanceLightNavigationBars = false
        }

        ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)

        setContent {
            MaterialTheme(colorScheme = GivaColors) {
                Box(Modifier.fillMaxSize().background(BgBase)) {
                    if (!vm.isAuthenticated) {
                        LoginScreen(
                            vm = vm,
                            canBio = biometricAvailable() && vm.api.hasSavedSession(),
                            onBio = { promptBiometric("Masuk dengan Sidik Jari") { vm.biometricLogin() } }
                        )
                    } else {
                        Dashboard(
                            vm = vm,
                            bioSupported = biometricAvailable(),
                            onPickImage = { cb -> pickImage(cb) },
                            onWa = { phone, text -> openWhatsApp(phone, text) },
                            onShareCsv = { shareCsv(it) },
                            onOpenStore = { openStore() },
                            onToggleBio = { enable ->
                                if (enable) promptBiometric("Aktifkan Kunci Sidik Jari") { vm.enableBiometric() }
                                else vm.disableBiometric()
                            },
                            onNotif = { testOrOpenNotifications() },
                            onBgSettings = { openBackgroundSettings() }
                        )
                    }
                    ToastPill(vm)
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Tombol lonceng: minta izin, buka pengaturan notifikasi bila mati, atau kirim notifikasi tes. */
    private fun testOrOpenNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF); return }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            toast("Notifikasi dimatikan. Aktifkan di pengaturan.")
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (e: Exception) { }
            return
        }
        vm.testNotification()
    }

    /** Langkah 1: izinkan abaikan hemat baterai. Langkah 2: Mulai otomatis (Xiaomi). Cadangan: info aplikasi. */
    private fun openBackgroundSettings() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                return
            }
        } catch (e: Exception) { }
        try {
            startActivity(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
            return
        } catch (e: Exception) { }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: Exception) { toast("Buka Pengaturan > Aplikasi > Giva Admin") }
    }

    private fun biometricAvailable() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric(title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED)
                        toast("Biometrik: $errString")
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Sentuh sensor sidik jari ponsel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Gunakan Password")
            .build()
        prompt.authenticate(info)
    }

    private fun pickImage(cb: (Uri) -> Unit) {
        pickCallback = cb
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(i, "Pilih Foto"), REQ_PICK_IMAGE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK) data?.data?.let { pickCallback?.invoke(it) }
    }

    private fun openWhatsApp(phone: String, text: String) {
        val clean = phone.replace(Regex("[^0-9]"), "").replace(Regex("^0"), "62")
        val uri = Uri.parse("https://wa.me/$clean?text=${Uri.encode(text)}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") })
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            catch (err: Exception) { toast("Aplikasi WhatsApp tidak terpasang.") }
        }
    }

    private fun openStore() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STORE_URL))) } catch (e: Exception) { toast("Tidak bisa membuka toko") }
    }

    private fun shareCsv(content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "orders-givastore.csv")
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(intent, "Export CSV"))
        } catch (e: Exception) { toast("Gagal membagikan CSV.") }
    }
}

/* ============================================================
   FIREBASE SERVICE (cadangan bila nanti memakai push FCM)
   ============================================================ */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        try {
            val title = remoteMessage.notification?.title ?: "Pesanan Baru Masuk! ⚡"
            val body = remoteMessage.notification?.body ?: "Buka GivaStore Admin untuk memproses orderan."
            ensureChannels(this)
            val notification = NotificationCompat.Builder(this, CH_ORDERS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX).setAutoCancel(true).build()
            getSystemService(NotificationManager::class.java)?.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) { e.printStackTrace() }
    }
}

/* ============================================================
   KOMPONEN UI DASAR
   ============================================================ */
@Composable
fun T(
    text: String, size: Float = 12.6f, color: Color = Ink, weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier, mono: Boolean = false, display: Boolean = false, upper: Boolean = false,
    spacing: Float = 0f, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null
) {
    Text(
        text = if (upper) text.uppercase() else text, modifier = modifier, color = color,
        fontSize = size.sp, fontWeight = if (display) FontWeight.Black else weight,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        letterSpacing = spacing.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align
    )
}

@Composable
fun GsCard(
    modifier: Modifier = Modifier, hero: Boolean = false, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    var m = modifier.clip(shape).background(if (hero) Color.Black else BgSurface).border(1.dp, if (hero) LineStrong else LineC, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), content = content)
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(shape).background(BgCard).border(1.dp, LineC, shape).padding(14.dp)) {
        T(title, 9.8f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.8f, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
fun MetricLabel(text: String, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        T(text, 9.5f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.8f, modifier = Modifier.weight(1f))
        if (icon != null) Icon(icon, null, tint = InkFaint, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun SectionTitle(title: String, small: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        T(title, 16f, display = true, upper = true)
        if (small != null) T(small, 10.1f, InkDim, FontWeight.SemiBold)
    }
}

@Composable
fun SegPills(selected: Int, labels: List<String>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(BgSurface).border(1.dp, LineC, CircleShape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(CircleShape).background(if (on) Ink else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) { T(l, 9.1f, if (on) BgBase else InkDim, FontWeight.Bold, upper = true, spacing = 0.5f) }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (selected) Ink else FieldBg)
            .border(1.dp, if (selected) Ink else LineC, CircleShape).clickable(onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 5.dp else 7.dp)
    ) { T(label, if (small) 9.5f else 10.4f, if (selected) BgBase else InkDim, FontWeight.Bold, maxLines = 1) }
}

enum class BtnKind { MAIN, SECONDARY, WA }

@Composable
fun BtnMain(
    text: String, icon: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    kind: BtnKind = BtnKind.MAIN, enabled: Boolean = true, loading: Boolean = false
) {
    val bg = when (kind) { BtnKind.MAIN -> Ink; BtnKind.SECONDARY -> Color.Transparent; BtnKind.WA -> WaGreen }
    val fg = when (kind) { BtnKind.MAIN -> BgBase; BtnKind.SECONDARY -> Ink; BtnKind.WA -> WaInk }
    val bd = when (kind) { BtnKind.MAIN -> Ink; BtnKind.SECONDARY -> LineStrong; BtnKind.WA -> WaGreen }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.alpha(if (enabled) 1f else 0.5f).clip(shape).background(bg).border(1.dp, bd, shape)
            .clickable(enabled = enabled && !loading, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) { CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        else if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); if (text.isNotEmpty()) Spacer(Modifier.width(8.dp)) }
        T(text, 12.3f, fg, FontWeight.ExtraBold, upper = true, spacing = 0.4f, maxLines = 1)
    }
}

@Composable
fun BtnAction(text: String, icon: ImageVector? = null, onClick: () -> Unit, modifier: Modifier = Modifier, wa: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.clip(shape).background(if (wa) WaGreen else BgCard).border(1.dp, if (wa) WaGreen else LineC, shape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = if (wa) WaInk else Ink, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        T(text, 10.4f, if (wa) WaInk else Ink, FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun IconSquare(icon: ImageVector, onClick: () -> Unit, tint: Color = Ink, size: Dp = 34.dp) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.size(size).clip(shape).background(BgCard).border(1.dp, LineC, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)) }
}

@Composable
fun GsField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false, singleLine: Boolean = true,
    minHeight: Dp = 0.dp, mono: Boolean = false, fontSize: TextUnit = 12.6.sp, upper: Boolean = false, error: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value, onValueChange = { onChange(if (upper) it.uppercase() else it) },
        singleLine = singleLine,
        textStyle = TextStyle(color = Ink, fontSize = fontSize, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default),
        cursorBrush = SolidColor(Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(FieldBg)
                    .border(1.dp, if (error) RedC else if (focused) Ink else LineC, shape).padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty()) Text(placeholder, color = InkFaint, fontSize = fontSize, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
                inner()
            }
        }
    )
}

@Composable
fun FieldHint(text: String, error: Boolean = false) {
    T(text, 10f, if (error) RedC else InkDim, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun FormGroup(label: String? = null, modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(bottom = 12.dp)) {
        if (label != null) T(label, 10.1f, InkDim, FontWeight.Bold, upper = true, spacing = 0.4f, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
fun GsDropdown(selectedLabel: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(FieldBg).border(1.dp, LineC, shape).clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            T(selectedLabel, 12.6f, Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, null, tint = InkDim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(BgCard)) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label, color = Ink) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

@Composable
fun GsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = BgBase, checkedTrackColor = Ink, checkedBorderColor = Ink,
            uncheckedThumbColor = InkDim, uncheckedTrackColor = BgCard, uncheckedBorderColor = LineStrong
        )
    )
}

@Composable
fun StatusBadge(label: String, color: Color, bg: Color) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).background(bg).border(1.dp, color.copy(alpha = 0.3f), shape).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        T(label, 9.1f, color, FontWeight.ExtraBold, upper = true, spacing = 0.5f)
    }
}

@Composable
fun LoadingBox(text: String, icon: ImageVector? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) { Icon(icon, null, tint = InkDim, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)) }
        T(text, 11.9f, InkDim, align = TextAlign.Center)
    }
}

@Composable
fun SpinnerBox(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), color = Ink, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        T(text, 11.9f, InkDim)
    }
}

@Composable
fun Divider1(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LineC))
}

@Composable
fun ToastPill(vm: AdminViewModel) {
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(vm.toastMsg) { vm.toastMsg?.let { shown = it } }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = vm.toastMsg != null,
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp).clip(CircleShape).background(Ink).padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, null, tint = BgBase, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                T(shown, 11f, BgBase, FontWeight.Bold, maxLines = 3)
            }
        }
    }
}

/* Gambar dari URL (tanpa library tambahan) */
object ImageCache {
    val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
}

fun fetchBitmap(client: OkHttpClient, url: String): Bitmap? {
    val bytes = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return null
        r.body?.bytes() ?: return null
    }
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var sample = 1
    while (o.outWidth / sample > 1000 || o.outHeight / sample > 1000) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
fun RemoteImage(
    url: String, client: OkHttpClient, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable () -> Unit = {}
) {
    var bmp by remember(url) { mutableStateOf<Bitmap?>(ImageCache.cache.get(url)) }
    LaunchedEffect(url) {
        if (url.isBlank() || bmp != null) return@LaunchedEffect
        val res = withContext(Dispatchers.IO) { try { fetchBitmap(client, url) } catch (e: Exception) { null } }
        if (res != null) { ImageCache.cache.put(url, res); bmp = res }
    }
    val b = bmp
    if (b != null) Image(bitmap = b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    else Box(modifier, contentAlignment = Alignment.Center) { placeholder() }
}

enum class PhotoMode { LOGO, BANNER, PRODUCT }

@Composable
fun PhotoPreview(url: String, client: OkHttpClient, mode: PhotoMode, emptyText: String, busy: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier.clip(shape).background(Color.Black).border(1.dp, LineC, shape)
    val m = when (mode) {
        PhotoMode.LOGO -> base.fillMaxWidth().height(74.dp)
        PhotoMode.BANNER -> base.fillMaxWidth().aspectRatio(16f / 7f)
        PhotoMode.PRODUCT -> base.size(96.dp)
    }
    val pad = if (mode == PhotoMode.BANNER) 0.dp else 8.dp
    Box(if (mode == PhotoMode.PRODUCT) Modifier.fillMaxWidth() else Modifier, contentAlignment = Alignment.Center) {
        Box(m, contentAlignment = Alignment.Center) {
            RemoteImage(
                url = resolveImg(url), client = client, modifier = Modifier.fillMaxSize().padding(pad),
                contentScale = if (mode == PhotoMode.BANNER) ContentScale.Crop else ContentScale.Fit,
                placeholder = { T(emptyText, 10.4f, InkFaint) }
            )
            if (busy) Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Ink, strokeWidth = 2.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsSheet(
    onDismiss: () -> Unit, title: String, subtitle: String,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        containerColor = BgSurface, contentColor = Ink,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        scrimColor = Color(0xC7000000),
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).size(36.dp, 4.dp).background(LineStrong, CircleShape)) }
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                T(title, 16.8f, display = true, upper = true, modifier = Modifier.padding(bottom = 4.dp))
                T(subtitle, 10.9f, InkDim, modifier = Modifier.padding(bottom = 18.dp))
                content()
                Spacer(Modifier.height(12.dp))
            }
            if (footer != null) {
                Divider1()
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), content = footer)
            }
        }
    }
}

/* ============================================================
   LOGIN
   ============================================================ */
@Composable
fun LoginScreen(vm: AdminViewModel, canBio: Boolean, onBio: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 24.dp).widthIn(max = 380.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(20.dp)).background(BgSurface).border(1.dp, LineStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Ink), contentAlignment = Alignment.Center) {
                T("G", 23.8f, BgBase, display = true)
            }
            Spacer(Modifier.height(12.dp))
            T("Giva Admin", 19f, display = true, upper = true)
            T("Monochrome Mobile Control Center", 10.9f, InkDim)
            Spacer(Modifier.height(26.dp))

            if (canBio) {
                BtnMain("Masuk dengan Sidik Jari", Icons.Default.Fingerprint, onClick = onBio, kind = BtnKind.SECONDARY)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(LineC))
                    T("Atau Dengan Password", 9.5f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.5f, modifier = Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(LineC))
                }
            }

            FormGroup("Email Admin") { GsField(email, { email = it }, placeholder = "admin@email.com", keyboardType = KeyboardType.Email, fontSize = 14.sp) }
            FormGroup("Password") { GsField(password, { password = it }, placeholder = "••••••••", password = true, keyboardType = KeyboardType.Password, fontSize = 14.sp) }
            BtnMain(
                if (vm.loginLoading) "Memeriksa Akun..." else "Masuk Sekarang", Icons.Default.Lock,
                onClick = { vm.login(email, password) }, enabled = !vm.loginLoading, loading = vm.loginLoading
            )
            vm.loginError?.let {
                Spacer(Modifier.height(12.dp))
                T(it, 11.2f, RedC, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/* ============================================================
   DASHBOARD
   ============================================================ */
@Composable
fun Dashboard(
    vm: AdminViewModel, bioSupported: Boolean,
    onPickImage: ((Uri) -> Unit) -> Unit, onWa: (String, String) -> Unit, onShareCsv: (String) -> Unit,
    onOpenStore: () -> Unit, onToggleBio: (Boolean) -> Unit, onNotif: () -> Unit, onBgSettings: () -> Unit
) {
    LaunchedEffect(Unit) { vm.initData() }
    LaunchedEffect(vm.monitorEnabled) { vm.syncMonitor() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val titles = listOf("Pesanan", "Keuangan", "Produk", "Konten", "Menu")
    val pending = vm.orders.count { it.status.equals("pending", true) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().imePadding()) {
            AppHeader(titles[vm.tab], vm.refreshing, onNotif) { vm.refreshAll() }
            Box(Modifier.weight(1f)) {
                when (vm.tab) {
                    0 -> OrdersTab(vm, onWa, onShareCsv)
                    1 -> FinanceTab(vm)
                    2 -> ProductsTab(vm)
                    3 -> ContentTab(vm, onPickImage)
                    else -> MoreTab(vm, bioSupported, onToggleBio, onOpenStore, onNotif, onBgSettings)
                }
                if (vm.tab == 2) {
                    Row(
                        Modifier.align(Alignment.BottomEnd).padding(16.dp).clip(CircleShape).background(Ink)
                            .clickable { vm.sheet = SheetType.ProductEdit(null) }.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, "Tambah Produk", tint = BgBase, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        T("Produk", 11.5f, BgBase, FontWeight.ExtraBold, upper = true, spacing = 0.5f)
                    }
                }
            }
            if (!imeOpen) BottomNav(vm.tab, pending) { vm.tab = it }
        }
    }

    when (val s = vm.sheet) {
        is SheetType.OrderDetail -> {
            val o = vm.orders.firstOrNull { it.id == s.id }
            if (o != null) OrderDetailSheet(vm, o, onWa)
        }
        is SheetType.ProductEdit -> ProductEditSheet(vm, s.id, onPickImage)
        SheetType.Category -> CategorySheet(vm)
        SheetType.Promo -> PromoSheet(vm)
        SheetType.Customer -> CustomerSheet(vm)
        SheetType.NewPopup -> NewPopupDialog(vm)
        null -> {}
    }

    vm.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.confirm = null }, containerColor = BgSurface,
            title = { T("Konfirmasi", 15f, Ink, FontWeight.Bold) },
            text = { T(c.message, 12.6f, InkDim) },
            confirmButton = {
                Button(onClick = { c.action(); vm.confirm = null }, colors = ButtonDefaults.buttonColors(containerColor = RedC)) {
                    Text("Ya, Lanjutkan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { vm.confirm = null }) { Text("Batal", color = InkDim) } }
        )
    }
}

@Composable
fun AppHeader(title: String, refreshing: Boolean, onNotif: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xEB0A0A0A)).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Ink), contentAlignment = Alignment.Center) {
                    T("G", 16f, BgBase, display = true)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    T(title, 13.3f, display = true, upper = true, maxLines = 1)
                    T("GivaStore 2026", 9.5f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.7f)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(BgCard).border(1.dp, LineC, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(WaGreen, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    T("LIVE", 9.5f, InkDim, FontWeight.Bold)
                }
                IconSquare(Icons.Default.Notifications, onNotif)
                if (refreshing) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(16.dp), color = Ink, strokeWidth = 2.dp) }
                } else IconSquare(Icons.Default.Refresh, onRefresh)
            }
        }
        Divider1()
    }
}

@Composable
fun BottomNav(selected: Int, pending: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Icons.Default.Receipt to "Pesanan", Icons.Default.AccountBalanceWallet to "Keuangan", Icons.Default.Inventory2 to "Produk",
        Icons.Default.Tune to "Konten", Icons.Default.MoreHoriz to "Menu"
    )
    Column(Modifier.fillMaxWidth().background(Color(0xF00E0E10))) {
        Divider1()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEachIndexed { i, (icon, label) ->
                val on = selected == i
                Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = if (on) Ink else InkFaint, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        T(label, 9.1f, if (on) Ink else InkFaint, FontWeight.Bold, upper = true, spacing = 0.5f, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(16.dp).height(2.dp).background(if (on) Ink else Color.Transparent, CircleShape))
                    }
                    if (i == 0 && pending > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).defaultMinSize(15.dp, 15.dp)
                                .background(RedC, CircleShape).padding(horizontal = 3.dp),
                            contentAlignment = Alignment.Center
                        ) { T("$pending", 8.1f, Color.White, FontWeight.ExtraBold) }
                    }
                }
            }
        }
    }
}

/* ============================================================
   TAB 1: PESANAN
   ============================================================ */
@Composable
fun OrdersTab(vm: AdminViewModel, onWa: (String, String) -> Unit, onShareCsv: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val periods = Period.values()
    val pendingCount = vm.orders.count { it.status.equals("pending", true) }
    val paidCount = vm.orders.count { it.isPaid }
    val expiredCount = vm.orders.count { it.status.equals("expired", true) || it.status.equals("cancel", true) }
    val todayStart = periodStart(Period.TODAY)
    val paidToday = vm.orders.count { it.isPaid && it.eventMillis >= todayStart }
    val filtered = vm.orders.filter { o ->
        val s = o.status.lowercase()
        val q = query.lowercase().trim()
        (status.isEmpty() || s == status || (status == "expired" && s == "cancel")) &&
            (q.isEmpty() || o.orderId.lowercase().contains(q) || o.productName.lowercase().contains(q) || o.whatsapp.lowercase().contains(q))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)) {
        item {
            GsCard(Modifier.fillMaxWidth().padding(bottom = 14.dp), hero = true) {
                SegPills(vm.omzetPeriod.ordinal, periods.map { it.pill }) { vm.omzetPeriod = periods[it] }
                Spacer(Modifier.height(12.dp))
                MetricLabel("Total Omzet ${vm.omzetPeriod.full}", Icons.Default.TrendingUp)
                T(fmt(vm.income(vm.omzetPeriod)), 27f, display = true, spacing = -0.5f, modifier = Modifier.padding(vertical = 4.dp))
                Spacer(Modifier.height(6.dp))
                Divider1()
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Period.WEEK to "Minggu Ini", Period.MONTH to "Bulan Ini", Period.YEAR to "Tahun Ini").forEach { (p, name) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x08FFFFFF))
                                .clickable { vm.omzetPeriod = p }.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            T(name, 8.4f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.5f, maxLines = 1)
                            T(fmtShort(vm.income(p)), 10.9f, Ink, FontWeight.Bold, mono = true, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GsCard(Modifier.weight(1f)) { MetricLabel("Pesanan Pending"); T("$pendingCount", 19.6f, display = true) }
                GsCard(Modifier.weight(1f)) { MetricLabel("Paid Hari Ini"); T("$paidToday", 19.6f, display = true) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                GsField(query, { query = it }, placeholder = "Cari ID pesanan, nama, no. WA...", fontSize = 12.6.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "" to "Semua (${vm.orders.size})", "pending" to "Pending ($pendingCount)",
                        "paid" to "Lunas ($paidCount)", "expired" to "Expired ($expiredCount)"
                    ).forEach { (k, l) -> Chip(l, status == k) { status = k } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    T("${filtered.size} Pesanan Ditampilkan", 10.5f, InkDim, FontWeight.Bold)
                    BtnAction("Export CSV", Icons.Default.Description, onClick = {
                        if (vm.orders.isEmpty()) vm.toast("Tidak ada data pesanan")
                        else {
                            fun cell(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                            val csv = buildString {
                                appendLine("Order ID,Produk,Paket,Total Bayar,No. WhatsApp,Status,Tanggal")
                                vm.orders.forEach { o ->
                                    appendLine(listOf(cell(o.orderId), cell(o.productName), cell(o.packageName), o.total.toString(),
                                        cell(o.whatsapp), cell(o.status), cell(fmtDateFull(o.createdMillis))).joinToString(","))
                                }
                            }
                            onShareCsv(csv); vm.toast("CSV siap dibagikan")
                        }
                    })
                }
            }
        }
        when {
            !vm.ordersLoaded -> item { SpinnerBox("Memuat pesanan...") }
            vm.ordersError != null && vm.orders.isEmpty() -> item { LoadingBox("Gagal memuat pesanan.", Icons.Default.Warning) }
            filtered.isEmpty() -> item { LoadingBox("Tidak ada transaksi yang cocok", Icons.Default.Inbox) }
            else -> items(filtered) { o -> OrderCard(vm, o, onWa) }
        }
    }
}

@Composable
fun OrderCard(vm: AdminViewModel, o: NativeOrder, onWa: (String, String) -> Unit) {
    val st = o.status.lowercase()
    val (label, color, bg) = when {
        st == "paid" -> Triple("Lunas", GreenC, Color(0xFF0C2013))
        st == "expired" || st == "cancel" -> Triple("Expired", RedC, Color(0xFF2B1114))
        else -> Triple("Pending", YellowC, Color(0xFF261F0E))
    }
    GsCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = { vm.sheet = SheetType.OrderDetail(o.id) }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            T("#${o.orderId} • ${timeAgo(o.createdMillis)}", 10.4f, InkDim, FontWeight.Bold, mono = true, modifier = Modifier.weight(1f), maxLines = 1)
            Spacer(Modifier.width(8.dp))
            StatusBadge(label, color, bg)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                T(o.productName + if (o.packageName.isNotEmpty()) " · ${o.packageName}" else "", 12.9f, Ink, FontWeight.Bold)
                T("+" + o.whatsapp.ifEmpty { "-" }, 10.9f, InkDim, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            T(fmt(o.total), 14.7f, display = true, spacing = -0.2f)
        }
        Spacer(Modifier.height(10.dp))
        Divider1()
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BtnAction("Detail Akun", Icons.Default.Visibility, onClick = { vm.sheet = SheetType.OrderDetail(o.id) }, modifier = Modifier.weight(1f))
            BtnAction("Chat WA", Icons.Default.ChatBubble, onClick = {
                onWa(o.whatsapp, "Halo kak, kami dari Admin GivaStore ingin konfirmasi pesanan ${o.productName} (${o.orderId}).")
            }, modifier = Modifier.weight(1f), wa = true)
        }
    }
}

@Composable
fun OrderDetailSheet(vm: AdminViewModel, o: NativeOrder, onWa: (String, String) -> Unit) {
    val clip = LocalClipboardManager.current
    val template = "Halo Kak, terima kasih telah order di GivaStore! 🎉\nBerikut detail pesanan ${o.productName} (#${o.orderId}):\n\n📧 Akun / Email: \n🔑 Sandi: \n⚡ Masa Garansi: Penuh sesuai durasi paket\n\nPanduan: Mohon login dan hindari merubah setelan billing utama. Bila ada kendala jangan ragu hubungi kami ya!"
    var text by remember(o.id) { mutableStateOf(o.accountData.ifEmpty { template }) }
    GsSheet(
        { vm.sheet = null }, "#${o.orderId}", "Waktu Transaksi: " + fmtDateFull(o.createdMillis),
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BtnMain("Chat WhatsApp", Icons.Default.ChatBubble, onClick = { onWa(o.whatsapp, text) }, modifier = Modifier.weight(1f), kind = BtnKind.WA)
                if (!o.isPaid) BtnMain("Tandai Lunas", Icons.Default.Check, onClick = { vm.markOrderPaid(o.id) }, modifier = Modifier.weight(1f), kind = BtnKind.SECONDARY)
            }
        }
    ) {
        FormGroup("Produk & Paket") {
            T(o.productName + if (o.packageName.isNotEmpty()) " (${o.packageName})" else "", 14f, Ink, FontWeight.Bold)
            T("${fmt(o.total)} • Status: ${o.status}", 11.9f, InkDim, mono = true, modifier = Modifier.padding(top = 2.dp))
        }
        FormGroup("Pelanggan (WhatsApp)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("+" + o.whatsapp.ifEmpty { "-" }, 12.6f, Ink, FontWeight.Bold, mono = true, modifier = Modifier.weight(1f))
                BtnAction("Salin", Icons.Default.ContentCopy, onClick = { clip.setText(AnnotatedString(o.whatsapp)); vm.toast("Nomor disalin") })
            }
        }
        FormGroup("Data Akun / Catatan Pelanggan") {
            GsField(text, { text = it }, singleLine = false, minHeight = 150.dp, mono = true, fontSize = 11.2.sp, placeholder = "Format akun kredensial...")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BtnAction("Salin Pesan", Icons.Default.ContentCopy, onClick = { clip.setText(AnnotatedString(text)); vm.toast("Pesan disalin") })
                BtnAction("Reset Template", Icons.Default.Refresh, onClick = { text = template })
            }
        }
    }
}

/* ============================================================
   TAB 2: KEUANGAN
   ============================================================ */
@Composable
fun SalesChart(labels: List<String>, data: List<Long>, modifier: Modifier = Modifier) {
    fun trimNum(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.US, "%.1f", d)
    fun axis(v: Long) = when {
        v >= 1_000_000 -> trimNum(v / 1e6) + "Jt"
        v >= 1_000 -> trimNum(v / 1e3) + "k"
        else -> v.toString()
    }
    val maxData = data.maxOrNull() ?: 0L
    val step = run {
        val raw = if (maxData <= 0) 1.0 else maxData / 4.0
        val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
        listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    }
    val maxV = Math.max(1L, Math.round(step * 4))
    Canvas(modifier) {
        val leftPad = 38.dp.toPx(); val bottomPad = 20.dp.toPx(); val topPad = 6.dp.toPx()
        val w = size.width - leftPad; val h = size.height - bottomPad - topPad
        val yPaint = Paint().apply { isAntiAlias = true; color = 0xFF7A7A85.toInt(); textSize = 9.sp.toPx(); textAlign = Paint.Align.RIGHT }
        val xPaint = Paint().apply { isAntiAlias = true; color = 0xFF7A7A85.toInt(); textSize = 8.5.sp.toPx(); textAlign = Paint.Align.CENTER }
        for (i in 0..4) {
            val y = topPad + h - h * i / 4f
            drawLine(Color(0x10FFFFFF), Offset(leftPad, y), Offset(size.width, y), 1.dp.toPx())
            drawIntoCanvas { it.nativeCanvas.drawText(axis(maxV * i / 4), leftPad - 6.dp.toPx(), y + 3.dp.toPx(), yPaint) }
        }
        val n = data.size
        if (n == 0) return@Canvas
        val slot = w / n
        val barW = Math.min(24.dp.toPx(), slot * 0.7f)
        data.forEachIndexed { i, v ->
            val bh = h * v / maxV.toFloat()
            val x = leftPad + slot * i + (slot - barW) / 2
            if (bh > 0f) drawRoundRect(Color.White, Offset(x, topPad + h - bh), Size(barW, bh), CornerRadius(4.dp.toPx()))
            if (i % 2 == 0) drawIntoCanvas { it.nativeCanvas.drawText(labels[i], x + barW / 2, size.height - 4.dp.toPx(), xPaint) }
        }
    }
}

@Composable
fun FinanceTab(vm: AdminViewModel) {
    val periods = Period.values()
    val cur = vm.finPeriod
    val inc = vm.income(cur); val exp = vm.expense(cur); val profit = inc - exp
    var desc by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Stok Produk") }
    var amount by remember { mutableStateOf("") }
    val chart = remember(vm.orders, vm.chartWeekly) { salesSeries(vm.orders, vm.chartWeekly) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionTitle("Laporan Keuangan", cur.full)
                Box(Modifier.padding(bottom = 14.dp)) { SegPills(cur.ordinal, periods.map { it.pill }) { vm.finPeriod = periods[it] } }
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GsCard(Modifier.weight(1f)) { MetricLabel("Pemasukan (${cur.full})"); T(fmt(inc), 16.5f, display = true, maxLines = 1) }
                    GsCard(Modifier.weight(1f)) { MetricLabel("Pengeluaran (${cur.full})"); T(fmt(exp), 16.5f, RedC, display = true, maxLines = 1) }
                }
                GsCard(Modifier.fillMaxWidth().padding(bottom = 20.dp), hero = true) {
                    MetricLabel("Laba Bersih (${cur.full})", Icons.Default.AccountBalanceWallet)
                    T(fmt(profit), 27f, if (profit >= 0) WaGreen else RedC, display = true, spacing = -0.5f, modifier = Modifier.padding(top = 4.dp))
                }

                GsCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        T("Ringkasan 4 Periode", 12f, Ink, FontWeight.Bold)
                        T("Otomatis Sinkron", 9.5f, InkFaint, FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        T("Periode", 9f, InkFaint, FontWeight.Bold, upper = true, modifier = Modifier.weight(0.95f))
                        T("Pemasukan", 9f, InkFaint, FontWeight.Bold, upper = true, modifier = Modifier.weight(1.25f))
                        T("Pengeluaran", 9f, InkFaint, FontWeight.Bold, upper = true, modifier = Modifier.weight(1.25f))
                        T("Laba Bersih", 9f, InkFaint, FontWeight.Bold, upper = true, align = TextAlign.End, modifier = Modifier.weight(1.25f))
                    }
                    Divider1()
                    periods.forEach { p ->
                        val i2 = vm.income(p); val e2 = vm.expense(p); val pr = i2 - e2
                        Row(Modifier.fillMaxWidth().clickable { vm.finPeriod = p }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(p.full, 10.6f, Ink, FontWeight.Bold, modifier = Modifier.weight(0.95f))
                            T(fmtShort(i2), 10f, Ink, mono = true, modifier = Modifier.weight(1.25f), maxLines = 1)
                            T(fmtShort(e2), 10f, RedC, mono = true, modifier = Modifier.weight(1.25f), maxLines = 1)
                            T(fmtShort(pr), 10f, if (pr >= 0) WaGreen else RedC, FontWeight.Bold, mono = true, align = TextAlign.End, modifier = Modifier.weight(1.25f), maxLines = 1)
                        }
                    }
                }

                GsCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        T("Grafik Penjualan", 12.3f, Ink, FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("14 Hari", !vm.chartWeekly, small = true) { vm.chartWeekly = false }
                            Chip("8 Minggu", vm.chartWeekly, small = true) { vm.chartWeekly = true }
                        }
                    }
                    SalesChart(chart.first, chart.second, Modifier.fillMaxWidth().height(200.dp))
                }

                GsCard(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    T("Catat Pengeluaran", 13.3f, display = true, upper = true, modifier = Modifier.padding(bottom = 10.dp))
                    FormGroup("Deskripsi") { GsField(desc, { desc = it }, placeholder = "Contoh: Restok akun Netflix") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormGroup("Kategori", Modifier.weight(1f)) {
                            GsDropdown(cat, listOf("Stok Produk", "Operasional", "Marketing", "Gaji Admin", "Lainnya").map { it to it }, { cat = it })
                        }
                        FormGroup("Jumlah (Rp)", Modifier.weight(1f)) {
                            GsField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, placeholder = "50000", keyboardType = KeyboardType.Number)
                        }
                    }
                    BtnMain("Catat Pengeluaran", Icons.Default.Add, onClick = {
                        val a = amount.toDoubleOrNull()
                        if (desc.isBlank() || a == null || a <= 0) vm.toast("Isi deskripsi dan nominal pengeluaran")
                        else vm.addExpense(desc.trim(), cat, a) { desc = ""; amount = "" }
                    })
                }
                SectionTitle("Riwayat Pengeluaran")
            }
        }
        when {
            !vm.expensesLoaded -> item { SpinnerBox("Memuat pengeluaran...") }
            vm.expenses.isEmpty() -> item { LoadingBox("Belum ada catatan pengeluaran") }
            else -> items(vm.expenses) { e ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(e.description, 12.3f, Ink, FontWeight.Bold)
                            T("${e.category} • ${fmtDay(e.dateMillis)}", 10.1f, InkDim)
                        }
                        T("-${fmt(e.amount)}", 12f, RedC, FontWeight.Bold, mono = true)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, LineC, RoundedCornerShape(8.dp))
                                .clickable { vm.confirm = ConfirmReq("Hapus catatan pengeluaran ini?") { vm.deleteExpense(e.id) } },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Delete, null, tint = RedC, modifier = Modifier.size(15.dp)) }
                    }
                    Divider1()
                }
            }
        }
    }
}

/* ============================================================
   TAB 3: PRODUK
   ============================================================ */
@Composable
fun ProductsTab(vm: AdminViewModel) {
    var query by remember { mutableStateOf("") }
    val filtered = vm.products.filter { query.isBlank() || it.name.lowercase().contains(query.lowercase().trim()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 100.dp)) {
        item {
            Column(Modifier.fillMaxWidth()) {
                SectionTitle("Katalog Produk", "${vm.products.size} Produk")
                GsField(query, { query = it }, placeholder = "Cari nama produk...")
                Spacer(Modifier.height(10.dp))
            }
        }
        when {
            !vm.productsLoaded -> item { SpinnerBox("Memuat produk...") }
            filtered.isEmpty() -> item { LoadingBox("Produk tidak ditemukan", Icons.Default.Inventory2) }
            else -> items(filtered) { p ->
                val ready = p.stock > 0
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(BgSurface)
                        .border(1.dp, LineC, RoundedCornerShape(14.dp)).clickable { vm.sheet = SheetType.ProductEdit(p.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(BgCard).border(1.dp, LineC, RoundedCornerShape(8.dp)).padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RemoteImage(resolveImg(p.imageUrl), vm.httpClient, Modifier.fillMaxSize(), ContentScale.Fit) {
                            T("GS", 13f, InkDim, display = true)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(p.name, 12.3f, Ink, FontWeight.Bold, maxLines = 1)
                        T("${fmt(p.basePrice)} • Stok: ${p.stock}", 10.9f, InkDim, mono = true, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (p.isFlash) StatusBadge("Flash", YellowC, Color(0xFF261F0E))
                            if (p.discount > 0) StatusBadge("-${p.discount}%", GreenC, Color(0xFF0C2013))
                            if (p.stock in 1..5) StatusBadge("Menipis", RedC, Color(0xFF2B1114))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        GsSwitch(ready) { vm.toggleStock(p.id, it) }
                        T(if (ready) "READY" else "HABIS", 8.7f, if (ready) InkDim else RedC, FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProductEditSheet(vm: AdminViewModel, editId: String?, onPickImage: ((Uri) -> Unit) -> Unit) {
    val p = remember(editId) { vm.products.firstOrNull { it.id == editId } }
    var name by remember(editId) { mutableStateOf(p?.name ?: "") }
    var slug by remember(editId) { mutableStateOf(p?.slug ?: "") }
    var slugTouched by remember(editId) { mutableStateOf(editId != null) }
    var categoryId by remember(editId) { mutableStateOf(p?.categoryId ?: "") }
    var image by remember(editId) { mutableStateOf(p?.imageUrl ?: "") }
    var price by remember(editId) { mutableStateOf(p?.basePrice?.takeIf { it != 0L }?.toString() ?: "") }
    var original by remember(editId) { mutableStateOf(p?.originalPrice?.takeIf { it != 0L }?.toString() ?: "") }
    var stock by remember(editId) { mutableStateOf(p?.stock?.toString() ?: "50") }
    var discount by remember(editId) { mutableStateOf(p?.discount?.takeIf { it != 0 }?.toString() ?: "") }
    var discountTouched by remember(editId) { mutableStateOf(editId != null) }
    var desc by remember(editId) { mutableStateOf(p?.description ?: "") }
    var flash by remember(editId) { mutableStateOf(p?.isFlash ?: false) }
    var tried by remember(editId) { mutableStateOf(false) }
    val pkgs = remember(editId) { mutableStateListOf<PkgRow>() }
    val inps = remember(editId) { mutableStateListOf<InpRow>() }

    LaunchedEffect(editId) {
        if (editId != null) {
            val ex = vm.fetchProductExtras(editId)
            pkgs.clear(); pkgs.addAll(ex.first); inps.clear(); inps.addAll(ex.second)
        } else {
            pkgs.add(PkgRow()); inps.add(InpRow("Nomor WhatsApp", "text"))
        }
    }
    // Diskon otomatis dari harga jual & harga coret (selama belum diubah manual)
    LaunchedEffect(price, original) {
        if (!discountTouched) {
            val pr = price.toLongOrNull() ?: 0L; val orr = original.toLongOrNull() ?: 0L
            discount = if (orr > pr && pr > 0) ((orr - pr) * 100 / orr).toString() else ""
        }
    }

    val nameErr = tried && name.isBlank()
    val slugErr = tried && slug.isBlank()
    val priceErr = tried && (price.toLongOrNull() ?: 0L) <= 0L
    val uploading = vm.uploadTarget == "product"

    GsSheet(
        { vm.sheet = null }, if (editId != null) "Edit Produk" else "Tambah Produk", "Lengkapi data, lalu simpan. Foto bisa dipilih dari galeri.",
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BtnMain(
                    if (vm.saving) "Menyimpan..." else if (editId != null) "Simpan Perubahan" else "Tambah Produk", Icons.Default.Check,
                    onClick = {
                        tried = true
                        if (name.isBlank() || slug.isBlank() || (price.toLongOrNull() ?: 0L) <= 0L) {
                            vm.toast("Lengkapi kolom yang bertanda merah"); return@BtnMain
                        }
                        val payload = JSONObject()
                            .put("name", name.trim()).put("slug", slug.trim())
                            .put("category_id", if (categoryId.isBlank()) JSONObject.NULL else categoryId)
                            .put("image_url", if (image.isBlank()) JSONObject.NULL else image.trim())
                            .put("base_price", price.toLongOrNull() ?: 0L).put("original_price", original.toLongOrNull() ?: 0L)
                            .put("stock", stock.toIntOrNull() ?: 0).put("discount_percentage", discount.toIntOrNull() ?: 0)
                            .put("description", if (desc.isBlank()) JSONObject.NULL else desc.trim())
                            .put("is_flash_sale", flash)
                        vm.saveProduct(
                            editId, payload,
                            pkgs.filter { it.name.isNotBlank() }.map { Triple(it.name.trim(), it.price.toIntOrNull() ?: 0, it.duration.trim()) },
                            inps.filter { it.label.isNotBlank() }.map { it.label.trim() to it.type }
                        )
                    },
                    modifier = Modifier.weight(1f), enabled = !vm.saving && !uploading, loading = vm.saving
                )
                if (editId != null) IconSquare(
                    Icons.Default.Delete,
                    { vm.confirm = ConfirmReq("Hapus produk \"$name\" beserta paket dan field-nya?") { vm.deleteProduct(editId) } },
                    RedC, 48.dp
                )
            }
        }
    ) {
        SectionCard("Foto Produk") {
            PhotoPreview(image, vm.httpClient, PhotoMode.PRODUCT, "Belum ada foto", busy = uploading)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BtnAction(if (uploading) "Mengunggah..." else "Pilih dari Galeri", Icons.Default.CameraAlt, onClick = {
                    if (!uploading) onPickImage { uri -> vm.uploadImage(uri, "product", "product") { image = it } }
                }, modifier = Modifier.weight(1f))
                if (image.isNotBlank()) BtnAction("Hapus Foto", Icons.Default.Delete, onClick = { image = "" })
            }
            Spacer(Modifier.height(8.dp))
            GsField(image, { image = it }, placeholder = "Atau tempel URL foto / image/netflix.png", fontSize = 11.5.sp)
        }

        SectionCard("Info Dasar") {
            FormGroup("Nama Produk *") {
                GsField(name, { name = it; if (!slugTouched) slug = slugify(it) }, placeholder = "Netflix Premium 1 Bulan", error = nameErr)
                if (nameErr) FieldHint("Nama produk wajib diisi", true)
            }
            FormGroup("Slug (alamat produk) *") {
                GsField(slug, { slugTouched = true; slug = it.lowercase().replace(' ', '-') }, placeholder = "netflix-premium", error = slugErr, mono = true, fontSize = 11.5.sp)
                if (slugErr) FieldHint("Slug wajib diisi", true) else FieldHint("Terisi otomatis dari nama, boleh diubah.")
            }
            FormGroup("Kategori") {
                if (vm.categories.isEmpty()) FieldHint("Belum ada kategori. Tambahkan di Menu → Kategori.")
                else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Tanpa Kategori", categoryId.isBlank()) { categoryId = "" }
                    vm.categories.forEach { c -> Chip(c.name, categoryId == c.id) { categoryId = c.id } }
                }
            }
            FormGroup("Deskripsi Singkat") {
                GsField(desc, { desc = it }, singleLine = false, minHeight = 80.dp, fontSize = 12.sp, placeholder = "Akun premium private legal garansi penuh...")
            }
        }

        SectionCard("Harga & Stok") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormGroup("Harga Jual (Rp) *", Modifier.weight(1f)) {
                    GsField(price, { price = it.filter(Char::isDigit) }, placeholder = "25000", keyboardType = KeyboardType.Number, error = priceErr)
                    if (priceErr) FieldHint("Harga wajib > 0", true)
                    else price.toLongOrNull()?.let { FieldHint(fmt(it)) }
                }
                FormGroup("Harga Coret (Rp)", Modifier.weight(1f)) {
                    GsField(original, { original = it.filter(Char::isDigit) }, placeholder = "50000", keyboardType = KeyboardType.Number)
                    original.toLongOrNull()?.let { FieldHint(fmt(it)) }
                }
            }
            FormGroup("Diskon (%)") {
                GsField(discount, { discountTouched = true; discount = it.filter(Char::isDigit).take(3) }, placeholder = "Otomatis dari harga coret", keyboardType = KeyboardType.Number)
            }
            FormGroup("Stok") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconSquare(Icons.Default.Remove, { stock = ((stock.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() }, size = 44.dp)
                    GsField(stock, { stock = it.filter(Char::isDigit) }, Modifier.weight(1f), placeholder = "0", keyboardType = KeyboardType.Number)
                    IconSquare(Icons.Default.Add, { stock = ((stock.toIntOrNull() ?: 0) + 1).toString() }, size = 44.dp)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 50, 100).forEach { n -> Chip("$n", false, small = true) { stock = n.toString() } }
                    Chip("Habis (0)", false, small = true) { stock = "0" }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Flash Sale Beranda", 12.3f, Ink, FontWeight.Bold)
                    T("Tampilkan di bagian flash sale toko", 10f, InkDim)
                }
                GsSwitch(flash) { flash = it }
            }
        }

        SectionCard("Paket Harga (Variasi)") {
            T("Isi jika produk punya beberapa pilihan durasi/paket.", 10.4f, InkDim, modifier = Modifier.padding(bottom = 8.dp))
            pkgs.forEach { r ->
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x08FFFFFF))
                        .border(1.dp, LineC, RoundedCornerShape(10.dp)).padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GsField(r.name, { r.name = it }, Modifier.weight(1f), placeholder = "Nama paket (mis. 1 Bulan)", fontSize = 12.sp)
                        IconSquare(Icons.Default.Close, { pkgs.remove(r) }, RedC, 36.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GsField(r.price, { r.price = it.filter(Char::isDigit) }, Modifier.weight(1f), placeholder = "Harga (Rp)", keyboardType = KeyboardType.Number, fontSize = 12.sp)
                        GsField(r.duration, { r.duration = it.uppercase() }, Modifier.weight(1f), placeholder = "Durasi", fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BtnAction("Paket", Icons.Default.Add, onClick = { pkgs.add(PkgRow()) })
                listOf("1 Bulan" to "1 BULAN", "3 Bulan" to "3 BULAN", "6 Bulan" to "6 BULAN", "1 Tahun" to "1 TAHUN").forEach { (l, d) ->
                    Chip("+ $l", false, small = true) { pkgs.add(PkgRow(l, "", d)) }
                }
            }
        }

        SectionCard("Input yang Diisi Pembeli") {
            T("Data yang diminta saat checkout (email akun, nomor WA, ID game, dll).", 10.4f, InkDim, modifier = Modifier.padding(bottom = 8.dp))
            inps.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GsField(r.label, { r.label = it }, Modifier.weight(1.6f), placeholder = "Label (mis. Email)", fontSize = 12.sp)
                    GsDropdown(if (r.type == "email") "Email" else "Teks", listOf("text" to "Teks", "email" to "Email"), { r.type = it }, Modifier.weight(1f))
                    IconSquare(Icons.Default.Close, { inps.remove(r) }, RedC, 36.dp)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BtnAction("Field", Icons.Default.Add, onClick = { inps.add(InpRow()) })
                listOf("Nomor WhatsApp" to "text", "Email Akun" to "email", "ID Game" to "text", "Username" to "text").forEach { (l, t) ->
                    Chip("+ $l", false, small = true) { if (inps.none { it.label == l }) inps.add(InpRow(l, t)) }
                }
            }
        }
    }
}

/* ============================================================
   TAB 4: KONTEN
   ============================================================ */
@Composable
fun ContentTab(vm: AdminViewModel, onPickImage: ((Uri) -> Unit) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)) {
        SectionTitle("Pengaturan Konten")

        GsCard(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            MetricLabel("Logo Toko (Header Web)")
            PhotoPreview(vm.logoUrl, vm.httpClient, PhotoMode.LOGO, "Belum ada logo", busy = vm.uploadTarget == "logo")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BtnAction(if (vm.uploadTarget == "logo") "Mengunggah..." else "Pilih Foto dari HP", Icons.Default.CameraAlt, onClick = {
                    if (vm.uploadTarget == null) onPickImage { uri -> vm.uploadImage(uri, "logo", "logo") { vm.logoUrl = it; vm.saveLogo() } }
                }, modifier = Modifier.weight(1f))
                if (vm.logoUrl.isNotBlank()) BtnAction("Hapus", Icons.Default.Delete, onClick = {
                    vm.confirm = ConfirmReq("Hapus logo toko?") { vm.logoUrl = ""; vm.saveLogo() }
                })
            }
            Spacer(Modifier.height(10.dp))
            FormGroup("Atau Masukkan URL Gambar") { GsField(vm.logoUrl, { vm.logoUrl = it }, placeholder = "https://...") }
            BtnMain("Simpan Logo", onClick = { vm.saveLogo() }, kind = BtnKind.SECONDARY)
        }

        GsCard(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            MetricLabel("Banner Carousel Beranda (Rasio 16:9 / Landscape)")
            val banners = listOf(vm.banner1, vm.banner2, vm.banner3)
            banners.forEachIndexed { idx, url ->
                val n = idx + 1
                Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    T("Banner Slot $n", 9.5f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.8f, modifier = Modifier.padding(bottom = 6.dp))
                    PhotoPreview(url, vm.httpClient, PhotoMode.BANNER, "Kosong", busy = vm.uploadTarget == "banner$n")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BtnAction(if (vm.uploadTarget == "banner$n") "Mengunggah..." else "Upload Banner $n", Icons.Default.CloudUpload, onClick = {
                            if (vm.uploadTarget == null) onPickImage { uri ->
                                vm.uploadImage(uri, "banner", "banner$n") { u -> vm.setBanner(n, u); vm.saveBannerSlot(n, u) }
                            }
                        }, modifier = Modifier.weight(1f))
                        if (url.isNotBlank()) BtnAction("Hapus", Icons.Default.Delete, onClick = {
                            vm.confirm = ConfirmReq("Hapus banner slot $n?") { vm.setBanner(n, ""); vm.saveBannerSlot(n, "") }
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    GsField(url, { v -> vm.setBanner(n, v) }, placeholder = "URL Banner $n https://...")
                }
            }
            BtnMain("Simpan URL Banner", onClick = { vm.saveBanners() }, kind = BtnKind.SECONDARY)
        }

        GsCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                T("Popup Pengumuman", 9.5f, InkFaint, FontWeight.Bold, upper = true, spacing = 0.8f)
                BtnAction("Buat Baru", Icons.Default.Add, onClick = { vm.sheet = SheetType.NewPopup })
            }
            when {
                !vm.popupsLoaded -> SpinnerBox("Memuat popup...")
                vm.popups.isEmpty() -> LoadingBox("Belum ada popup pengumuman")
                else -> vm.popups.forEach { pop ->
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(BgCard)
                            .border(1.dp, LineC, RoundedCornerShape(8.dp)).padding(10.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            T(pop.title, 11.9f, Ink, FontWeight.Bold, modifier = Modifier.weight(1f))
                            GsSwitch(pop.isActive) { vm.togglePopup(pop.id, it) }
                        }
                        T(pop.message, 10.5f, InkDim, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NewPopupDialog(vm: AdminViewModel) {
    var title by remember { mutableStateOf("Flash Sale Malam Ini ⚡") }
    var msg by remember { mutableStateOf("Dapatkan diskon hingga 50% sebelum tengah malam!") }
    AlertDialog(
        onDismissRequest = { vm.sheet = null }, containerColor = BgSurface,
        title = { T("Buat Popup Baru", 15f, Ink, FontWeight.Bold) },
        text = {
            Column {
                FormGroup("Judul Popup") { GsField(title, { title = it }) }
                FormGroup("Pesan Pengumuman") { GsField(msg, { msg = it }, singleLine = false, minHeight = 80.dp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isBlank()) vm.toast("Judul wajib diisi") else { vm.createPopup(title.trim(), msg.trim()); vm.sheet = null }
            }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Simpan", color = BgBase, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { vm.sheet = null }) { Text("Batal", color = InkDim) } }
    )
}

/* ============================================================
   TAB 5: MENU
   ============================================================ */
@Composable
fun DrawerItem(
    icon: ImageVector, label: String, sub: String? = null, tint: Color = InkDim, textColor: Color = Ink,
    borderColor: Color = LineC, onClick: () -> Unit, trailing: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(BgCard).border(1.dp, borderColor, shape)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(label, 12.6f, textColor, FontWeight.Bold)
            if (sub != null) T(sub, 9.8f, InkDim)
        }
        trailing()
    }
}

@Composable
fun MoreTab(
    vm: AdminViewModel, bioSupported: Boolean, onToggleBio: (Boolean) -> Unit, onOpenStore: () -> Unit,
    onNotif: () -> Unit, onBgSettings: () -> Unit
) {
    val chevron: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, tint = InkDim, modifier = Modifier.size(18.dp)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp)) {
        SectionTitle("Notifikasi Pesanan")
        DrawerItem(
            Icons.Default.NotificationsActive, "Notifikasi Pesanan Baru",
            if (vm.monitorEnabled) "Aktif, memantau walau aplikasi ditutup" else "Mati, hanya cek saat aplikasi dibuka",
            tint = WaGreen, onClick = { vm.setMonitor(!vm.monitorEnabled) }
        ) { GsSwitch(vm.monitorEnabled) { vm.setMonitor(it) } }
        DrawerItem(
            Icons.Default.Settings, "Izin Latar Belakang (Penting)",
            "Mulai otomatis & tanpa batasan baterai, wajib di Xiaomi/Oppo/Vivo",
            onClick = onBgSettings, trailing = chevron
        )
        DrawerItem(Icons.Default.Notifications, "Kirim Notifikasi Tes", "Cek apakah notifikasi sudah muncul", onClick = onNotif, trailing = chevron)
        Spacer(Modifier.height(6.dp))

        if (bioSupported) {
            SectionTitle("Keamanan & Masuk")
            DrawerItem(
                Icons.Default.Fingerprint, "Kunci Sidik Jari (Biometrik)",
                if (vm.bioEnabled) "Login cepat sekali sentuh aktif" else "Aktifkan login cepat sekali sentuh",
                tint = WaGreen, onClick = { onToggleBio(!vm.bioEnabled) }
            ) { GsSwitch(vm.bioEnabled) { onToggleBio(it) } }
            Spacer(Modifier.height(6.dp))
        }
        SectionTitle("Menu Manajemen")
        DrawerItem(Icons.Default.FolderOpen, "Kelola Kategori Produk", onClick = { vm.sheet = SheetType.Category }, trailing = chevron)
        DrawerItem(Icons.Default.ConfirmationNumber, "Kode Promo & Diskon", onClick = { vm.sheet = SheetType.Promo }, trailing = chevron)
        DrawerItem(Icons.Default.People, "Daftar Pelanggan & Riwayat", onClick = { vm.sheet = SheetType.Customer }, trailing = chevron)
        DrawerItem(Icons.Default.Storefront, "Buka Tampilan Pembeli (Toko)", onClick = onOpenStore) {
            Icon(Icons.Default.OpenInNew, null, tint = InkDim, modifier = Modifier.size(16.dp))
        }
        DrawerItem(
            Icons.Default.ExitToApp, "Keluar (Logout)", tint = RedC, textColor = RedC, borderColor = RedC.copy(alpha = 0.3f),
            onClick = { vm.confirm = ConfirmReq("Yakin ingin keluar dari Admin Center?") { vm.logout() } }
        )
    }
}

/* ============================================================
   SHEET: KATEGORI, PROMO, PELANGGAN
   ============================================================ */
@Composable
fun CategorySheet(vm: AdminViewModel) {
    var name by remember { mutableStateOf("") }
    GsSheet({ vm.sheet = null }, "Kategori Produk", "Tambah dan atur label kategori") {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GsField(name, { name = it }, Modifier.weight(1f), placeholder = "Nama kategori baru...")
            BtnMain("", Icons.Default.Add, onClick = {
                if (name.isNotBlank()) vm.addCategory(name.trim()) { name = "" }
            }, modifier = Modifier.width(60.dp))
        }
        if (vm.categories.isEmpty()) LoadingBox("Belum ada kategori")
        vm.categories.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                T(c.name, 12.6f, Ink, FontWeight.Bold, modifier = Modifier.weight(1f))
                IconSquare(Icons.Default.Delete, { vm.confirm = ConfirmReq("Hapus kategori ini?") { vm.deleteCategory(c.id) } }, RedC, 32.dp)
            }
            Divider1()
        }
    }
}

@Composable
fun PromoSheet(vm: AdminViewModel) {
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("percentage") }
    var value by remember { mutableStateOf("") }
    var minOrder by remember { mutableStateOf("") }
    var maxDisc by remember { mutableStateOf("") }
    GsSheet({ vm.sheet = null }, "Kode Promo", "Kupon diskon transaksi pembeli") {
        GsCard(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            FormGroup("Kode Promo *") { GsField(code, { code = it }, placeholder = "HEMAT20", upper = true) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormGroup("Tipe", Modifier.weight(1f)) {
                    GsDropdown(if (type == "percentage") "Persentase (%)" else "Nominal (Rp)", listOf("percentage" to "Persentase (%)", "fixed" to "Nominal (Rp)"), { type = it })
                }
                FormGroup("Nilai Diskon *", Modifier.weight(1f)) { GsField(value, { value = it.filter { c -> c.isDigit() || c == '.' } }, placeholder = "20", keyboardType = KeyboardType.Number) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormGroup("Min Belanja (Rp)", Modifier.weight(1f)) { GsField(minOrder, { minOrder = it.filter(Char::isDigit) }, placeholder = "0", keyboardType = KeyboardType.Number) }
                FormGroup("Maks Diskon (Rp)", Modifier.weight(1f)) { GsField(maxDisc, { maxDisc = it.filter(Char::isDigit) }, placeholder = "10000", keyboardType = KeyboardType.Number) }
            }
            BtnMain("Simpan Kode", Icons.Default.Check, onClick = {
                val v = value.toDoubleOrNull()
                if (code.isBlank() || v == null || v <= 0) vm.toast("Isi kode dan nilai promo")
                else vm.savePromo(code.trim().uppercase(), type, v, minOrder.toDoubleOrNull() ?: 0.0, maxDisc.toDoubleOrNull()) {
                    code = ""; value = ""; minOrder = ""; maxDisc = ""
                }
            })
        }
        if (vm.promos.isEmpty()) LoadingBox("Belum ada kode promo")
        vm.promos.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(p.code, 12.6f, Ink, FontWeight.ExtraBold, mono = true)
                    val v = if (p.value % 1.0 == 0.0) p.value.toLong().toString() else p.value.toString()
                    T((if (p.type == "percentage") "$v%" else fmt(p.value)) + " • Min: ${fmt(p.minOrder)}", 10.1f, InkDim)
                }
                IconSquare(Icons.Default.Delete, { vm.confirm = ConfirmReq("Hapus kode promo ini?") { vm.deletePromo(p.id) } }, RedC, 32.dp)
            }
            Divider1()
        }
    }
}

@Composable
fun CustomerSheet(vm: AdminViewModel) {
    var q by remember { mutableStateOf("") }
    val all = remember(vm.orders) {
        val map = LinkedHashMap<String, Pair<Int, Long>>()
        vm.orders.forEach { o ->
            val wa = o.whatsapp.trim()
            if (wa.isNotEmpty()) {
                val cur = map[wa] ?: (0 to 0L)
                map[wa] = (cur.first + 1) to (cur.second + if (o.isPaid) o.total else 0L)
            }
        }
        map.entries.map { it.key to it.value }
    }
    val list = all.filter { it.first.contains(q.trim().lowercase()) }
    GsSheet({ vm.sheet = null }, "Data Pelanggan", "Daftar kontak pembeli & riwayat order") {
        FormGroup { GsField(q, { q = it }, placeholder = "Cari nomor WhatsApp...", keyboardType = KeyboardType.Phone) }
        if (list.isEmpty()) LoadingBox("Tidak ada kontak pelanggan")
        list.forEach { (wa, stat) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("+$wa", 12.6f, Ink, FontWeight.Bold, mono = true)
                    T("${stat.first} total order", 10.1f, InkDim)
                }
                T(fmt(stat.second), 12.3f, Ink, FontWeight.ExtraBold, mono = true)
            }
            Divider1()
        }
    }
}
