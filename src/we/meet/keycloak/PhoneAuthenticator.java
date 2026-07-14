package we.meet.keycloak;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Passwordless phone-number authenticator for Keycloak 26.
 *
 * Flow:
 *   1. authenticate() → show phone-input.ftl
 *   2. action()       → validate phone, find/create user, send OTP, show phone-otp.ftl
 *   3. action()       → verify OTP → success or error
 *
 * Re-send:  POST action=resend from phone-otp.ftl regenerates and resends the OTP.
 */
public class PhoneAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(PhoneAuthenticator.class.getName());

    // Keys used to persist state across requests in the authentication session
    static final String NOTE_PHONE    = "pa_phone";
    static final String NOTE_OTP      = "pa_otp";
    static final String NOTE_OTP_TIME = "pa_otp_time";
    static final String NOTE_ATTEMPTS = "pa_attempts";

    // -------------------------------------------------------------------------
    // Keycloak SPI entry points
    // -------------------------------------------------------------------------

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        String phone = ctx.getAuthenticationSession().getAuthNote(NOTE_PHONE);
        if (phone != null) {
            // OTP already sent (e.g. browser back button) – show OTP form again
            ctx.challenge(otpForm(ctx, phone, null));
        } else {
            ctx.challenge(ctx.form().createForm("phone-input.ftl"));
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        MultivaluedMap<String, String> form =
                ctx.getHttpRequest().getDecodedFormParameters();

        String phone = ctx.getAuthenticationSession().getAuthNote(NOTE_PHONE);

        if (phone == null) {
            handlePhoneSubmit(ctx, form);
        } else {
            String action = form.getFirst("action");
            if ("resend".equals(action)) {
                handleResend(ctx, phone);
            } else {
                handleOtpSubmit(ctx, form, phone);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 1 – phone number submission
    // -------------------------------------------------------------------------

    private void handlePhoneSubmit(AuthenticationFlowContext ctx,
                                   MultivaluedMap<String, String> form) {
        String phone = trimOrNull(form.getFirst("phone"));

        if (phone == null) {
            ctx.challenge(ctx.form().setError("phone.required").createForm("phone-input.ftl"));
            return;
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            ctx.challenge(ctx.form()
                    .setAttribute("phone", phone)
                    .setError("phone.invalid")
                    .createForm("phone-input.ftl"));
            return;
        }

        // Look up or auto-create the user
        UserModel user = findOrCreateUser(ctx, phone);
        ctx.setUser(user);

        // Generate and send OTP (demo phones use a fixed OTP + skip SMS)
        String otp = issueOtp(ctx, phone);
        if (otp == null) {
            clearNotes(ctx);
            ctx.challenge(ctx.form().setError("sms.failed").createForm("phone-input.ftl"));
            return;
        }

        // Persist state
        ctx.getAuthenticationSession().setAuthNote(NOTE_PHONE,    phone);
        ctx.getAuthenticationSession().setAuthNote(NOTE_OTP,      otp);
        ctx.getAuthenticationSession().setAuthNote(NOTE_OTP_TIME, Long.toString(System.currentTimeMillis()));
        ctx.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, "0");

        ctx.challenge(otpForm(ctx, phone, null));
    }

    // -------------------------------------------------------------------------
    // Step 2 – OTP verification
    // -------------------------------------------------------------------------

    private void handleOtpSubmit(AuthenticationFlowContext ctx,
                                 MultivaluedMap<String, String> form,
                                 String phone) {
        String storedOtp  = ctx.getAuthenticationSession().getAuthNote(NOTE_OTP);
        long   sentAt     = longNote(ctx, NOTE_OTP_TIME);
        int    attempts   = intNote(ctx, NOTE_ATTEMPTS);
        int    maxAttempts = intCfg(ctx, "otp_max_attempts", 3);
        long   expiryMs   = intCfg(ctx, "otp_expiry_seconds", 300) * 1000L;

        // Expired?
        if (System.currentTimeMillis() - sentAt > expiryMs) {
            clearNotes(ctx);
            ctx.challenge(ctx.form().setError("otp.expired").createForm("phone-input.ftl"));
            return;
        }

        // Too many attempts?
        if (attempts >= maxAttempts) {
            clearNotes(ctx);
            ctx.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }

        String input = trimOrNull(form.getFirst("otp"));
        if (storedOtp != null && storedOtp.equals(input)) {
            ctx.success();
            return;
        }

        // Wrong code
        attempts++;
        ctx.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, Integer.toString(attempts));
        int remaining = maxAttempts - attempts;
        String errKey = remaining > 0 ? "otp.wrong" : "otp.locked";
        ctx.challenge(otpForm(ctx, phone, errKey, remaining));
    }

    // -------------------------------------------------------------------------
    // Re-send OTP
    // -------------------------------------------------------------------------

    private void handleResend(AuthenticationFlowContext ctx, String phone) {
        String otp = issueOtp(ctx, phone);
        if (otp != null) {
            ctx.getAuthenticationSession().setAuthNote(NOTE_OTP,      otp);
            ctx.getAuthenticationSession().setAuthNote(NOTE_OTP_TIME, Long.toString(System.currentTimeMillis()));
            ctx.getAuthenticationSession().setAuthNote(NOTE_ATTEMPTS, "0");
            ctx.challenge(ctx.form()
                    .setAttribute("phone", phone)
                    .setAttribute("resent", Boolean.TRUE)
                    .createForm("phone-otp.ftl"));
        } else {
            ctx.challenge(otpForm(ctx, phone, "sms.failed"));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Response otpForm(AuthenticationFlowContext ctx, String phone, String errKey, Object... errArgs) {
        var form = ctx.form().setAttribute("phone", phone);
        if (errKey != null) form = form.setError(errKey, errArgs);
        return form.createForm("phone-otp.ftl");
    }

    private UserModel findOrCreateUser(AuthenticationFlowContext ctx, String phone) {
        RealmModel realm = ctx.getRealm();
        // Search by phone_number attribute so login still works after username is changed
        UserModel user = ctx.getSession().users()
                .searchForUserByUserAttributeStream(realm, "phoneNumber", phone)
                .findFirst().orElse(null);
        if (user == null) {
            LOG.info("PhoneAuth: creating new user for phone " + phone);
            user = ctx.getSession().users().addUser(realm, phone);
            user.setEnabled(true);
            user.setSingleAttribute("phoneNumber", phone);
        }
        return user;
    }

    private String generateOtp(int length) {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(rng.nextInt(10));
        return sb.toString();
    }

    /**
     * Issue an OTP for the phone. Demo phones (config demo_phones, comma-separated)
     * get the fixed demo_otp with SMS skipped — mirrors the backend's
     * MOBILE_AUTH_DEMO_PHONES / MOBILE_AUTH_DEMO_OTP, so app-review / test logins
     * keep working now that web login goes through Keycloak instead of the mobile
     * OTP API. Returns the OTP to store, or null if the SMS gateway send failed.
     */
    private String issueOtp(AuthenticationFlowContext ctx, String phone) {
        String demoOtp = strCfg(ctx, "demo_otp", "");
        if (!demoOtp.isEmpty() && isDemoPhone(ctx, phone)) {
            LOG.info("PhoneAuth: demo phone " + phone + " — fixed OTP, SMS skipped");
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

    private void clearNotes(AuthenticationFlowContext ctx) {
        ctx.getAuthenticationSession().removeAuthNote(NOTE_PHONE);
        ctx.getAuthenticationSession().removeAuthNote(NOTE_OTP);
        ctx.getAuthenticationSession().removeAuthNote(NOTE_OTP_TIME);
        ctx.getAuthenticationSession().removeAuthNote(NOTE_ATTEMPTS);
    }

    // Config helpers
    private String strCfg(AuthenticationFlowContext ctx, String key, String def) {
        if (ctx.getAuthenticatorConfig() == null) return def;
        return ctx.getAuthenticatorConfig().getConfig().getOrDefault(key, def);
    }
    private int intCfg(AuthenticationFlowContext ctx, String key, int def) {
        try { return Integer.parseInt(strCfg(ctx, key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    // Session note helpers
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

    private static String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    // -------------------------------------------------------------------------
    // Boilerplate
    // -------------------------------------------------------------------------

    @Override public boolean requiresUser() { return false; }
    @Override public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) { return true; }
    @Override public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {}
    @Override public void close() {}
}
