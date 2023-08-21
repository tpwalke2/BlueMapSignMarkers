package com.tpwalke2.bluemapsignmarkers.core.reactive;

@FunctionalInterface
public interface MessageProcessorCallback<T> {
    void processMessage(T message);
}
