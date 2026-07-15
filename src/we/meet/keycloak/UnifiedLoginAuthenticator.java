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
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 统一登录 authenticator（抖音式双栏 · 单页）：一页双栏 —— 左「扫码登录」+
 * 右「手机号验证码登录」，action() 按提交字段分派。设计见 we-meet
 * docs/features/qr_login_sso.md、docs/features/single_page_otp_login.md。
 *
 * 手机侧（单页，OTP 生命周期下沉后端，与 mobile app 复用）：
 *   - 「获取验证码」由页面 unified-otp.js **AJAX 直发后端** /api/keycloak-sms/otp/send/，
 *     不刷新页面、启动倒计时（authenticator 不参与发码）。
 *   - 「验证登录」整页 POST（phone+otp）→ action() 调后端 /api/keycloak-sms/otp/verify/
 *     （shared-bearer 校验、只回 valid 不发 token）→ valid 则 findOrCreateUser+setUser
 *     +success 建会话；invalid 则重渲染报错。loginAction 只命中这一次，无 session_code 复用。
 *
 * 扫码侧（不变）：authenticate 出示二维码；页面 AJAX 轮询后端 /ready/；confirmed 时
 * 提交 qrConfirm → 服务端查受保护 authenticator-status → setUser+success 建会话。
 */
public class UnifiedLoginAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(UnifiedLoginAuthenticator.class.getName());
    static final String NOTE_QR_TOKEN = "ul_qr_token";
    private static final int QR_SIZE = 240;

    // -------------------------------------------------------------------------
    // SPI entry points
    // -------------------------------------------------------------------------

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        ensureQrToken(ctx);
        ctx.challenge(renderPage(ctx, null, null));
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        MultivaluedMap<String, String> form = ctx.getHttpRequest().getDecodedFormParameters();

        // 扫码列：JS 侦测到 confirmed 后提交的最终确认
        if (form.getFirst("qrConfirm") != null) {
            handleQrConfirm(ctx);
            return;
        }
        // 手机列：验证登录（phone + otp 一次提交）
        handlePhoneLogin(ctx, form);
    }

    // -------------------------------------------------------------------------
    // 手机列 —— 单页验证登录
    // -------------------------------------------------------------------------

    private void handlePhoneLogin(AuthenticationFlowContext ctx,
                                  MultivaluedMap<String, String> form) {
        String phone = trimOrNull(form.getFirst("phone"));
        String otp = trimOrNull(form.getFirst("otp"));

        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            ctx.challenge(renderPage(ctx, phone, "phone.invalid"));
            return;
        }
        if (otp == null) {
            ctx.challenge(renderPage(ctx, phone, "请先输入验证码"));
            return;
        }

        SmsGatewayClient.OtpVerify r = SmsGatewayClient.verifyOtp(
                strCfg(ctx, "backend_base_url", ""),
                strCfg(ctx, "sms_gateway_token", ""),
                phone, otp);

        if (r == null) {
            ctx.challenge(renderPage(ctx, phone, "服务暂时不可用，请稍后重试"));
            return;
        }
        if (!r.valid) {
            ctx.challenge(renderPage(ctx, phone, r.error != null ? r.error : "验证码错误"));
            return;
        }

        UserModel user = findOrCreateUser(ctx, phone);
        ctx.setUser(user);
        ctx.success();
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
        ctx.challenge(renderPage(ctx, null, null));
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
    // 渲染（双栏页）
    // -------------------------------------------------------------------------

    private Response renderPage(AuthenticationFlowContext ctx, String phone, String errKey) {
        String qrToken = ctx.getAuthenticationSession().getAuthNote(NOTE_QR_TOKEN);
        var form = ctx.form().setAttribute("readBase", strCfg(ctx, "backend_base_url", ""));
        if (qrToken != null) {
            form = form.setAttribute("qrImage", qrDataUri("we-meet://qr-login?token=" + qrToken))
                       .setAttribute("qrToken", qrToken);
        }
        if (phone != null) form = form.setAttribute("phone", phone);
        // errKey 可以是 message key（如 phone.invalid）或后端回来的原文——未命中 key
        // 的字符串 Keycloak 直接按字面渲染，两种都可用。
        if (errKey != null) form = form.setError(errKey);
        return form.createForm("unified-login.ftl");
    }

    /** 服务端用 zxing 生成二维码 PNG，返回 data URI 供 ftl <img> 直接嵌入。 */
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
    // 用户助手（建 KC 用户，扫码/手机共用）
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

    @Override public boolean requiresUser() { return false; }
    @Override public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) { return true; }
    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {}
    @Override public void close() {}
}
