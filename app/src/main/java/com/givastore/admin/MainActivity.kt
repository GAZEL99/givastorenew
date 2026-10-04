package com.givastore.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Model Data Utama
data class OrderItem(
    val id: String,
    val invoice: String,
    val customerName: String,
    val customerPhone: String,
    val productName: String,
    val amount: Long,
    val status: String,
    val createdAt: String
)

data class FinanceSummary(
    val todayRevenue: Long = 0,
    val weekRevenue: Long = 0,
    val monthRevenue: Long = 0,
    val yearRevenue: Long = 0
)

data class ProductItem(
    val id: String,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Long,
    val isReady: Boolean
)

class MainActivity : FragmentActivity() {

    private val supabaseUrl = "https://givastore.biz.id/api"
    private val supabaseKey = "PUBLIC-ANON-KEY-GIVASTORE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0F0F0F),
                    surface = Color(0xFF1E1E1E),
                    primary = Color.White,
                    onPrimary = Color.Black,
                    onSurface = Color.White
                )
            ) {
                GivaAdminMainScreen(this)
            }
        }
    }

    suspend fun fetchOrdersFromSupabase(): List<OrderItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<OrderItem>()
        try {
            val url = URL("$supabaseUrl/rest/v1/orders?select=*&order=created_at.desc")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", supabaseKey)
                setRequestProperty("Authorization", "Bearer $supabaseKey")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArr = JSONArray(responseText)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        OrderItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            invoice = obj.optString("invoice_number", "INV-${System.currentTimeMillis()}"),
                            customerName = obj.optString("customer_name", "Pembeli"),
                            customerPhone = obj.optString("customer_phone", ""),
                            productName = obj.optString("product_name", "Item Digital"),
                            amount = obj.optLong("total_amount", obj.optLong("total_payment", 0L)),
                            status = obj.optString("status", "PAID"),
                            createdAt = obj.optString("created_at", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return@withContext list
    }

    suspend fun fetchProductsFromSupabase(): List<ProductItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ProductItem>()
        try {
            val url = URL("$supabaseUrl/rest/v1/products?select=*&order=name.asc")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", supabaseKey)
                setRequestProperty("Authorization", "Bearer $supabaseKey")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArr = JSONArray(responseText)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        ProductItem(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", "Produk"),
                            category = obj.optString("category", "General"),
                            price = obj.optLong("price", 0L),
                            stock = obj.optLong("stock", 0L),
                            isReady = obj.optBoolean("is_active", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return@withContext list
    }
}

@Composable
fun GivaAdminMainScreen(activity: MainActivity) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var orders by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
    var products by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refreshAll() {
        scope.launch {
            isLoading = true
            orders = activity.fetchOrdersFromSupabase()
            products = activity.fetchProductsFromSupabase()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF141414)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("Pesanan", fontSize = 11.sp) },
                    icon = { Text("📦", fontSize = 18.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = Color(0xFF2C2C2C)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("Keuangan", fontSize = 11.sp) },
                    icon = { Text("💳", fontSize = 18.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = Color(0xFF2C2C2C)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("Produk", fontSize = 11.sp) },
                    icon = { Text("🏷️", fontSize = 18.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = Color(0xFF2C2C2C)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    label = { Text("Konten", fontSize = 11.sp) },
                    icon = { Text("🎨", fontSize = 18.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = Color(0xFF2C2C2C)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    label = { Text("Menu", fontSize = 11.sp) },
                    icon = { Text("⚙️", fontSize = 18.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = Color(0xFF2C2C2C)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> OrdersScreen(orders, isLoading, onRefresh = { refreshAll() })
                1 -> FinanceScreen(orders)
                2 -> ProductsScreen(products, onRefresh = { refreshAll() })
                3 -> ContentScreen()
                4 -> MenuScreen()
            }
        }
    }
}

fun formatRupiah(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(amount).replace("Rp", "Rp ")
}

@Composable
fun OrdersScreen(orders: List<OrderItem>, isLoading: Boolean, onRefresh: () -> Unit) {
    var periodTab by remember { mutableIntStateOf(2) } // 0: Hari, 1: Minggu, 2: Bulan, 3: Tahun
    val periods = listOf("Hari Ini", "Minggu", "Bulan", "Tahun")

    val totalOmzet = orders.filter { it.status.uppercase() == "PAID" || it.status.uppercase() == "LUNAS" || it.status.uppercase() == "SUCCESS" }
        .sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("GIVASTORE ADMIN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Pusat Kendali Realtime", fontSize = 12.sp, color = Color.Gray)
            }
            Surface(
                color = Color(0xFF1E3A1E),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("● ONLINE", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card Omzet
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF121212), RoundedCornerShape(10.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    periods.forEachIndexed { index, text ->
                        Surface(
                            color = if (periodTab == index) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { periodTab = index }
                        ) {
                            Text(
                                text = text,
                                fontSize = 12.sp,
                                fontWeight = if (periodTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (periodTab == index) Color.Black else Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Total Omzet ${periods[periodTab]}", fontSize = 12.sp, color = Color.Gray)
                Text(formatRupiah(totalOmzet), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PESANAN TERBARU", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                text = if (isLoading) "Memuat..." else "🔄 Refresh",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onRefresh() }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada data pesanan.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(orders) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.invoice, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Surface(
                                    color = if (item.status.uppercase() in listOf("PAID", "LUNAS", "SUCCESS")) Color(0xFF1B3B22) else Color(0xFF3B2A1B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(item.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.productName, fontSize = 12.sp, color = Color(0xFFCCCCCC))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.customerName, fontSize = 12.sp, color = Color.Gray)
                                Text(formatRupiah(item.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FinanceScreen(orders: List<OrderItem>) {
    val totalRevenue = orders.filter { it.status.uppercase() in listOf("PAID", "LUNAS", "SUCCESS") }.sumOf { it.amount }
    val totalExpense = 0L
    val netProfit = totalRevenue - totalExpense

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("LAPORAN KEUANGAN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Ringkasan Arus Kas & Margin", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Pemasukan (Omzet)", fontSize = 12.sp, color = Color.Gray)
                Text(formatRupiah(totalRevenue), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Total Pengeluaran", fontSize = 12.sp, color = Color.Gray)
                Text(formatRupiah(totalExpense), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                Divider(color = Color(0xFF333333), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                Text("Estimasi Laba Bersih", fontSize = 12.sp, color = Color.Gray)
                Text(formatRupiah(netProfit), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
fun ProductsScreen(products: List<ProductItem>, onRefresh: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("KATALOG PRODUK", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${products.size} Produk Terdaftar", fontSize = 12.sp, color = Color.Gray)
            }
            Text("🔄 Refresh", color = Color.White, fontSize = 12.sp, modifier = Modifier.clickable { onRefresh() })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada data produk.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(products) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Kategori: ${item.category} • Stok: ${item.stock}", fontSize = 12.sp, color = Color.Gray)
                                Text(formatRupiah(item.price), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                            Surface(color = if (item.isReady) Color(0xFF1B3B22) else Color(0xFF3B1B1B), shape = RoundedCornerShape(6.dp)) {
                                Text(if (item.isReady) "READY" else "HABIS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContentScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("MANAJEMEN KONTEN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Logo, Banner Hero, & Pengumuman", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Slot Banner Carousel Beranda", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("3 Banner aktif dikelola via Supabase storage", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun MenuScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("MASTER DATA & PENGATURAN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Pengaturan Toko & Keamanan", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        val menus = listOf("🏷️ Kelola Kategori Produk", "🎟️ Kode Promo Diskon", "👥 Data Kontak Pelanggan", "🔒 Keamanan Biometrik", "🚪 Keluar Akun")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(menus) { menu ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)), shape = RoundedCornerShape(10.dp)) {
                    Text(menu, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}
