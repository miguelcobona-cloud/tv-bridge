# TV-Bridge Signaling Server

Lightweight **Node.js** signaling server and **web sender UI** for the TV-Bridge stack.

- No Express, no Socket.io — native `http` / `https` + [`ws`](https://github.com/websockets/ws)
- Serves static files from `public/`
- WebSocket registry for Android TVs and web/phone senders
- Auto-generated self-signed HTTPS on port `3443` (via `selfsigned`)

## Quick start

```bash
npm install
npm start
```

| Endpoint | URL |
|----------|-----|
| HTTP | `http://localhost:3000` |
| HTTPS | `https://localhost:3443` |

## Environment

Requires **Node.js 18+**.

TLS certificate files are written to `certs/` on first run (gitignored). Delete that folder to regenerate.

## Protocol

See the [root README](../README.md#signaling-protocol) for message types.

## License

[MIT](../LICENSE)
