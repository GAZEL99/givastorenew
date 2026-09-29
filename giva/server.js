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

// ==================== SEO Server-Side Rendering untuk halaman produk ====================
// Supaya Googlebot & bot preview WhatsApp/Telegram/Facebook (yang TIDAK menjalankan JavaScript)
// tetap melihat title/deskripsi/gambar yang benar per produk, bukan teks generik "Memuat...".
const SUPABASE_URL = 'https://rblktttasrxemtkhknvt.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJibGt0dHRhc3J4ZW10a2hrbnZ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NDQ5MzMsImV4cCI6MjEwMTAyMDkzM30.l4mPqAlPhZk-Z73_sKNARc3qTxAfUsKZyNl9u6N90Lw';

let productCache = null;
let productCacheAt = 0;
const PRODUCT_CACHE_TTL = 5 * 60 * 1000; // 5 menit

function slugify(raw) {
  return String(raw || '').toLowerCase().trim()
    .replace(/\+/g, 'plus').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function escAttr(str) {
  return String(str || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function fixImagePath(p) {
  if (!p) return '';
  return p.startsWith('../') ? p.substring(3) : p;
}

function fetchAllProducts(callback) {
  const now = Date.now();
  if (productCache && (now - productCacheAt) < PRODUCT_CACHE_TTL) {
    return callback(null, productCache);
  }
  const options = {
    hostname: 'rblktttasrxemtkhknvt.supabase.co',
    path: '/rest/v1/products?select=*,categories(name)',
    method: 'GET',
    headers: { apikey: SUPABASE_ANON_KEY, Authorization: 'Bearer ' + SUPABASE_ANON_KEY }
  };
  const reqSb = https.request(options, resSb => {
    let body = '';
    resSb.on('data', d => body += d);
    resSb.on('end', () => {
      try {
        const data = JSON.parse(body);
        if (Array.isArray(data)) {
          productCache = data;
          productCacheAt = now;
          callback(null, data);
        } else {
          callback(new Error('Respons Supabase tidak valid'));
        }
      } catch (e) { callback(e); }
    });
  });
  reqSb.on('error', callback);
  reqSb.end();
}

function injectProductSeo(html, product, req) {
  const proto = req.headers['x-forwarded-proto'] || 'https';
  const baseUrl = proto + '://' + req.headers.host;
  const pageUrl = baseUrl + req.url;
  const title = escAttr(product.name + ' | GivaStore');
  const desc = escAttr((product.description || (product.name + ' murah, proses cepat & garansi penuh.')).slice(0, 155));
  const imgPath = fixImagePath(product.image_url);
  const absImage = imgPath ? (imgPath.startsWith('http') ? imgPath : baseUrl + '/' + imgPath.replace(/^\//, '')) : (baseUrl + '/image/logo.jpg');
  const ld = {
    '@context': 'https://schema.org/',
    '@type': 'Product',
    name: product.name,
    image: absImage,
    description: desc,
    brand: { '@type': 'Brand', name: 'GivaStore' },
    offers: {
      '@type': 'Offer', priceCurrency: 'IDR', price: String(product.base_price || 0),
      availability: (product.stock && product.stock > 0) ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock'
    }
  };

  return html
    .replace(
      /<title id="meta-title">[^<]*<\/title>/,
      '<title id="meta-title">' + title + '</title>'
    )
    .replace(
      /<meta id="meta-desc" name="description" content="[^"]*">/,
      '<meta id="meta-desc" name="description" content="' + desc + '">'
    )
    .replace(
      /<meta id="og-type" property="og:type" content="[^"]*">/,
      '<meta id="og-type" property="og:type" content="product">\n  <meta property="og:url" content="' + escAttr(pageUrl) + '">'
    )
    .replace(
      /<meta id="og-title" property="og:title" content="[^"]*">/,
      '<meta id="og-title" property="og:title" content="' + title + '">'
    )
    .replace(
      /<meta id="og-desc" property="og:description" content="[^"]*">/,
      '<meta id="og-desc" property="og:description" content="' + desc + '">'
    )
    .replace(
      /<meta id="og-image" property="og:image" content="[^"]*">/,
      '<meta id="og-image" property="og:image" content="' + escAttr(absImage) + '">'
    )
    .replace(
      /<script type="application\/ld\+json" id="ld-json">\{\}<\/script>/,
      '<script type="application/ld+json" id="ld-json">' + JSON.stringify(ld) + '</script>'
    )
    .replace(
      /<\/head>/,
      '  <link rel="canonical" href="' + escAttr(pageUrl) + '">\n</head>'
    );
}

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

  // Deteksi halaman produk (bukan /product/pay/) untuk suntik SEO server-side
  const productMatch = filePath.replace(/\\/g, '/').match(/\/product\/([^\/]+)\/index\.html$/);
  const slug = (productMatch && productMatch[1] !== 'pay') ? productMatch[1] : null;

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
    } else if (slug) {
      fetchAllProducts((sbErr, products) => {
        res.writeHead(200, { 'Content-Type': contentType });
        if (sbErr) {
          console.warn('[SEO] Gagal ambil data produk dari Supabase, kirim HTML apa adanya:', sbErr.message);
          return res.end(content);
        }
        const product = products.find(p => slugify(p.slug || p.name) === slugify(slug));
        if (!product) return res.end(content);
        try {
          res.end(injectProductSeo(content.toString('utf-8'), product, req));
        } catch (e) {
          console.warn('[SEO] Gagal suntik meta produk:', e.message);
          res.end(content);
        }
      });
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