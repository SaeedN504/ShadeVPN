# libbox integration contract

The app does not fetch an unpinned native binary during a build. Before the
first real VPN connection, pin a reviewed libbox AAR by commit or release,
record its SHA-256 here, and add it under `app/libs/`.

Required adapter steps:

1. Implement `LibboxRuntime` using the pinned libbox API.
2. Pass the Android-owned `VpnService` TUN descriptor to libbox.
3. Translate libbox callbacks into `EngineState`.
4. Implement `verifyDataPlane()` with a real routed packet round-trip.
5. Keep secrets out of logs and never return `verified = true` on handshake
   success alone.

The rest of the app depends only on `VpnEngine`, so changing the native engine
later does not leak through the UI or orchestration code.
