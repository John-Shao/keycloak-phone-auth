package we.meet.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Arrays;
import java.util.List;

/**
 * 统一登录 authenticator（阶段二·抖音式双栏：扫码 + 手机号 OTP 一页）。config 合并
 * phone（sms_gateway_* / otp_* / demo_* / email_domain）与 scan（backend_base_url /
 * gateway_token）两侧。设计见 we-meet docs/features/qr_login_sso.md。
 */
public class UnifiedLoginAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "unified-login-authenticator";

    private static final UnifiedLoginAuthenticator SINGLETON = new UnifiedLoginAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    private static ProviderConfigProperty prop(String name, String label, String type,
                                               String def, String help) {
        ProviderConfigProperty p = new ProviderConfigProperty();
        p.setName(name);
        p.setLabel(label);
        p.setType(type);
        if (def != null) p.setDefaultValue(def);
        p.setHelpText(help);
        return p;
    }

    static {
        String S = ProviderConfigProperty.STRING_TYPE;
        String P = ProviderConfigProperty.PASSWORD;
        CONFIG_PROPERTIES = Arrays.asList(
            // ---- 手机号 OTP 侧 ----
            prop("sms_gateway_url", "SMS Gateway URL", S, null,
                 "e.g. https://meet.we-meet.online/keycloak-sms/send/"),
            prop("sms_gateway_token", "SMS Gateway Bearer Token", P, null,
                 "Value of backend KEYCLOAK_SMS_GATEWAY_TOKEN."),
            prop("otp_length", "OTP Length", S, "6", "Number of digits (4–8)."),
            prop("otp_expiry_seconds", "OTP Expiry (seconds)", S, "300", "OTP 有效期."),
            prop("otp_max_attempts", "Max OTP Attempts", S, "3", "错误尝试上限."),
            prop("demo_phones", "Demo Phone Numbers", S, null,
                 "Comma-separated demo phones (mirror backend MOBILE_AUTH_DEMO_PHONES)."),
            prop("demo_otp", "Demo OTP", S, null, "Fixed OTP for demo phones."),
            prop("email_domain", "Synthetic Email Domain", S, "phone.we-meet.online",
                 "合成手机用户 email 的域名，免 VERIFY_PROFILE 卡住."),
            // ---- 扫码侧 ----
            prop("backend_base_url", "Backend Base URL", S, null,
                 "meet-backend origin, e.g. https://meet.we-meet.online (用于 /api/qr-login/*)."),
            prop("gateway_token", "QR Authenticator Gateway Token", P, null,
                 "Value of backend QR_AUTHENTICATOR_GATEWAY_TOKEN.")
        );
    }

    @Override public String getId()               { return PROVIDER_ID; }
    @Override public String getDisplayType()      { return "Unified Login (Scan + Phone OTP)"; }
    @Override public String getReferenceCategory() { return "otp"; }
    @Override public String getHelpText() {
        return "双栏统一登录：左扫码 + 右手机号验证码，Keycloak 建浏览器会话（SSO）。";
    }
    @Override public boolean isConfigurable()      { return true; }
    @Override public boolean isUserSetupAllowed()  { return false; }
    @Override public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return REQUIREMENT_CHOICES; }
    @Override public List<ProviderConfigProperty> getConfigProperties() { return CONFIG_PROPERTIES; }
    @Override public Authenticator create(KeycloakSession session) { return SINGLETON; }
    @Override public void init(Config.Scope config) {}
    @Override public void postInit(KeycloakSessionFactory factory) {}
    @Override public void close() {}
}
