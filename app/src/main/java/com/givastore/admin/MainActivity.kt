package com.givastore.admin

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
   KONFIGURASI SUPABASE
   Anon key hanya aman jika RLS benar. GANTI key lama (sudah bocor).
   ============================================================ */
private const val SUPABASE_URL = "https://rblktttasrxemtkhknvt.supabase.co"
private const val SUPABASE_ANON_KEY = "GANTI_DENGAN_ANON_KEY_BARU"

/* ============================================================
   PALET WARNA & TEMA
   ============================================================ */
val BgDark = Color(0xFF0A0A0A)
val CardDark = Color(0xFF141416)
val SurfaceDark = Color(0xFF1C1C20)
val InkWhite = Color(0xFFF7F7F5)
val InkDim = Color(0xFF9E9EA7)
val LineBorder = Color(0xFF26262B)
val WaGreen = Color(0xFF25D366)
val BadgeRed = Color(0xFFFF4757)
val BadgeYellow = Color(0xFFF59E0B)

val GivaColors = darkColorScheme(
    primary = InkWhite, onPrimary = BgDark,
    background = BgDark, onBackground = InkWhite,
    surface = CardDark, onSurface = InkWhite,
    surfaceVariant = SurfaceDark, onSurfaceVariant = InkDim,
    outline = LineBorder,
    secondaryContainer = SurfaceDark, onSecondaryContainer = InkWhite
)

/* ============================================================
   SUPABASE API (Auth + REST, auto-refresh token saat 401)
   ============================================================ */
class SupabaseApi(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: OkHttpClient,
    private val store: SharedPreferences
) {
    @Volatile private var accessToken: String? = null
    private val json = "application/json".toMediaType()

    fun hasSavedSession() = !store.getString("refresh", null).isNullOrEmpty()

    fun signIn(email: String, password: String) {
        authCall("password", JSONObject().put("email", email).put("password", password).toString())
    }

    fun refreshSession(): Boolean {
        val rt = store.getString("refresh", null) ?: return false
        return try {
            authCall("refresh_token", JSONObject().put("refresh_token", rt).toString()); true
        } catch (e: Exception) { false }
    }

    fun signOut() {
        accessToken = null
        store.edit().remove("refresh").apply()
    }

    private fun authCall(grant: String, body: String) {
        val req = Request.Builder().url("$baseUrl/auth/v1/token?grant_type=$grant")
            .addHeader("apikey", anonKey).post(body.toRequestBody(json)).build()
        val (code, text) = client.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        if (code !in 200..299) throw IOException(parseError(text, code))
        val obj = JSONObject(text)
        accessToken = obj.getString("access_token")
        store.edit().putString("refresh", obj.optString("refresh_token")).apply()
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
    fun post(path: String, body: JSONObject, prefer: String? = null) { call("POST", path, body.toString(), prefer) }
    fun patch(path: String, body: JSONObject) { call("PATCH", path, body.toString()) }
    fun delete(path: String) { call("DELETE", path) }

    private fun parseError(text: String, code: Int): String = try {
        val o = JSONObject(text)
        listOf("error_description", "msg", "message").map { o.optString(it) }.firstOrNull { it.isNotEmpty() } ?: "HTTP $code"
    } catch (e: Exception) { "HTTP $code" }
}

/* ============================================================
   ACTIVITY
   ============================================================ */
class MainActivity : FragmentActivity() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api by lazy {
        val masterKey = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val prefs = EncryptedSharedPreferences.create(
            this, "gs_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        SupabaseApi(SUPABASE_URL, SUPABASE_ANON_KEY, httpClient, prefs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)

        setContent {
            MaterialTheme(colorScheme = GivaColors) {
                var isAuthenticated by remember { mutableStateOf(false) }
                if (!isAuthenticated) {
                    NativeLoginScreen(
                        api = api,
                        canBiometric = biometricAvailable() && api.hasSavedSession(),
                        onBiometricRequest = { triggerBiometric { isAuthenticated = true } },
                        onAuthSuccess = { isAuthenticated = true }
                    )
                } else {
                    NativeFullDashboard(
                        api = api,
                        onLogout = { api.signOut(); isAuthenticated = false },
                        onDispatchWa = { phone, text -> openWhatsApp(phone, text) },
                        onShareCsv = { content -> shareCsv(content) }
                    )
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun biometricAvailable() =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Biometrik hanya membuka sesi tersimpan. Tidak ada bypass jika gagal/tidak tersedia. */
    private fun triggerBiometric(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { api.refreshSession() }
                        if (ok) onSuccess() else toast("Sesi habis, masuk dengan password.")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) toast("Biometrik: $errString")
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("GivaStore Admin Biometrik")
            .setSubtitle("Sentuh sensor sidik jari untuk masuk")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Gunakan Password")
            .build()
        prompt.authenticate(info)
    }

    private fun openWhatsApp(phone: String, text: String) {
        val clean = phone.replace(Regex("[^0-9]"), "").replace(Regex("^0"), "62")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$clean&text=${Uri.encode(text)}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") })
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            catch (err: Exception) { toast("Aplikasi WhatsApp tidak terpasang.") }
        }
    }

    private fun shareCsv(content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Pesanan GivaStore")
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(intent, "Ekspor Pesanan CSV"))
        } catch (e: Exception) {
            toast("Gagal membagikan CSV.")
        }
    }
}

/* ============================================================
   FIREBASE SERVICE
   ============================================================ */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        try {
            val title = remoteMessage.notification?.title ?: "Pesanan Baru Masuk! ⚡"
            val body = remoteMessage.notification?.body ?: "Buka GivaStore Admin untuk memproses orderan."
            val channelId = "givastore_admin_orders"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Pesanan Baru GivaStore", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Notifikasi transaksi toko"; enableVibration(true) }
                nm.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/* ============================================================
   DATA MODELS & HELPER
   ============================================================ */
data class NativeOrder(
    val id: String, val orderId: String, val productName: String, val packageName: String,
    val totalAmount: Long, val whatsapp: String, val status: String, val createdAt: String,
    val createdMillis: Long, val accountData: String
)

data class NativeProduct(
    val id: String, val name: String, val basePrice: Long, val stock: Int, val isFlashSale: Boolean
)

data class NativeExpense(val id: String, val description: String, val category: String, val amount: Long, val date: String)
data class NativeCategory(val id: String, val name: String)
data class NativePromo(val id: String, val code: String, val discountType: String, val discountValue: Long)
data class NativePopup(val id: String, val title: String, val message: String, val isActive: Boolean)

enum class TimePeriod(val label: String) {
    TODAY("Hari Ini"), WEEK("Minggu"), MONTH("Bulan"), YEAR("Tahun")
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

fun JSONObject.str(key: String, fb: String = ""): String = if (isNull(key)) fb else optString(key, fb)

fun JSONObject.optSafeLong(key: String, fallback: Long = 0L): Long {
    if (isNull(key)) return fallback
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: fallback
        else -> fallback
    }
}

fun parseIsoMillis(s: String): Long = try {
    if (s.length >= 19) {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        f.parse(s.substring(0, 19))?.time ?: 0L
    } else if (s.length >= 10) {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        f.parse(s.substring(0, 10))?.time ?: 0L
    } else 0L
} catch (e: Exception) { 0L }

fun periodCutoff(p: TimePeriod): Long {
    val day = 86_400_000L
    return when (p) {
        TimePeriod.TODAY -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        TimePeriod.WEEK -> System.currentTimeMillis() - 7 * day
        TimePeriod.MONTH -> System.currentTimeMillis() - 30 * day
        TimePeriod.YEAR -> System.currentTimeMillis() - 365 * day
    }
}

private val rupiahFmt: NumberFormat = NumberFormat.getInstance(Locale.forLanguageTag("id-ID"))
fun rp(v: Long) = "Rp " + rupiahFmt.format(v)
fun csvCell(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

fun JSONObject.toOrder(): NativeOrder {
    val created = str("created_at")
    return NativeOrder(
        id = str("id"),
        orderId = str("order_id", str("id", "GS-00")),
        productName = str("product_name", "Produk"),
        packageName = str("package_name", "-"),
        totalAmount = if (!isNull("total_payment")) optSafeLong("total_payment") else optSafeLong("total_amount"),
        whatsapp = str("customer_whatsapp", "-"),
        status = str("status", "Pending"),
        createdAt = created,
        createdMillis = parseIsoMillis(created),
        accountData = str("account_data")
    )
}

/* ============================================================
   KOMPONEN UI BERSAMA
   ============================================================ */
@Composable
fun gsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = InkWhite, unfocusedTextColor = InkWhite,
    focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder
)

@Composable
fun GsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = BorderStroke(1.dp, LineBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) { Column(modifier = Modifier.padding(12.dp), content = content) }
}

@Composable
fun PeriodTabs(selected: TimePeriod, onSelect: (TimePeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceDark, CircleShape).padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TimePeriod.entries.forEach { p ->
            val sel = selected == p
            Box(
                modifier = Modifier.weight(1f)
                    .background(if (sel) InkWhite else Color.Transparent, CircleShape)
                    .clickable { onSelect(p) }.padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) { Text(p.label, color = if (sel) BgDark else InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/* ============================================================
   LOGIN SCREEN (Supabase Auth)
   ============================================================ */
@Composable
fun NativeLoginScreen(
    api: SupabaseApi,
    canBiometric: Boolean,
    onBiometricRequest: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(BgDark).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
                .background(CardDark, RoundedCornerShape(20.dp))
                .border(1.dp, LineBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Box(Modifier.size(54.dp).background(InkWhite, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("G", color = BgDark, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Text("GIVASTORE ADMIN", color = InkWhite, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Pusat Kendali Android Native", color = InkDim, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))

            if (canBiometric) {
                Button(
                    onClick = onBiometricRequest,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                ) {
                    Icon(Icons.Default.Fingerprint, null, tint = InkWhite)
                    Spacer(Modifier.width(10.dp))
                    Text("Masuk Sidik Jari", color = InkWhite, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("ATAU PASSWORD", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() }, singleLine = true,
                label = { Text("Email Admin") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = gsFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = gsFieldColors()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = BadgeRed, fontSize = 11.sp)
            }
            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !loading,
                onClick = {
                    if (email.isBlank() || password.isBlank()) { error = "Email dan password wajib diisi"; return@Button }
                    loading = true; error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.signIn(email, password) }
                            onAuthSuccess()
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { error = e.message ?: "Login gagal" }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = BgDark, strokeWidth = 2.dp)
                else Text("Masuk", color = BgDark, fontWeight = FontWeight.Black)
            }
        }
    }
}

/* ============================================================
   DASHBOARD UTAMA
   ============================================================ */
@Composable
fun NativeFullDashboard(
    api: SupabaseApi,
    onLogout: () -> Unit,
    onDispatchWa: (String, String) -> Unit,
    onShareCsv: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    var selectedNav by remember { mutableIntStateOf(0) }
    var orders by remember { mutableStateOf<List<NativeOrder>>(emptyList()) }
    var products by remember { mutableStateOf<List<NativeProduct>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<NativeExpense>>(emptyList()) }
    var categories by remember { mutableStateOf<List<NativeCategory>>(emptyList()) }
    var promos by remember { mutableStateOf<List<NativePromo>>(emptyList()) }
    var popups by remember { mutableStateOf<List<NativePopup>>(emptyList()) }
    var logoUrl by remember { mutableStateOf("") }
    var banner1 by remember { mutableStateOf("") }
    var banner2 by remember { mutableStateOf("") }
    var banner3 by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    var orderPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var finPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var statusFilter by remember { mutableStateOf("") }
    var orderSearch by remember { mutableStateOf("") }
    var productSearch by remember { mutableStateOf("") }

    var detailOrder by remember { mutableStateOf<NativeOrder?>(null) }
    var showAddProduct by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var showAddPromo by remember { mutableStateOf(false) }
    var showAddPopup by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    // Tiap tabel dimuat terpisah: satu gagal tidak menggagalkan yang lain.
    fun reloadAllData() {
        scope.launch {
            isLoading = true
            val errors = mutableListOf<String>()
            fun <T> safe(label: String, block: () -> T): T? =
                try { block() } catch (e: Exception) { errors.add("$label: ${e.message}"); null }

            withContext(Dispatchers.IO) {
                safe("Pesanan") { api.get("orders?select=*&order=created_at.desc&limit=1000").objects().map { it.toOrder() } }
            }?.let { orders = it }

            withContext(Dispatchers.IO) {
                safe("Produk") {
                    api.get("products?select=*&order=name.asc").objects().map {
                        NativeProduct(it.str("id"), it.str("name", "-"), it.optSafeLong("base_price"),
                            it.optInt("stock", 0), it.optBoolean("is_flash_sale", false))
                    }
                }
            }?.let { products = it }

            withContext(Dispatchers.IO) {
                safe("Pengeluaran") {
                    api.get("expenses?select=*&order=expense_date.desc&limit=500").objects().map {
                        NativeExpense(it.str("id"), it.str("description", "-"), it.str("category", "Operasional"),
                            it.optSafeLong("amount"), it.str("expense_date"))
                    }
                }
            }?.let { expenses = it }

            withContext(Dispatchers.IO) {
                safe("Kategori") {
                    api.get("categories?select=*&order=name.asc").objects().map { NativeCategory(it.str("id"), it.str("name", "-")) }
                }
            }?.let { categories = it }

            withContext(Dispatchers.IO) {
                safe("Promo") {
                    api.get("promo_codes?select=*&order=created_at.desc").objects().map {
                        NativePromo(it.str("id"), it.str("code", "-"), it.str("discount_type", "percentage"), it.optSafeLong("discount_value"))
                    }
                }
            }?.let { promos = it }

            withContext(Dispatchers.IO) {
                safe("Popup") {
                    api.get("site_popups?select=*&order=created_at.desc").objects().map {
                        NativePopup(it.str("id"), it.str("title", "-"), it.str("message", "-"), it.optBoolean("is_active", true))
                    }
                }
            }?.let { popups = it }

            withContext(Dispatchers.IO) { safe("Pengaturan") { api.get("site_settings?select=*").objects() } }?.forEach {
                when (it.str("key")) {
                    "logo_url" -> logoUrl = it.str("value")
                    "carousel_banner_1" -> banner1 = it.str("value")
                    "carousel_banner_2" -> banner2 = it.str("value")
                    "carousel_banner_3" -> banner3 = it.str("value")
                }
            }

            isLoading = false
            if (errors.isNotEmpty()) toast("Gagal memuat — " + errors.joinToString("; ").take(160))
        }
    }

    // Aksi tulis aman: error ditangkap, tidak crash.
    fun act(okMsg: String? = null, block: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                okMsg?.let { toast(it) }
                reloadAllData()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { toast("Gagal: ${e.message}") }
        }
    }

    fun saveSetting(k: String, v: String) =
        api.post("site_settings?on_conflict=key", JSONObject().put("key", k).put("value", v), "resolution=merge-duplicates")

    LaunchedEffect(Unit) { reloadAllData() }

    // Perhitungan nyata berdasarkan tanggal
    val paidOrders = orders.filter { it.status.equals("Paid", true) }
    val omzet = paidOrders.filter { it.createdMillis >= periodCutoff(orderPeriod) }.sumOf { it.totalAmount }
    val finIncome = paidOrders.filter { it.createdMillis >= periodCutoff(finPeriod) }.sumOf { it.totalAmount }
    val periodExpenses = expenses.filter { parseIsoMillis(it.date) >= periodCutoff(finPeriod) }
    val finExpense = periodExpenses.sumOf { it.amount }
    val finProfit = finIncome - finExpense

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            NavigationBar(containerColor = CardDark, tonalElevation = 0.dp) {
                listOf(
                    Icons.Default.Receipt to "Pesanan",
                    Icons.Default.AccountBalanceWallet to "Keuangan",
                    Icons.Default.Inventory2 to "Produk",
                    Icons.Default.ViewCarousel to "Konten",
                    Icons.Default.MoreHoriz to "Menu"
                ).forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = selectedNav == i, onClick = { selectedNav = i },
                        icon = { Icon(icon, null) }, label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedNav == 2) FloatingActionButton(
                onClick = { showAddProduct = true }, containerColor = InkWhite, contentColor = BgDark
            ) { Icon(Icons.Default.Add, "Tambah Produk") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("GIVASTORE ADMIN", color = InkWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Pusat Kendali Lengkap", color = InkDim, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { reloadAllData() }) { Icon(Icons.Default.Refresh, null, tint = InkWhite) }
                    Box(Modifier.background(SurfaceDark, CircleShape).border(1.dp, LineBorder, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("● LIVE", color = WaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            when (selectedNav) {
                /* ---------------- TAB PESANAN ---------------- */
                0 -> {
                    GsCard(Modifier.fillMaxWidth()) {
                        PeriodTabs(orderPeriod) { orderPeriod = it }
                        Spacer(Modifier.height(12.dp))
                        Text("Total Omzet ${orderPeriod.label}", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(rp(omzet), color = InkWhite, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = orderSearch, onValueChange = { orderSearch = it }, singleLine = true,
                        placeholder = { Text("Cari ID, Nama, No. WA...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = InkDim) },
                        modifier = Modifier.fillMaxWidth(), colors = gsFieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("" to "Semua", "Pending" to "Pending", "Paid" to "Lunas").forEach { (k, lbl) ->
                            val sel = statusFilter == k
                            Box(
                                Modifier.background(if (sel) InkWhite else SurfaceDark, CircleShape)
                                    .border(1.dp, LineBorder, CircleShape)
                                    .clickable { statusFilter = k }.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) { Text(lbl, color = if (sel) BgDark else InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            val csv = buildString {
                                appendLine("Order ID,Produk,Paket,Total Bayar,WhatsApp,Status,Tanggal")
                                orders.forEach { o ->
                                    appendLine(listOf(csvCell(o.orderId), csvCell(o.productName), csvCell(o.packageName),
                                        o.totalAmount.toString(), csvCell(o.whatsapp), csvCell(o.status), csvCell(o.createdAt)).joinToString(","))
                                }
                            }
                            onShareCsv(csv)
                        }) { Icon(Icons.Default.Share, "Ekspor CSV", tint = InkWhite) }
                    }
                    Spacer(Modifier.height(8.dp))

                    val filtered = orders.filter { o ->
                        (statusFilter.isEmpty() || o.status.equals(statusFilter, true)) &&
                            (orderSearch.isEmpty() || o.orderId.contains(orderSearch, true) ||
                                o.productName.contains(orderSearch, true) || o.whatsapp.contains(orderSearch))
                    }
                    when {
                        isLoading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = InkWhite)
                        }
                        filtered.isEmpty() -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Tidak ada pesanan yang sesuai", color = InkDim, fontSize = 12.sp)
                        }
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { order ->
                                val isPaid = order.status.equals("Paid", true)
                                GsCard(Modifier.fillMaxWidth().clickable { detailOrder = order }) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("#${order.orderId}", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Box(Modifier.background((if (isPaid) WaGreen else BadgeYellow).copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text(if (isPaid) "LUNAS" else "PENDING", color = if (isPaid) WaGreen else BadgeYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(order.productName + if (order.packageName.isNotEmpty() && order.packageName != "-") " - ${order.packageName}" else "",
                                        color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(rp(order.totalAmount), color = InkDim, fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { detailOrder = order },
                                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                            shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).height(34.dp)
                                        ) { Text("Detail Akun", color = InkWhite, fontSize = 11.sp) }
                                        Button(
                                            onClick = {
                                                onDispatchWa(order.whatsapp,
                                                    "Halo Kak, terima kasih telah order ${order.productName} di GivaStore! Pesanan #${order.orderId} sedang kami proses.")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
                                            shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f).height(34.dp)
                                        ) { Text("Chat WA", color = BgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }

                /* ---------------- TAB KEUANGAN ---------------- */
                1 -> {
                    PeriodTabs(finPeriod) { finPeriod = it }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GsCard(Modifier.weight(1f)) {
                            Text("Pemasukan", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(rp(finIncome), color = WaGreen, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                        GsCard(Modifier.weight(1f)) {
                            Text("Pengeluaran", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(rp(finExpense), color = BadgeRed, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GsCard(Modifier.fillMaxWidth()) {
                        Text("Laba Bersih (${finPeriod.label})", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(rp(finProfit), color = if (finProfit >= 0) WaGreen else BadgeRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("RIWAYAT PENGELUARAN (${periodExpenses.size})", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { showAddExpense = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Text("Catat", fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(periodExpenses, key = { it.id }) { exp ->
                            GsCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(exp.description, color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${exp.category} • ${exp.date}", color = InkDim, fontSize = 11.sp)
                                    }
                                    Text("-${rp(exp.amount)}", color = BadgeRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = {
                                        confirm = "Hapus pengeluaran \"${exp.description}\"?" to {
                                            act("Dihapus") { api.delete("expenses?id=eq.${Uri.encode(exp.id)}") }
                                        }
                                    }) { Icon(Icons.Default.Delete, null, tint = InkDim, modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }
                    }
                }

                /* ---------------- TAB PRODUK ---------------- */
                2 -> {
                    OutlinedTextField(
                        value = productSearch, onValueChange = { productSearch = it }, singleLine = true,
                        placeholder = { Text("Cari Produk...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = InkDim) },
                        modifier = Modifier.fillMaxWidth(), colors = gsFieldColors()
                    )
                    Spacer(Modifier.height(10.dp))
                    val filteredProducts = products.filter { productSearch.isEmpty() || it.name.contains(productSearch, true) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredProducts, key = { it.id }) { prod ->
                            GsCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(prod.name, color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${rp(prod.basePrice)} • Stok: ${prod.stock}", color = InkDim, fontSize = 11.sp)
                                        if (prod.isFlashSale) Text("⚡ FLASH SALE", color = BadgeYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    // Stok diubah bertahap, tidak menimpa angka asli
                                    IconButton(onClick = {
                                        act { api.patch("products?id=eq.${Uri.encode(prod.id)}", JSONObject().put("stock", (prod.stock - 1).coerceAtLeast(0))) }
                                    }) { Icon(Icons.Default.Remove, "Kurangi stok", tint = InkWhite) }
                                    IconButton(onClick = {
                                        act { api.patch("products?id=eq.${Uri.encode(prod.id)}", JSONObject().put("stock", prod.stock + 1)) }
                                    }) { Icon(Icons.Default.Add, "Tambah stok", tint = InkWhite) }
                                }
                            }
                        }
                    }
                }

                /* ---------------- TAB KONTEN ---------------- */
                3 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        item {
                            GsCard(Modifier.fillMaxWidth()) {
                                Text("LOGO TOKO", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = logoUrl, onValueChange = { logoUrl = it }, singleLine = true,
                                    placeholder = { Text("URL Logo https://...", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(), colors = gsFieldColors()
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { act("Logo disimpan") { saveSetting("logo_url", logoUrl) } },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) { Text("Simpan Logo", fontSize = 11.sp, color = InkWhite) }
                            }
                        }
                        item {
                            GsCard(Modifier.fillMaxWidth()) {
                                Text("BANNER BERANDA (3 SLOT)", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                listOf(Triple("Banner 1", banner1, 1), Triple("Banner 2", banner2, 2), Triple("Banner 3", banner3, 3)).forEach { (lbl, v, n) ->
                                    OutlinedTextField(
                                        value = v, singleLine = true,
                                        onValueChange = { when (n) { 1 -> banner1 = it; 2 -> banner2 = it; else -> banner3 = it } },
                                        placeholder = { Text("$lbl https://...", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(), colors = gsFieldColors()
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                Button(
                                    onClick = {
                                        act("Banner disimpan") {
                                            saveSetting("carousel_banner_1", banner1)
                                            saveSetting("carousel_banner_2", banner2)
                                            saveSetting("carousel_banner_3", banner3)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) { Text("Simpan Banner", fontSize = 11.sp, color = InkWhite) }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("POPUP PENGUMUMAN", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { showAddPopup = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                    shape = RoundedCornerShape(8.dp), modifier = Modifier.height(30.dp)
                                ) { Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Text("Buat", fontSize = 10.sp) }
                            }
                        }
                        items(popups, key = { it.id }) { pop ->
                            GsCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pop.title, color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(pop.message, color = InkDim, fontSize = 10.sp)
                                    }
                                    Switch(checked = pop.isActive, onCheckedChange = { active ->
                                        act { api.patch("site_popups?id=eq.${Uri.encode(pop.id)}", JSONObject().put("is_active", active)) }
                                    })
                                }
                            }
                        }
                    }
                }

                /* ---------------- TAB MENU ---------------- */
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        item { Text("PENGATURAN MASTER DATA", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        item {
                            GsCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Kategori Produk (${categories.size})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { showAddCategory = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null, tint = InkWhite) }
                                }
                                Spacer(Modifier.height(8.dp))
                                categories.forEach { cat ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(cat.name, color = InkDim, fontSize = 12.sp)
                                        IconButton(onClick = {
                                            confirm = "Hapus kategori ${cat.name}?" to {
                                                act("Dihapus") { api.delete("categories?id=eq.${Uri.encode(cat.id)}") }
                                            }
                                        }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Delete, null, tint = BadgeRed, modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        }
                        item {
                            GsCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Kode Promo (${promos.size})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { showAddPromo = true }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Add, null, tint = InkWhite) }
                                }
                                Spacer(Modifier.height(8.dp))
                                promos.forEach { prm ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text(prm.code, color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(if (prm.discountType == "percentage") "${prm.discountValue}%" else rp(prm.discountValue), color = InkDim, fontSize = 10.sp)
                                        }
                                        IconButton(onClick = {
                                            confirm = "Hapus promo ${prm.code}?" to {
                                                act("Dihapus") { api.delete("promo_codes?id=eq.${Uri.encode(prm.id)}") }
                                            }
                                        }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Delete, null, tint = BadgeRed, modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        }
                        item {
                            val customers = orders.groupBy { it.whatsapp }.filter { it.key.isNotBlank() && it.key != "-" }
                            GsCard(Modifier.fillMaxWidth()) {
                                Text("Daftar Pelanggan (${customers.size})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                customers.entries.take(15).forEach { (wa, ords) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(wa, color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("${ords.size}x Order", color = InkDim, fontSize = 10.sp)
                                        }
                                        Text(rp(ords.filter { it.status.equals("Paid", true) }.sumOf { it.totalAmount }),
                                            color = WaGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onLogout,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, null, tint = BadgeRed)
                                Spacer(Modifier.width(8.dp))
                                Text("Keluar dari Admin", color = BadgeRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    /* ---------------- DIALOG KONFIRMASI HAPUS ---------------- */
    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = CardDark,
            title = { Text("Konfirmasi", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = { Text(msg, color = InkDim) },
            confirmButton = {
                Button(onClick = { action(); confirm = null }, colors = ButtonDefaults.buttonColors(containerColor = BadgeRed)) {
                    Text("Hapus", color = InkWhite, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal", color = InkDim) } }
        )
    }

    /* ---------------- DIALOG DETAIL PESANAN ---------------- */
    detailOrder?.let { order ->
        var credentialText by remember(order.id) {
            mutableStateOf(order.accountData.ifEmpty {
                "Halo Kak, terima kasih telah order ${order.productName} di GivaStore! Berikut kredensial akun Anda:\n\nEmail: \nPassword: \nGaransi: Penuh sesuai durasi paket."
            })
        }
        AlertDialog(
            onDismissRequest = { detailOrder = null }, containerColor = CardDark,
            title = { Text("Pesanan #${order.orderId}", color = InkWhite, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("${order.productName} (${order.packageName})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Total: ${rp(order.totalAmount)}", color = WaGreen, fontSize = 12.sp)
                    Text("WhatsApp: ${order.whatsapp}", color = InkDim, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Kredensial / Pesan Akun:", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = credentialText, onValueChange = { credentialText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp), colors = gsFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onDispatchWa(order.whatsapp, credentialText); detailOrder = null },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreen)
                ) { Text("Kirim ke WA", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                if (!order.status.equals("Paid", true)) {
                    Button(
                        onClick = {
                            act("Ditandai lunas") { api.patch("orders?id=eq.${Uri.encode(order.id)}", JSONObject().put("status", "Paid")) }
                            detailOrder = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                    ) { Text("Tandai Lunas", color = InkWhite) }
                }
            }
        )
    }

    /* ---------------- DIALOG TAMBAH PRODUK ---------------- */
    if (showAddProduct) {
        var pName by remember { mutableStateOf("") }
        var pSlug by remember { mutableStateOf("") }
        var pPrice by remember { mutableStateOf("") }
        var pStock by remember { mutableStateOf("50") }
        AlertDialog(
            onDismissRequest = { showAddProduct = false }, containerColor = CardDark,
            title = { Text("Tambah Produk Baru", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(pName, { pName = it }, label = { Text("Nama Produk") }, singleLine = true, colors = gsFieldColors())
                    OutlinedTextField(pSlug, { pSlug = it.lowercase().replace(" ", "-") }, label = { Text("Slug (cth: netflix)") }, singleLine = true, colors = gsFieldColors())
                    OutlinedTextField(pPrice, { pPrice = it.filter(Char::isDigit) }, label = { Text("Harga (Rp)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = gsFieldColors())
                    OutlinedTextField(pStock, { pStock = it.filter(Char::isDigit) }, label = { Text("Stok") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = gsFieldColors())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pName.isBlank() || pSlug.isBlank()) { toast("Nama dan slug wajib diisi"); return@Button }
                        act("Produk ditambahkan") {
                            api.post("products", JSONObject().put("name", pName).put("slug", pSlug)
                                .put("base_price", pPrice.toLongOrNull() ?: 0L).put("stock", pStock.toIntOrNull() ?: 50))
                        }
                        showAddProduct = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Simpan", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddProduct = false }) { Text("Batal", color = InkDim) } }
        )
    }

    /* ---------------- DIALOG CATAT PENGELUARAN ---------------- */
    if (showAddExpense) {
        var eDesc by remember { mutableStateOf("") }
        var eAmount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddExpense = false }, containerColor = CardDark,
            title = { Text("Catat Pengeluaran", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(eDesc, { eDesc = it }, label = { Text("Deskripsi (cth: Restok Akun)") }, singleLine = true, colors = gsFieldColors())
                    OutlinedTextField(eAmount, { eAmount = it.filter(Char::isDigit) }, label = { Text("Nominal (Rp)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = gsFieldColors())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = eAmount.toLongOrNull()
                        if (eDesc.isBlank() || amount == null || amount <= 0) { toast("Isi deskripsi dan nominal yang valid"); return@Button }
                        act("Pengeluaran dicatat") {
                            api.post("expenses", JSONObject().put("description", eDesc).put("category", "Operasional")
                                .put("amount", amount)
                                .put("expense_date", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())))
                        }
                        showAddExpense = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Simpan", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddExpense = false }) { Text("Batal", color = InkDim) } }
        )
    }

    /* ---------------- DIALOG TAMBAH KATEGORI ---------------- */
    if (showAddCategory) {
        var catName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategory = false }, containerColor = CardDark,
            title = { Text("Tambah Kategori", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = { OutlinedTextField(catName, { catName = it }, label = { Text("Nama Kategori") }, singleLine = true, colors = gsFieldColors()) },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isBlank()) { toast("Nama kategori wajib diisi"); return@Button }
                        act("Kategori ditambahkan") { api.post("categories", JSONObject().put("name", catName)) }
                        showAddCategory = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Tambah", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddCategory = false }) { Text("Batal", color = InkDim) } }
        )
    }

    /* ---------------- DIALOG TAMBAH PROMO ---------------- */
    if (showAddPromo) {
        var prmCode by remember { mutableStateOf("") }
        var prmVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPromo = false }, containerColor = CardDark,
            title = { Text("Tambah Kode Promo", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prmCode, { prmCode = it.uppercase().trim() }, label = { Text("Kode (cth: HEMAT20)") }, singleLine = true, colors = gsFieldColors())
                    OutlinedTextField(prmVal, { prmVal = it.filter(Char::isDigit) }, label = { Text("Diskon Persen (%)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = gsFieldColors())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pct = prmVal.toLongOrNull()
                        if (prmCode.isBlank() || pct == null || pct !in 1..100) { toast("Kode wajib diisi, diskon 1–100%"); return@Button }
                        act("Promo dibuat") {
                            api.post("promo_codes", JSONObject().put("code", prmCode).put("discount_type", "percentage")
                                .put("discount_value", pct).put("min_order_amount", 0L).put("is_active", true))
                        }
                        showAddPromo = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Buat Promo", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddPromo = false }) { Text("Batal", color = InkDim) } }
        )
    }

    /* ---------------- DIALOG TAMBAH POPUP ---------------- */
    if (showAddPopup) {
        var popTitle by remember { mutableStateOf("") }
        var popMsg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPopup = false }, containerColor = CardDark,
            title = { Text("Buat Popup Pengumuman", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(popTitle, { popTitle = it }, label = { Text("Judul Popup") }, singleLine = true, colors = gsFieldColors())
                    OutlinedTextField(popMsg, { popMsg = it }, label = { Text("Pesan Pengumuman") }, colors = gsFieldColors())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (popTitle.isBlank() || popMsg.isBlank()) { toast("Judul dan pesan wajib diisi"); return@Button }
                        act("Popup dipasang") {
                            api.post("site_popups", JSONObject().put("title", popTitle).put("message", popMsg)
                                .put("is_active", true).put("popup_type", "announcement")
                                .put("position", "center").put("show_on_page", "all"))
                        }
                        showAddPopup = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Pasang Popup", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddPopup = false }) { Text("Batal", color = InkDim) } }
        )
    }
}
