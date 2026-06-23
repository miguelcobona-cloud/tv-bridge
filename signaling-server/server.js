const http = require('http');

const https = require('https');

const fs = require('fs');

const path = require('path');

const crypto = require('crypto');
const { loadHttpsCredentials, getCertPem, getLocalIPv4Addresses } = require('./https-certs');
const { WebSocketServer, WebSocket } = require('ws');



const HTTP_PORT = 3000;

const HTTPS_PORT = 3443;

const PUBLIC_DIR = path.join(__dirname, 'public');

const SENDER_APK_PATH = path.join(
  __dirname,
  '..',
  'android-phone-sender',
  'app',
  'build',
  'outputs',
  'apk',
  'release',
  'app-release.apk',
);



/** Tipos MIME soportados para archivos estáticos en public/ */

const MIME_TYPES = {

  '.html': 'text/html; charset=utf-8',

  '.css': 'text/css; charset=utf-8',

  '.js': 'application/javascript; charset=utf-8',

  '.json': 'application/json; charset=utf-8',

  '.png': 'image/png',

  '.jpg': 'image/jpeg',

  '.jpeg': 'image/jpeg',

  '.svg': 'image/svg+xml',

  '.ico': 'image/x-icon',

  '.webp': 'image/webp',

  '.woff2': 'font/woff2',

  '.apk': 'application/vnd.android.package-archive',

};



/**

 * Mapeo en RAM de TVs conectadas.

 * Clave: tvId | Valor: { id, name, ws }

 */

const connectedTvs = new Map();



/** Emisores web conectados que reciben la lista de TVs en tiempo real */

const webClients = new Set();



/**

 * Resuelve la ruta del archivo solicitado dentro de public/ evitando path traversal.

 */

function resolvePublicPath(urlPath) {

  // Usar barras URL; path.normalize('/') en Windows devuelve '\\' y rompe la raíz.

  const urlStyle = urlPath.split('?')[0].replace(/\\/g, '/');

  const withoutTraversal = urlStyle.replace(/^(\.\.\/)+/, '');

  const relativePath =

    withoutTraversal === '/' || withoutTraversal === ''

      ? 'index.html'

      : withoutTraversal.replace(/^\//, '');

  const filePath = path.join(PUBLIC_DIR, relativePath);

  const resolvedPublic = path.resolve(PUBLIC_DIR);

  const resolvedFile = path.resolve(filePath);



  if (!resolvedFile.startsWith(resolvedPublic)) {

    return null;

  }



  return resolvedFile;

}



/**

 * Sirve un archivo estático desde public/ usando el módulo http nativo de Node.js.

 */

function serveApkDownload(res) {
  fs.stat(SENDER_APK_PATH, (statError, stats) => {
    if (statError || !stats.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('APK no encontrado. Compila android-phone-sender con .\\gradlew.bat assembleRelease');
      return;
    }

    fs.readFile(SENDER_APK_PATH, (readError, content) => {
      if (readError) {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Error al leer APK');
        return;
      }

      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="tv-bridge-emisor.apk"',
        'Content-Length': content.length,
      });
      res.end(content);
    });
  });
}

function serveStaticFile(req, res) {
  const urlPath = req.url.split('?')[0];

  if (urlPath === '/cert/tv-bridge.pem') {
    res.writeHead(200, {
      'Content-Type': 'application/x-pem-file; charset=utf-8',
      'Content-Disposition': 'attachment; filename="tv-bridge.pem"',
    });
    res.end(getCertPem());
    return;
  }

  if (urlPath === '/downloads/tv-bridge-emisor.apk') {
    serveApkDownload(res);
    return;
  }

  const filePath = resolvePublicPath(urlPath);



  if (!filePath) {

    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });

    res.end('Forbidden');

    return;

  }



  fs.stat(filePath, (statError, stats) => {

    if (statError || !stats.isFile()) {

      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });

      res.end('Not Found');

      return;

    }



    const extension = path.extname(filePath).toLowerCase();

    const contentType = MIME_TYPES[extension] || 'application/octet-stream';



    fs.readFile(filePath, (readError, content) => {

      if (readError) {

        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });

        res.end('Internal Server Error');

        return;

      }



      res.writeHead(200, { 'Content-Type': contentType });

      res.end(content);

    });

  });

}



/**

 * Envía un mensaje JSON a un socket si sigue abierto.

 */

function sendJson(ws, payload) {

  if (ws.readyState === WebSocket.OPEN) {

    ws.send(JSON.stringify(payload));

  }

}



/**

 * Construye la lista pública de TVs (sin referencias al socket).

 */

function buildTvList() {

  return Array.from(connectedTvs.values()).map(({ id, name }) => ({ id, name }));

}



/**

 * Notifica a todos los emisores web la lista actualizada de TVs conectadas.

 */

function broadcastTvList() {

  const message = { type: 'tv-list', tvs: buildTvList() };



  for (const client of webClients) {

    sendJson(client, message);

  }

}



/**

 * Retransmite señalización WebRTC desde el emisor web hacia una TV concreta.

 */

function relayWebToTv(sourceWs, message) {

  const { targetId, payload } = message;



  if (!targetId || payload === undefined) {

    sendJson(sourceWs, {

      type: 'error',

      message: 'Se requiere targetId y payload para enviar señalización a la TV.',

    });

    return;

  }



  const targetTv = connectedTvs.get(targetId);



  if (!targetTv) {

    sendJson(sourceWs, {

      type: 'error',

      message: `La TV con id "${targetId}" no está conectada.`,

    });

    return;

  }



  sendJson(targetTv.ws, {

    type: 'signal',

    from: 'web',

    payload,

  });

}



/**

 * Retransmite señalización WebRTC desde una TV hacia los emisores web conectados.

 */

function relayTvToWeb(sourceWs, message) {

  const { payload } = message;



  if (payload === undefined) {

    sendJson(sourceWs, {

      type: 'error',

      message: 'Se requiere payload para enviar señalización al emisor web.',

    });

    return;

  }



  const outbound = {

    type: 'signal',

    from: sourceWs.tvId,

    payload,

  };



  for (const client of webClients) {

    sendJson(client, outbound);

  }

}



/**

 * Procesa mensajes entrantes del protocolo de señalización TV-Bridge.

 */

function handleMessage(ws, rawData) {

  let message;



  try {

    message = JSON.parse(rawData.toString());

  } catch {

    sendJson(ws, { type: 'error', message: 'Mensaje JSON inválido.' });

    return;

  }



  switch (message.type) {

    case 'register-web': {

      ws.clientType = 'web';

      webClients.add(ws);

      sendJson(ws, { type: 'tv-list', tvs: buildTvList() });

      break;

    }



    case 'register-tv': {

      const name = typeof message.name === 'string' ? message.name.trim() : '';



      if (!name) {

        sendJson(ws, { type: 'error', message: 'El nombre de la TV es obligatorio.' });

        return;

      }



      const tvId = crypto.randomUUID();

      ws.clientType = 'tv';

      ws.tvId = tvId;



      connectedTvs.set(tvId, { id: tvId, name, ws });



      sendJson(ws, { type: 'registered', id: tvId, name });

      broadcastTvList();

      break;

    }



    case 'signal': {

      if (ws.clientType === 'web') {

        relayWebToTv(ws, message);

      } else if (ws.clientType === 'tv') {

        relayTvToWeb(ws, message);

      } else {

        sendJson(ws, {

          type: 'error',

          message: 'Debes registrarte como web o TV antes de enviar señalización.',

        });

      }

      break;

    }



    default: {

      sendJson(ws, { type: 'error', message: `Tipo de mensaje desconocido: ${message.type}` });

    }

  }

}



/**

 * Limpia el estado en memoria cuando un cliente WebSocket se desconecta.

 */

function handleDisconnect(ws) {

  if (ws.clientType === 'web') {

    webClients.delete(ws);

    return;

  }



  if (ws.clientType === 'tv' && ws.tvId) {

    connectedTvs.delete(ws.tvId);

    broadcastTvList();

  }

}



/**

 * Registra manejadores WebSocket en un servidor HTTP(S).

 * @param {import('http').Server | import('https').Server} server

 */

function attachWebSocketServer(server) {

  const wss = new WebSocketServer({ server });



  wss.on('connection', (ws) => {

    ws.clientType = null;

    ws.tvId = null;



    ws.on('message', (data) => handleMessage(ws, data));

    ws.on('close', () => handleDisconnect(ws));

    ws.on('error', () => handleDisconnect(ws));

  });

}



/**
 * Certificado HTTPS local con SAN para la IP de la red (requerido en Android).
 */
const httpsCredentials = loadHttpsCredentials();



if (!fs.existsSync(PUBLIC_DIR)) {

  fs.mkdirSync(PUBLIC_DIR, { recursive: true });

}



const httpServer = http.createServer(serveStaticFile);

attachWebSocketServer(httpServer);



const httpsServer = https.createServer(httpsCredentials, serveStaticFile);

attachWebSocketServer(httpsServer);



httpServer.listen(HTTP_PORT, () => {

  console.log(`TV-Bridge HTTP  → http://localhost:${HTTP_PORT} (TV + emisor en PC)`);

});



httpsServer.listen(HTTPS_PORT, () => {
  const ips = getLocalIPv4Addresses().filter((ip) => ip !== '127.0.0.1');
  console.log(`TV-Bridge HTTPS → https://localhost:${HTTPS_PORT} (emisor móvil)`);
  for (const ip of ips) {
    console.log(`  Celular: https://${ip}:${HTTPS_PORT}`);
    console.log(`  Certificado CA: http://${ip}:${HTTP_PORT}/cert/tv-bridge.pem`);
  }
  console.log('En Android: descarga el .pem por HTTP, instálalo como certificado CA y luego abre HTTPS.');
});

