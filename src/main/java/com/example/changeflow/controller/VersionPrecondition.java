package com.example.changeflow.controller;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import static com.example.changeflow.controller.ApiExceptionHandler.Code.*;

/** Explicit helper, not a filter or argument resolver: call only after current read access is established. */
public final class VersionPrecondition {
    private static final Pattern TAG = Pattern.compile(
            "\"request-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-v(0|[1-9][0-9]*)\"");

    private VersionPrecondition() { }

    public static String etag(UUID id, long version) {
        if (id == null || version < 0) { throw new IllegalArgumentException("Invalid version identity"); }
        return "\"request-" + id + "-v" + version + "\"";
    }

    public static long requireForVisibleResource(List<String> headers, UUID id, long currentVersion) {
        var expected = parseForVisibleResource(headers);
        if (!expected.id().equals(id) || expected.version() != currentVersion) {
            throw new ApiExceptionHandler.Failure(VERSION_CONFLICT);
        }
        return expected.version();
    }

    public static com.example.changeflow.domain.ExpectedVersion parseForVisibleResource(List<String> headers) {
        if (headers == null || headers.isEmpty()) { throw new ApiExceptionHandler.Failure(PRECONDITION_REQUIRED); }
        if (headers.size() != 1 || headers.getFirst() == null) {
            throw new ApiExceptionHandler.Failure(INVALID_PRECONDITION);
        }
        // HTTP optional whitespace is SP/HTAB, not arbitrary Unicode whitespace or line breaks.
        var matcher = TAG.matcher(headers.getFirst().replaceAll("^[ \\t]+|[ \\t]+$", ""));
        if (!matcher.matches()) { throw new ApiExceptionHandler.Failure(INVALID_PRECONDITION); }
        long version;
        try { version = Long.parseLong(matcher.group(2)); }
        catch (NumberFormatException failure) { throw new ApiExceptionHandler.Failure(INVALID_PRECONDITION); }
        return new com.example.changeflow.domain.ExpectedVersion(UUID.fromString(matcher.group(1)), version);
    }
}
