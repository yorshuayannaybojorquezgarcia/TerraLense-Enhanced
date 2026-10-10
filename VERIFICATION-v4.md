# Verification v4 — 32×32 development baseline

## Verified by source inspection
- `PackLayout`: 32 rows × 8 pixels = 256px glyph height, no remaining vertical lift for 32×32; this is a real constraint for top-right placement.
- `HudGlyphs`: 16 arrow rotations occupy E300–EAFF (row slots of 128); marker slots start EB00, so they do not overlap. Row glyphs E000–E01F, spacing E200–E20F / E280–E28F and kern E2F0 are separate.
- `PackGenerator`: emits one 8×256 cell texture per row, 16 arrow textures, four marker shapes per row, and font providers for each. Tests now check PNG headers/dimensions and presence.
- `MapViewport`: 32×32 uses the same coordinate conversion as 24×24. Minecraft client appearance is not verified.

## Changes in v4
- Reject unsupported >32 grid sizes early in PackLayout and reject negative margins.
- Add 32×32 tests for glyph uniqueness, limits and generated resource-pack PNG dimensions.
- Workflow uses `bash ./gradlew` (works even if executable bit was lost in ZIP), captures full Gradle output and uploads logs even on failure.

## Compilation status
- Attempted `bash ./gradlew clean build --offline` locally; wrapper could not download Gradle 9.7.0 because this environment has no network/DNS. Local Java is 21, project toolchain is 25. No build success, no JAR, no test run, no Paper/Minecraft verification.

## GitHub Actions steps
1. Create a GitHub repository and upload **the contents** of this ZIP to the repository root (not a parent folder).
2. Open repository → Actions → **Build TerraLense** → **Run workflow** → select branch → Run workflow.
3. Open the resulting run → `build` → `Build and test` to read Gradle errors.
4. Download `TerraLense-build-diagnostics` under Artifacts for `gradle-build.log` and test reports (uploaded even on failed builds).
5. Only on successful build, download `TerraLense-development-build` from Artifacts; verify JAR content and run a Paper test server before production.

## Remaining limitations
32×32 top-anchored HUD has zero transparent lift rows. Correct appearance/position depends on client GUI scaling and needs visual testing. No claim of functional Minecraft verification. Large map remains 24×24 by design; it is a separate UI.
