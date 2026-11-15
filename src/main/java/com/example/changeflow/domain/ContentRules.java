package com.example.changeflow.domain;

/** Exact stored-text validation, independent of transport, persistence and action authorization. */
public final class ContentRules {
    public static final int TITLE_LIMIT = 200;
    public static final int DESCRIPTION_LIMIT = 5000;
    public static final int CHANGE_REASON_LIMIT = 2000;
    public static final int RETURN_REASON_LIMIT = 2000;

    private ContentRules() { }

    public static void validateForSave(String title, String description, String reason) {
        validate(title, TITLE_LIMIT, false, "title");
        validate(description, DESCRIPTION_LIMIT, false, "changeDescription");
        validate(reason, CHANGE_REASON_LIMIT, false, "reasonForChange");
    }

    public static void validateForSubmission(String title, String description, String reason) {
        validate(title, TITLE_LIMIT, true, "title");
        validate(description, DESCRIPTION_LIMIT, true, "changeDescription");
        validate(reason, CHANGE_REASON_LIMIT, true, "reasonForChange");
    }

    public static void validateReturnReason(String reason) {
        validate(reason, RETURN_REASON_LIMIT, true, "reason");
    }

    private static void validate(String input, int limit, boolean required, String field) {
        String text = input == null ? "" : input;
        // Validate UTF-16 before codePointCount, which otherwise tolerates unpaired surrogates.
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == 0) {
                throw new IllegalArgumentException(field + " contains invalid Unicode");
            }
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    throw new IllegalArgumentException(field + " contains invalid Unicode");
                }
                i++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(field + " contains invalid Unicode");
            }
        }
        if (text.codePointCount(0, text.length()) > limit) {
            throw new IllegalArgumentException(field + " exceeds its character limit");
        }
        if (required && text.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
    }
}
