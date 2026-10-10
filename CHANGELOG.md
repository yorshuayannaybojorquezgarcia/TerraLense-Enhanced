# Changelog

## Unreleased

### Added

- Startup update check against SpigotMC. Logs a one-line notice when a newer
  version is published, and optionally tells operators when they join. Runs off
  the main thread, never delays startup, and stays quiet on failure. Configurable
  under `general.update-check`.
- `/tl status` reports the available version when the server is behind.

### Fixed

- Two comments contained a literal `\n` from an earlier formatting pass.

## 0.1.0

First release.

### Added

- Server-side minimap for vanilla clients. Terrain, rotation, markers and
  waypoints with no client mod required.
- Terrain rendering with 21 material categories covering the Overworld, Nether
  and End. Blocks are classified by name pattern, so blocks added in future
  Minecraft versions are categorised without a code change.
- Optional height shading, sampled diagonally so terrain shape shows in both
  axes. Liquids are never shaded.
- Two orientation modes: `player` rotates the terrain so your heading is up,
  `north` keeps north up and rotates only the arrow.
- Smoothed rotation that takes the shortest path across the 0/360 boundary.
- Per-player zoom from 1 to 16 blocks per cell, bounded by the config.
- Circle, rounded-square and square HUD shapes with configurable size and margin.
- Large map view via `/tl map` or a double-tap of the swap-hands key.
- Deterministic resource pack generation. The same inputs produce byte-identical
  output, so the SHA-1 works as a cache key and clients don't re-download on
  every restart.
- Built-in pack HTTP server, so no external web host is needed. One file, one
  fixed path, GET and HEAD only. An explicit `resource-pack.url` overrides it.
- Waypoints with SQLite persistence, a versioned schema with migrations, a
  64-per-player cap and per-player unique names.
- Markers for the player arrow, nearby players, waypoints, death location, world
  spawn and respawn point.
- Player radar respecting vanish, tab-list hiding, spectator mode, permissions,
  world separation and a configurable distance.
- Death markers with configurable expiry, plus `/tl death` and `/tl death clear`.
- Public marker API (`TerraLenseAPI`) for other plugins. Faulty providers are
  isolated instead of breaking the render.
- Commands with full tab completion and clear validation errors.
- `/tl status` and `/tl debug` reporting render times, queue depth, cache hit
  rate, skip rate, dropped frames and pack state.
- In-place config reload that preserves sessions, caches and registered marker
  providers, and reports settings that need a restart.
- Graceful degradation: a database failure falls back to session-only storage, a
  pack failure disables only the HUD, and a failed render can't crash the server.

### Performance

- Terrain is read through immutable `ChunkSnapshot` copies, so no world scanning
  happens on the main thread.
- Chunks are never loaded or generated to draw a map. Unloaded areas render as
  empty space, so players can't drive world generation by walking around.
- Update work is split by cause: standing still does nothing, turning in place
  re-transforms cached terrain, and a block change invalidates a single chunk and
  flags only the players who can see it.
- Rotation uses inverse sampling, so every destination pixel is written exactly
  once and no gaps appear at any angle.
- Same-colour runs are merged and colour instances interned per frame, which cuts
  HUD packet size substantially on flat terrain.
- Terrain cache, render queue, storage queue and thread pools are all bounded.

### Known limitations

- The pack is delivered over HTTP, so a reachable TCP port is required and
  `public-address` must be set for anything beyond local testing.
- The pack server is unauthenticated, since clients can't present credentials
  when fetching a pack.
- Resource packs can't be merged automatically. If your server already sends one,
  `auto` mode stands down; merge manually and use `external` mode.
- Folia is not supported. It's detected and the minimap stays disabled.
- The HUD uses the boss bar by default and will conflict with other plugins that
  use boss bars.
- Surface rendering only. No cave mode or 3D view.
- Biome colouring is not implemented. The config key is reserved.
- Arrow rotation is quantised to 16 steps, because font textures can't be rotated
  at runtime.

## Enhanced development continuation (unreleased)
- Added `hud.style` visual presets (modern/classic/minimal), with validated configuration and targeted unit tests.
- Wired style presets into the real minimap rendering path; classic retains configurable legacy shape/border.
- Added configuration and testing status documentation.

## Enhanced partial v3 (unverified)
- Added selectable 32x32 sampling resolution (`hud.resolution: 32`) through player viewport, resource pack layout, and calibration calculation; legacy 24 remains default.
- Moved marker glyph allocation to a disjoint private-use block, preventing collision with arrow glyph allocations.
- Added 32-cell layout and glyph collision regression tests. Tests have not been run.
