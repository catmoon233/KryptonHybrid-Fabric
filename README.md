Krypton Hybrid
====
[![](https://badges.moddingx.org/curseforge/downloads/1474749)](https://www.curseforge.com/minecraft/mc-mods/krypton-hybrid)
[![License: LGPL-3.0](https://img.shields.io/badge/License-LGPL--3.0-blue.svg)](LICENSE)

[![curseforge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/curseforge_vector.svg)](https://www.curseforge.com/minecraft/mc-mods/krypton-hybrid)
[![github](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg)](https://github.com/dreamforSh/KryptonHybrid)

## Overview
Krypton Hybrid is a fork based on [Krypton fnp](https://github.com/404Setup/KryptonFNP) legacy（1.19.2),Completed the missing optimizations in the Krypton fnp lite version, added support for the zstd compression algorithm, and made improvements to the original encoding and Netty program processing.while retaining the features of [Krypton fnp](https://github.com/404Setup/KryptonFNP) and RecastLib.

## Differentiation
1. Compatibility support for hybrid servers is offered.

2. Commands are provided to monitor traffic usage.

3. This project prioritizes using zstd as the primary compression algorithm, while also providing optimizations for entity tracking and communication encoding from the original version.

## Technical Details

### 1. Network Compression Pipeline

Krypton Hybrid replaces Minecraft's default zlib codec by injecting into `Connection.setupCompression`.

| Feature | Detail |
|---|---|
| Primary algorithm | Zstd via `zstd-jni` (native) |
| Fallback algorithm | zlib via `velocity-native` |
| Level range | 1 – 22 (default 3) |
| Multi-threaded | `workers` ≥ 1 activates native thread pool |
| Dictionary | Optional `.zdict` pre-trained file, fail-open or fail-fast |
| Advanced tuning | `overlap_log`, `job_size`, `strategy`, `LDM` + `window_log` |

> **Both server and client must use the same algorithm.** A mismatch corrupts the session immediately.

---

### 2. Light Payload Optimization — Uniform-RLE

Sky-light sections above the surface are almost always uniform (`0xFF` = all-15). Instead of writing 2 048 bytes per uniform layer, Krypton encodes them as 2 bytes.

**Wire format** (`ClientboundLightUpdatePacketData`):

```
[0x4B]            – Krypton marker (1 byte)
[skyYMask]        – BitSet
[blockYMask]      – BitSet
[emptySkyYMask]   – BitSet
[emptyBlockYMask] – BitSet
[skyCount]        – VarInt
For each sky DataLayer:
  [0x01] + 1 byte     – uniform (all nibbles == byte)
  [0x00] + 2048 bytes – raw    (fixed size, no VarInt prefix)
[blockCount]      – VarInt
For each block DataLayer: same as above
```

**Savings guard:** only activates when `2048 × uniform_count + raw_count − 1 > 0`.
**Max saving:** ~40 KB per chunk in open-sky environments (view-distance 12 → up to 20 uniform sky layers).

---

### 3. Chunk Data Optimization — XOR-Delta Heightmaps + Biome Stream

**Wire format** (`ClientboundLevelChunkPacketData`):

```
[0x4B]            – Krypton chunk-data marker (1 byte)

──── Heightmaps (binary + XOR-delta) ────
VarInt  entry_count
Per entry:
  UTF-8  key
  VarInt long_count
  long[] XOR-delta encoded  (delta[i] = raw[i] ^ raw[i-1])

──── Block data (sections without biomes) ────
VarInt  blocks_length
byte[]  blocks  (short nonEmptyCount + PalettedContainer<BlockState> per section)

──── Biome data (compact stream) ────
VarInt  section_count
Per section:
  0x01 + VarInt(biomeId)          – single-value section (2 bytes vs. 3+)
  0x00 + VarInt(len) + raw bytes  – multi-value section

──── Block entities ────
LIST_STREAM_CODEC (unchanged vanilla encoding)
```

| Optimization | Mechanism | Benefit |
|---|---|---|
| XOR-delta heightmaps | Adjacent columns correlate → many near-zero longs | Better Zstd/zlib ratio + ~40 B NBT overhead eliminated |
| Biome stream split | Biome data grouped; single-value sections compacted | Single-value: 2 B vs. 3+ B; cross-section redundancy exploited |

---

### 4. Delayed Chunk Cache (DCC)

Departing chunks are **buffered** instead of immediately dropped. If the player re-enters range before the entry expires, the full resend is skipped.

| Config key | Default | Description |
|---|---|---|
| `dcc.enabled` | `true` | Master switch |
| `dcc.size_limit` | 60 | Max buffered chunks per player |
| `dcc.distance` | 5 | Cache radius around player position (chunks) |
| `dcc.timeout_seconds` | 30 | Forced eviction timeout |

Eviction occurs on three dimensions: **distance**, **capacity**, and **timeout**. Evicted entries trigger deferred `dropChunk` callbacks on the next `ChunkMap.tick()`.

---

### 5. Flush Consolidation + Entity Packet Bundling

Two complementary layers reduce syscall and framing overhead during the entity tracking tick:

```
ChunkMap.tick() HEAD
  ① Recovery: flush any stale batch from previous failed tick
  ② Disable auto-flush for all players
  ③ Open EntityBundleCollector batch window

  ↳ TrackedEntity.broadcast() → packets collected per player

ChunkMap.tick() RETURN
  ④ Close batch → emit ClientboundBundlePackets (buffered, not yet flushed)
  ⑤ Re-enable auto-flush → single kernel flush per player
```

| Layer | Mechanism | Benefit |
|---|---|---|
| Flush consolidation (Netty) | Buffer writes, one `flush()` at end of tick | Fewer syscalls on busy servers |
| Bundle coalescing (protocol) | N entity packets → 1 `ClientboundBundlePacket` | Reduced framing overhead; better compression ratio; client-side atomicity |

---

### 6. Hot-Path Micro-Optimizations

| Target | Optimization |
|---|---|
| `VarInt.read()` | Branchless 4-byte `getIntLE` + bit-twiddling fast path (covers values 0 – 268 M) |
| `VarInt.getByteSize()` | Lookup-table via `Integer.numberOfLeadingZeros` |
| `VarLong.getByteSize()` | Same lookup-table approach |
| `FriendlyByteBuf.writeVarInt()` | Peeled 1/2/3/4/5-byte paths, writes directly to backing `ByteBuf` |
| `FriendlyByteBuf.writeUtf()` | Single-pass via `ByteBufUtil.utf8Bytes` — no intermediate `byte[]` |
| `Varint21FrameDecoder` | Velocity-derived efficient framing + nullping attack mitigation |

---

### 7. Observability — `/krypton` Commands

```
/krypton stats show          – compression ratio, bandwidth saving, uptime
/krypton stats reset         – reset all counters
/krypton packets bycount [n] – top N packet types by count
/krypton packets bybytes [n] – top N packet types by bytes
/krypton mods bycount [n]    – top N mod namespaces by count
/krypton mods bybytes [n]    – top N mod namespaces by bytes
```

Requires operator permission level 2.

## Credit

- [Krypton Fabric](https://modrinth.com/mod/krypton)
- [Velocity](https://github.com/PaperMC/Velocity)
- [VelocityNT Recast](https://github.com/404Setup/VelocityNT-Recast)
- [Paper](https://github.com/PaperMC/Paper)
- [RecastSSL](https://github.com/404Setup/RecastSSL)
- [NotEnoughBandwidth](https://github.com/USS-Shenzhou/NotEnoughBandwidth)
