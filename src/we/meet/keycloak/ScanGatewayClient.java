package we.meet.keycloak;

import org.keycloak.util.JsonSerialization;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 调 we-meet 后端 qr-login 网关的瘦 HTTP 客户端（照 SmsGatewayClient，无外部依赖，
 * 只用 HttpURLConnection + Keycloak 自带 JsonSerialization）。
 *
 *   initiate: POST {base}/api/qr-login/initiate/           → {"token": "..."}
 *   status:   GET  {base}/api/qr-login/authenticator-status/?token=...
 *             Authorization: Bearer <gateway_token>
 *             → {"status": "...", "user": {"sub","phone","name"}}
 *
 * 详见 we-meet docs/features/qr_login_sso.md。
 */
public class ScanGatewayClient {

    private static final Logger LOG = Logger.getLogger(ScanGatewayClient.class.getName());
    private static final int TIMEOUT_MS = 10_000;

    /** 后端返回的扫码态 + 已确认用户身份（不含 token）。 */
    public static class Status {
        public String status;
        public String sub;
        public String phone;
        public String name;
    }

    /** 生成新 qrToken；失败返回 null。 */
    public static String initiate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            LOG.severe("ScanAuth: backend_base_url is not configured");
            return null;
        }
        try {
            HttpURLConnection conn = open(trimSlash(baseUrl) + "/api/qr-login/initiate/", "POST", null);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            if (!ok(conn)) return null;
            Map<String, Object> data = JsonSerialization.readValue(conn.getInputStream(), Map.class);
            Object t = data.get("token");
            return t != null ? t.toString() : null;
        } catch (Exception e) {
            LOG.severe("ScanAuth: initiate failed: " + e.getMessage());
            return null;
        }
    }

    /** 查扫码态 + 身份；网络/HTTP 失败返回 null（调用方按 expired 处理）。 */
    public static Status status(String baseUrl, String gwToken, String qrToken) {
        if (baseUrl == null || baseUrl.isBlank()) return null;
        try {
            String url = trimSlash(baseUrl) + "/api/qr-login/authenticator-status/?token="
                    + URLEncoder.encode(qrToken, StandardCharsets.UTF_8);
            HttpURLConnection conn = open(url, "GET", gwToken);
            if (!ok(conn)) return null;
            Map<String, Object> data = JsonSerialization.readValue(conn.getInputStream(), Map.class);
            Status s = new Status();
            Object st = data.get("status");
            s.status = st != null ? st.toString() : null;
            Object u = data.get("user");
            if (u instanceof Map<?, ?> um) {
                s.sub = str(um.get("sub"));
                s.phone = str(um.get("phone"));
                s.name = str(um.get("name"));
            }
            return s;
        } catch (Exception e) {
            LOG.severe("ScanAuth: status failed: " + e.getMessage());
            return null;
        }
    }

    private static HttpURLConnection open(String url, String method, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        if (bearer != null && !bearer.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        return conn;
    }

    private static boolean ok(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            LOG.warning("ScanAuth: gateway HTTP " + code);
            return false;
        }
        return true;
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
