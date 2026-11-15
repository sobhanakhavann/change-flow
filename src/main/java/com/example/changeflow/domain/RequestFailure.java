package com.example.changeflow.domain;

/** Business/application outcome without HTTP status or transport dependency. */
public final class RequestFailure extends RuntimeException {
    public enum Kind { PERMISSION_DENIED, REQUEST_NOT_FOUND, INVALID_STATE, VERSION_CONFLICT, SERVICE_UNAVAILABLE }
    private final Kind kind;
    public RequestFailure(Kind kind) { super(kind.name()); this.kind = kind; }
    public Kind kind() { return kind; }
}
