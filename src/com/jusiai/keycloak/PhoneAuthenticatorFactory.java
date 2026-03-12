package com.jusiai.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Arrays;
import java.util.List;

public class PhoneAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "phone-authenticator";

    private static final PhoneAuthenticator SINGLETON = new PhoneAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        ProviderConfigProperty gatewayUrl = new ProviderConfigProperty();
        gatewayUrl.setName("sms_gateway_url");
        gatewayUrl.setLabel("SMS Gateway URL");
        gatewayUrl.setType(ProviderConfigProperty.STRING_TYPE);
        gatewayUrl.setHelpText("SMS gateway POST endpoint, e.g. https://meet.jusiai.com/keycloak-sms/send/");

        ProviderConfigProperty gatewayToken = new ProviderConfigProperty();
        gatewayToken.setName("sms_gateway_token");
        gatewayToken.setLabel("SMS Gateway Bearer Token");
        gatewayToken.setType(ProviderConfigProperty.PASSWORD);
        gatewayToken.setHelpText("Value of KEYCLOAK_SMS_GATEWAY_TOKEN. Leave blank to disable auth.");

        ProviderConfigProperty otpLength = new ProviderConfigProperty();
        otpLength.setName("otp_length");
        otpLength.setLabel("OTP Length");
        otpLength.setType(ProviderConfigProperty.STRING_TYPE);
        otpLength.setDefaultValue("6");
        otpLength.setHelpText("Number of digits in the OTP (4–8).");

        ProviderConfigProperty otpExpiry = new ProviderConfigProperty();
        otpExpiry.setName("otp_expiry_seconds");
        otpExpiry.setLabel("OTP Expiry (seconds)");
        otpExpiry.setType(ProviderConfigProperty.STRING_TYPE);
        otpExpiry.setDefaultValue("300");
        otpExpiry.setHelpText("How long the OTP remains valid.");

        ProviderConfigProperty maxAttempts = new ProviderConfigProperty();
        maxAttempts.setName("otp_max_attempts");
        maxAttempts.setLabel("Max OTP Attempts");
        maxAttempts.setType(ProviderConfigProperty.STRING_TYPE);
        maxAttempts.setDefaultValue("3");
        maxAttempts.setHelpText("Number of wrong OTP entries allowed before the session is invalidated.");

        CONFIG_PROPERTIES = Arrays.asList(gatewayUrl, gatewayToken, otpLength, otpExpiry, maxAttempts);
    }

    @Override public String getId()           { return PROVIDER_ID; }
    @Override public String getDisplayType()  { return "Phone OTP Authentication"; }
    @Override public String getReferenceCategory() { return "otp"; }
    @Override public String getHelpText() {
        return "Passwordless login by phone number + SMS OTP. Creates the user automatically if not found.";
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
