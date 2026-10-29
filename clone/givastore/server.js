const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 8080;
console.log('[archive] This duplicate server is kept for reference only. Use root server.js for production.');

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain; charset=UTF-8' });
  res.end('This copy is archived under clone/givastore and is not the production server.');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Archived duplicate server started on port', PORT);
});
