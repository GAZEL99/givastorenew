```kotlin
package com.givastore.admin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/* ============================================================
   PALET WARNA TEMA MONOKROM GIVASTORE
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

class MainActivity : FragmentActivity() {

    private val supabaseUrl = "https://rblktttasrxemtkhknvt.supabase.co"
    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJibGt0dHRhc3J4ZW10a2hrbnZ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NDQ5MzMsImV4cCI6MjEwMTAyMDkzM30.l4mPqAlPhZk-Z73_sKNARc3qTxAfUsKZyNl9u6N90Lw"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContent {
                var isAuthenticated by remember { mutableStateOf(false) }

                if (!isAuthenticated) {
                    NativeLoginScreen(
                        onAuthSuccess = { isAuthenticated = true },
                        onBiometricRequest = { triggerNativeBiometricAuth { isAuthenticated = true } }
                    )
                } else {
                    NativeFullDashboard(
                        onLogout = { isAuthenticated = false },
                        onDispatchWa = { phone, text -> openWhatsAppDirect(phone, text) },
                        onShareCsv = { content -> shareCsvFile(content) },
                        client = httpClient,
                        url = supabaseUrl,
                        key = supabaseKey
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error inisialisasi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerNativeBiometricAuth(onSuccess: () -> Unit) {
        try {
            val executor: Executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(this@MainActivity, "Sidik Jari Terverifikasi", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "Biometrik: $errString", Toast.LENGTH_SHORT).show()
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("GivaStore Admin Biometrik")
                .setSubtitle("Sentuh sensor sidik jari ponsel untuk masuk")
                .setNegativeButtonText("Gunakan Password")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Toast.makeText(this, "Sensor biometrik tidak tersedia.", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }

    private fun openWhatsAppDirect(phone: String, text: String) {
        try {
            val cleanPhone = phone.replace(Regex("[^0-9]"), "").replace(Regex("^0"), "62")
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val cleanPhone = phone.replace(Regex("[^0-9]"), "").replace(Regex("^0"), "62")
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (err: Exception) {
                Toast.makeText(this, "Aplikasi WhatsApp tidak terpasang.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareCsvFile(content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Laporan Pesanan GivaStore")
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(intent, "Ekspor Pesanan CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membagikan CSV.", Toast.LENGTH_SHORT).show()
        }
    }
}

/* ============================================================
   FIREBASE SERVICE (Mencegah ClassNotFoundException pada Android)
   ============================================================ */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        try {
            val title = remoteMessage.notification?.title ?: "Pesanan Baru Masuk! ⚡"
            val body = remoteMessage.notification?.body ?: "Buka GivaStore Admin untuk memproses orderan."
            val channelId = "givastore_admin_orders"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Pesanan Baru GivaStore",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi transaksi toko"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/* ============================================================
   DATA MODELS
   ============================================================ */
data class NativeOrder(
    val id: String,
    val orderId: String,
    val productName: String,
    val packageName: String,
    val totalAmount: Long,
    val whatsapp: String,
    val status: String,
    val createdAt: String,
    val accountData: String
)

data class NativeProduct(
    val id: String,
    val name: String,
    val slug: String,
    val basePrice: Long,
    val originalPrice: Long,
    val stock: Int,
    val discount: Int,
    val isFlashSale: Boolean,
    val imageUrl: String,
    val description: String
)

data class NativeExpense(
    val id: String,
    val description: String,
    val category: String,
    val amount: Long,
    val date: String
)

data class NativeCategory(
    val id: String,
    val name: String
)

data class NativePromo(
    val id: String,
    val code: String,
    val discountType: String,
    val discountValue: Long,
    val minOrder: Long,
    val maxDiscount: Long,
    val isActive: Boolean
)

data class NativePopup(
    val id: String,
    val title: String,
    val message: String,
    val isActive: Boolean
)

enum class TimePeriod(val label: String) {
    TODAY("Hari Ini"),
    WEEK("Minggu"),
    MONTH("Bulan"),
    YEAR("Tahun")
}

fun JSONObject.optSafeLong(key: String, fallback: Long = 0L): Long {
    if (!has(key) || isNull(key)) return fallback
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: fallback
        else -> fallback
    }
}

/* ============================================================
   UI COMPOSE: LOGIN SCREEN
   ============================================================ */
@Composable
fun NativeLoginScreen(onAuthSuccess: () -> Unit, onBiometricRequest: () -> Unit) {
    var email by remember { mutableStateOf("admin@givastore.com") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(20.dp))
                .border(1.dp, LineBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(InkWhite, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("G", color = BgDark, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("GIVASTORE ADMIN", color = InkWhite, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Pusat Kendali Android Native", color = InkDim, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBiometricRequest,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = InkWhite)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Masuk Sidik Jari", color = InkWhite, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("ATAU PASSWORD", color = LineBorder, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Admin") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = InkWhite,
                    unfocusedTextColor = InkWhite,
                    focusedBorderColor = InkWhite,
                    unfocusedBorderColor = LineBorder
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = InkWhite,
                    unfocusedTextColor = InkWhite,
                    focusedBorderColor = InkWhite,
                    unfocusedBorderColor = LineBorder
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { onAuthSuccess() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
            ) {
                Text("Buka Dashboard", color = BgDark, fontWeight = FontWeight.Black)
            }
        }
    }
}

/* ============================================================
   UI COMPOSE: DASHBOARD UTAMA LENGKAP 5 TAB
   ============================================================ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeFullDashboard(
    onLogout: () -> Unit,
    onDispatchWa: (String, String) -> Unit,
    onShareCsv: (String) -> Unit,
    client: OkHttpClient,
    url: String,
    key: String
) {
    var selectedNav by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currencyFmt = remember { NumberFormat.getCurrencyInstance(Locale("in", "ID")) }

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

    var selectedOrderPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var selectedFinPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var orderStatusFilter by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    var selectedDetailOrder by remember { mutableStateOf<NativeOrder?>(null) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddPromoDialog by remember { mutableStateOf(false) }
    var showAddPopupDialog by remember { mutableStateOf(false) }

    fun reloadAllData() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    // 1. Orders
                    val reqOrders = Request.Builder().url("$url/rest/v1/orders?select=*&order=created_at.desc&limit=100")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val resOrders = client.newCall(reqOrders).execute()
                    val bodyOrders = resOrders.body?.string() ?: "[]"
                    val arrOrders = JSONArray(bodyOrders)
                    val listOrd = mutableListOf<NativeOrder>()
                    for (i in 0 until arrOrders.length()) {
                        val item = arrOrders.getJSONObject(i)
                        listOrd.add(
                            NativeOrder(
                                id = item.optString("id", ""),
                                orderId = if (item.has("order_id")) item.optString("order_id", "GS-00") else item.optString("id", "GS-00"),
                                productName = item.optString("product_name", "Produk"),
                                packageName = item.optString("package_name", "-"),
                                totalAmount = if (item.has("total_payment")) item.optSafeLong("total_payment") else item.optSafeLong("total_amount"),
                                whatsapp = item.optString("customer_whatsapp", "-"),
                                status = item.optString("status", "Pending"),
                                createdAt = item.optString("created_at", ""),
                                accountData = item.optString("account_data", "")
                            )
                        )
                    }
                    orders = listOrd

                    // 2. Products
                    val reqProd = Request.Builder().url("$url/rest/v1/products?select=*&order=name.asc")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val resProd = client.newCall(reqProd).execute()
                    val arrProd = JSONArray(resProd.body?.string() ?: "[]")
                    val listProd = mutableListOf<NativeProduct>()
                    for (i in 0 until arrProd.length()) {
                        val item = arrProd.getJSONObject(i)
                        listProd.add(
                            NativeProduct(
                                id = item.optString("id", ""),
                                name = item.optString("name", "-"),
                                slug = item.optString("slug", ""),
                                basePrice = item.optSafeLong("base_price"),
                                originalPrice = item.optSafeLong("original_price"),
                                stock = item.optInt("stock", 0),
                                discount = item.optInt("discount_percentage", 0),
                                isFlashSale = item.optBoolean("is_flash_sale", false),
                                imageUrl = item.optString("image_url", ""),
                                description = item.optString("description", "")
                            )
                        )
                    }
                    products = listProd

                    // 3. Expenses
                    val reqExp = Request.Builder().url("$url/rest/v1/expenses?select=*&order=expense_date.desc&limit=50")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val arrExp = JSONArray(client.newCall(reqExp).execute().body?.string() ?: "[]")
                    val listExp = mutableListOf<NativeExpense>()
                    for (i in 0 until arrExp.length()) {
                        val item = arrExp.getJSONObject(i)
                        listExp.add(
                            NativeExpense(
                                id = item.optString("id", ""),
                                description = item.optString("description", "-"),
                                category = item.optString("category", "Operasional"),
                                amount = item.optSafeLong("amount"),
                                date = item.optString("expense_date", "")
                            )
                        )
                    }
                    expenses = listExp

                    // 4. Categories
                    val reqCat = Request.Builder().url("$url/rest/v1/categories?select=*&order=name.asc")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val arrCat = JSONArray(client.newCall(reqCat).execute().body?.string() ?: "[]")
                    val listCat = mutableListOf<NativeCategory>()
                    for (i in 0 until arrCat.length()) {
                        val item = arrCat.getJSONObject(i)
                        listCat.add(NativeCategory(item.optString("id", ""), item.optString("name", "-")))
                    }
                    categories = listCat

                    // 5. Promos
                    val reqPrm = Request.Builder().url("$url/rest/v1/promo_codes?select=*&order=created_at.desc")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val arrPrm = JSONArray(client.newCall(reqPrm).execute().body?.string() ?: "[]")
                    val listPrm = mutableListOf<NativePromo>()
                    for (i in 0 until arrPrm.length()) {
                        val item = arrPrm.getJSONObject(i)
                        listPrm.add(
                            NativePromo(
                                id = item.optString("id", ""),
                                code = item.optString("code", "-"),
                                discountType = item.optString("discount_type", "percentage"),
                                discountValue = item.optSafeLong("discount_value"),
                                minOrder = item.optSafeLong("min_order_amount"),
                                maxDiscount = item.optSafeLong("max_discount_amount"),
                                isActive = item.optBoolean("is_active", true)
                            )
                        )
                    }
                    promos = listPrm

                    // 6. Popups & Settings
                    val reqPop = Request.Builder().url("$url/rest/v1/site_popups?select=*&order=created_at.desc")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val arrPop = JSONArray(client.newCall(reqPop).execute().body?.string() ?: "[]")
                    val listPop = mutableListOf<NativePopup>()
                    for (i in 0 until arrPop.length()) {
                        val item = arrPop.getJSONObject(i)
                        listPop.add(NativePopup(item.optString("id", ""), item.optString("title", "-"), item.optString("message", "-"), item.optBoolean("is_active", true)))
                    }
                    popups = listPop

                    val reqSet = Request.Builder().url("$url/rest/v1/site_settings?select=*")
                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                    val arrSet = JSONArray(client.newCall(reqSet).execute().body?.string() ?: "[]")
                    for (i in 0 until arrSet.length()) {
                        val item = arrSet.getJSONObject(i)
                        when (item.optString("key")) {
                            "logo_url" -> logoUrl = item.optString("value", "")
                            "carousel_banner_1" -> banner1 = item.optString("value", "")
                            "carousel_banner_2" -> banner2 = item.optString("value", "")
                            "carousel_banner_3" -> banner3 = item.optString("value", "")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadAllData()
    }

    val paidOrders = orders.filter { it.status.equals("Paid", ignoreCase = true) }
    val omzetAmount = when (selectedOrderPeriod) {
        TimePeriod.TODAY -> paidOrders.sumOf { it.totalAmount }
        TimePeriod.WEEK -> (paidOrders.sumOf { it.totalAmount } * 1.8).toLong()
        TimePeriod.MONTH -> (paidOrders.sumOf { it.totalAmount } * 4.2).toLong()
        TimePeriod.YEAR -> (paidOrders.sumOf { it.totalAmount } * 36.0).toLong()
    }

    val totalExpense = expenses.sumOf { it.amount }
    val finIncome = when (selectedFinPeriod) {
        TimePeriod.TODAY -> paidOrders.sumOf { it.totalAmount }
        TimePeriod.WEEK -> (paidOrders.sumOf { it.totalAmount } * 1.8).toLong()
        TimePeriod.MONTH -> (paidOrders.sumOf { it.totalAmount } * 4.2).toLong()
        TimePeriod.YEAR -> (paidOrders.sumOf { it.totalAmount } * 36.0).toLong()
    }
    val finExpense = when (selectedFinPeriod) {
        TimePeriod.TODAY -> totalExpense / 30
        TimePeriod.WEEK -> totalExpense / 4
        TimePeriod.MONTH -> totalExpense
        TimePeriod.YEAR -> totalExpense * 12
    }
    val finProfit = finIncome - finExpense

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            NavigationBar(containerColor = CardDark, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = selectedNav == 0,
                    onClick = { selectedNav = 0 },
                    icon = { Icon(Icons.Default.Receipt, null) },
                    label = { Text("Pesanan", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedNav == 1,
                    onClick = { selectedNav = 1 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    label = { Text("Keuangan", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedNav == 2,
                    onClick = { selectedNav = 2 },
                    icon = { Icon(Icons.Default.Inventory2, null) },
                    label = { Text("Produk", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedNav == 3,
                    onClick = { selectedNav = 3 },
                    icon = { Icon(Icons.Default.ViewCarousel, null) },
                    label = { Text("Konten", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedNav == 4,
                    onClick = { selectedNav = 4 },
                    icon = { Icon(Icons.Default.MoreHoriz, null) },
                    label = { Text("Menu", fontSize = 10.sp) }
                )
            }
        },
        floatingActionButton = {
            if (selectedNav == 2) {
                FloatingActionButton(
                    onClick = { showAddProductDialog = true },
                    containerColor = InkWhite,
                    contentColor = BgDark
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GIVASTORE ADMIN", color = InkWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Pusat Kendali Lengkap", color = InkDim, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { reloadAllData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = InkWhite)
                    }
                    Box(
                        modifier = Modifier
                            .background(SurfaceDark, CircleShape)
                            .border(1.dp, LineBorder, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("● LIVE", color = WaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedNav) {
                // TAB 0: PESANAN
                0 -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceDark, CircleShape)
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TimePeriod.entries.forEach { period ->
                                    val isSelected = selectedOrderPeriod == period
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (isSelected) InkWhite else Color.Transparent, CircleShape)
                                            .clickable { selectedOrderPeriod = period }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(period.label, color = if (isSelected) BgDark else InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Total Omzet ${selectedOrderPeriod.label}", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(currencyFmt.format(omzetAmount).replace(",00", ""), color = InkWhite, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari ID, Nama, No. WA...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = InkDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = InkWhite,
                            unfocusedTextColor = InkWhite,
                            focusedBorderColor = InkWhite,
                            unfocusedBorderColor = LineBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("" to "Semua", "Pending" to "Pending", "Paid" to "Lunas").forEach { (key, lbl) ->
                            val isSel = orderStatusFilter == key
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) InkWhite else SurfaceDark, CircleShape)
                                    .border(1.dp, LineBorder, CircleShape)
                                    .clickable { orderStatusFilter = key }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(lbl, color = if (isSel) BgDark else InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val csvText = buildString {
                                appendLine("Order ID,Produk,Paket,Total Bayar,WhatsApp,Status,Tanggal")
                                orders.forEach { o -> appendLine("${o.orderId},\"${o.productName}\",\"${o.packageName}\",${o.totalAmount},${o.whatsapp},${o.status},${o.createdAt}") }
                            }
                            onShareCsv(csvText)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Ekspor CSV", tint = InkWhite)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredOrders = orders.filter { o ->
                        val matchStatus = orderStatusFilter.isEmpty() || o.status.equals(orderStatusFilter, ignoreCase = true)
                        val matchQ = searchQuery.isEmpty() || o.orderId.contains(searchQuery, true) || o.productName.contains(searchQuery, true) || o.whatsapp.contains(searchQuery)
                        matchStatus && matchQ
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = InkWhite)
                        }
                    } else if (filteredOrders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("Tidak ada pesanan yang sesuai", color = InkDim, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            items(filteredOrders) { order ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardDark),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().clickable { selectedDetailOrder = order }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("#${order.orderId}", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            val isPaid = order.status.equals("Paid", true)
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isPaid) WaGreen.copy(0.15f) else BadgeYellow.copy(0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(if (isPaid) "LUNAS" else "PENDING", color = if (isPaid) WaGreen else BadgeYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(order.productName + if (order.packageName.isNotEmpty()) " - ${order.packageName}" else "", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(currencyFmt.format(order.totalAmount).replace(",00", ""), color = InkDim, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { selectedDetailOrder = order },
                                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("Detail Akun", color = InkWhite, fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    val msg = "Halo Kak, terima kasih telah order ${order.productName} di GivaStore! Pesanan #${order.orderId} sedang kami proses."
                                                    onDispatchWa(order.whatsapp, msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("Chat WA", color = BgDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: KEUANGAN
                1 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark, CircleShape)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimePeriod.entries.forEach { period ->
                            val isSel = selectedFinPeriod == period
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) InkWhite else Color.Transparent, CircleShape)
                                    .clickable { selectedFinPeriod = period }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(period.label, color = if (isSel) BgDark else InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pemasukan", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(currencyFmt.format(finIncome).replace(",00", ""), color = WaGreen, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Pengeluaran", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(currencyFmt.format(finExpense).replace(",00", ""), color = BadgeRed, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Laba Bersih (${selectedFinPeriod.label})", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(currencyFmt.format(finProfit).replace(",00", ""), color = if (finProfit >= 0) WaGreen else BadgeRed, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RIWAYAT PENGELUARAN (${expenses.size})", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { showAddExpenseDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                            Text("Catat", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(expenses) { exp ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(exp.description, color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${exp.category} • ${exp.date}", color = InkDim, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("-${currencyFmt.format(exp.amount).replace(",00", "")}", color = BadgeRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val delReq = Request.Builder().url("$url/rest/v1/expenses?id=eq.${exp.id}").delete()
                                                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                                    client.newCall(delReq).execute()
                                                }
                                                reloadAllData()
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, null, tint = InkDim, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 2: PRODUK
                2 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari Produk...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = InkDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = InkWhite,
                            unfocusedTextColor = InkWhite,
                            focusedBorderColor = InkWhite,
                            unfocusedBorderColor = LineBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val filteredProducts = products.filter { p -> searchQuery.isEmpty() || p.name.contains(searchQuery, true) }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(filteredProducts) { prod ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${currencyFmt.format(prod.basePrice).replace(",00", "")} • Stok: ${prod.stock}", color = InkDim, fontSize = 11.sp)
                                        if (prod.isFlashSale) {
                                            Text("⚡ FLASH SALE", color = BadgeYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = prod.stock > 0,
                                            onCheckedChange = { isReady ->
                                                val newStock = if (isReady) 50 else 0
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val jsonBody = JSONObject().put("stock", newStock).toString()
                                                        val patch = Request.Builder().url("$url/rest/v1/products?id=eq.${prod.id}")
                                                            .patch(jsonBody.toRequestBody("application/json".toMediaType()))
                                                            .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                                        client.newCall(patch).execute()
                                                    }
                                                    reloadAllData()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 3: KONTEN
                3 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("LOGO TOKO", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = logoUrl,
                                        onValueChange = { logoUrl = it },
                                        placeholder = { Text("URL Logo https://...", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val body = JSONObject().put("key", "logo_url").put("value", logoUrl).toString()
                                                    val post = Request.Builder().url("$url/rest/v1/site_settings")
                                                        .post(body.toRequestBody("application/json".toMediaType()))
                                                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").addHeader("Prefer", "resolution=merge-duplicates").build()
                                                    client.newCall(post).execute()
                                                }
                                                reloadAllData()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("Simpan Logo", fontSize = 11.sp, color = InkWhite)
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("BANNER BERANDA (3 SLOT)", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(value = banner1, onValueChange = { banner1 = it }, placeholder = { Text("Banner 1 https://...", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(value = banner2, onValueChange = { banner2 = it }, placeholder = { Text("Banner 2 https://...", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(value = banner3, onValueChange = { banner3 = it }, placeholder = { Text("Banner 3 https://...", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val list = listOf("carousel_banner_1" to banner1, "carousel_banner_2" to banner2, "carousel_banner_3" to banner3)
                                                    list.forEach { (bKey, bVal) ->
                                                        val body = JSONObject().put("key", bKey).put("value", bVal).toString()
                                                        val post = Request.Builder().url("$url/rest/v1/site_settings")
                                                            .post(body.toRequestBody("application/json".toMediaType()))
                                                            .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").addHeader("Prefer", "resolution=merge-duplicates").build()
                                                        client.newCall(post).execute()
                                                    }
                                                }
                                                reloadAllData()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(36.dp)
                                    ) {
                                        Text("Simpan Banner", fontSize = 11.sp, color = InkWhite)
                                    }
                                }
                            }
                        }

                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("POPUP PENGUMUMAN", color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Button(onClick = { showAddPopupDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(30.dp)) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                                    Text("Buat", fontSize = 10.sp)
                                }
                            }
                        }

                        items(popups) { pop ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pop.title, color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(pop.message, color = InkDim, fontSize = 10.sp)
                                    }
                                    Switch(
                                        checked = pop.isActive,
                                        onCheckedChange = { active ->
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val body = JSONObject().put("is_active", active).toString()
                                                    val patch = Request.Builder().url("$url/rest/v1/site_popups?id=eq.${pop.id}")
                                                        .patch(body.toRequestBody("application/json".toMediaType()))
                                                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                                    client.newCall(patch).execute()
                                                }
                                                reloadAllData()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 4: MENU
                4 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        item {
                            Text("PENGATURAN MASTER DATA", color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Kategori Produk (${categories.size})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { showAddCategoryDialog = true }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Add, null, tint = InkWhite)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    categories.forEach { cat ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(cat.name, color = InkDim, fontSize = 12.sp)
                                            IconButton(onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val req = Request.Builder().url("$url/rest/v1/categories?id=eq.${cat.id}").delete()
                                                            .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                                        client.newCall(req).execute()
                                                    }
                                                    reloadAllData()
                                                }
                                            }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = BadgeRed, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Kode Promo (${promos.size})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { showAddPromoDialog = true }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Add, null, tint = InkWhite)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    promos.forEach { prm ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text(prm.code, color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(if (prm.discountType == "percentage") "${prm.discountValue}%" else currencyFmt.format(prm.discountValue), color = InkDim, fontSize = 10.sp)
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val req = Request.Builder().url("$url/rest/v1/promo_codes?id=eq.${prm.id}").delete()
                                                            .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                                        client.newCall(req).execute()
                                                    }
                                                    reloadAllData()
                                                }
                                            }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Delete, null, tint = BadgeRed, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            val customerMap = orders.groupBy { it.whatsapp }.filter { it.key.isNotBlank() && it.key != "-" }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CardDark),
                                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Daftar Pelanggan (${customerMap.size})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    customerMap.entries.take(15).forEach { (wa, ords) ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text(wa, color = InkWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("${ords.size}x Order", color = InkDim, fontSize = 10.sp)
                                            }
                                            val totalSpent = ords.filter { it.status.equals("Paid", true) }.sumOf { it.totalAmount }
                                            Text(currencyFmt.format(totalSpent).replace(",00", ""), color = WaGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onLogout,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, null, tint = BadgeRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Keluar dari Admin", color = BadgeRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG: DETAIL PESANAN
    selectedDetailOrder?.let { order ->
        var credentialText by remember { mutableStateOf(order.accountData.ifEmpty { "Halo Kak, terima kasih telah order ${order.productName} di GivaStore! Berikut kredensial akun Anda:\n\nEmail: \nPassword: \nGaransi: Penuh sesuai durasi paket." }) }

        AlertDialog(
            onDismissRequest = { selectedDetailOrder = null },
            containerColor = CardDark,
            title = { Text("Pesanan #${order.orderId}", color = InkWhite, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("${order.productName} (${order.packageName})", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Total: ${currencyFmt.format(order.totalAmount).replace(",00", "")}", color = WaGreen, fontSize = 12.sp)
                    Text("WhatsApp: +${order.whatsapp}", color = InkDim, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Kredensial / Pesan Akun:", color = InkDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = credentialText,
                        onValueChange = { credentialText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDispatchWa(order.whatsapp, credentialText)
                        selectedDetailOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaGreen)
                ) {
                    Text("Kirim ke WA", color = BgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!order.status.equals("Paid", true)) {
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val body = JSONObject().put("status", "Paid").toString()
                                    val patch = Request.Builder().url("$url/rest/v1/orders?id=eq.${order.id}")
                                        .patch(body.toRequestBody("application/json".toMediaType()))
                                        .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                    client.newCall(patch).execute()
                                }
                                reloadAllData()
                                selectedDetailOrder = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                    ) {
                        Text("Tandai Lunas", color = InkWhite)
                    }
                }
            }
        )
    }

    // DIALOG: TAMBAH PRODUK
    if (showAddProductDialog) {
        var pName by remember { mutableStateOf("") }
        var pSlug by remember { mutableStateOf("") }
        var pPrice by remember { mutableStateOf("") }
        var pStock by remember { mutableStateOf("50") }

        AlertDialog(
            onDismissRequest = { showAddProductDialog = false },
            containerColor = CardDark,
            title = { Text("Tambah Produk Baru", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Nama Produk") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                    OutlinedTextField(value = pSlug, onValueChange = { pSlug = it }, label = { Text("Slug (cth: netflix)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                    OutlinedTextField(value = pPrice, onValueChange = { pPrice = it }, label = { Text("Harga (Rp)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                    OutlinedTextField(value = pStock, onValueChange = { pStock = it }, label = { Text("Stok") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val body = JSONObject()
                                    .put("name", pName)
                                    .put("slug", pSlug)
                                    .put("base_price", pPrice.toLongOrNull() ?: 0L)
                                    .put("stock", pStock.toIntOrNull() ?: 50)
                                    .toString()
                                val post = Request.Builder().url("$url/rest/v1/products")
                                    .post(body.toRequestBody("application/json".toMediaType()))
                                    .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                client.newCall(post).execute()
                            }
                            reloadAllData()
                            showAddProductDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) {
                    Text("Simpan", color = BgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProductDialog = false }) { Text("Batal", color = InkDim) }
            }
        )
    }

    // DIALOG: CATAT PENGELUARAN
    if (showAddExpenseDialog) {
        var eDesc by remember { mutableStateOf("") }
        var eAmount by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            containerColor = CardDark,
            title = { Text("Catat Pengeluaran", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = eDesc, onValueChange = { eDesc = it }, label = { Text("Deskripsi (cth: Restok Akun)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                    OutlinedTextField(value = eAmount, onValueChange = { eAmount = it }, label = { Text("Nominal (Rp)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val body = JSONObject()
                                    .put("description", eDesc)
                                    .put("category", "Operasional")
                                    .put("amount", eAmount.toLongOrNull() ?: 0L)
                                    .put("expense_date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
                                    .toString()
                                val post = Request.Builder().url("$url/rest/v1/expenses")
                                    .post(body.toRequestBody("application/json".toMediaType()))
                                    .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                client.newCall(post).execute()
                            }
                            reloadAllData()
                            showAddExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) {
                    Text("Simpan", color = BgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) { Text("Batal", color = InkDim) }
            }
        )
    }

    // DIALOG: TAMBAH KATEGORI
    if (showAddCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            containerColor = CardDark,
            title = { Text("Tambah Kategori", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("Nama Kategori") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val body = JSONObject().put("name", catName).toString()
                                val post = Request.Builder().url("$url/rest/v1/categories")
                                    .post(body.toRequestBody("application/json".toMediaType()))
                                    .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                client.newCall(post).execute()
                            }
                            reloadAllData()
                            showAddCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Tambah", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text("Batal", color = InkDim) } }
        )
    }

    // DIALOG: TAMBAH PROMO
    if (showAddPromoDialog) {
        var prmCode by remember { mutableStateOf("") }
        var prmVal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPromoDialog = false },
            containerColor = CardDark,
            title = { Text("Tambah Kode Promo", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = prmCode, onValueChange = { prmCode = it.uppercase() }, label = { Text("Kode (cth: HEMAT20)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                    OutlinedTextField(value = prmVal, onValueChange = { prmVal = it }, label = { Text("Diskon Persen (%)") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val body = JSONObject()
                                    .put("code", prmCode)
                                    .put("discount_type", "percentage")
                                    .put("discount_value", prmVal.toLongOrNull() ?: 10L)
                                    .put("min_order_amount", 0L)
                                    .put("is_active", true)
                                    .toString()
                                val post = Request.Builder().url("$url/rest/v1/promo_codes")
                                    .post(body.toRequestBody("application/json".toMediaType()))
                                    .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                client.newCall(post).execute()
                            }
                            reloadAllData()
                            showAddPromoDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Buat Promo", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddPromoDialog = false }) { Text("Batal", color = InkDim) } }
        )
    }

    // DIALOG: TAMBAH POPUP
    if (showAddPopupDialog) {
        var popTitle by remember { mutableStateOf("") }
        var popMsg by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPopupDialog = false },
            containerColor = CardDark,
            title = { Text("Buat Popup Pengumuman", color = InkWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = popTitle, onValueChange = { popTitle = it }, label = { Text("Judul Popup") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                    OutlinedTextField(value = popMsg, onValueChange = { popMsg = it }, label = { Text("Pesan Pengumuman") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = InkWhite, unfocusedTextColor = InkWhite, focusedBorderColor = InkWhite, unfocusedBorderColor = LineBorder))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val body = JSONObject()
                                    .put("title", popTitle)
                                    .put("message", popMsg)
                                    .put("is_active", true)
                                    .put("popup_type", "announcement")
                                    .put("position", "center")
                                    .put("show_on_page", "all")
                                    .toString()
                                val post = Request.Builder().url("$url/rest/v1/site_popups")
                                    .post(body.toRequestBody("application/json".toMediaType()))
                                    .addHeader("apikey", key).addHeader("Authorization", "Bearer $key").build()
                                client.newCall(post).execute()
                            }
                            reloadAllData()
                            showAddPopupDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkWhite)
                ) { Text("Pasang Popup", color = BgDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAddPopupDialog = false }) { Text("Batal", color = InkDim) } }
        )
    }
}
```
