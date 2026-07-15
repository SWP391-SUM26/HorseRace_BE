package com.SWP391.horserace.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VNPay gateway settings bound from {@code app.vnpay.*}. Secrets ({@code tmnCode},
 * {@code hashSecret}) come from environment / the gitignored local properties — NEVER committed.
 */
@Component
@ConfigurationProperties(prefix = "app.vnpay")
@Getter
@Setter
public class VnPayProperties {
    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String returnUrl;
    private String ipnUrl;
    /** When true, the browser return endpoint may credit through the SAME atomic gate as IPN. */
    private boolean returnCreditFallback = false;
}
