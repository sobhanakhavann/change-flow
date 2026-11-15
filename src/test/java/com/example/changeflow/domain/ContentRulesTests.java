package com.example.changeflow.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ContentRulesTests {
    @ParameterizedTest
    @ValueSource(strings = {"a", "\uD83D\uDE00", "e\u0301"})
    void validatesEveryFieldAtAndAboveItsCodePointLimit(String unit) {
        String title = atLength(unit, 200);
        String description = atLength(unit, 5000);
        String reason = atLength(unit, 2000);
        assertDoesNotThrow(() -> ContentRules.validateForSave(title, description, reason));
        assertDoesNotThrow(() -> ContentRules.validateForSubmission(title, description, reason));
        assertDoesNotThrow(() -> ContentRules.validateReturnReason(reason));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSave(title + "a", description, reason));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSave(title, description + "a", reason));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSave(title, description, reason + "a"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateReturnReason(reason + "a"));
    }

    @Test
    void countsCombiningMarksSeparatelyWithoutNormalization() {
        assertDoesNotThrow(() -> ContentRules.validateForSave("\u00e9".repeat(200), "", ""));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSave("e\u0301".repeat(101), "", ""));
    }

    @Test
    void missingContentCanBeSavedButCannotBeSubmitted() {
        assertDoesNotThrow(() -> ContentRules.validateForSave(null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSubmission(null, "d", "r"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSubmission("t", null, "r"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSubmission("t", "d", null));
        assertThrows(IllegalArgumentException.class, () -> ContentRules.validateReturnReason(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u3000"})
    void whitespaceUnionIsAllowedOnSaveButNotForRequiredFields(String blank) {
        assertDoesNotThrow(() -> ContentRules.validateForSave(blank, blank, blank));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSubmission(blank, "d", "r"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSubmission("t", blank, "r"));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSubmission("t", "d", blank));
        assertThrows(IllegalArgumentException.class, () -> ContentRules.validateReturnReason(blank));
    }

    @Test
    void acceptsNonWhitespaceScalarsEvenInDisplayedSequences() {
        assertDoesNotThrow(() -> ContentRules.validateForSubmission(" t ", "\u200b", "\u0301"));
        assertDoesNotThrow(() -> ContentRules.validateReturnReason(" \uD83D\uDE00 "));
        assertThrows(IllegalArgumentException.class,
                () -> ContentRules.validateForSave("\uD83D\uDC69\u200d\uD83D\uDCBB".repeat(67), "", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "a\u0000b", "\uD800", "\uDC00", "\uD800x", "x\uDC00", "\uDC00\uD800"})
    void rejectsInvalidUnicodeInEveryFieldEvenOnIncompleteSave(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> ContentRules.validateForSave(invalid, "", ""));
        assertThrows(IllegalArgumentException.class, () -> ContentRules.validateForSave("", invalid, ""));
        assertThrows(IllegalArgumentException.class, () -> ContentRules.validateForSave("", "", invalid));
        assertThrows(IllegalArgumentException.class, () -> ContentRules.validateReturnReason(invalid));
    }

    private static String atLength(String unit, int limit) {
        int count = unit.codePointCount(0, unit.length());
        return unit.repeat(limit / count) + "a".repeat(limit % count);
    }
}
