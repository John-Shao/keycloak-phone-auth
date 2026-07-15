package we.meet.keycloak;

import org.keycloak.util.JsonSerialization;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

    /** 后端 otp/verify 的结果（transport 失败返回 null）。reason: expired/locked/wrong/phone/null。 */
    public static class OtpVerify {
        public boolean valid;
        public String reason;
        public Integer remaining;
    }

    /**
     * 调后端校验验证码（单页手机登录，KC 服务端→后端 shared-bearer）。
     *   POST {baseUrl}/api/keycloak-sms/otp/verify/  {"phone","otp"}  Bearer <token>
     *   → {"valid": bool, "error": str}
     * transport/HTTP 失败返回 null；否则返回 OtpVerify。
     */
    public static OtpVerify verifyOtp(String baseUrl, String token, String phone, String otp) {
        if (baseUrl == null || baseUrl.isBlank()) {
            LOG.severe("UnifiedAuth: backend_base_url is not configured");
            return null;
        }
        String body = "{\"phone\":\"" + escape(phone) + "\",\"otp\":\"" + escape(otp) + "\"}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            String url = trimSlash(baseUrl) + "/api/keycloak-sms/otp/verify/";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
            if (status < 200 || status >= 300) {
                LOG.warning("UnifiedAuth: otp/verify HTTP " + status);
                return null;
            }
            Map<String, Object> data = JsonSerialization.readValue(conn.getInputStream(), Map.class);
            OtpVerify r = new OtpVerify();
            r.valid = Boolean.TRUE.equals(data.get("valid"));
            Object rs = data.get("reason");
            r.reason = rs != null ? rs.toString() : null;
            Object rm = data.get("remaining");
            if (rm instanceof Number n) r.remaining = n.intValue();
            return r;
        } catch (Exception ex) {
            LOG.severe("UnifiedAuth: otp/verify call failed: " + ex.getMessage());
            return null;
        }
    }

    /** Minimal JSON string escaping (backslash, double-quote, control chars). */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
