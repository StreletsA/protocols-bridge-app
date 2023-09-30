package com.github.streletsa.protocols.bridge.app.common;

public class Result<T> {
    private final T receivedContent;
    private final Class<T> contentType;
    private final boolean success;
    private final String errorMessage;

    public static <T> Result<T> success(Class<T> contentType, T receivedContent) {
        return new Result<>(contentType, receivedContent, true, null);
    }

    public static <T> Result<T> error(Class<T> contentType, String errorMessage) {
        return new Result<>(contentType, null, false, errorMessage);
    }

    public Result(Class<T> contentType, T receivedContent, boolean success, String errorMessage) {
        this.receivedContent = receivedContent;
        this.contentType = contentType;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public T getReceivedContent() {
        return receivedContent;
    }

    public Class<T> getContentType() {
        return contentType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
