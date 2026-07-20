package com.SWP391.horserace.wallets.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shared VNPay hashing helper used by BOTH create-url signing and callback verification.
 *
 * <p>ONE consistent charset (UTF-8) and ONE ordering rule (ascending key order via {@link TreeMap})
 * are used everywhere so a URL this class builds always verifies against itself. The two hash
 * fields ({@code vnp_SecureHash}, {@code vnp_SecureHashType}) are always excluded from the signed
 * data. HMAC-SHA512, lowercase hex.
 */
public final class VnPayHashUtil {

    private static final String HMAC_ALGO = "HmacSHA512";

    private VnPayHashUtil() {}

    /**
     * Builds the sorted, URL-encoded {@code key=value&key=value} data string used for signing.
     * Null/blank values and the two hash fields are skipped.
     */
    public static String buildQuery(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if ("vnp_SecureHash".equals(key) || "vnp_SecureHashType".equals(key)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** HMAC-SHA512 of {@code data} keyed by {@code secret}, lowercase hex. */
    public static String hashData(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute VNPay HMAC", ex);
        }
    }

    /**
     * Verifies {@code vnp_SecureHash} against a freshly-computed HMAC over the (hash-field-stripped,
     * sorted, url-encoded) params. The two hash fields are excluded by {@link #buildQuery}.
     */
    public static boolean verifySignature(Map<String, String> params, String secret) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        String expected = hashData(secret, buildQuery(params));
        return expected.equalsIgnoreCase(received);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
