package com.creditcard.notification.util;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class DataMaskingUtil {

    private static final Pattern AADHAAR_PATTERN =
            Pattern.compile("^(\\d{4})(\\d{4})(\\d{4})$");


    private static final Pattern PAN_PATTERN =
            Pattern.compile("^([A-Z]{5})(\\d{4})([A-Z])$");

    /**
     * Masks an Aadhaar number, preserving only the last 4 digits.
     *
     * <pre>
     *   "1234 5678 9012"  →  "XXXX-XXXX-9012"
     *   "123456789012"    →  "XXXX-XXXX-9012"
     *   null / blank      →  "XXXX-XXXX-****"
     * </pre>
     *
     * @param aadhaar raw Aadhaar number (may contain spaces)
     * @return masked Aadhaar safe for logging
     */
    public String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.isBlank()) {
            return "XXXX-XXXX-****";
        }
        // Normalise: strip spaces/hyphens before matching
        String normalised = aadhaar.replaceAll("[\\s\\-]", "");
        Matcher matcher = AADHAAR_PATTERN.matcher(normalised);
        if (matcher.matches()) {
            // group(1) & group(2) masked; group(3) = last 4 digits kept
            return "XXXX-XXXX-" + matcher.group(3);
        }
        // Fallback: keep only last 4 characters, mask the rest
        if (normalised.length() > 4) {
            String last4 = normalised.substring(normalised.length() - 4);
            return "XXXX-XXXX-" + last4;
        }
        return "XXXX-XXXX-****";
    }

    public String maskPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return "XXXXX****X";
        }
        String normalised = pan.strip().toUpperCase();
        Matcher matcher = PAN_PATTERN.matcher(normalised);
        if (matcher.matches()) {
            // group(1) = 5 letters (ABCDE), group(2) = 4 digits, group(3) = last letter
            String prefix  = matcher.group(1);   // e.g. "ABCDE"
            String digits  = matcher.group(2);   // e.g. "1234"  – kept in full
            String suffix  = matcher.group(3);   // e.g. "F"     – kept

            // Keep first letter of prefix only; mask the remaining 4
            String maskedPrefix = maskSegment(prefix);  // reuse existing helper → "AXXXX"
            return maskedPrefix + digits + suffix;
        }
        // Fallback: mask everything except last character
        if (normalised.length() > 1) {
            String lastChar = normalised.substring(normalised.length() - 1);
            return "XXXXX****" + lastChar;
        }
        return "XXXXX****X";
    }
}
