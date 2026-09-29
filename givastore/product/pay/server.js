const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 8080;

// ==========================================
// 1. KONFIGURASI KLIKQRIS
// ==========================================
const KLIKQRIS_CONFIG = {
  apiKey: "nj5DWQZGncqzlDCT4N8sm2Sgr9scdGaqVwbiw9TA",
  merchantId: "177202932458",
  createHost: "klikqris.com",
  createPath: "/api/qris/create",
  statusHost: "klikqris.com",
  statusBasePath: "/api/qris/status/"
};

// ==========================================
// 2. KONFIGURASI FONNTE (WHATSAPP GATEWAY)
// ==========================================
// Dapatkan Token di dashboard Fonnte (https://fonnte.com/ -> Device)
const FONNTE_CONFIG = {
  token: "K3mbkYqByHo8prxutgvF", // <-- Masukkan Token Fonnte Anda di sini
  host: "api.fonnte.com",
  path: "/send"
};

// Database memori sementara untuk mencatat data pembeli & status kirim WA
// Struktur: order_id -> { phone, item, amount, waSent: false }
const orderDatabase = new Map();

const MIME_TYPES = {
  '.html': 'text/html; charset=UTF-8',
  '.css': 'text/css; charset=UTF-8',
  '.js': 'application/javascript; charset=UTF-8',
  '.json': 'application/json; charset=UTF-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.mp4': 'video/mp4',
  '.xml': 'application/xml',
  '.txt': 'text/plain'
};

function setCorsHeaders(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, x-api-key, id_merchant');
}

/**
 * Fungsi Otomatis Mengirim Pesan WhatsApp via Fonnte API
 */
function sendWhatsappViaFonnte(customerPhone, orderId, itemName, totalAmount) {
  if (!customerPhone || customerPhone.length < 8) {
    console.log(`⚠️ [FONNTE] Nomor telepon customer untuk order ${orderId} tidak valid/kosong.`);
    return;
  }

  if (FONNTE_CONFIG.token === "ISI_DENGAN_TOKEN_FONNTE_ANDA") {
    console.log(`⚠️ [FONNTE] Peringatan: Token Fonnte belum diisi di server.js!`);
    console.log(`   Pesan simulasi untuk ${customerPhone}: Pesanan ${orderId} sedang diproses 1x24 jam.`);
    return;
  }

  // Format Pesan Otomatis 1x24 Jam
  const messageText = 
`Halo Kak! 👋

Terima kasih banyak telah berbelanja di *GivaStore*.

Pembayaran Anda telah *BERHASIL KAMI TERIMA* dengan rincian berikut:

📋 *DETAIL PESANAN:*
• No. Invoice: *${orderId}*
• Layanan/Produk: *${itemName}*
• Total Bayar: *Rp ${Number(totalAmount).toLocaleString('id-ID')}*
• Status: *LUNAS (PAID)*

⏱️ *ESTIMASI PROSES:*
Pesanan Anda saat ini langsung masuk ke antrean pengerjaan sistem dengan estimasi pengerjaan *maksimal 1x24 Jam*.

Data akun / akses produk akan segera kami kirimkan ke nomor WhatsApp ini setelah selesai diproses. Mohon ditunggu ya Kak! 🙏✨

_Salam hangat,_
*Tim Admin GivaStore*`;

  const payload = JSON.stringify({
    target: String(customerPhone),
    message: messageText,
    countryCode: "62"
  });

  const options = {
    hostname: FONNTE_CONFIG.host,
    path: FONNTE_CONFIG.path,
    method: 'POST',
    headers: {
      'Authorization': FONNTE_CONFIG.token,
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(payload)
    }
  };

  console.log(`📲 [FONNTE] Mengirim pesan otomatis ke WhatsApp customer: ${customerPhone}...`);

  const reqFonnte = https.request(options, resFonnte => {
    let resBody = '';
    resFonnte.on('data', d => resBody += d);
    resFonnte.on('end', () => {
      try {
        const json = JSON.parse(resBody);
        if (json.status) {
          console.log(` [FONNTE SUKSES] Pesan 1x24 jam berhasil terkirim ke customer (${customerPhone})!`);
        } else {
          console.log(`❌ [FONNTE GAGAL] Respons server Fonnte:`, json.reason || json.message || resBody);
        }
      } catch (e) {
        console.log(`[FONNTE RESPONS]`, resBody);
      }
    });
  });

  reqFonnte.on('error', err => {
    console.error(`❌ [FONNTE ERROR] Gagal menghubungi API Fonnte:`, err.message);
  });

  reqFonnte.write(payload);
  reqFonnte.end();
}

/**
 * Handle Buat Tagihan QRIS KlikQRIS
 */
function handleCreatePayment(req, res) {
  let bodyData = '';
  req.on('data', chunk => {
    bodyData += chunk;
  });

  req.on('end', () => {
    try {
      const parsed = JSON.parse(bodyData || '{}');
      const orderId = String(parsed.order_id || ('GIVA-' + Date.now()));
      const amount = parseInt(parsed.amount || 15000);
      const itemName = parsed.keterangan || "Layanan Digital GivaStore";
      const customerPhone = String(parsed.phone || '').trim();

      // Simpan data pemesan ke database memori
      orderDatabase.set(orderId, {
        phone: customerPhone,
        item: itemName,
        amount: amount,
        waSent: false
      });

      const payload = JSON.stringify({
        order_id: orderId,
        id_merchant: String(KLIKQRIS_CONFIG.merchantId),
        amount: amount,
        keterangan: itemName
      });

      console.log(`\n----------------------------------------------------`);
      console.log(`[CREATE TAGIHAN] Order: ${orderId} | Nominal: Rp ${amount} | No. HP: ${customerPhone || '(tidak ada)'}`);

      const options = {
        hostname: KLIKQRIS_CONFIG.createHost,
        path: KLIKQRIS_CONFIG.createPath,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload),
          'x-api-key': KLIKQRIS_CONFIG.apiKey,
          'id_merchant': KLIKQRIS_CONFIG.merchantId
        }
      };

      const proxyReq = https.request(options, proxyRes => {
        let responseBody = '';
        proxyRes.on('data', d => responseBody += d);
        proxyRes.on('end', () => {
          setCorsHeaders(res);
          res.writeHead(proxyRes.statusCode, { 'Content-Type': 'application/json' });
          res.end(responseBody);
          console.log(`[TAGIHAN DIBUAT] Status Response: ${proxyRes.statusCode}`);
        });
      });

      proxyReq.on('error', err => {
        console.error('[CREATE TAGIHAN ERROR]', err.message);
        setCorsHeaders(res);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: false, message: 'Gagal terhubung ke KlikQRIS: ' + err.message }));
      });

      proxyReq.write(payload);
      proxyReq.end();
    } catch (e) {
      setCorsHeaders(res);
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: false, message: 'Format data JSON tidak valid' }));
    }
  });
}

/**
 * Handle Pengecekan Status Transaksi (Auto-detect Lunas & Trigger Fonnte)
 */
function handleCheckStatus(req, res, orderId) {
  const options = {
    hostname: KLIKQRIS_CONFIG.statusHost,
    path: KLIKQRIS_CONFIG.statusBasePath + encodeURIComponent(orderId),
    method: 'GET',
    headers: {
      'x-api-key': KLIKQRIS_CONFIG.apiKey,
      'id_merchant': KLIKQRIS_CONFIG.merchantId
    }
  };

  const proxyReq = https.request(options, proxyRes => {
    let responseBody = '';
    proxyRes.on('data', d => responseBody += d);
    proxyRes.on('end', () => {
      setCorsHeaders(res);
      res.writeHead(proxyRes.statusCode, { 'Content-Type': 'application/json' });
      res.end(responseBody);

      try {
        const json = JSON.parse(responseBody);
        if (json && json.data && (json.data.status === 'SUCCESS' || json.data.status === 'PAID')) {
          const order = orderDatabase.get(orderId) || {};
          
          if (!order.waSent) {
            order.waSent = true;
            orderDatabase.set(orderId, order);

            console.log(`\n====================================================`);
            console.log(`🎉 [PEMBAYARAN DITERIMA] Order ID: ${orderId} TELAH LUNAS!`);
            console.log(`⏱️  ESTIMASI PROSES: Maksimal 1x24 Jam`);
            console.log(`📲 Memicu pengiriman notifikasi otomatis via Fonnte...`);
            console.log(`====================================================\n`);

            const customerPhone = order.phone || '';
            const itemName = order.item || json.data.keterangan || 'Pesanan Digital';
            const totalAmount = json.data.total_amount || order.amount || 0;

            sendWhatsappViaFonnte(customerPhone, orderId, itemName, totalAmount);
          }
        }
      } catch (e) {}
    });
  });

  proxyReq.on('error', err => {
    console.error(`[STATUS CHECK ERROR - ${orderId}]`, err.message);
    setCorsHeaders(res);
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: false, message: 'Gagal mengecek status: ' + err.message }));
  });

  proxyReq.end();
}

/**
 * Handle Webhook Callback dari KlikQRIS (Jika Dikonfigurasikan)
 */
function handleKlikQrisWebhook(req, res) {
  let bodyData = '';
  req.on('data', chunk => {
    bodyData += chunk;
  });

  req.on('end', () => {
    try {
      const payload = JSON.parse(bodyData || '{}');
      const orderId = payload.order_id;
      const status = (payload.status || '').toUpperCase();

      console.log(`🔔 [WEBHOOK KLIKQRIS DITERIMA] Order: ${orderId} -> Status: ${status}`);

      if (status === 'PAID' || status === 'SUCCESS') {
        const order = orderDatabase.get(orderId) || {};
        if (!order.waSent) {
          order.waSent = true;
          orderDatabase.set(orderId, order);

          const customerPhone = order.phone || '';
          const itemName = payload.keterangan || order.item || 'Layanan GivaStore';
          const totalAmount = payload.total_amount || payload.amount || order.amount || 0;

          sendWhatsappViaFonnte(customerPhone, orderId, itemName, totalAmount);
        }
      }

      setCorsHeaders(res);
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: true, message: 'Webhook processed' }));
    } catch (e) {
      setCorsHeaders(res);
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: false, message: 'Bad request' }));
    }
  });
}

function serveStaticFile(req, res, pathname) {
  if (
    pathname === '/product/pay' || 
    pathname === '/product/pay/' || 
    pathname === '/pay' || 
    pathname === '/pay/'
  ) {
    pathname = '/pay.html';
  }

  if (pathname.includes('/image/logo.jpg') || pathname.includes('/logo.jpg')) {
    pathname = '/image/logo.jpg';
  }

  let filePath = path.join(__dirname, pathname === '/' ? 'index.html' : pathname);

  if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
    filePath = path.join(filePath, 'index.html');
  }

  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, content) => {
    if (err) {
      if (err.code === 'ENOENT') {
        const notFoundPath = path.join(__dirname, '404.html');
        if (fs.existsSync(notFoundPath)) {
          res.writeHead(404, { 'Content-Type': 'text/html' });
          fs.createReadStream(notFoundPath).pipe(res);
        } else {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          res.end('404 Not Found');
        }
      } else {
        res.writeHead(500);
        res.end('Server Error: ' + err.code);
      }
    } else {
      res.writeHead(200, { 'Content-Type': contentType });
      res.end(content);
    }
  });
}

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  if (req.method === 'OPTIONS') {
    setCorsHeaders(res);
    res.writeHead(204);
    res.end();
    return;
  }

  // Rute API Create Tagihan
  if (req.method === 'POST' && pathname === '/api/payment/create') {
    handleCreatePayment(req, res);
    return;
  }

  // Rute API Check Status
  if (req.method === 'GET' && pathname.startsWith('/api/payment/status/')) {
    const orderId = pathname.replace('/api/payment/status/', '');
    handleCheckStatus(req, res, orderId);
    return;
  }

  // Rute Webhook KlikQRIS
  if (req.method === 'POST' && (pathname === '/api/payment/webhook' || pathname === '/webhook')) {
    handleKlikQrisWebhook(req, res);
    return;
  }

  serveStaticFile(req, res, pathname);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('====================================================');
  console.log(`🚀 GivaStore Server (Node.js) Berjalan Normal!`);
  console.log(`🌐 Alamat: http://127.0.0.1:${PORT}`);
  console.log(`💳 Halaman Bayar: http://127.0.0.1:${PORT}/pay.html`);
  console.log(`📲 WhatsApp Gateway: Terintegrasi Fonnte (Auto Notif 1x24 Jam)`);
  console.log('====================================================');
});