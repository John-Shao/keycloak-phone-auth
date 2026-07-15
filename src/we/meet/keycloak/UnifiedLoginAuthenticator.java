package we.meet.keycloak;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 统一登录 authenticator（阶段二 · 抖音式双栏）：单个 execution 渲染一页双栏 ——
 * 左「扫码登录」+ 右「手机号验证码登录」，action() 按提交字段分派。合并了阶段一
 * ScanAuthenticator + PhoneAuthenticator 的逻辑（HTTP 客户端复用 ScanGatewayClient /
 * SmsGatewayClient，不重复）。设计见 we-meet docs/features/qr_login_sso.md。
 *
 * 分派（action）：
 *   - 表单带 qrConfirm       → 扫码列：查后端 authenticator-status，confirmed 则
 *                              getUserById(sub)+setUser+success 建 KC 会话。
 *   - 无 ul_phone note + phone → 手机列第 1 步：校验手机号、发 OTP、转 otp 态。
 *   - 有 ul_phone note        → 手机列第 2 步：action=resend 重发，否则校验 OTP → success。
 *
 * 扫码列轮询走页面 unified-poll.js 的 **AJAX**（fetch 后端 /api/qr-login/ready/，
 * 只读 status），不整页 reload —— 否则会冲掉用户正在输入的手机号/验证码。确认后
 * JS 才提交一次 qrConfirm 整表单，由本类服务端查受保护 authenticator-status 建会话。
 */
public class UnifiedLoginAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(UnifiedLoginAuthenticator.class.getName());

    static final String NOTE_PHONE    = "ul_phone";
    static final String NOTE_OTP      = "ul_otp";
    static final String NOTE_OTP_TIME = "ul_otp_time";
    static final String NOTE_ATTEMPTS = "ul_attempts";
    static final String NOTE_QR_TOKEN = "ul_qr_token";
    private static final int QR_SIZE = 240;

    // -------------------------------------------------------------------------
    // SPI entry points
    // -------------------------------------------------------------------------

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        ensureQrToken(ctx);
        ctx.challenge(renderPage(ctx, null));
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        MultivaluedMap<String, String> form = ctx.getHttpRequest().getDecodedFormParameters();

        // 扫码列：JS 侦测到 confirmed 后提交的最终确认
        if (form.getFirst("qrConfirm") != null) {
            handleQrConfirm(ctx);
            return;
        }

        // 手机列
        String phone = ctx.getAuthenticationSession().getAuthNote(NOTE_PHONE);
        if (phone == null) {
            handlePhoneSubmit(ctx, form);
        } else if ("resend".equals(form.getFirst("action"))) {
            handleResend(ctx, phone);
        } else {
            handleOtpSubmit(ctx, form, phone);
        }
    }

    // -------------------------------------------------------------------------
    // 扫码列
    // -------------------------------------------------------------------------

    private void handleQrConfirm(AuthenticationFlowContext ctx) {
        String qrToken = ctx.getAuthenticationSession().getAuthNote(NOTE_QR_TOKEN);
        if (qrToken == null) { authenticate(ctx); return; }

        ScanGatewayClient.Status st = ScanGatewayClient.status(
                strCfg(ctx, "backend_base_url", ""),
                strCfg(ctx, "gateway_token", ""), qrToken);

        if (st != null && "confirmed".equals(st.status) && st.sub != null) {
            // App 用户本就是已存在的 KC 用户 → 按 sub(=KC UUID) 取，setUser+success 建会话。
            UserModel user = ctx.getSession().users().getUserById(ctx.getRealm(), st.sub);
            if (user == null) {
                LOG.warning("UnifiedAuth: confirmed sub not found: " + st.sub);
                ctx.failure(AuthenticationFlowError.UNKNOWN_USER);
                return;
            }
            LOG.info("UnifiedAuth: scan confirmed → building SSO session for " + st.sub);
            ctx.setUser(user);
            ctx.success();
            return;
        }
        // 竞态/未确认 → 回渲染（JS 会继续轮询）
        ctx.challenge(renderPage(ctx, null));
    }

    private void ensureQrToken(AuthenticationFlowContext ctx) {
        String qrToken = ctx.getAuthenticationSession().getAuthNote(NOTE_QR_TOKEN);
        String base = strCfg(ctx, "backend_base_url", "");
        boolean alive = qrToken != null;
        if (alive) {
            ScanGatewayClient.Status st = ScanGatewayClient.status(
                    base, strCfg(ctx, "gateway_token", ""), qrToken);
            String s = (st == null || st.status == null) ? "expired" : st.status;
            alive = !("expired".equals(s) || "cancelled".equals(s));
        }
        if (!alive) {
            String fresh = ScanGatewayClient.initiate(base);
            if (fresh != null) {
                ctx.getAuthenticationSession().setAuthNote(NOTE_QR_TOKEN, fresh);
            } else {
                ctx.getAuthenticationSession().removeAuthNote(NOTE_QR_TOKEN);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 手机列 —— 第 1 步（手机号 → 发 OTP）
    // -------------------------------------------------------------------------

    private void handlePhoneSubmit(AuthenticationFlowContext ctx,
                                   MultivaluedMap<String, String> form) {
        String phone = trimOrNull(form.getFirst("phone"));
        if (phone == null) {
            ctx.challenge(renderPage(ctx, "phone.required"));
            return;
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            ctx.challenge(renderPageWithPhone(ctx, phone, "input", "phone.invalid"));
            return;
        }

        UserModel user = findOrCreateUser(ctx, phone);
        ctx.setUser(user);

        String otp = issueOtp(ctx, phone);
        if (otp == null) {
            clearPhoneNotes(ctx);
            ctx.challenge(renderPageWithPhone(ctx, phone, "input", "sms.failed"));
            return;
        }

        ctx.getAuthenticationSession().setAuthNote(NOTE_PHONE,    phone);
        ctx.getAuthenticationSession().setAuthNote(NOTE_OTP,      otp);
        ctx.getAuthenticationSession().setAuthNote(NOTE_OTP_TIME, Long.toString(System.currentTimeMillis()));
        ctx.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, "0");
        ctx.challenge(renderPageWithPhone(ctx, phone, "otp", null));
    }

    // -------------------------------------------------------------------------
    // 手机列 —— 第 2 步（校验 OTP）
    // -------------------------------------------------------------------------

    private void handleOtpSubmit(AuthenticationFlowContext ctx,
                                 MultivaluedMap<String, String> form, String phone) {
        String storedOtp   = ctx.getAuthenticationSession().getAuthNote(NOTE_OTP);
        long   sentAt      = longNote(ctx, NOTE_OTP_TIME);
        int    attempts    = intNote(ctx, NOTE_ATTEMPTS);
        int    maxAttempts = intCfg(ctx, "otp_max_attempts", 3);
        long   expiryMs    = intCfg(ctx, "otp_expiry_seconds", 300) * 1000L;

        if (System.currentTimeMillis() - sentAt > expiryMs) {
            clearPhoneNotes(ctx);
            ctx.challenge(renderPage(ctx, "otp.expired"));
            return;
        }
        if (attempts >= maxAttempts) {
            clearPhoneNotes(ctx);
            ctx.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        String input = trimOrNull(form.getFirst("otp"));
        if (storedOtp != null && storedOtp.equals(input)) {
            ctx.success();
            return;
        }

        attempts++;
        ctx.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, Integer.toString(attempts));
        int remaining = maxAttempts - attempts;
        String errKey = remaining > 0 ? "otp.wrong" : "otp.locked";
        ctx.challenge(renderPageWithPhone(ctx, phone, "otp", errKey, remaining));
    }

    private void handleResend(AuthenticationFlowContext ctx, String phone) {
        String otp = issueOtp(ctx, phone);
        if (otp != null) {
            ctx.getAuthenticationSession().setAuthNote(NOTE_OTP,      otp);
            ctx.getAuthenticationSession().setAuthNote(NOTE_OTP_TIME, Long.toString(System.currentTimeMillis()));
            ctx.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, "0");
            ctx.challenge(renderPageWithPhone(ctx, phone, "otp", null, true));
        } else {
            ctx.challenge(renderPageWithPhone(ctx, phone, "otp", "sms.failed"));
        }
    }

    // -------------------------------------------------------------------------
    // 渲染（双栏页）
    // -------------------------------------------------------------------------

    private Response renderPage(AuthenticationFlowContext ctx, String errKey) {
        String phone = ctx.getAuthenticationSession().getAuthNote(NOTE_PHONE);
        return renderPageWithPhone(ctx, phone, phone == null ? "input" : "otp", errKey);
    }

    private Response renderPageWithPhone(AuthenticationFlowContext ctx, String phone,
                                         String phoneStep, String errKey, Object... errArgs) {
        String qrToken = ctx.getAuthenticationSession().getAuthNote(NOTE_QR_TOKEN);
        var form = ctx.form()
                .setAttribute("phoneStep", phoneStep)
                .setAttribute("readyBase", strCfg(ctx, "backend_base_url", ""));
        if (qrToken != null) {
            form = form.setAttribute("qrImage", qrDataUri("we-meet://qr-login?token=" + qrToken))
                       .setAttribute("qrToken", qrToken);
        }
        if (phone != null) form = form.setAttribute("phone", phone);
        // resend 成功提示：errArgs 里若首个为 Boolean.TRUE 视为 resent 标记
        if (errArgs.length == 1 && Boolean.TRUE.equals(errArgs[0])) {
            form = form.setAttribute("resent", Boolean.TRUE);
        } else if (errKey != null) {
            form = form.setError(errKey, errArgs);
        }
        return form.createForm("unified-login.ftl");
    }

    /** resend 成功的重载（第 4 参给布尔标记而非错误）。 */
    private Response renderPageWithPhone(AuthenticationFlowContext ctx, String phone,
                                         String phoneStep, String errKey, boolean resent) {
        if (resent) return renderPageWithPhone(ctx, phone, phoneStep, null, (Object) Boolean.TRUE);
        return renderPageWithPhone(ctx, phone, phoneStep, errKey);
    }

    private String qrDataUri(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            LOG.severe("UnifiedAuth: QR render failed: " + e.getMessage());
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // 用户 / OTP 助手（平移自 PhoneAuthenticator）
    // -------------------------------------------------------------------------

    private UserModel findOrCreateUser(AuthenticationFlowContext ctx, String phone) {
        RealmModel realm = ctx.getRealm();
        UserModel user = ctx.getSession().users()
                .searchForUserByUserAttributeStream(realm, "phoneNumber", phone)
                .findFirst().orElse(null);
        if (user == null) {
            LOG.info("UnifiedAuth: creating new user for phone " + phone);
            user = ctx.getSession().users().addUser(realm, phone);
            user.setEnabled(true);
            user.setSingleAttribute("phoneNumber", phone);
        }
        fillProfile(ctx, user, phone);
        return user;
    }

    private void fillProfile(AuthenticationFlowContext ctx, UserModel user, String phone) {
        if (isBlank(user.getEmail())) {
            user.setEmail(phone + "@" + strCfg(ctx, "email_domain", "phone.we-meet.online"));
            user.setEmailVerified(true);
        }
        if (isBlank(user.getFirstName())) {
            user.setFirstName("meet-" + phone.substring(phone.length() - 4));
        }
        if (isBlank(user.getLastName())) {
            user.setLastName("we");
        }
    }

    private String issueOtp(AuthenticationFlowContext ctx, String phone) {
        String demoOtp = strCfg(ctx, "demo_otp", "");
        if (!demoOtp.isEmpty() && isDemoPhone(ctx, phone)) {
            LOG.info("UnifiedAuth: demo phone " + phone + " — fixed OTP, SMS skipped");
            return demoOtp;
        }
        String otp = generateOtp(intCfg(ctx, "otp_length", 6));
        boolean sent = SmsGatewayClient.sendOtp(
                strCfg(ctx, "sms_gateway_url", ""),
                strCfg(ctx, "sms_gateway_token", ""),
                phone, otp);
        return sent ? otp : null;
    }

    private boolean isDemoPhone(AuthenticationFlowContext ctx, String phone) {
        for (String p : strCfg(ctx, "demo_phones", "").split(",")) {
            if (p.trim().equals(phone)) return true;
        }
        return false;
    }

    private String generateOtp(int length) {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(rng.nextInt(10));
        return sb.toString();
    }

    private void clearPhoneNotes(AuthenticationFlowContext ctx) {
        ctx.getAuthenticationSession().removeAuthNote(NOTE_PHONE);
        ctx.getAuthenticationSession().removeAuthNote(NOTE_OTP);
        ctx.getAuthenticationSession().removeAuthNote(NOTE_OTP_TIME);
        ctx.getAuthenticationSession().removeAuthNote(NOTE_ATTEMPTS);
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private String strCfg(AuthenticationFlowContext ctx, String key, String def) {
        if (ctx.getAuthenticatorConfig() == null) return def;
        return ctx.getAuthenticatorConfig().getConfig().getOrDefault(key, def);
    }
    private int intCfg(AuthenticationFlowContext ctx, String key, int def) {
        try { return Integer.parseInt(strCfg(ctx, key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
    private long longNote(AuthenticationFlowContext ctx, String key) {
        String v = ctx.getAuthenticationSession().getAuthNote(key);
        try { return v != null ? Long.parseLong(v) : 0L; }
        catch (NumberFormatException e) { return 0L; }
    }
    private int intNote(AuthenticationFlowContext ctx, String key) {
        String v = ctx.getAuthenticationSession().getAuthNote(key);
        try { return v != null ? Integer.parseInt(v) : 0; }
        catch (NumberFormatException e) { return 0; }
    }

    @Override public boolean requiresUser() { return false; }
    @Override public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) { return true; }
    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {}
    @Override public void close() {}
}
