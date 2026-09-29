// Vercel Serverless Function
// Menggantikan fitur SSR SEO yang di server.js (untuk hosting VPS) dengan versi
// yang jalan di Vercel: dipanggil lewat rewrite di vercel.json untuk semua
// request ke /product/:slug/ (kecuali /product/pay/, yang tetap file statis).
const https = require('https');
const fs = require('fs');
const path = require('path');

const SUPABASE_URL = 'https://rblktttasrxemtkhknvt.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJibGt0dHRhc3J4ZW10a2hrbnZ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NDQ5MzMsImV4cCI6MjEwMTAyMDkzM30.l4mPqAlPhZk-Z73_sKNARc3qTxAfUsKZyNl9u6N90Lw';

// Cache di memori proses (bertahan antar-request selama function masih "warm",
// direset kalau Vercel spin up instance baru - tidak masalah, cuma soal efisiensi).
let productCache = null;
let productCacheAt = 0;
const PRODUCT_CACHE_TTL = 5 * 60 * 1000; // 5 menit

let templateHtml = null;
function getTemplate() {
  if (!templateHtml) {
    templateHtml = fs.readFileSync(path.join(process.cwd(), 'templates', 'product-template.html'), 'utf-8');
  }
  return templateHtml;
}

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

function fetchAllProducts() {
  const now = Date.now();
  if (productCache && (now - productCacheAt) < PRODUCT_CACHE_TTL) {
    return Promise.resolve(productCache);
  }
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'rblktttasrxemtkhknvt.supabase.co',
      path: '/rest/v1/products?select=*,categories(name)',
      method: 'GET',
      headers: { apikey: SUPABASE_ANON_KEY, Authorization: 'Bearer ' + SUPABASE_ANON_KEY },
      timeout: 8000
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
            resolve(data);
          } else {
            reject(new Error('Respons Supabase tidak valid'));
          }
        } catch (e) { reject(e); }
      });
    });
    reqSb.on('timeout', () => reqSb.destroy(new Error('Timeout menghubungi Supabase')));
    reqSb.on('error', reject);
    reqSb.end();
  });
}

function injectProductSeo(html, product, req) {
  const proto = req.headers['x-forwarded-proto'] || 'https';
  const host = req.headers['x-forwarded-host'] || req.headers.host;
  const baseUrl = proto + '://' + host;
  const slug = (req.query && req.query.slug) || '';
  const pageUrl = baseUrl + '/product/' + slug + '/';
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
    .replace(/<title id="meta-title">[^<]*<\/title>/, '<title id="meta-title">' + title + '</title>')
    .replace(/<meta id="meta-desc" name="description" content="[^"]*">/, '<meta id="meta-desc" name="description" content="' + desc + '">')
    .replace(/<meta id="og-type" property="og:type" content="[^"]*">/,
      '<meta id="og-type" property="og:type" content="product">\n  <meta property="og:url" content="' + escAttr(pageUrl) + '">')
    .replace(/<meta id="og-title" property="og:title" content="[^"]*">/, '<meta id="og-title" property="og:title" content="' + title + '">')
    .replace(/<meta id="og-desc" property="og:description" content="[^"]*">/, '<meta id="og-desc" property="og:description" content="' + desc + '">')
    .replace(/<meta id="og-image" property="og:image" content="[^"]*">/, '<meta id="og-image" property="og:image" content="' + escAttr(absImage) + '">')
    .replace(/<script type="application\/ld\+json" id="ld-json">\{\}<\/script>/, '<script type="application/ld+json" id="ld-json">' + JSON.stringify(ld) + '</script>')
    .replace(/<\/head>/, '  <link rel="canonical" href="' + escAttr(pageUrl) + '">\n</head>');
}

module.exports = async (req, res) => {
  const slug = req.query.slug;
  const html = getTemplate();

  if (!slug) {
    res.setHeader('Content-Type', 'text/html; charset=UTF-8');
    return res.status(404).send('Produk tidak ditemukan');
  }

  try {
    const products = await fetchAllProducts();
    const product = products.find(p => slugify(p.slug || p.name) === slugify(slug));
    res.setHeader('Content-Type', 'text/html; charset=UTF-8');
    res.setHeader('Cache-Control', 'no-cache');
    if (!product) return res.status(200).send(html);
    return res.status(200).send(injectProductSeo(html, product, req));
  } catch (e) {
    console.warn('[SEO] Gagal ambil data produk dari Supabase, kirim HTML apa adanya:', e.message);
    res.setHeader('Content-Type', 'text/html; charset=UTF-8');
    return res.status(200).send(html);
  }
};
