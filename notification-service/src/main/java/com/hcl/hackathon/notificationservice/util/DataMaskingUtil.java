package com.hcl.hackathon.notificationservice.util;

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
     * Masks a credit-card application reference for logs (middle segment redacted).
     */
    public String maskApplicationId(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            return "APP-XXXX-****";
        }
        String raw = applicationId.trim();
        String[] parts = raw.split("-");
        if (parts.length == 3) {
            return parts[0] + "-XXXX-" + lastFour(parts[2]);
        }
        String normalised = raw.replaceAll("[\\s-]", "");
        if (normalised.length() > 4) {
            return "APP-XXXX-" + lastFour(normalised);
        }
        return "APP-XXXX-****";
    }

    /**
     * Masks an email for logs (first character of local part and domain kept).
     */
    public String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "****@****.***";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return "****@****.***";
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        if (local.isEmpty() || domain.isEmpty()) {
            return "****@****.***";
        }
        char firstLocal = local.charAt(0);
        int lastDot = domain.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == domain.length() - 1) {
            return "****@****.***";
        }
        String host = domain.substring(0, lastDot);
        String tld = domain.substring(lastDot);
        if (host.isEmpty()) {
            return "****@****.***";
        }
        char firstHost = host.charAt(0);
        return firstLocal + "***@" + firstHost + "*****" + tld;
    }

    private static String lastFour(String segment) {
        if (segment == null || segment.length() < 4) {
            return "****";
        }
        return segment.substring(segment.length() - 4);
    }

    /**
     * Masks an Aadhaar number, preserving only the last 4 digits.
     */
    public String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.isBlank()) {
            return "XXXX-XXXX-****";
        }
        String normalised = aadhaar.replaceAll("[\\s\\-]", "");
        Matcher matcher = AADHAAR_PATTERN.matcher(normalised);
        if (matcher.matches()) {
            return "XXXX-XXXX-" + matcher.group(3);
        }
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
            String prefix = matcher.group(1);
            String digits = matcher.group(2);
            String suffix = matcher.group(3);
            String maskedPrefix = maskSegment(prefix);
            return maskedPrefix + digits + suffix;
        }
        if (normalised.length() > 1) {
            String lastChar = normalised.substring(normalised.length() - 1);
            return "XXXXX****" + lastChar;
        }
        return "XXXXX****X";
    }

    private static String maskSegment(String prefix) {
        if (prefix == null || prefix.length() < 2) {
            return "XXXXX";
        }
        return prefix.charAt(0) + "XXXX";
    }
}
