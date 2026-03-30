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

## Technical details to Coding
This is just one example
 <h3>Uniform-RLE light encoding</h3>

 * <pre>
 *   [0x4B]           – Krypton marker (1 byte)
 *   [trustEdges]     – boolean
 *   [skyYMask]       – BitSet (FriendlyByteBuf.writeBitSet)
 *   [blockYMask]     – BitSet
 *   [emptySkyYMask]  – BitSet
 *   [emptyBlockYMask]– BitSet
 *   [skyCount]       – VarInt
 *   For each sky DataLayer:
 *     [0x00] + 2048 bytes  – raw encoding (fixed size; no VarInt prefix)
 *     [0x01] + 1 byte      – uniform encoding (all nibble pairs == byte)
 *   [blockCount]     – VarInt
 *   For each block DataLayer: same as above
 * </pre>

## Credit

- [Krypton Fabric](https://modrinth.com/mod/krypton)
- [Velocity](https://github.com/PaperMC/Velocity)
- [VelocityNT Recast](https://github.com/404Setup/VelocityNT-Recast)
- [Paper](https://github.com/PaperMC/Paper)
- [RecastSSL](https://github.com/404Setup/RecastSSL)
- [NotEnoughBandwidth](https://github.com/USS-Shenzhou/NotEnoughBandwidth)
