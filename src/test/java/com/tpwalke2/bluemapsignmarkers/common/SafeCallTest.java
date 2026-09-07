package com.tpwalke2.bluemapsignmarkers.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeCallTest {

    @Test
    void runsTheActionWhenItSucceeds() {
        var ran = new boolean[1];

        SafeCall.run("test", () -> ran[0] = true);

        assertTrue(ran[0]);
    }

    @Test
    void swallowsARuntimeExceptionInsteadOfPropagatingIt() {
        assertDoesNotThrow(() -> SafeCall.run("test", () -> {
            throw new RuntimeException("boom");
        }));
    }

    @Test
    void swallowsAnErrorInsteadOfPropagatingIt() {
        assertDoesNotThrow(() -> SafeCall.run("test", () -> {
            throw new StackOverflowError();
        }));
    }
}
