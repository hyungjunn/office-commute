package com.company.officecommute.global.persistence;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

public final class DatabaseConstraintMatcher {

    private static final String H2_INDEX_MARKER = "_INDEX_";

    private DatabaseConstraintMatcher() {
    }

    public static boolean matches(Throwable throwable, String expectedConstraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return normalize(constraintViolationException.getConstraintName())
                        .equalsIgnoreCase(expectedConstraintName);
            }
            current = current.getCause();
        }
        return false;
    }

    private static String normalize(String constraintName) {
        if (constraintName == null) {
            return "";
        }

        String normalized = constraintName;
        int schemaSeparator = normalized.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            normalized = normalized.substring(schemaSeparator + 1);
        }
        int h2IndexMarker = normalized.toUpperCase(Locale.ROOT).lastIndexOf(H2_INDEX_MARKER);
        if (h2IndexMarker >= 0) {
            String h2IndexDecoration = normalized.substring(h2IndexMarker + H2_INDEX_MARKER.length());
            if (!h2IndexDecoration.isEmpty()
                    && h2IndexDecoration.chars().allMatch(Character::isLetterOrDigit)) {
                normalized = normalized.substring(0, h2IndexMarker);
            }
        }
        return normalized;
    }
}
