package com.SWP391.horserace.wallets.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit tests for the VNPay hash/sign/verify helper. Uses a FIXED test secret and a
 * pre-computed golden HMAC-SHA512 vector (NOT the real sandbox secret).
 */
class VnPayHashUtilTest {

    /** Arbitrary test-only secret. Never the production/sandbox key. */
    private static final String SECRET = "secret-key-123";

    @Test
    void hashData_producesStableHmacSha512Hex_forKnownKeyAndPayload() {
        // golden vector computed independently (python hmac-sha512, utf-8)
        String expected = "28e33d04c4611d8b3ad9452730ef26fb21cd2059b33aef371674047407c04dfb"
                + "4d2e8457a0d5424cfd9088b719dbeee0a965a9f15885a805ca025606f6dc7bd9";
        assertThat(VnPayHashUtil.hashData(SECRET, "hello-world")).isEqualTo(expected);
    }

    @Test
    void hashData_matchesGoldenVector_forSortedVnPayLikeData() {
        String data = "vnp_Amount=1000000&vnp_Command=pay&vnp_TmnCode=TESTTMN&vnp_TxnRef=ORDER123";
        String expected = "051c2089211fa9052baf76648378272a4abe081df9337b3393c40bb052c4155a"
                + "77b449766879186281b78e222888acb538dc7798829975461f0836805f27aead";
        assertThat(VnPayHashUtil.hashData(SECRET, data)).isEqualTo(expected);
    }

    @Test
    void urlBuiltByBuildQuery_verifiesTrue_roundTrip() {
        Map<String, String> params = signedParams();
        assertThat(VnPayHashUtil.verifySignature(params, SECRET)).isTrue();
    }

    @Test
    void tamperingAnyParam_makesVerifyFalse() {
        Map<String, String> params = signedParams();
        params.put("vnp_Amount", "9999900"); // tamper after signing
        assertThat(VnPayHashUtil.verifySignature(params, SECRET)).isFalse();
    }

    @Test
    void verifyStripsSecureHashAndTypeBeforeHashing() {
        Map<String, String> params = signedParams();
        // vnp_SecureHashType present but must NOT participate in the hash
        params.put("vnp_SecureHashType", "HmacSHA512");
        assertThat(VnPayHashUtil.verifySignature(params, SECRET)).isTrue();
    }

    @Test
    void paramsSignedInAscendingKeyOrder_regardlessOfInsertionOrder() {
        // Insert in a deliberately non-sorted order; the signature must still verify because
        // buildQuery sorts by key (TreeMap).
        Map<String, String> shuffled = new LinkedHashMap<>();
        shuffled.put("vnp_TxnRef", "ORDER123");
        shuffled.put("vnp_Amount", "1000000");
        shuffled.put("vnp_TmnCode", "TESTTMN");
        shuffled.put("vnp_Command", "pay");
        String hash = VnPayHashUtil.hashData(SECRET, VnPayHashUtil.buildQuery(shuffled));
        shuffled.put("vnp_SecureHash", hash);
        assertThat(VnPayHashUtil.verifySignature(shuffled, SECRET)).isTrue();
    }

    /** Builds a param map with a valid vnp_SecureHash over the (sorted) query. */
    private Map<String, String> signedParams() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "1000000");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "TESTTMN");
        params.put("vnp_TxnRef", "ORDER123");
        params.put("vnp_ResponseCode", "00");
        String hash = VnPayHashUtil.hashData(SECRET, VnPayHashUtil.buildQuery(params));
        params.put("vnp_SecureHash", hash);
        return params;
    }
}
