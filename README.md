# ShadeVPN Android

Android client and native transport layer for ShadeVPN.

## Milestone 2

This repo now has an **honest orchestration scaffold** for the first real transport target: **VLESS + Reality over TCP**.

What exists now:

- Kotlin connection state model with explicit control-plane vs data-plane phases
- `VpnService` lifecycle scaffold with TUN ownership staying on Android
- VLESS + Reality profile parser for `tcp`, `ws`, and `xhttp`
- Rust/JNI lane-preparation skeleton for the Reality lane
- Compose debug surface that refuses to show connected until the data-plane probe is marked successful

What still does **not** exist yet:

- Real socket handshake
- Packet pump across JNI
- Actual data-plane probe
- Kill switch, leak protection, reconnect, fallback racing

### Repository split

- `ShadeVPN`: TypeScript/React/Xray server panel and PWA.
- `ShadeVPN-Android`: Android client, VpnService/TUN owner, and native transport adapters.

### Transport policy for Iran

1. VLESS + Reality over TCP: primary, because the existing Xray panel supports it and UDP/QUIC availability is inconsistent on Iranian networks.
2. MASQUE H2 over TCP: fallback after the primary adapter is proven.
3. Shadowsocks 2022: compatibility lane, never preferred over Reality.
4. Hysteria2/MASQUE H3: experimental until UDP/QUIC passes a real network test.

OpenVPN and MTProto are intentionally out of scope.

## Next build target

Milestone 3 should replace the fake lane builder with:

- actual Reality handshake state
- packet pump JNI surface
- verified packet round-trip through TUN
- structured failure reasons surfaced back to UI

## Security rules

- Never disable TLS verification in production code.
- Never log UUIDs, passwords, private keys, subscription tokens, or raw profile URLs.
- Keep GPL components out of this MIT-licensed client.
- A connection is healthy only after a real data-plane probe succeeds.
