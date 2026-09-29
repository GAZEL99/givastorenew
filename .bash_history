cd public
cd giva
python -m http.server 8080
apk add python3
apk update && apk upgrade
apk add   php   php-fpm   php-mysqli   php-pdo   php-pdo_mysql   php-curl   php-mbstring   php-openssl   php-json   php-xml   php-zip   php-fileinfo   php-session   nodejs   npm   git   curl   wget   unzip   nano   bash   nginx
cd givastore
pwd
cd ~
rm -rf .git
ls ~
cd ~/givastore
pwd
git init
git branch -M main
cat > .gitignore << 'EOF'
node_modules/
.DS_Store
*.log
EOF

git add .
git config --global user.email "gilangar940@gmail.com"
git config --global user.name "GAZEL99"
git commit -m "update givastore"
https://github.com/GAZEL99/givastore.git
https://github.com/GAZEL99/givastorenew
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -u origin main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -u origin main
cd /public/givastore
git remote -v
git push -u origin main
git push -u origin main
git push -u origin main
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git push
cd givastore
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git push
ls -la
git init
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
cd ~/givastore
git init
git add .
git commit -m "Setup Vercel: SSR SEO via serverless function"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
cd ~/givastore
git add vercel.json
git commit -m "Fix vercel.json: remove invalid handle filesystem entry"
git push origin main
cd ~/givastore
sed -i '/class="brand-mark"/d' index.html kontak.html pay.html testimoni.html cara-order.html lacak-pesanan.html templates/product-template.html product/*/index.html
grep -rl 'class="brand-mark"' *.html templates/*.html product/*/index.html
cat > ~/givastore/vercel.json << 'EOF'
{
  "rewrites": [
    { "source": "/pay", "destination": "/pay.html" },
    { "source": "/product/:slug", "destination": "/api/product-page?slug=:slug" },
    { "source": "/product/:slug/", "destination": "/api/product-page?slug=:slug" }
  ],
  "headers": [
    {
      "source": "/assets/(.*)",
      "headers": [{ "key": "Cache-Control", "value": "public, max-age=604800" }]
    },
    {
      "source": "/video/(.*)",
      "headers": [{ "key": "Cache-Control", "value": "public, max-age=2592000" }]
    },
    {
      "source": "/(.*)",
      "headers": [
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "X-Frame-Options", "value": "SAMEORIGIN" },
        { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" }
      ]
    }
  ]
}
EOF

cd ~/givastore
git add .
git commit -m "Hapus logo video G, fix 404 di /pay"
git push origin main
cd givastore
git add . && git commit -m "fix wa dobel" && git push origin main
git init
git add .
git commit -m "fix wa dobel + fix data email hilang"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
git init
git add .
git commit -m "fix wa dobel + fix data email hilang"
git branch -M main
git remote add origin https://github.com/GAZEL99/givastorenew.git
git push -f origin main
cd givastorenew
pwd
