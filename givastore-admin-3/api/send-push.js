/**
 * Dipanggil oleh Supabase Database Webhook setiap ada INSERT/UPDATE di tabel `orders`.
 * Kalau status order baru berubah jadi "paid" (dan sebelumnya BUKAN "paid", biar gak dobel),
 * kirim Web Push ke semua device admin yang tersimpan di tabel `push_subscriptions`.
 *
 * ENV VAR yang wajib di-set di Vercel (Project Settings -> Environment Variables):
 *   VAPID_PUBLIC_KEY        (harus SAMA PERSIS dengan yang dipakai di index.html)
 *   VAPID_PRIVATE_KEY       (RAHASIA - jangan pernah taruh di client-side code)
 *   VAPID_SUBJECT           contoh: mailto:xyzopedia@gmail.com
 *   SUPABASE_URL            https://rblktttasrxemtkhknvt.supabase.co
 *   SUPABASE_SERVICE_ROLE_KEY  (dari Supabase Dashboard -> Project Settings -> API -> service_role)
 *   PUSH_WEBHOOK_SECRET     (bebas, string acak) -- dicocokkan dengan header dari Supabase Webhook
 */

const webpush = require('web-push');

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  // Verifikasi secret, biar endpoint ini gak bisa dipanggil sembarang orang
  const expectedSecret = process.env.PUSH_WEBHOOK_SECRET;
  const gotSecret = req.headers['x-webhook-secret'];
  if (expectedSecret && gotSecret !== expectedSecret) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const {
    VAPID_PUBLIC_KEY,
    VAPID_PRIVATE_KEY,
    VAPID_SUBJECT,
    SUPABASE_URL,
    SUPABASE_SERVICE_ROLE_KEY,
  } = process.env;

  if (!VAPID_PUBLIC_KEY || !VAPID_PRIVATE_KEY || !SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    console.error('send-push: env var belum lengkap');
    res.status(500).json({ error: 'Server belum dikonfigurasi lengkap' });
    return;
  }

  webpush.setVapidDetails(
    VAPID_SUBJECT || 'mailto:admin@example.com',
    VAPID_PUBLIC_KEY,
    VAPID_PRIVATE_KEY
  );

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  body = body || {};

  const record = body.record || {};
  const oldRecord = body.old_record || null;

  const newStatus = String(record.status || '').toLowerCase();
  const oldStatus = oldRecord ? String(oldRecord.status || '').toLowerCase() : '';

  const justBecamePaid = newStatus === 'paid' && oldStatus !== 'paid';

  if (!justBecamePaid) {
    // Bukan transisi ke "paid" -> tidak perlu kirim notifikasi, tapi tetap balas 200
    res.status(200).json({ skipped: true });
    return;
  }

  // Ambil semua subscription admin dari Supabase pakai service role key (bypass RLS)
  let subs = [];
  try {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/push_subscriptions?select=endpoint,p256dh,auth`,
      {
        headers: {
          apikey: SUPABASE_SERVICE_ROLE_KEY,
          Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        },
      }
    );
    subs = await r.json();
    if (!Array.isArray(subs)) subs = [];
  } catch (e) {
    console.error('send-push: gagal fetch subscriptions', e);
    res.status(500).json({ error: 'Gagal ambil daftar subscription' });
    return;
  }

  const amount = record.total_payment || record.total_amount || 0;
  const productName = record.product_name || 'Pesanan';
  const payload = JSON.stringify({
    title: 'Pembayaran Diterima!',
    body: `${productName} - Rp${Number(amount).toLocaleString('id-ID')}`,
    tag: 'giva-order-' + (record.id || Date.now()),
    url: './index.html',
  });

  const results = await Promise.allSettled(
    subs.map((s) =>
      webpush.sendNotification(
        { endpoint: s.endpoint, keys: { p256dh: s.p256dh, auth: s.auth } },
        payload
      )
    )
  );

  // Hapus subscription yang sudah gak valid lagi (device uninstall/logout/expired)
  const deadEndpoints = [];
  results.forEach((r, i) => {
    if (r.status === 'rejected') {
      const code = r.reason && r.reason.statusCode;
      if (code === 404 || code === 410) deadEndpoints.push(subs[i].endpoint);
    }
  });

  if (deadEndpoints.length) {
    try {
      await Promise.all(
        deadEndpoints.map((endpoint) =>
          fetch(
            `${SUPABASE_URL}/rest/v1/push_subscriptions?endpoint=eq.${encodeURIComponent(endpoint)}`,
            {
              method: 'DELETE',
              headers: {
                apikey: SUPABASE_SERVICE_ROLE_KEY,
                Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
              },
            }
          )
        )
      );
    } catch (e) {
      console.warn('send-push: gagal bersihkan subscription mati', e);
    }
  }

  const sent = results.filter((r) => r.status === 'fulfilled').length;
  res.status(200).json({ sent, total: subs.length, cleaned: deadEndpoints.length });
};
