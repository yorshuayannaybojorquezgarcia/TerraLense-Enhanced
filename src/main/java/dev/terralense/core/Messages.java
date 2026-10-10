package dev.terralense.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Central place for player-facing chat formatting so command output stays visually consistent and
 * the brand colours are defined exactly once.
 */
public final class Messages {
    public static final TextColor PRIMARY = TextColor.color(0x4F, 0xD1, 0xA5);
    public static final TextColor ACCENT = TextColor.color(0x9B, 0xE8, 0xCF);
    public static final TextColor BODY = TextColor.color(0xD9, 0xE2, 0xDF);
    public static final TextColor WARN = TextColor.color(0xF5, 0xC4, 0x6B);
    public static final TextColor ERROR = TextColor.color(0xE0, 0x5C, 0x5C);

    private static final Component PREFIX = Component.text()
            .append(Component.text("TerraLense", PRIMARY, TextDecoration.BOLD))
            .append(Component.text(" \u00BB ", ACCENT))
            .build();

    private Messages() {
    }

    public static Component prefixed(Component body) {
        return Component.text().append(PREFIX).append(body).build();
    }

    public static Component info(String text) {
        return prefixed(Component.text(text, BODY));
    }

    public static Component success(String text) {
        return prefixed(Component.text(text, PRIMARY));
    }

    public static Component warn(String text) {
        return prefixed(Component.text(text, WARN));
    }

    public static Component error(String text) {
        return prefixed(Component.text(text, ERROR));
    }

    /** Renders a {@code label: value} line, used by status and debug output. */
    public static Component detail(String label, String value) {
        return prefixed(Component.text()
                .append(Component.text(label + ": ", ACCENT))
                .append(Component.text(value, BODY))
                .build());
    }

    /** A bare (unprefixed) detail line, for multi-line status blocks. */
    public static Component detailLine(String label, String value) {
        return Component.text()
                .append(Component.text("  " + label + ": ", ACCENT))
                .append(Component.text(value, BODY))
                .build();
    }

    /** A clickable URL that also copies on hover. */
    public static Component link(String url) {
        return Component.text(url, ACCENT, TextDecoration.UNDERLINED)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                .hoverEvent(Component.text("Open in browser", BODY));
    }
}
