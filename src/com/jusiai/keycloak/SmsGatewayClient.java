package com.jusiai.keycloak;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Thin HTTP client that calls the meet-backend SMS gateway.
 *
 * POST {"msisdn": "138xxxx", "message": "您的验证码是：123456，5分钟内有效，请勿泄露。"}
 *
 * The gateway (keycloak_sms.py) extracts the 6-digit code via regex and
 * forwards it to Volcengine SendSms with the configured template.
 */
public class SmsGatewayClient {

    private static final Logger LOG = Logger.getLogger(SmsGatewayClient.class.getName());
    private static final int TIMEOUT_MS = 10_000;

    /**
     * Send OTP to the given phone number via the meet-backend SMS gateway.
     *
     * @param gatewayUrl  Full URL of the gateway endpoint
     * @param token       Bearer token (may be empty)
     * @param phone       Recipient phone number
     * @param otp         Numeric OTP code
     * @return true if the gateway returned HTTP 2xx
     */
    public static boolean sendOtp(String gatewayUrl, String token, String phone, String otp) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            LOG.severe("PhoneAuth: sms_gateway_url is not configured");
            return false;
        }

        // Build JSON body manually – no external JSON library required
        String message  = "您的验证码是：" + otp + "，5分钟内有效，请勿泄露。";
        String body     = "{\"msisdn\":\"" + escape(phone) + "\",\"message\":\"" + escape(message) + "\"}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(gatewayUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(bodyBytes.length));
            if (token != null && !token.isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            LOG.info("PhoneAuth: SMS gateway " + phone + " → HTTP " + status);
            return status >= 200 && status < 300;

        } catch (Exception e) {
            LOG.severe("PhoneAuth: SMS gateway call failed for " + phone + ": " + e.getMessage());
            return false;
        }
    }

    /** Minimal JSON string escaping (backslash, double-quote, control chars). */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
