# ShadeVPN Android: architecture v2

This document replaces the v1 design. v1 tried to hand-write VLESS + Reality
in Rust. That is abandoned. See "Why v1 died" below.

## Layers

```
Compose UI            Connect | Catalog | Events | Settings
      |
Kotlin orchestration  ConnectionOrchestrator, phase state machine,
                      health prober, carrier resolver, catalog scorer
      |
libbox (sing-box)     transport engine, shipped as an AAR
      |
ShadeVpnService       owns the TUN descriptor, foreground service,
                      revoke handling, kill switch
```

Rules that do not bend:

1. The UI never touches sockets or the engine. It observes a `StateFlow`.
2. Only `ShadeVpnService` owns the TUN file descriptor.
3. `Connected` is reported only after the engine reports a live outbound
   AND a data-plane round-trip is verified. A handshake alone is not enough.
4. No secret material is ever logged, persisted in plaintext, or rendered.

## Transports

Supplied by the engine, not by us:

| Lane | Role |
| --- | --- |
| VLESS + Reality over TCP | primary, matches the Xray panel |
| VLESS + XHTTP / WebSocket | CDN-fronted fallback |
| Shadowsocks 2022 | compatibility lane |
| Trojan | compatibility lane |

Removed permanently: OpenVPN, MTProto, MASQUE H2, MASQUE H3, Hysteria2,
WireGuard. UDP and QUIC lanes are not reliable on the target networks and
MASQUE has no server support in the panel.

## Why v1 died

v1 owned a Rust cdylib that was going to implement Reality-compatible TLS,
VLESS framing, and a packet pump by hand. Three problems killed it:

- `perform_reality_handshake()` was a permanent `Unsupported` error. The
  hard part was never started.
- Reality's value is that its TLS fingerprint is indistinguishable from a
  real browser. A hand-rolled ClientHello is *more* identifiable than no
  obfuscation at all. Getting this wrong is worse than not shipping.
- Everything downstream (packet pump, fallback racing, leak protection,
  reconnect) was blocked behind it.

sing-box already solves all of it, is audited by real adversarial use, and
speaks the protocols the ShadeVPN panel already serves.

## The Catalog

The differentiator. Every other client treats a config as an opaque string
you paste and pray over. Here a config is a tracked entity with measured
history.

Each catalog entry records:

- the sanitized profile (secrets held separately, encrypted)
- outcome of every connection attempt: phase reached, failure reason
- handshake latency and data-plane RTT, p50 and p95
- success rate over a rolling window
- **the carrier and ASN each observation came from**
- last-known-good timestamp, per carrier

Carrier tagging is the point. On the target networks a profile that works
on one mobile carrier is frequently dead on another, and every existing
client makes the user rediscover this by hand. The Catalog answers "what
actually worked, on my carrier, recently" and can auto-pick on that basis.

### Sharing

Sharing is opt-in and scoped, never a public firehose.

- Copy exports a standard `vless://` / `ss://` URI, so entries stay
  portable to any other client.
- Circles: a named group with a shared key. Members publish entries and
  health observations to each other, and nobody outside can read them.
- Publishing an entry is an explicit per-entry action, never a default.

This is a deliberate design constraint, not caution for its own sake.
Publicly posted Reality endpoints get harvested and blocked within days,
which would make the Catalog's health data worthless. Small trusted circles
keep endpoints alive long enough for the scoring to mean something.

## Build queue

| id | task |
| --- | --- |
| SVA-02 | architecture contract (this document) |
| SVA-03 | delete the v1 Rust crate, native CI, and JNI bridge |
| SVA-04 | libbox dependency, engine boundary interface |
| SVA-05 | profile model, URI import, sanitized secret storage |
| SVA-06 | engine config generation from a profile |
| SVA-07 | ShadeVpnService and TUN wiring against libbox |
| SVA-08 | ConnectionOrchestrator and phase state machine |
| SVA-09 | data-plane verification before reporting Connected |
| SVA-10 | carrier and ASN resolver |
| SVA-11 | Catalog store and scoring engine |
| SVA-12 | Catalog tab UI |
| SVA-13 | Connect tab UI |
| SVA-14 | Events tab |
| SVA-15 | Settings tab, kill switch, per-app routing |
| SVA-16 | Circles: shared-key export and import |
| SVA-17 | signed release pipeline |
