package dev.terralense.hud;

import dev.terralense.map.MapImage;
import net.kyori.adventure.text.Component;

/** Converts a rendered map image into a component the client can draw. */
public interface HudRenderer {
    /** Builds the HUD component for one frame. */
    Component render(MapImage image, HudFrame frame);
}
