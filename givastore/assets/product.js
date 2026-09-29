
    const SUPABASE_URL = 'https://rblktttasrxemtkhknvt.supabase.co';
    const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJibGt0dHRhc3J4ZW10a2hrbnZ0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NDQ5MzMsImV4cCI6MjEwMTAyMDkzM30.l4mPqAlPhZk-Z73_sKNARc3qTxAfUsKZyNl9u6N90Lw';

    let supabaseClient = null;
    try { supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY); } catch(e) {}

    function slugify(raw) {
      return String(raw || '').toLowerCase().trim()
        .replace(/\+/g, 'plus').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    }
    function getSlugFromUrl() {
      var params = new URLSearchParams(window.location.search);
      if (params.get('slug')) return params.get('slug');
      if (params.get('product')) return params.get('product');
      var parts = window.location.pathname.split('/').filter(Boolean);
      var idx = parts.indexOf('product');
      if (idx !== -1 && parts[idx + 1]) return decodeURIComponent(parts[idx + 1]);
      var last = parts[parts.length - 1] || '';
      return decodeURIComponent(last.replace(/\.html?$/i, ''));
    }
    function escHtml(str) {
      if (!str) return '';
      return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function fmt(n) { return 'Rp ' + (n || 0).toLocaleString('id-ID'); }
    function fixImagePath(p) {
      if (!p) return 'https://placehold.co/300x300/141518/f5f6f8?text=GS';
      return p.startsWith('../') ? p.substring(3) : p;
    }

    /* Alamat root situs (path absolut) dari posisi halaman ini.
       Benar untuk /product/x, /product/x/, /product/x/index.html, dan situs di subfolder (mis. /givanew/product/x/). */
    function getRoot() {
      var path = window.location.pathname;
      var m = path.match(/^(.*)\/product\/[^\/]+(?:\/(?:index\.html)?)?$/);
      if (m) return m[1] + '/';
      // Cadangan: folder produk berada satu tingkat di bawah root
      var segs = path.split('/').filter(Boolean);
      if (segs.length && /\.[a-z0-9]+$/i.test(segs[segs.length - 1])) segs.pop();
      segs.pop();
      return segs.length ? '/' + segs.join('/') + '/' : '/';
    }

    /* ===== Logo video ===== */
    function initBrandVideo() {
      try {
        var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        document.querySelectorAll('.brand-mark video, .brand-text video').forEach(function (v) {
          v.removeAttribute('loop');
          if (reduce) {
            v.removeAttribute('autoplay');
            v.pause();
            var showFrame = function () { v.currentTime = parseFloat(v.getAttribute('data-still')) || 0; };
            if (v.readyState >= 1) showFrame();
            else v.addEventListener('loadedmetadata', showFrame, { once: true });
          } else {
            // Main lalu berhenti di frame terakhir, lalu putar ulang dari awal tiap 20 detik
            v.addEventListener('ended', function () { v.pause(); });
            var p = v.play();
            if (p && p.catch) p.catch(function () {});
            setInterval(function () {
              v.currentTime = 0;
              var p2 = v.play();
              if (p2 && p2.catch) p2.catch(function () {});
            }, 20000);
          }
        });
      } catch (e) { /* logo video tidak boleh mengganggu fungsi halaman */ }
    }

    document.addEventListener('DOMContentLoaded', function () {
      document.getElementById('currentYear').textContent = new Date().getFullYear();

      var ROOT = getRoot();
      document.getElementById('logo-link').setAttribute('href', ROOT + 'index.html');
      var favEl = document.getElementById('favicon-link');
      if (favEl) favEl.setAttribute('href', ROOT + favEl.getAttribute('href'));
      document.querySelectorAll('.nav-rel-link').forEach(function(el) {
        el.setAttribute('href', ROOT + el.dataset.rel);
      });
      document.querySelectorAll('.brand-mark video, .brand-text video').forEach(function(v) {
        v.setAttribute('poster', ROOT + v.getAttribute('data-poster'));
        v.setAttribute('src', ROOT + v.getAttribute('data-src'));
      });
      initBrandVideo();
      function navigateWithTransition(url) {
        document.body.classList.add('page-leaving');
        setTimeout(function() { window.location.href = url; }, 180);
      }
      document.getElementById('logo-link').addEventListener('click', function(e) { e.preventDefault(); navigateWithTransition(this.getAttribute('href')); });
      document.querySelectorAll('.nav-rel-link').forEach(function(el) {
        el.addEventListener('click', function(e) { e.preventDefault(); navigateWithTransition(this.getAttribute('href')); });
      });

      /* ===== Drawer ===== */
      function toggleDrawer() {
        var drawer = document.getElementById('drawer');
        var overlay = document.getElementById('drawerOverlay');
        var btn = document.getElementById('menuBtn');
        var icon = document.getElementById('menuIcon');
        var isOpen = drawer ? drawer.classList.toggle('open') : false;
        if (overlay) overlay.classList.toggle('open', isOpen);
        if (btn) btn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        if (icon) icon.className = isOpen ? 'fas fa-xmark' : 'fas fa-bars';
        document.body.style.overflow = isOpen ? 'hidden' : '';
      }
      window.toggleDrawer = toggleDrawer;
      var menuBtn = document.getElementById('menuBtn');
      if (menuBtn) menuBtn.addEventListener('click', toggleDrawer);
      var drawerOverlay = document.getElementById('drawerOverlay');
      if (drawerOverlay) drawerOverlay.addEventListener('click', toggleDrawer);

      /* ===== Toast ===== */
      function showToast(msg, icon) {
        var t = document.getElementById('appToast');
        if (!t) {
          t = document.createElement('div');
          t.id = 'appToast';
          t.className = 'toast';
          t.innerHTML = '<i class="fas"></i><span></span>';
          document.body.appendChild(t);
        }
        t.querySelector('i').className = 'fas ' + (icon || 'fa-check');
        t.querySelector('span').textContent = msg;
        t.classList.add('show');
        clearTimeout(t._hideTimer);
        t._hideTimer = setTimeout(function() { t.classList.remove('show'); }, 2400);
      }

      /* ===== State ===== */
      var activeProduct = null;
      var activePackages = [];
      var activeInputs = [];
      var selectedPackage = null;
      var appliedPromo = null;

      var qtyInput = document.getElementById('qty-input');
      var minusBtn = document.getElementById('minus-btn');
      var plusBtn = document.getElementById('plus-btn');

      var sumProduct = document.getElementById('sum-product');
      var sumPackage = document.getElementById('sum-package');
      var sumPrice = document.getElementById('sum-price');
      var sumPromoRow = document.getElementById('sum-promo-row');
      var sumDiscount = document.getElementById('sum-discount');
      var sumTotal = document.getElementById('sum-total');

      var promoCodeInput = document.getElementById('promo-code');
      var btnApplyPromo = document.getElementById('btn-apply-promo');
      var promoMessage = document.getElementById('promo-message');
      var btnSubmit = document.getElementById('btn-submit-order');
      var btnSubmitMobile = document.getElementById('btn-submit-order-mobile');
      var stickyPrice = document.getElementById('sticky-price');
      var priceNow = document.getElementById('price-now');
      var priceOrig = document.getElementById('price-orig');

      function updateSummary() {
        if (!selectedPackage) return;
        var qty = parseInt(qtyInput.value) || 1;
        var base = selectedPackage.price * qty;
        var disc = 0;

        if (appliedPromo) {
          if (appliedPromo.discount_type === 'percentage') {
            disc = Math.round(base * appliedPromo.discount);
            if (appliedPromo.max_discount && disc > appliedPromo.max_discount) disc = appliedPromo.max_discount;
          } else if (appliedPromo.discount_type === 'fixed') {
            disc = Math.min(appliedPromo.fixed_amount, base);
          }
        }

        var total = Math.max(0, base - disc);
        sumPackage.textContent = selectedPackage.name + ' (' + qty + 'x)';
        sumPrice.textContent = fmt(selectedPackage.price);
        sumTotal.textContent = fmt(total);
        stickyPrice.textContent = fmt(total);
        priceNow.textContent = fmt(selectedPackage.price);

        if (appliedPromo) {
          sumPromoRow.style.display = 'flex';
          sumDiscount.textContent = '-' + fmt(disc);
        } else {
          sumPromoRow.style.display = 'none';
        }
        validate();
      }

      function validate() {
        var dynFieldsOk = true;
        activeInputs.forEach(function(inp) {
          if (!inp.is_required) return;
          var el = document.getElementById('input-dyn-' + inp.name);
          if (!el || el.value.trim().length < 2) dynFieldsOk = false;
        });
        var waOk = true;
        if (!hasDynamicWaField()) {
          var waVal = document.getElementById('input-wa').value.trim();
          waOk = waVal.length >= 8;
        }
        var ok = !!(selectedPackage && dynFieldsOk && waOk);
        [btnSubmit, btnSubmitMobile].forEach(function(b) {
          if (!b) return;
          b.disabled = !ok;
        });
      }
      document.getElementById('input-wa').addEventListener('input', validate);

      minusBtn.onclick = function() {
        var q = parseInt(qtyInput.value) || 1;
        if (q > 1) { qtyInput.value = q - 1; updateSummary(); }
      };
      plusBtn.onclick = function() {
        qtyInput.value = (parseInt(qtyInput.value) || 1) + 1;
        updateSummary();
      };

      btnApplyPromo.onclick = async function() {
        var code = promoCodeInput.value.trim().toUpperCase();
        if (!code) {
          promoMessage.className = 'promo-msg error';
          promoMessage.textContent = 'Masukkan kode promo terlebih dahulu.';
          return;
        }
        if (!supabaseClient) {
          promoMessage.className = 'promo-msg error';
          promoMessage.textContent = 'Server promo sedang tidak bisa diakses.';
          return;
        }
        btnApplyPromo.disabled = true;
        btnApplyPromo.textContent = '...';
        try {
          var qty0 = parseInt(qtyInput.value) || 1;
          var currentTotal = selectedPackage ? selectedPackage.price * qty0 : 0;
          var res = await fetch(SUPABASE_URL + '/functions/v1/check-promo', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + SUPABASE_ANON_KEY, 'apikey': SUPABASE_ANON_KEY },
            body: JSON.stringify({ code: code, order_total: currentTotal })
          }).then(function(r) { return r.json(); });

          btnApplyPromo.disabled = false;
          btnApplyPromo.textContent = 'Gunakan';

          if (!res || res.success !== true || !res.data) {
            appliedPromo = null;
            promoMessage.className = 'promo-msg error';
            promoMessage.textContent = (res && res.message) ? res.message : 'Kode promo tidak valid atau sudah kedaluwarsa.';
            updateSummary();
            return;
          }
          var data = res.data;
          var label = data.discount_type === 'percentage' ? data.discount_value + '%' : fmt(Number(data.discount_value));
          appliedPromo = {
            code: data.code,
            discount_type: data.discount_type,
            discount: data.discount_type === 'percentage' ? data.discount_value / 100 : null,
            fixed_amount: data.discount_type === 'fixed' ? data.discount_value : null,
            max_discount: data.max_discount_amount || null
          };
          promoMessage.className = 'promo-msg success';
          promoMessage.textContent = 'Promo terpasang. Diskon ' + label + '.';
          showToast('Kode promo ' + data.code + ' berhasil dipakai', 'fa-tag');
        } catch(e) {
          btnApplyPromo.disabled = false;
          btnApplyPromo.textContent = 'Gunakan';
          promoMessage.className = 'promo-msg error';
          promoMessage.textContent = 'Terjadi kesalahan saat memeriksa kode promo.';
        }
        updateSummary();
      };

      function hasDynamicWaField() {
        return activeInputs.some(function(inp) {
          return inp.type === 'tel' || String(inp.name || '').toLowerCase() === 'whatsapp';
        });
      }

      function renderDynamicFields() {
        var container = document.getElementById('dynamic-fields');
        container.innerHTML = '';
        var fields = activeInputs.length > 0 ? activeInputs : [{
          name: 'whatsapp', type: 'tel', label: 'Nomor WhatsApp',
          placeholder: '081234567890', is_required: true,
          hint: 'Dipakai untuk kirim akun & notifikasi pesanan.'
        }];
        fields.forEach(function(inp) {
          var inputType = inp.type === 'email' ? 'email' : (inp.type === 'tel' ? 'tel' : 'text');
          var div = document.createElement('div');
          div.className = 'form-group';
          div.innerHTML =
            '<label>' + escHtml(inp.label) + (inp.is_required ? ' <span class="req">*wajib</span>' : ' <span class="req">opsional</span>') + '</label>' +
            '<input type="' + inputType + '" id="input-dyn-' + escHtml(inp.name) + '" placeholder="' + escHtml(inp.placeholder || '') + '">' +
            (inp.hint ? '<div class="input-hint"><i class="fas fa-circle-info"></i> ' + escHtml(inp.hint) + '</div>' : '');
          container.appendChild(div);
          div.querySelector('input').addEventListener('input', validate);
        });

        // Produk yang input-nya sendiri sudah minta nomor WhatsApp (mayoritas produk) tidak perlu
        // field "Kontak Konfirmasi Pesanan" lagi di bawah - itu dobel. Cuma tampil buat produk yang
        // field khususnya bukan nomor WA (mis. yang cuma minta email).
        var waBlock = document.getElementById('wa-confirm-block');
        if (waBlock) waBlock.style.display = hasDynamicWaField() ? 'none' : '';
      }

      function renderPackages() {
        var container = document.getElementById('packages-container');
        container.innerHTML = '';
        if (activePackages.length === 0) {
          container.innerHTML = '<div class="mono" style="color:var(--faint);font-size:0.8rem;">Belum ada paket tersedia.</div>';
          return;
        }
        activePackages.forEach(function(pkg, i) {
          var isPopular = (pkg.name || '').toLowerCase().includes('shared') || pkg.is_popular;
          var card = document.createElement('div');
          card.className = 'pkg-card' + (i === 0 ? ' selected' : '');
          card.innerHTML =
            (isPopular ? '<div class="pkg-badge-popular">Terlaris</div>' : '') +
            '<div class="pkg-name">' + escHtml(pkg.name) + '</div>' +
            '<div class="pkg-price mono">' + fmt(pkg.price) + '</div>' +
            '<div class="pkg-dur">' + escHtml(pkg.duration || '1 BULAN') + '</div>';
          card.onclick = function() {
            document.querySelectorAll('.pkg-card').forEach(function(c) { c.classList.remove('selected'); });
            card.classList.add('selected');
            selectedPackage = pkg;
            updateSummary();
          };
          container.appendChild(card);
          if (i === 0) selectedPackage = pkg;
        });
        updateSummary();
      }

      function generateOrderToken() {
        var now = new Date();
        var dd = String(now.getDate()).padStart(2, '0');
        var mm = String(now.getMonth() + 1).padStart(2, '0');
        var yy = String(now.getFullYear()).slice(-2);
        var arr = new Uint16Array(1);
        (window.crypto || window.msCrypto).getRandomValues(arr);
        var num = arr[0] % 10000;
        return 'GIVA-' + dd + mm + yy + '-' + String(num).padStart(4, '0');
      }

      async function submitOrder() {
        if (btnSubmit.disabled || !selectedPackage || !activeProduct) return;
        var qty = parseInt(qtyInput.value) || 1;
        var base = selectedPackage.price * qty;
        var disc = 0;
        if (appliedPromo) {
          if (appliedPromo.discount_type === 'percentage') {
            disc = Math.round(base * appliedPromo.discount);
            if (appliedPromo.max_discount && disc > appliedPromo.max_discount) disc = appliedPromo.max_discount;
          } else if (appliedPromo.discount_type === 'fixed') {
            disc = Math.min(appliedPromo.fixed_amount, base);
          }
        }
        var total = Math.max(0, base - disc);

        var accountData = {};
        var fields = activeInputs.length > 0 ? activeInputs : [{ name: 'whatsapp' }];
        fields.forEach(function(inp) {
          var el = document.getElementById('input-dyn-' + inp.name);
          if (el) accountData[inp.name] = el.value.trim();
        });
        var wa = hasDynamicWaField()
          ? (accountData.whatsapp || (function () {
              var waField = activeInputs.filter(function (inp) { return inp.type === 'tel' || String(inp.name || '').toLowerCase() === 'whatsapp'; })[0];
              return waField ? (accountData[waField.name] || '') : '';
            })())
          : document.getElementById('input-wa').value.trim();
        if (appliedPromo) { accountData.promo = appliedPromo.code; accountData.discount = disc; }

        [btnSubmit, btnSubmitMobile].forEach(function(b) {
          if (!b) return;
          b.disabled = true;
          b.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Memproses...';
        });

        var token = null;
        var maxAttempts = 5;
        for (var attempt = 0; attempt < maxAttempts; attempt++) {
          var candidate = generateOrderToken();
          try {
            if (!supabaseClient) { token = candidate; break; }
            var insertRes = await supabaseClient.from('orders').insert({
              order_id: candidate,
              product_name: activeProduct.name,
              package_name: selectedPackage.name,
              price: selectedPackage.price,
              quantity: qty,
              total_amount: total,
              customer_whatsapp: wa,
              account_data: accountData,
              status: 'Draft'
            });
            if (insertRes.error) {
              if (insertRes.error.code === '23505') continue;
              console.error('GivaStore: gagal membuat draft order', insertRes.error);
            }
            token = candidate;
            break;
          } catch (e) { console.error('GivaStore: gagal membuat draft order', e); }
        }
        if (!token) token = generateOrderToken();

        document.body.classList.add('page-leaving');
        setTimeout(function() {
          window.location.href = getRoot() + 'pay?o=' + token;
        }, 200);
      }
      btnSubmit.onclick = submitOrder;
      btnSubmitMobile.onclick = submitOrder;

      function renderProductUI() {
        var p = activeProduct;
        document.getElementById('meta-title').textContent = p.name + ' | GivaStore';
        var desc = (p.description || (p.name + ' murah, proses cepat & garansi penuh.')).slice(0, 155);
        document.getElementById('meta-desc').setAttribute('content', desc);
        document.getElementById('og-title').setAttribute('content', p.name + ' | GivaStore');
        document.getElementById('og-desc').setAttribute('content', desc);
        document.getElementById('og-image').setAttribute('content', fixImagePath(p.image_url));

        var ld = {
          "@context": "https://schema.org/",
          "@type": "Product",
          "name": p.name,
          "image": fixImagePath(p.image_url),
          "description": desc,
          "brand": { "@type": "Brand", "name": "GivaStore" },
          "offers": {
            "@type": "Offer", "priceCurrency": "IDR", "price": String(p.base_price || 0),
            "availability": (p.stock && p.stock > 0) ? "https://schema.org/InStock" : "https://schema.org/OutOfStock"
          }
        };
        document.getElementById('ld-json').textContent = JSON.stringify(ld);

        var catName = (p.categories && p.categories.name) ? p.categories.name : 'Produk Digital';
        document.getElementById('ph-category').textContent = catName;
        document.getElementById('ph-cat-over').textContent = catName;
        document.getElementById('crumb-name').textContent = p.name;
        document.getElementById('product-name').textContent = p.name;
        document.getElementById('product-desc').textContent = p.description ? p.description.slice(0, 140) : '';
        document.getElementById('desc-full').textContent = p.description || ('Produk digital ' + p.name + ' dengan proses aktivasi cepat dan garansi penuh selama masa berlangganan.');

        // Ketersediaan nyata berdasarkan data stok, bukan status dekoratif
        var availLine = document.getElementById('avail-line');
        var availText = document.getElementById('avail-text');
        var hasStock = (typeof p.stock !== 'number') || p.stock > 0;
        if (hasStock) {
          availLine.classList.remove('out');
          availText.textContent = (typeof p.stock === 'number') ? ('Tersedia \u00b7 stok ' + p.stock) : 'Tersedia';
        } else {
          availLine.classList.add('out');
          availText.textContent = 'Stok habis \u2014 chat admin untuk info re-stock';
        }

        var img = document.getElementById('ph-logo-img');
        img.src = fixImagePath(p.image_url);
        img.alt = p.name;
        img.onload = function() { img.classList.add('loaded'); };

        sumProduct.textContent = p.name;
        priceOrig.style.display = 'none';

        document.getElementById('loadingState').style.display = 'none';
        document.getElementById('pageContent').style.display = 'block';
        document.getElementById('stickyBar').style.display = 'flex';
      }

      function showError() {
        document.getElementById('loadingState').style.display = 'none';
        document.getElementById('errorState').style.display = 'block';
      }

      function loadFallback(slug) {
        activeProduct = {
          name: slug.charAt(0).toUpperCase() + slug.slice(1),
          description: 'Produk premium murah dengan proses cepat & garansi penuh.',
          base_price: 0, image_url: '', stock: 10, categories: null
        };
        activePackages = [{ id: '1', name: '1 Bulan', price: 15000, duration: '1 BULAN', is_popular: true }];
        activeInputs = [];
        renderDynamicFields();
        renderPackages();
        renderProductUI();
      }

      async function fetchProductData() {
        var slug = getSlugFromUrl();
        if (!slug) { showError(); return; }
        try {
          if (!supabaseClient) throw new Error('Supabase client null');
          var prodResult = await supabaseClient.from('products').select('*, categories(name)');
          if (prodResult.error) throw prodResult.error;
          var match = (prodResult.data || []).find(function(p) { return slugify(p.slug || p.name) === slugify(slug); });
          if (!match) { showError(); return; }
          activeProduct = match;

          var pkgResult = await supabaseClient.from('product_packages').select('*').eq('product_id', activeProduct.id);
          activePackages = (!pkgResult.error && pkgResult.data && pkgResult.data.length > 0) ? pkgResult.data : [
            { id: 'default', name: '1 Bulan', price: activeProduct.base_price || 15000, duration: '1 BULAN', is_popular: true }
          ];
          var inputResult = await supabaseClient.from('product_inputs').select('*').eq('product_id', activeProduct.id);
          activeInputs = (!inputResult.error && inputResult.data) ? inputResult.data : [];

          renderDynamicFields();
          renderPackages();
          renderProductUI();
        } catch (e) {
          console.warn('GivaStore: gagal ambil data produk, pakai fallback:', e);
          loadFallback(slug);
        }
      }

      fetchProductData();
    });

/* Tombol kembali ke atas */
    (function () {
      var b = document.getElementById('scrollTopBtn');
      if (!b) return;
      function t() { b.classList.toggle('visible', window.scrollY > 500); }
      window.addEventListener('scroll', t, { passive: true });
      t();
      b.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: 'smooth' }); });
    })();
