package com.tpwalke2.bluemapsignmarkers.common;

import com.tpwalke2.bluemapsignmarkers.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Shared safe-call wrapper for every game-thread entry point into mod logic (mod lifecycle hooks, both
// mixins, SignManager's static entry points) - one place to catch Throwable, log with context, and
// swallow, so an uncaught exception can never propagate into live Minecraft server code and crash the
// server. See docs/adr/0003-shared-safe-call-utility-for-crash-safety.md.
public class SafeCall {

    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    private SafeCall() {
    }

    public static void run(String context, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOGGER.error("Unhandled exception in {}; continuing without crashing the server.", context, t);
        }
    }
}
