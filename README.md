# ShadeVPN Android

Android client and native transport layer for ShadeVPN.

## First milestone

This milestone establishes a clean Kotlin/Compose + Rust/JNI foundation. It does not claim a working VPN tunnel yet. The first production transport target is **VLESS + Reality over TCP**, followed by a TCP fallback and optional QUIC transports after real carrier testing.

### Repository split

- `ShadeVPN`: TypeScript/React/Xray server panel and PWA.
- `ShadeVPN-Android`: Android client, VpnService/TUN owner, and native transport adapters.

### Scope

- Android Kotlin + Jetpack Compose shell
- Android `VpnService` declaration and permission flow entry point
- Rust native crate with a deliberately small JNI boundary
- CI for Android checks and Rust tests
- ABI targets: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Secret-redacted diagnostics

### Transport policy for Iran

1. VLESS + Reality over TCP: primary, because the existing Xray panel supports it and UDP/QUIC availability is inconsistent on Iranian networks.
2. MASQUE H2 over TCP: fallback after the primary adapter is proven.
3. Shadowsocks 2022: compatibility lane, never preferred over Reality.
4. Hysteria2/MASQUE H3: experimental until UDP/QUIC passes a real network test.

OpenVPN and MTProto are intentionally out of scope. OpenVPN introduces an incompatible GPL dependency boundary for this client, while MTProto is not a full-device TUN transport.

## Build prerequisites

- JDK 17
- Android Studio Ladybug or newer
- Android SDK 35 and NDK 27+
- Rust stable and Android targets
- `cargo fmt` and `cargo clippy`

## Local checks

```bash
./gradlew test lintDebug assembleDebug
cargo fmt --manifest-path native/Cargo.toml -- --check
cargo test --manifest-path native/Cargo.toml
```

The first milestone should install and launch on a clean emulator, show the VPN permission entry point, and report whether the native library loaded. It must not present a fake connected state.

## Security rules

- Never disable TLS verification in production code.
- Never log UUIDs, passwords, private keys, subscription tokens, or raw profile URLs.
- Keep GPL components out of this MIT-licensed client.
- A connection is healthy only after a real data-plane probe succeeds.
