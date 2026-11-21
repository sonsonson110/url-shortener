package com.urlshortener.shortener;

/**
 * Base62 codec for turning sequence ids into short codes.
 *
 * <p>Alphabet order is fixed: {@code 0-9}, {@code a-z}, {@code A-Z}
 * (so 0 -> "0", 10 -> "a", 36 -> "A", 61 -> "Z", 62 -> "10").
 *
 * <p>Invariant: {@link #encode(long)} is one-to-one (no leading zeros except for "0"),
 * so distinct ids always yield distinct codes. This is what makes ID generation
 * collision-free (see docs/adr/0002-id-generation.md).
 */
public final class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    /** Encodes a non-negative number. Throws IllegalArgumentException if negative. */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    /** Decodes a base62 string. Throws IllegalArgumentException on empty or invalid input. */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be empty");
        }
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("invalid base62 character: " + code.charAt(i));
            }
            result = Math.addExact(Math.multiplyExact(result, BASE), digit);
        }
        return result;
    }
}
