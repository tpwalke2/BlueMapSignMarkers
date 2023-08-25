package com.tpwalke2.bluemapsignmarkers.core.reactive;

@FunctionalInterface
public interface MessageProcessorErrorCallback {
    void onError(Throwable throwable);
}
