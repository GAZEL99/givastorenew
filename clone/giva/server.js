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

console.log('[archive] This duplicate server is kept for reference only. Use root server.js for production.');

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain; charset=UTF-8' });
  res.end('This copy is archived under clone/giva and is not the production server.');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Archived duplicate server started on port', PORT);
});
