// Express server to serve the built React app and proxy backend requests
const express = require('express');
const path = require('path');
const proxy = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware to parse JSON
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Proxy API requests to backend
const backendUrl = process.env.BACKEND_URL || 'http://localhost:8080';

app.use('/api', (req, res, next) => {
  // Forward to backend
  const proxyReq = require('http').request({
    hostname: new URL(backendUrl).hostname,
    port: new URL(backendUrl).port,
    path: req.url,
    method: req.method,
    headers: req.headers
  }, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res);
  });

  if (req.method !== 'GET') {
    req.pipe(proxyReq);
  } else {
    proxyReq.end();
  }
});

// Serve static files from React build folder
app.use(express.static(path.join(__dirname, 'build')));

// Handle SPA routing - send index.html for all non-file requests
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'build', 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server is running on port ${PORT}`);
});
