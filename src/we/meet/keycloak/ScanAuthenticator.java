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

import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * 扫码登录 authenticator：web 走 OIDC 进 Keycloak 页出示二维码、已登录 App 扫码确认，
 * Keycloak 自己 setUser + success **建浏览器 SSO 会话**（而非把 token 发给 web，那样
 * 只登进 meet 单点、给不了跨系统 SSO）。详见 we-meet docs/features/qr_login_sso.md。
 *
 * 轮询模型（纯 GET reload —— 无 session_code 轮转、无跨域 CORS、无双轮询者竞态）：
 *   - authenticate()：如无 qrToken 则调后端 initiate 拿一个并存 authNote；随后查后端
 *     authenticator-status 决定渲染或建会话。这是唯一的轮询点。
 *   - scan-login.ftl 内 scan-poll.js 每 3s `location.reload()`（GET）→ 重入 authenticate()。
 *   - confirmed → getUserById(sub) + setUser + success → 302 回 meet。
 *   - expired/cancelled → 换新码继续。
 * 后端 authenticator-status 是唯一后端轮询者（KC 服务端→后端，带 shared-bearer）；
 * 浏览器只跟 KC 同源通信，身份只在服务端流转、不经浏览器。
 */
public class ScanAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(ScanAuthenticator.class.getName());
    static final String NOTE_QR_TOKEN = "sa_qr_token";
    private static final int QR_SIZE = 240;

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        String base = strCfg(ctx, "backend_base_url", "");
        String qrToken = ctx.getAuthenticationSession().getAuthNote(NOTE_QR_TOKEN);

        if (qrToken == null) {
            qrToken = ScanGatewayClient.initiate(base);
            if (qrToken == null) {
                ctx.challenge(ctx.form().setError("scan.initFailed").createForm("scan-login.ftl"));
                return;
            }
            ctx.getAuthenticationSession().setAuthNote(NOTE_QR_TOKEN, qrToken);
        }

        // 每次(重)加载都查一次后端态：页面 JS 每 3s 整页 reload → 重入本方法。
        ScanGatewayClient.Status st =
                ScanGatewayClient.status(base, strCfg(ctx, "gateway_token", ""), qrToken);
        String status = (st == null || st.status == null) ? "expired" : st.status;

        if ("confirmed".equals(status) && st.sub != null) {
            // App 用户本就是已存在的 KC 用户（已登录）→ 直接按 sub(=KC UUID) 取，
            // 不 find-or-create。setUser + success 让 Keycloak 建浏览器 SSO 会话。
            UserModel user = ctx.getSession().users().getUserById(ctx.getRealm(), st.sub);
            if (user == null) {
                LOG.warning("ScanAuth: confirmed sub not found in realm: " + st.sub);
                ctx.failure(AuthenticationFlowError.UNKNOWN_USER);
                return;
            }
            LOG.info("ScanAuth: confirmed → building SSO session for " + st.sub);
            ctx.setUser(user);
            ctx.success();
            return;
        }

        if ("expired".equals(status) || "cancelled".equals(status)) {
            // 二维码失效 → 立刻换一个 pending 码（下次 reload 就展示新码）。
            ctx.getAuthenticationSession().removeAuthNote(NOTE_QR_TOKEN);
            String fresh = ScanGatewayClient.initiate(base);
            if (fresh == null) {
                ctx.challenge(ctx.form().setError("scan.initFailed").createForm("scan-login.ftl"));
                return;
            }
            ctx.getAuthenticationSession().setAuthNote(NOTE_QR_TOKEN, fresh);
            qrToken = fresh;
            status = "pending";
            st = null;
        }

        ctx.challenge(scanForm(ctx, qrToken, status, st));
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        // 扫码页无表单交互（轮询靠整页 GET reload）；任何 POST（含 tryAnotherWay 之外的
        // 意外提交）一律回到渲染。tryAnotherWay 由 Keycloak 处理器在此之前拦截。
        authenticate(ctx);
    }

    private Response scanForm(AuthenticationFlowContext ctx, String qrToken, String status,
                             ScanGatewayClient.Status st) {
        var form = ctx.form()
                .setAttribute("qrImage", qrDataUri("we-meet://qr-login?token=" + qrToken))
                .setAttribute("scanStatus", status);
        if (st != null && "scanned".equals(status) && st.name != null) {
            form = form.setAttribute("scannedName", st.name);
        }
        return form.createForm("scan-login.ftl");
    }

    /** 服务端用 zxing 生成二维码 PNG，返回 data URI 供 ftl <img> 直接嵌入。 */
    private String qrDataUri(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            LOG.severe("ScanAuth: QR render failed: " + e.getMessage());
            return "";
        }
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
