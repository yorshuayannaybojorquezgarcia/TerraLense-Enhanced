package dev.terralense.core;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Thin logging facade for TerraLense. */
public final class Log {
    private final Logger logger;
    private volatile boolean debug;

    public Log(Logger logger) {
        this.logger = logger;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void info(String message) {
        logger.info(message);
    }

    public void warn(String message) {
        logger.warning(message);
    }

    public void error(String message) {
        logger.severe(message);
    }

    /** Logs a failure with its cause. */
    public void error(String message, Throwable thrown) {
        logger.log(Level.SEVERE, message, thrown);
    }

    /** Logs a debug message. */
    public void debug(String message) {
        if (debug) {
            logger.info("[debug] " + message);
        }
    }

    public void debug(Supplier<String> message) {
        if (debug) {
            logger.info("[debug] " + message.get());
        }
    }
}
