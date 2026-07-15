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

public class ScanAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "scan-authenticator";

    private static final ScanAuthenticator SINGLETON = new ScanAuthenticator();

    // 与 phone-authenticator 一样只支持 REQUIRED/DISABLED —— 需嵌在 ALTERNATIVE
    // 子流里（配 Cookie 步骤保 SSO），见 bootstrap-scan-auth.sh。
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        ProviderConfigProperty baseUrl = new ProviderConfigProperty();
        baseUrl.setName("backend_base_url");
        baseUrl.setLabel("Backend Base URL");
        baseUrl.setType(ProviderConfigProperty.STRING_TYPE);
        baseUrl.setHelpText("meet-backend origin, e.g. https://meet.we-meet.online (用于 /api/qr-login/*).");

        ProviderConfigProperty gwToken = new ProviderConfigProperty();
        gwToken.setName("gateway_token");
        gwToken.setLabel("Authenticator Gateway Token");
        gwToken.setType(ProviderConfigProperty.PASSWORD);
        gwToken.setHelpText("Value of backend QR_AUTHENTICATOR_GATEWAY_TOKEN (作 Bearer 发给 authenticator-status).");

        CONFIG_PROPERTIES = Arrays.asList(baseUrl, gwToken);
    }

    @Override public String getId()               { return PROVIDER_ID; }
    @Override public String getDisplayType()      { return "Scan (QR) Login Authentication"; }
    @Override public String getReferenceCategory() { return "otp"; }
    @Override public String getHelpText() {
        return "扫码登录：web 出示二维码，已登录 App 扫码确认，Keycloak 建浏览器会话（SSO）。";
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
