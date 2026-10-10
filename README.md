# TerraLense

A server-side minimap for Paper. Real terrain, rotation, waypoints and markers,
drawn on the HUD of an ordinary vanilla client.

No Fabric. No Forge. No JourneyMap or Xaero's. Players join with the vanilla
launcher and the map is just there.

![Paper](https://img.shields.io/badge/Paper-26.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![License](https://img.shields.io/badge/license-MIT-green)

**[Download on SpigotMC](https://www.spigotmc.org/resources/terralense.138116/)**

## Features

- **Terrain map** with 21 material categories across the Overworld, Nether and End
- **Height shading** so hills, cliffs and coastlines are readable at a glance
- **Rotating map** that follows where the player is looking, or locks north up
- **Waypoints** saved to SQLite, 64 per player, with tab-completed names
- **Death marker** so players can walk back to their stuff
- **Player radar** that respects vanish, spectator mode, tab-list hiding and permissions
- **Large map view** with `/tl map`, or by double-tapping the swap-hands key
- **Per-player settings** for zoom, orientation, visibility and horizontal position
- **Marker API** for other plugins to draw on the map
- **Async rendering** with no world scanning on the main thread
- **Update notice** on startup when a newer version is on Spigot

## Requirements

- Paper 26.2 or newer
- Java 25
- An open TCP port for the resource pack (8123 by default), or your own web host

Folia is not supported. See [Limitations](#limitations).

## Installation

1. Drop `TerraLense-x.y.z.jar` into `plugins/`
2. Start the server. The config, resource pack and pack server are all set up automatically
3. If players connect to your server remotely, open `plugins/TerraLense/config.yml`
   and set `resource-pack.self-host.public-address` to your public IP or hostname
4. Make sure port `8123` is reachable, then run `/tl reload`
5. Players reconnect, accept the pack, and the map appears

Testing on localhost? Step 2 is all you need.

The plugin needs internet access on first start so Paper can download the SQLite
driver declared in `plugin.yml`.

## About the resource pack

The map is drawn with a generated resource pack, because that is the only way to
put custom graphics on a vanilla client's screen. Clients download packs over
HTTP, and a server can't push pack data through the game connection.

TerraLense handles the whole thing: it builds the pack, computes the SHA-1 and
serves it from a small built-in HTTP server on port 8123. Nothing to host.

The one thing to set for a public server is `public-address`, since the plugin
can't work out your public IP from behind NAT, Docker or a proxy. Leave it empty
and it falls back to `localhost` with a warning, which only works locally.

Want to host the pack yourself? Set `resource-pack.url` and the built-in server
stays off. An explicit URL always wins.

If your server already sends its own resource pack, `auto` mode stands down and
leaves it alone. Merge the generated assets into your pack and switch to
`external` mode.

## Commands

`/terralense`, aliased to `/tl` and `/minimap`.

| Command | Permission | What it does |
|---|---|---|
| `/tl` | `terralense.use` | List available commands |
| `/tl toggle` | `terralense.toggle` | Show or hide your map |
| `/tl zoom <1-16>` | `terralense.use` | Blocks per map cell |
| `/tl mode <north\|player>` | `terralense.use` | Orientation |
| `/tl position <left\|right\|center>` | `terralense.use` | Nudge the map sideways |
| `/tl map [zoom]` | `terralense.use` | Open the large map |
| `/tl waypoint add <name>` | `terralense.waypoint` | Save your position |
| `/tl waypoint remove <name>` | `terralense.waypoint` | Delete a waypoint |
| `/tl waypoint list` | `terralense.waypoint` | List your waypoints |
| `/tl waypoint tp <name>` | `terralense.waypoint.teleport` | Teleport to a waypoint |
| `/tl death` | `terralense.use` | Show your last death location |
| `/tl death clear` | `terralense.use` | Clear the death marker |
| `/tl status` | `terralense.status` | Version, timings, pack state |
| `/tl debug [on\|off\|reset]` | `terralense.debug` | Diagnostics and verbose logging |
| `/tl reload` | `terralense.reload` | Reload the config |

Everything tab-completes.

## Permissions

| Permission | Default | Grants |
|---|---|---|
| `terralense.use` | true | The minimap and player commands |
| `terralense.toggle` | true | Toggling the map |
| `terralense.waypoint` | true | Managing personal waypoints |
| `terralense.waypoint.teleport` | op | Teleporting to own waypoints |
| `terralense.radar` | true | Being visible to others on the radar |
| `terralense.admin` | op | All admin permissions |
| `terralense.reload` | op | `/tl reload` |
| `terralense.debug` | op | `/tl debug` |
| `terralense.status` | op | `/tl status` |

Nothing about normal play needs op.

## Configuration

Defaults: top of the screen, circular, rotating with the player, zoom 8, with
terrain, a player arrow, nearby players, waypoints and a death marker.

The settings people usually change:

```yaml
hud:
  size: 96              # 32-168 GUI pixels
  shape: circle         # circle, rounded-square, square
  anchor: boss-bar      # boss-bar or action-bar

map:
  orientation: player   # player or north
  zoom:
    default: 8          # blocks per cell

resource-pack:
  self-host:
    public-address: ''  # set this for a public server

general:
  update-check:
    enabled: true       # check Spigot for a newer version on startup
    notify-admins: true # also tell operators when they join
```

The update check runs off the main thread, never delays startup, and stays quiet
if it fails. Nothing is downloaded or installed automatically. Set
`update-check.enabled: false` to turn it off entirely.

Every option is documented inline in `config.yml`. Values out of range get
clamped and logged rather than breaking startup, and `/tl reload` applies changes
without dropping sessions or caches.

## Performance

- Terrain is read through `ChunkSnapshot`, so sampling and rendering run on
  worker threads and never touch the world off the main thread
- Chunks are never loaded to draw a map. Unloaded areas render as empty space,
  so players can't drive world generation by walking around
- Standing still costs nothing. Turning in place re-transforms the cached terrain
  instead of re-sampling it. A block change invalidates one chunk and flags only
  the players who can see it
- Cache, render queue, storage queue and thread pools are all bounded. Nothing
  grows with uptime and there's no thread per player
- Same-colour runs are merged and colours interned per frame, which cuts HUD
  packet size a lot on flat terrain

`/tl status` reports render times, queue depth, cache hit rate and dropped frames.

## Limitations

Listed up front rather than discovered later:

- **A reachable HTTP port is required.** Vanilla clients fetch packs over HTTP.
  TerraLense serves the pack itself, but the port has to be open and
  `public-address` has to be set for anything beyond local testing.
- **The pack server is unauthenticated**, because clients can't present
  credentials when fetching a pack. It serves one file on one fixed path, answers
  only GET and HEAD, and never builds a filesystem path from the request. The
  only thing exposed is a pack every player gets anyway.
- **Packs can't be merged automatically.** If your server already sends a pack,
  merge the assets by hand and use `external` mode.
- **Folia isn't supported.** It's detected at startup and the minimap stays off
  rather than risking world state on the wrong thread. The rest of your server is
  unaffected.
- **The map can't be pinned to a screen corner.** The client never tells the
  server its screen size or GUI scale, and the only surfaces a server can draw on
  are centre-aligned. The map sits at top-centre inside the boss bar's fixed
  width so it's always visible; players nudge it with `/tl position`.
- **The HUD uses the boss bar** by default, so it clashes with other plugins that
  use boss bars. `hud.anchor: action-bar` moves it above the hotbar instead.
- **BungeeCord blocks the pack.** It doesn't forward resource-pack packets from
  backend servers, so players connecting through it get no map. This is detected
  and warned about at startup. Velocity forwards them fine.
- **Surface view only.** No cave mode, no 3D.
- **Biome colouring isn't implemented.** The `terrain.biome-colors` key is
  reserved and does nothing yet.
- **Arrow rotation is quantised to 16 steps** (22.5° apart), since a font texture
  can't be rotated at runtime. Each angle is a separate baked glyph.
- **Players who decline the pack see no map.** They're never kicked and normal
  gameplay isn't affected.

## For developers

Other plugins can add their own markers:

```java
if (TerraLenseAPI.isAvailable()) {
    TerraLenseAPI.get().markers().register(myProvider);
}
```

A `MarkerProvider` returns markers for one player each frame. Providers that
throw get isolated and disabled instead of breaking the render.

### Building

```bash
./gradlew clean build
```

The jar lands in `build/libs/`. Needs a JDK 25 toolchain; the wrapper pins Gradle
9.7.0 for Java 25 support.

## Support

Bugs and feature requests: open an issue here, or post in the
[SpigotMC discussion thread](https://www.spigotmc.org/resources/terralense.138116/).

Include your `/tl status` output and your server version. If the map isn't
showing, `/tl status` will usually tell you why in one line.

## License

MIT. See [LICENSE](LICENSE).

No code, assets or pack files are taken from any other minimap project. All pack
textures are generated at runtime from `dev.terralense.resourcepack`.
