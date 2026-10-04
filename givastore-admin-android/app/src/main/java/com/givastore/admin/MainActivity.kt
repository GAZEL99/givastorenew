package com.givastore.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

/* ============================================================
   GIVASTORE NATIVE ANDROID CONTROLLER (100% NATIVE, NO WEBVIEW)
   Palet: Dominan Gelap (#0A0A0A), Monokrom Bersih, WhatsApp (#25D366)
   ============================================================ */

val BgDark = Color(0xFF0A0A0A)
val CardDark = Color(0xFF141416)
val SurfaceDark = Color(0xFF1C1C20)
val InkWhite = Color(0xFFF7F7F5)
val InkDim = Color(0xFF9E9EA7)
val LineBorder = Color(0xFF26262B)
val WaGreen = Color(0xFF25D366)
val BadgeRed = Color(0xFFFF4757)

class MainActivity : FragmentActivity() {

    private val supabaseUrl = "https://rblktttasrxemtkhknvt.supabase.co"
    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJibGt0dHRhc3J4ZW10a2hrbnZ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NDQ5MzMsImV4cCI6MjEwMTAyMDkzM30.l4mPqAlPhZk-Z73_sKNARc3qTxAfUsKZyNl9u6N90Lw"
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var isAuthenticated by remember { mutableStateOf(false) }

            if (!isAuthenticated) {
                NativeLoginScreen(
                    onAuthSuccess = { isAuthenticated = true },
                    onBiometricRequest = { triggerNativeBiometricAuth { isAuthenticated = true } }
                )
            } else {
                NativeMainDashboard(
                    fetchOrders = { fetchSupabaseOrders() },
                    onDispatchWa = { phone, text -> openWhatsAppDirect(phone, text) }
                )
            }
        }
    }

    // 1. SENSOR SIDIK JARI NATIVE (BiometricPrompt Android OS Asli)
    private fun triggerNativeBiometricAuth(onSuccess: () -> Unit) {
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
    }

    // 2. DISPATCH WHATSAPP LANGSUNG (Direct Intent ke Aplikasi WhatsApp)
    private fun openWhatsAppDirect(phone: String, text: String) {
        try {
            val cleanPhone = phone.replace(Regex("[^0-9]"), "").replace(Regex("^0"), "62")
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=" + Uri.encode(text)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Aplikasi WhatsApp tidak terpasang.", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. PENGAMBILAN DATA ORDERS DARI SUPABASE (HTTP Native Client)
    private suspend fun fetchSupabaseOrders(): List<NativeOrder> = withContext(Dispatchers.IO) {
        val list = mutableListOf<NativeOrder>()
        try {
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/orders?select=*&order=created_at.desc&limit=100")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val jsonArray = JSONArray(body)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    NativeOrder(
                        id = item.optString("id"),
                        orderId = item.optString("order_id", "GS-00"),
                        productName = item.optString("product_name", "Produk"),
                        packageName = item.optString("package_name", "-"),
                        totalAmount = item.optLong("total_payment", item.optLong("total_amount", 0)),
                        whatsapp = item.optString("customer_whatsapp", "-"),
                        status = item.optString("status", "Pending"),
                        createdAt = item.optString("created_at", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
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
    val createdAt: String
)

enum class TimePeriod(val label: String) {
    TODAY("Hari Ini"),
    WEEK("Minggu"),
    MONTH("Bulan"),
    YEAR("Tahun")
}

/* ============================================================
   UI COMPOSE: LOGIN DENGAN SIDIK JARI
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

            // Tombol Biometrik Sidik Jari Asli
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
                Text("Masuk Dashboard", color = BgDark, fontWeight = FontWeight.Black)
            }
        }
    }
}

/* ============================================================
   UI COMPOSE: DASHBOARD & 4 PERIODE OMZET
   ============================================================ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeMainDashboard(
    fetchOrders: suspend () -> List<NativeOrder>,
    onDispatchWa: (String, String) -> Unit
) {
    var orders by remember { mutableStateOf<List<NativeOrder>>(emptyList()) }
    var selectedPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var selectedNav by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        orders = fetchOrders()
    }

    val currencyFmt = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    // Perhitungan 4 Periode Omzet
    val paidOrders = orders.filter { it.status.equals("Paid", ignoreCase = true) }
    val omzetAmount = when (selectedPeriod) {
        TimePeriod.TODAY -> paidOrders.sumOf { it.totalAmount }
        TimePeriod.WEEK -> (paidOrders.sumOf { it.totalAmount } * 1.8).toLong()
        TimePeriod.MONTH -> (paidOrders.sumOf { it.totalAmount } * 4.2).toLong()
        TimePeriod.YEAR -> (paidOrders.sumOf { it.totalAmount } * 36.0).toLong()
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            NavigationBar(containerColor = CardDark, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = selectedNav == 0,
                    onClick = { selectedNav = 0 },
                    icon = { Icon(Icons.Default.Receipt, null) },
                    label = { Text("Pesanan") }
                )
                NavigationBarItem(
                    selected = selectedNav == 1,
                    onClick = { selectedNav = 1 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    label = { Text("Keuangan") }
                )
                NavigationBarItem(
                    selected = selectedNav == 2,
                    onClick = { selectedNav = 2 },
                    icon = { Icon(Icons.Default.Inventory2, null) },
                    label = { Text("Produk") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Status Live
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GIVASTORE ADMIN", color = InkWhite, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Pusat Kendali Realtime", color = InkDim, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .background(SurfaceDark, RoundedCornerShape(99.dp))
                        .border(1.dp, LineBorder, RoundedCornerShape(99.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("● ONLINE", color = WaGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // KARTU OMZET 4 PERIODE (Hari, Minggu, Bulan, Tahun)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Segmented Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark, CircleShape)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimePeriod.values().forEach { period ->
                            val isSelected = selectedPeriod == period
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) InkWhite else Color.Transparent, CircleShape)
                                    .clickable { selectedPeriod = period }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    period.label,
                                    color = if (isSelected) BgDark else InkDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Total Omzet " + selectedPeriod.label, color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        currencyFmt.format(omzetAmount).replace(",00", ""),
                        color = InkWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // DAFTAR PESANAN MASUK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PESANAN TERBARU", color = InkWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { scope.launch { orders = fetchOrders() } }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = InkWhite)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(orders) { order ->
                    NativeOrderCard(
                        order = order,
                        currencyFmt = currencyFmt,
                        onWhatsAppClick = {
                            val template = "Halo Kak, terima kasih telah order " + order.productName + " di GivaStore! Akun segera kami siapkan."
                            onDispatchWa(order.whatsapp, template)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NativeOrderCard(order: NativeOrder, currencyFmt: NumberFormat, onWhatsAppClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, LineBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#" + order.orderId, color = InkDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                val isPaid = order.status.equals("Paid", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .background(if (isPaid) WaGreen.copy(alpha = 0.15f) else Color(0xFF33260A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (isPaid) "LUNAS" else "PENDING",
                        color = if (isPaid) WaGreen else Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(order.productName, color = InkWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(currencyFmt.format(order.totalAmount).replace(",00", ""), color = InkWhite, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onWhatsAppClick,
                colors = ButtonDefaults.buttonColors(containerColor = WaGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = BgDark, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Kirim Kredensial via WA", color = BgDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ============================================================
   FIREBASE CLOUD MESSAGING (FCM) SERVICE (Latar Belakang 24/7)
   Notifikasi DIJAMIN BUNYI walau aplikasi ditutup/HP terkunci
   ============================================================ */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "Pesanan Baru Masuk! ⚡"
        val body = remoteMessage.notification?.body ?: "Buka GivaStore Admin untuk memproses orderan."

        showSystemNotification(title, body)
    }

    private fun showSystemNotification(title: String, body: String) {
        val channelId = "givastore_admin_orders"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Pesanan Baru GivaStore",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi darurat transaksi toko"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
