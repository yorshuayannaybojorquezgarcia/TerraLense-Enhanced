# TerraLense Enhanced — partial v3

Implemented in source: selectable 24/32 grid sampling and pack generation, disjoint marker/arrow glyph blocks, and regression tests. The original 24 grid remains default for compatibility. Changing grid size requires regenerating and accepting the resource pack.

Not implemented: original graphic redesign, robust per-player right-edge calibration across client GUI scales, large-map overhaul, 48 grid, and verified server-side testing.

Build not verified: runtime has JDK 21 while build.gradle.kts requests JDK 25. GitHub Actions workflow is included but has not been executed. No enhanced JAR is provided.
