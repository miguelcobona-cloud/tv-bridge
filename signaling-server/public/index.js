'use strict';

/**
 * TV-Bridge — Cliente emisor web (WebRTC + WebSocket)
 *
 * Protocolo de señalización (ver server.js):
 *   register-web  → recibe tv-list
 *   signal        → { targetId, payload } hacia TV | { from, payload } desde TV
 */

// ── Configuración WebRTC ────────────────────────────────────────────────────

const ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }];

// ── Referencias DOM ─────────────────────────────────────────────────────────

const connectionStatus = document.getElementById('connection-status');
const statusDot = document.getElementById('status-dot');
const statusText = document.getElementById('status-text');
const tvListEl = document.getElementById('tv-list');
const tvCountEl = document.getElementById('tv-count');
const emptyStateEl = document.getElementById('empty-state');
const previewPanel = document.getElementById('preview-panel');
const previewWrap = document.getElementById('preview-wrap');
const previewVideo = document.getElementById('preview-video');
const previewTarget = document.getElementById('preview-target');
const btnStop = document.getElementById('btn-stop');
const toastContainer = document.getElementById('toast-container');
const mobileHintBanner = document.getElementById('mobile-hint-banner');

// ── Estado de la aplicación ─────────────────────────────────────────────────

/** @type {WebSocket | null} */
let socket = null;

/** @type {RTCPeerConnection | null} */
let peerConnection = null;

/** @type {MediaStream | null} */
let localStream = null;

/** ID de la TV con la que estamos transmitiendo */
let activeTvId = null;

/** Nombre de la TV activa (solo UI) */
let activeTvName = null;

/** TVs disponibles: Map<tvId, { id, name }> */
const availableTvs = new Map();

/** Evita reconexiones simultáneas */
let reconnectTimer = null;

// ── Detección de plataforma / captura ───────────────────────────────────────

const HTTPS_PORT = 3443;

/**
 * @returns {boolean}
 */
function isMobileDevice() {
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
}

/**
 * @returns {boolean}
 */
function isIosDevice() {
  return /iPhone|iPad|iPod/i.test(navigator.userAgent);
}

/**
 * @returns {boolean}
 */
function canUseDisplayMedia() {
  return typeof navigator.mediaDevices?.getDisplayMedia === 'function';
}

/**
 * Instrucciones para habilitar HTTPS en Android.
 * @returns {string}
 */
function getMobileHttpsSetupMessage() {
  const host =
    window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
      ? '192.168.100.10'
      : window.location.hostname;

  return [
    'Para transmitir desde Android:',
    `1) Descarga el certificado: http://${host}:3000/cert/tv-bridge.pem`,
    '2) Ajustes → Seguridad → Más ajustes de seguridad → Cifrado → Instalar certificado → Certificado CA',
    `3) Abre https://${host}:${HTTPS_PORT}`,
  ].join(' ');
}

/**
 * Explica por qué la captura no está disponible en este dispositivo/navegador.
 * @returns {string | null}
 */
function getScreenCaptureBlockReason() {
  if (isIosDevice()) {
    return 'Safari en iPhone/iPad no permite compartir pantalla desde el navegador. Usa un PC con Chrome o Edge.';
  }

  if (!window.isSecureContext) {
    const host = window.location.hostname;
    if (host !== 'localhost' && host !== '127.0.0.1') {
      return getMobileHttpsSetupMessage();
    }
  }

  if (!canUseDisplayMedia()) {
    if (isMobileDevice()) {
      return getMobileHttpsSetupMessage();
    }
    return 'Tu navegador no soporta captura de pantalla. Prueba Chrome o Edge actualizados.';
  }

  return null;
}

/**
 * Opciones de getDisplayMedia según plataforma.
 * displaySurface: 'monitor' solo existe en escritorio y rompe en móvil.
 * @returns {MediaStreamConstraints}
 */
function getDisplayMediaOptions() {
  if (isMobileDevice()) {
    return {
      video: {
        width: { ideal: window.screen.width * window.devicePixelRatio },
        height: { ideal: window.screen.height * window.devicePixelRatio },
        frameRate: { ideal: 30 },
      },
      audio: false,
    };
  }

  return {
    video: {
      cursor: 'always',
      width: { ideal: 1920 },
      height: { ideal: 1080 },
      frameRate: { ideal: 30, max: 60 },
    },
    audio: {
      echoCancellation: false,
      noiseSuppression: false,
      autoGainControl: false,
      suppressLocalAudioPlayback: true,
    },
  };
}

/**
 * Muestra un aviso persistente si el emisor móvil está en HTTP.
 */
function updateMobileHintBanner() {
  if (!mobileHintBanner) return;

  const blockReason = getScreenCaptureBlockReason();
  if (blockReason && isMobileDevice()) {
    mobileHintBanner.hidden = false;
    mobileHintBanner.textContent = blockReason;
  } else {
    mobileHintBanner.hidden = true;
  }
}

// ── Utilidades UI ───────────────────────────────────────────────────────────

/**
 * Muestra una notificación toast temporal.
 * @param {string} message
 * @param {'info' | 'error'} [variant='info']
 */
function showToast(message, variant = 'info') {
  const toast = document.createElement('div');
  toast.className = `toast toast--${variant}`;
  toast.textContent = message;
  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.remove();
  }, 4000);
}

/**
 * Actualiza el indicador de conexión WebSocket.
 * @param {'connecting' | 'connected' | 'streaming' | 'error'} state
 * @param {string} label
 */
function setConnectionUI(state, label) {
  statusText.textContent = label;
  statusDot.className = 'status-dot';

  if (state === 'connected') {
    statusDot.classList.add('status-dot--connected');
  } else if (state === 'streaming') {
    statusDot.classList.add('status-dot--streaming');
  } else if (state === 'error') {
    statusDot.classList.add('status-dot--error');
  }
}

/**
 * Renderiza la lista dinámica de TVs recibida por WebSocket.
 * @param {Array<{ id: string, name: string }>} tvs
 */
function renderTvList(tvs) {
  availableTvs.clear();
  tvListEl.innerHTML = '';

  for (const tv of tvs) {
    availableTvs.set(tv.id, tv);
  }

  const count = tvs.length;
  tvCountEl.textContent = String(count);

  if (count === 0) {
    emptyStateEl.hidden = false;
    tvListEl.hidden = true;
    return;
  }

  emptyStateEl.hidden = true;
  tvListEl.hidden = false;

  const isStreaming = activeTvId !== null;

  for (const tv of tvs) {
    const li = document.createElement('li');
    li.setAttribute('role', 'listitem');

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'tv-item';
    btn.dataset.tvId = tv.id;
    btn.disabled = isStreaming;

    if (activeTvId === tv.id) {
      btn.classList.add('tv-item--loading');
    }

    btn.innerHTML = `
      <img class="tv-item__icon" src="icons/icon-tv.svg" width="32" height="32" alt="">
      <span class="tv-item__body">
        <span class="tv-item__name">${escapeHtml(tv.name)}</span>
        <span class="tv-item__action">${isStreaming ? 'Transmisión activa' : 'Toca para transmitir'}</span>
      </span>
      <span class="tv-item__arrow" aria-hidden="true">›</span>
    `;

    btn.addEventListener('click', () => startTransmission(tv.id, tv.name));
    li.appendChild(btn);
    tvListEl.appendChild(li);
  }
}

/**
 * Escapa HTML para prevenir XSS en nombres de TV.
 * @param {string} str
 * @returns {string}
 */
function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

// ── WebSocket ───────────────────────────────────────────────────────────────

/**
 * Construye la URL del WebSocket según el origen actual.
 * @returns {string}
 */
function getWebSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}`;
}

/**
 * Conecta al servidor de señalización y se registra como emisor web.
 */
function connectWebSocket() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return;
  }

  setConnectionUI('connecting', 'Conectando…');

  socket = new WebSocket(getWebSocketUrl());

  socket.addEventListener('open', () => {
    setConnectionUI('connected', 'Conectado');
    socket.send(JSON.stringify({ type: 'register-web' }));
  });

  socket.addEventListener('message', (event) => {
    handleServerMessage(event.data);
  });

  socket.addEventListener('close', () => {
    if (activeTvId) {
      stopTransmission(false);
    }
    setConnectionUI('error', 'Desconectado');
    scheduleReconnect();
  });

  socket.addEventListener('error', () => {
    setConnectionUI('error', 'Error de conexión');
  });
}

/**
 * Programa un reintento de conexión WebSocket.
 */
function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectWebSocket();
  }, 3000);
}

/**
 * Envía un mensaje JSON al servidor si el socket está abierto.
 * @param {object} payload
 */
function sendToServer(payload) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload));
  }
}

/**
 * Retransmite señalización WebRTC hacia una TV concreta.
 * @param {string} targetId
 * @param {object} signalPayload
 */
function sendSignal(targetId, signalPayload) {
  sendToServer({
    type: 'signal',
    targetId,
    payload: signalPayload,
  });
}

/**
 * Procesa mensajes entrantes del servidor de señalización.
 * @param {string} rawData
 */
function handleServerMessage(rawData) {
  let message;

  try {
    message = JSON.parse(rawData);
  } catch {
    showToast('Mensaje del servidor inválido.', 'error');
    return;
  }

  switch (message.type) {
    case 'tv-list':
      renderTvList(message.tvs ?? []);
      break;

    case 'signal':
      handleIncomingSignal(message.from, message.payload);
      break;

    case 'error':
      showToast(message.message ?? 'Error desconocido.', 'error');
      break;

    default:
      console.warn('Tipo de mensaje desconocido:', message.type);
  }
}

/**
 * Ajusta el contenedor de vista previa al aspect ratio real del stream capturado
 * para evitar recortes o bandas forzadas a 16:9.
 */
function syncPreviewAspectRatio() {
  if (!previewWrap || !previewVideo) return;

  const width = previewVideo.videoWidth;
  const height = previewVideo.videoHeight;

  if (width > 0 && height > 0) {
    previewWrap.style.aspectRatio = `${width} / ${height}`;
  }
}

/**
 * Restaura el contenedor de vista previa al salir de transmisión.
 */
function resetPreviewAspectRatio() {
  if (!previewWrap) return;
  previewWrap.style.aspectRatio = '';
}

// ── WebRTC ──────────────────────────────────────────────────────────────────

/**
 * Inicia la captura de pantalla y la negociación WebRTC con la TV seleccionada.
 * @param {string} tvId
 * @param {string} tvName
 */
async function startTransmission(tvId, tvName) {
  if (activeTvId) {
    showToast('Ya hay una transmisión activa.', 'error');
    return;
  }

  if (!canUseDisplayMedia()) {
    const reason = getScreenCaptureBlockReason();
    showToast(reason ?? 'Tu navegador no soporta captura de pantalla.', 'error');
    return;
  }

  const blockReason = getScreenCaptureBlockReason();
  if (blockReason) {
    showToast(blockReason, 'error');
    return;
  }

  activeTvId = tvId;
  activeTvName = tvName;
  renderTvList(Array.from(availableTvs.values()));

  try {
    // Captura de pantalla/ventana — el usuario elige qué compartir
    localStream = await navigator.mediaDevices.getDisplayMedia(getDisplayMediaOptions());

    // Si el usuario detiene la captura desde el diálogo del SO
    localStream.getVideoTracks()[0].addEventListener('ended', () => {
      stopTransmission(true);
    });

    previewVideo.srcObject = localStream;
    previewVideo.onloadedmetadata = syncPreviewAspectRatio;
    previewVideo.onresize = syncPreviewAspectRatio;
    previewPanel.hidden = false;
    previewTarget.textContent = `Transmitiendo a: ${tvName}`;
    setConnectionUI('streaming', 'Transmitiendo');

    await createPeerConnectionAndOffer(tvId);
  } catch (err) {
    console.error('Error al iniciar transmisión:', err);
    activeTvId = null;
    activeTvName = null;
    previewPanel.hidden = true;
    renderTvList(Array.from(availableTvs.values()));
    setConnectionUI('connected', 'Conectado');

    if (err.name === 'NotAllowedError') {
      showToast('Permiso de captura denegado.', 'error');
    } else if (err.name === 'NotSupportedError' || err.name === 'SecurityError') {
      showToast(getScreenCaptureBlockReason() ?? 'Captura no disponible en este dispositivo.', 'error');
    } else {
      showToast('No se pudo iniciar la transmisión.', 'error');
    }
  }
}

/**
 * Crea el RTCPeerConnection, añade tracks y envía la oferta SDP a la TV.
 * @param {string} tvId
 */
async function createPeerConnectionAndOffer(tvId) {
  peerConnection = new RTCPeerConnection({ iceServers: ICE_SERVERS });

  // Añadir cada track de video capturado al peer connection
  for (const track of localStream.getTracks()) {
    peerConnection.addTrack(track, localStream);
  }

  // Enviar candidatos ICE al servidor → TV
  peerConnection.addEventListener('icecandidate', (event) => {
    if (event.candidate) {
      sendSignal(tvId, {
        kind: 'ice-candidate',
        candidate: event.candidate.toJSON(),
      });
    }
  });

  peerConnection.addEventListener('connectionstatechange', () => {
    const state = peerConnection?.connectionState;

    if (state === 'connected') {
      showToast(`Conectado con ${activeTvName}`, 'info');
    } else if (state === 'failed' || state === 'disconnected') {
      showToast('Conexión WebRTC perdida.', 'error');
      stopTransmission(true);
    }
  });

  // Crear y enviar la oferta SDP
  const offer = await peerConnection.createOffer();
  await peerConnection.setLocalDescription(offer);

  sendSignal(tvId, {
    kind: 'offer',
    sdp: offer.sdp,
  });
}

/**
 * Procesa señalización WebRTC entrante desde la TV (answer / ICE).
 * @param {string} fromTvId
 * @param {object} payload
 */
async function handleIncomingSignal(fromTvId, payload) {
  if (!activeTvId || fromTvId !== activeTvId || !peerConnection) {
    return;
  }

  try {
    if (payload.kind === 'answer') {
      await peerConnection.setRemoteDescription({
        type: 'answer',
        sdp: payload.sdp,
      });
    } else if (payload.kind === 'ice-candidate' && payload.candidate) {
      await peerConnection.addIceCandidate(payload.candidate);
    }
  } catch (err) {
    console.error('Error procesando señalización:', err);
    showToast('Error en la negociación WebRTC.', 'error');
  }
}

/**
 * Detiene la transmisión activa y libera recursos.
 * @param {boolean} notify
 */
function stopTransmission(notify) {
  if (localStream) {
    for (const track of localStream.getTracks()) {
      track.stop();
    }
    localStream = null;
  }

  if (peerConnection) {
    peerConnection.close();
    peerConnection = null;
  }

  previewVideo.srcObject = null;
  previewVideo.onloadedmetadata = null;
  previewVideo.onresize = null;
  resetPreviewAspectRatio();
  previewPanel.hidden = true;
  activeTvId = null;
  activeTvName = null;

  if (socket?.readyState === WebSocket.OPEN) {
    setConnectionUI('connected', 'Conectado');
  }

  renderTvList(Array.from(availableTvs.values()));

  if (notify) {
    showToast('Transmisión detenida.', 'info');
  }
}

// ── Inicialización ──────────────────────────────────────────────────────────

btnStop.addEventListener('click', () => stopTransmission(true));

updateMobileHintBanner();
connectWebSocket();
