const fs = require('fs');
const os = require('os');
const path = require('path');
const selfsigned = require('selfsigned');

const CERTS_DIR = path.join(__dirname, 'certs');
const CERT_PATH = path.join(CERTS_DIR, 'dev-cert.pem');
const KEY_PATH = path.join(CERTS_DIR, 'dev-key.pem');
const META_PATH = path.join(CERTS_DIR, 'meta.json');

/**
 * @returns {string[]}
 */
function getLocalIPv4Addresses() {
  const ips = new Set();
  const interfaces = os.networkInterfaces();

  for (const entries of Object.values(interfaces)) {
    for (const entry of entries ?? []) {
      if (entry.family === 'IPv4') {
        ips.add(entry.address);
      }
    }
  }

  return [...ips];
}

/**
 * @param {string[]} ips
 * @returns {Array<{ type: number, value?: string, ip?: string }>}
 */
function buildAltNames(ips) {
  const altNames = [
    { type: 2, value: 'localhost' },
    { type: 7, ip: '127.0.0.1' },
  ];

  for (const ip of ips) {
    if (ip !== '127.0.0.1') {
      altNames.push({ type: 7, ip });
    }
  }

  return altNames;
}

/**
 * @param {string[]} ips
 */
function writeCertificateFiles(ips) {
  fs.mkdirSync(CERTS_DIR, { recursive: true });

  const attrs = [{ name: 'commonName', value: 'TV-Bridge Local' }];
  const pems = selfsigned.generate(attrs, {
    days: 825,
    keySize: 2048,
    algorithm: 'sha256',
    extensions: [
      {
        name: 'basicConstraints',
        cA: false,
      },
      {
        name: 'keyUsage',
        digitalSignature: true,
        keyEncipherment: true,
      },
      {
        name: 'extKeyUsage',
        serverAuth: true,
      },
      {
        name: 'subjectAltName',
        altNames: buildAltNames(ips),
      },
    ],
  });

  fs.writeFileSync(KEY_PATH, pems.private, 'utf8');
  fs.writeFileSync(CERT_PATH, pems.cert, 'utf8');
  fs.writeFileSync(
    META_PATH,
    JSON.stringify({ ips, createdAt: new Date().toISOString() }, null, 2),
    'utf8',
  );

  return {
    key: pems.private,
    cert: pems.cert,
  };
}

/**
 * Regenera el certificado si cambió la IP local (DHCP).
 * @returns {{ key: string, cert: string }}
 */
function loadHttpsCredentials() {
  const currentIps = getLocalIPv4Addresses().sort();
  let storedIps = [];

  if (fs.existsSync(META_PATH)) {
    try {
      const meta = JSON.parse(fs.readFileSync(META_PATH, 'utf8'));
      storedIps = Array.isArray(meta.ips) ? [...meta.ips].sort() : [];
    } catch {
      storedIps = [];
    }
  }

  const hasFiles = fs.existsSync(CERT_PATH) && fs.existsSync(KEY_PATH);
  const ipsMatch =
    storedIps.length === currentIps.length &&
    storedIps.every((ip, index) => ip === currentIps[index]);

  if (hasFiles && ipsMatch) {
    return {
      key: fs.readFileSync(KEY_PATH, 'utf8'),
      cert: fs.readFileSync(CERT_PATH, 'utf8'),
    };
  }

  return writeCertificateFiles(currentIps);
}

/**
 * @returns {string}
 */
function getCertPem() {
  loadHttpsCredentials();
  return fs.readFileSync(CERT_PATH, 'utf8');
}

module.exports = {
  CERTS_DIR,
  getLocalIPv4Addresses,
  loadHttpsCredentials,
  getCertPem,
};
