package com.SWP391.horserace.auth.service;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link JavaMailSender} for sending password-reset emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Send a password-reset email containing the 6-digit code.
     */
    public void sendResetCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Equine Elite — Password Reset Code");
            helper.setText(buildHtmlBody(code), true);

            mailSender.send(message);
            log.info("Password reset code sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send reset email to {}", toEmail, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String buildHtmlBody(String code) {
        return """
                <div style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 32px; background: #f8faf8; border-radius: 12px;">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <h2 style="color: #0d3b2e; margin: 0;">Equine Elite</h2>
                        <p style="color: #6b7280; margin: 4px 0 0;">Password Reset</p>
                    </div>
                    <div style="background: #ffffff; border-radius: 8px; padding: 24px; border: 1px solid #e5e7eb;">
                        <p style="color: #374151; margin: 0 0 16px;">We received a request to reset your password. Use the verification code below:</p>
                        <div style="text-align: center; margin: 24px 0;">
                            <span style="display: inline-block; font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #0d3b2e; background: #ecfdf5; padding: 12px 24px; border-radius: 8px; border: 2px dashed #0d3b2e;">
                                %s
                            </span>
                        </div>
                        <p style="color: #6b7280; font-size: 14px; margin: 16px 0 0;">This code expires in <strong>15 minutes</strong>. If you didn't request this, please ignore this email.</p>
                    </div>
                    <p style="color: #9ca3af; font-size: 12px; text-align: center; margin: 16px 0 0;">© Equine Elite — Horse Racing Tournament Management</p>
                </div>
                """.formatted(code);
    }
}
