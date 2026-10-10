# Enhanced visual styles

`hud.style` supports `modern` (default), `classic`, and `minimal`.

- `modern`: circular mask and dark-blue border, unless the border is disabled with `hud.border: none`.
- `classic`: uses the original `hud.shape` and `hud.border` values without overriding them.
- `minimal`: circular mask and no border.

All three styles use the existing 24×24 glyph renderer. This is a functional visual preset, not a new texture or a higher-resolution renderer. For a square HUD with custom border use `classic`.

## Experimental grid resolution
`hud.resolution: 24` preserves the original 24x24 map. `hud.resolution: 32` selects 32x32 actual terrain sampling and matching generated font glyphs. Other values fall back to 24. The 32-cell glyph is 256 px high (the configured client limit) with no lift padding. Test visually before deploying. A changed pack must be accepted by clients.
