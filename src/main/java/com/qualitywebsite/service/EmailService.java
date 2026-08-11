package com.qualitywebsite.service;

import com.qualitywebsite.dto.FeedbackRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.feedback.recipients:admin@example.com}")
    private String[] recipients;

    @Value("${app.feedback.from-email:admin@example.com}")
    private String fromEmail;

    @Value("${app.feedback.from-name:IAST Quality Portal}")
    private String fromName;

    @Value("${app.feedback.email-subject:New Feedback Received}")
    private String emailSubject;

    public void sendFeedbackEmail(FeedbackRequest request) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setReplyTo(request.getEmail(), request.getName());
        helper.setSubject(emailSubject);

        // Configure all target recipients
        if (recipients != null && recipients.length > 0) {
            String[] cleanRecipients = Arrays.stream(recipients)
                    .map(String::trim)
                    .filter(email -> !email.isEmpty())
                    .toArray(String[]::new);
            if (cleanRecipients.length == 0) {
                throw new IllegalArgumentException("No valid recipient emails configured under app.feedback.recipients");
            }
            helper.setTo(cleanRecipients);
        } else {
            throw new IllegalArgumentException("No recipient emails configured under app.feedback.recipients");
        }

        // Establish the timestamp for email header
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        String submittedAt = LocalDateTime.now().atZone(zone)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss z"));

        String htmlContent = buildHtmlBody(request.getName(), request.getEmail(), request.getMessage(), submittedAt);
        String textContent = buildTextBody(request.getName(), request.getEmail(), request.getMessage(), submittedAt);

        helper.setText(textContent, htmlContent);

        mailSender.send(message);
        log.info("Feedback email dispatched successfully to recipients: {}", Arrays.toString(recipients));
    }

    public void sendAdminInvitationEmail(String toEmail, String invitationUrl, String invitedBy) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(toEmail.trim());
        helper.setSubject("Administrator Invitation — IAST Quality Portal");

        String htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"/><title>Administrator Invitation</title></head>
        <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,sans-serif;">
          <center style="width:100%%;background-color:#f4f7fb;">
            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" align="center" style="width:600px;margin:0 auto;background-color:#ffffff;">
              <tr>
                <td style="background-color:#0d2b45;padding:20px 30px;">
                  <span style="font-size:18px;font-weight:bold;color:#ffffff;">IAST Quality Portal</span>
                  <span style="font-size:12px;color:#00aabb;display:block;margin-top:2px;">Administrator Invitation</span>
                </td>
              </tr>
              <tr>
                <td style="padding:30px;color:#333333;font-size:14px;line-height:1.6;">
                  <p style="margin-top:0;">Hello,</p>
                  <p>You have been invited by <strong>%s</strong> to become an Administrator on the IAST Quality Portal.</p>
                  <p>Please click the button below to complete your administrator account registration:</p>
                  <p style="text-align:center;margin:30px 0;">
                    <a href="%s" style="background-color:#00aabb;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;display:inline-block;">Accept Administrator Invitation</a>
                  </p>
                  <p style="font-size:12px;color:#666666;">This invitation link is single-use and will expire in 48 hours. If you did not expect this invitation, please ignore this email.</p>
                </td>
              </tr>
            </table>
          </center>
        </body>
        </html>
        """.formatted(escapeHtml(invitedBy), escapeHtml(invitationUrl));

        helper.setText(htmlContent, true);
        mailSender.send(message);
        log.info("[Security Audit] Administrator invitation email sent to: {}", toEmail);
    }

    public void sendPasswordResetEmail(String toEmail, String resetUrl, String username) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(toEmail.trim());
        helper.setSubject("Password Reset Request — IAST Quality Portal");

        String htmlContent = """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="UTF-8"/><title>Password Reset Request</title></head>
        <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,sans-serif;">
          <center style="width:100%%;background-color:#f4f7fb;">
            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" align="center" style="width:600px;margin:0 auto;background-color:#ffffff;">
              <tr>
                <td style="background-color:#0d2b45;padding:20px 30px;">
                  <span style="font-size:18px;font-weight:bold;color:#ffffff;">IAST Quality Portal</span>
                  <span style="font-size:12px;color:#00aabb;display:block;margin-top:2px;">Password Reset Request</span>
                </td>
              </tr>
              <tr>
                <td style="padding:30px;color:#333333;font-size:14px;line-height:1.6;">
                  <p style="margin-top:0;">Hello %s,</p>
                  <p>We received a request to reset your administrator password for the IAST Quality Portal.</p>
                  <p>Please click the button below to choose a new password:</p>
                  <p style="text-align:center;margin:30px 0;">
                    <a href="%s" style="background-color:#00aabb;color:#ffffff;padding:12px 24px;text-decoration:none;border-radius:4px;font-weight:bold;display:inline-block;">Reset My Password</a>
                  </p>
                  <p style="font-size:12px;color:#666666;">This password reset link is single-use and will expire in 1 hour. If you did not request a password reset, please ignore this email.</p>
                </td>
              </tr>
            </table>
          </center>
        </body>
        </html>
        """.formatted(escapeHtml(username != null ? username : "Administrator"), escapeHtml(resetUrl));

        helper.setText(htmlContent, true);
        mailSender.send(message);
        log.info("[Security Audit] Password reset email sent to: {}", toEmail);
    }

    private String buildHtmlBody(String name, String email, String message, String submittedAt) {
        String nameSafe = escapeHtml(name);
        String emailSafe = escapeHtml(email);
        String messageSafe = escapeHtml(message).replace("\n", "<br/>");

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>New Feedback – IAST Quality Website</title>
        </head>
        <body style="margin:0;padding:0;background-color:#f4f7fb;">
          <center style="width:100%;background-color:#f4f7fb;">
            <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" align="center" style="width:600px;max-width:600px;margin:0 auto;background-color:#ffffff;">
        
              <!-- Header -->
              <tr>
                <td style="background-color:#0d2b45;padding:20px 30px;">
                  <span style="font-family:Arial,Helvetica,sans-serif;font-size:18px;font-weight:bold;color:#ffffff;">IAST</span>
                  <span style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#00aabb;display:block;margin-top:2px;">Feedback Notification</span>
                </td>
              </tr>
        
              <!-- Body -->
              <tr>
                <td style="padding:30px;font-family:Arial,Helvetica,sans-serif;color:#333333;">
        
                  <p style="margin:0 0 16px 0;font-size:15px;line-height:1.5;">
                    You've received new feedback through the IAST Quality Website.
                  </p>
        
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#333333;margin-bottom:20px;">
                    <tr>
                      <td style="padding:6px 0;width:120px;color:#666666;">Name:</td>
                      <td style="padding:6px 0;">[NAME]</td>
                    </tr>
                    <tr>
                      <td style="padding:6px 0;color:#666666;">Email:</td>
                      <td style="padding:6px 0;">[EMAIL]</td>
                    </tr>
                    <tr>
                      <td style="padding:6px 0;color:#666666;">Date:</td>
                      <td style="padding:6px 0;">[SUBMITTED_AT]</td>
                    </tr>
                  </table>
        
                  <p style="margin:0 0 6px 0;font-size:13px;font-weight:bold;color:#0d2b45;">Message:</p>
                  <p style="margin:0 0 24px 0;padding:14px 16px;background-color:#f4f7fb;border-left:3px solid #00aabb;font-size:14px;line-height:1.6;color:#333333;">
                    [MESSAGE]
                  </p>
        
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                    <tr>
                      <td style="background-color:#00aabb;border-radius:4px;">
                        <a href="mailto:[EMAIL]?subject=Re: Your Feedback on IAST Quality Website" target="_blank" style="display:inline-block;padding:12px 24px;font-family:Arial,Helvetica,sans-serif;font-size:14px;font-weight:bold;color:#ffffff;text-decoration:none;">
                          Reply to [NAME]
                        </a>
                      </td>
                    </tr>
                  </table>
        
                </td>
              </tr>
        
              <!-- Footer -->
              <tr>
                <td style="padding:20px 30px;border-top:1px solid #e9eef4;font-family:Arial,Helvetica,sans-serif;font-size:11px;color:#888888;">
                  IAST Software Solutions &middot; Quality Website Feedback System<br/>
                  ISO 9001 &middot; ISO 27001 &middot; ASPICE L2
                </td>
              </tr>
        
            </table>
          </center>
        </body>
        </html>
        """
        .replace("[NAME]", nameSafe)
        .replace("[EMAIL]", emailSafe)
        .replace("[MESSAGE]", messageSafe)
        .replace("[SUBMITTED_AT]", submittedAt);
    }

    private String buildTextBody(String name, String email, String message, String submittedAt) {
        return """
        New feedback received on IAST Quality Website
        ==============================================
        
        Name:    %s
        Email:   %s
        Date:    %s
        
        Message:
        %s
        
        ---
        IAST Software Solutions – Quality Website
        """.formatted(name, email, submittedAt, message);
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;");
    }
}