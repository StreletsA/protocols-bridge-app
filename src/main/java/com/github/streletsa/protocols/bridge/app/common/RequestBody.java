package com.github.streletsa.protocols.bridge.app.common;

public abstract class RequestBody<T> {
    private final byte[] content;

    public RequestBody(byte[] content) {
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }
}
