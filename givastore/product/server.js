const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 8080;
const KLIKQRIS_CONFIG = {
  apiKey: "nj5DWQZGncqzlDCT4N8sm2Sgr9scdGaqVwbiw9TA",
  merchantId: "177202932458",
  createHost: "klikqris.com",
  createPath: "/api/qris/create",
  statusHost: "klikqris.com",
  statusBasePath: "/api/qris/status/"
};

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

function handleCreatePayment(req, res) {
  let bodyData = '';
  req.on('data', chunk => {
    bodyData += chunk;
  });

  req.on('end', () => {
    try {
      const parsed = JSON.parse(bodyData || '{}');
      const payload = JSON.stringify({
        order_id: String(parsed.order_id || ('GIVA-' + Date.now())),
        id_merchant: String(KLIKQRIS_CONFIG.merchantId),
        amount: parseInt(parsed.amount || 15000),
        keterangan: parsed.keterangan || "Pembayaran GivaStore"
      });

      console.log(`[PAYMENT CREATE] Mengirim tagihan ke KlikQRIS: Order ${parsed.order_id} (Rp ${parsed.amount})`);

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
          console.log(`[PAYMENT CREATE RESPONSE] Status: ${proxyRes.statusCode}`);
        });
      });

      proxyReq.on('error', err => {
        console.error('[PAYMENT CREATE ERROR]', err.message);
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

function handleCheckStatus(req, res, orderId) {
  console.log(`[STATUS CHECK] Memeriksa status Order ID: ${orderId}`);

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
    });
  });

  proxyReq.on('error', err => {
    console.error('[STATUS CHECK ERROR]', err.message);
    setCorsHeaders(res);
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: false, message: 'Gagal mengecek status: ' + err.message }));
  });

  proxyReq.end();
}

function serveStaticFile(req, res, pathname) {
  // Atasi rute pintas seperti /product/pay agar langsung memuat pay.html (mencegah error 404)
  if (pathname === '/product/pay' || pathname === '/pay') {
    pathname = '/pay.html';
  }

  let filePath = path.join(__dirname, pathname === '/' ? 'index.html' : pathname);

  // Jika berupa folder, cari index.html di dalamnya
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

  // Handle preflight OPTIONS request
  if (req.method === 'OPTIONS') {
    setCorsHeaders(res);
    res.writeHead(204);
    res.end();
    return;
  }

  // Rute API Backend (Bebas CORS 100%)
  if (req.method === 'POST' && pathname === '/api/payment/create') {
    handleCreatePayment(req, res);
    return;
  }

  if (req.method === 'GET' && pathname.startsWith('/api/payment/status/')) {
    const orderId = pathname.replace('/api/payment/status/', '');
    handleCheckStatus(req, res, orderId);
    return;
  }

  // Layani berkas statis website
  serveStaticFile(req, res, pathname);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('====================================================');
  console.log(`🚀 GivaStore Server (Node.js) Berjalan Normal!`);
  console.log(`🌐 Alamat Lokal: http://127.0.0.1:${PORT}`);
  console.log(`🌐 Alamat Jaringan: http://localhost:${PORT}`);
  console.log(`💳 Endpoint QRIS: http://127.0.0.1:${PORT}/pay.html`);
  console.log(`🛡️ Status CORS: Dinonaktifkan (Server-to-Server aman)`);
  console.log('====================================================');
});