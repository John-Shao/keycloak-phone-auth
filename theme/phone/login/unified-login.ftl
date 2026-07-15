<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg" alt="we-meet" />
        <span class="wm-page-title">${msg("loginTitle")}</span>

    <#elseif section = "form">

        <#if realm.internationalizationEnabled && locale?? && (locale.supported?size gt 1)>
            <div class="wm-locale">
                <select id="wm-locale-select" class="wm-locale-select" aria-label="Language">
                    <#list locale.supported as l>
                        <option value="${l.languageTag}"<#if l.languageTag == locale.currentLanguageTag> selected</#if>>${l.label}</option>
                    </#list>
                </select>
            </div>
        </#if>

        <#if message?has_content && message.type = "error">
            <div class="wm-alert wm-alert--error">${kcSanitize(message.summary)?no_esc}</div>
        </#if>
        <#if resent?? && resent>
            <div class="wm-alert wm-alert--success">${msg("otpResent")}</div>
        </#if>

        <div class="wm-dual">
            <!-- 左：扫码登录 -->
            <div class="wm-col wm-col-scan">
                <div class="wm-col-title">${msg("scanTitle")}</div>
                <#if qrImage?? && qrImage?has_content>
                    <div id="wm-scan" class="wm-scan"
                         data-ready="${readBase}/api/qr-login/ready/?token=${qrToken}"
                         data-interval="2500">
                        <div class="wm-qr-box">
                            <img class="wm-qr" src="${qrImage}" alt="QR" width="200" height="200" />
                            <div id="wm-scan-overlay" class="wm-qr-overlay" style="display:none">
                                <div class="wm-qr-ok">✓ ${msg("scanScanned")}</div>
                                <div class="wm-qr-sub">${msg("scanAwaitConfirm")}</div>
                            </div>
                        </div>
                        <p class="wm-helper">${msg("scanOpenApp")}</p>
                    </div>
                    <form id="wm-qr-confirm-form" action="${url.loginAction}" method="post" style="display:none">
                        <input type="hidden" name="qrConfirm" value="1" />
                    </form>
                    <script src="${url.resourcesPath}/js/unified-poll.js"></script>
                <#else>
                    <p class="wm-helper">${msg("qrGenFailed")}</p>
                </#if>
            </div>

            <div class="wm-col-divider"></div>

            <!-- 右：手机号验证码登录（单页） -->
            <div class="wm-col wm-col-phone">
                <div class="wm-col-title">${msg("phoneTitle")}</div>
                <div class="wm-phone-main">
                    <form id="wm-login-form" class="wm-form" action="${url.loginAction}" method="post"
                          data-send="${readBase}/api/keycloak-sms/otp/send/"
                          data-sending="${msg('jsSending')}"
                          data-resend="${msg('jsResendIn')}"
                          data-sent="${msg('jsOtpSent')}"
                          data-invalid="${msg('jsPhoneInvalid')}"
                          data-fail="${msg('jsSendFail')}"
                          data-neterr="${msg('jsNetErr')}">
                        <div class="wm-field">
                            <span class="wm-prefix">+86</span>
                            <input type="tel" id="wm-phone" name="phone" class="wm-input" placeholder="${msg('phonePlaceholder')}"
                                   value="${(phone!'')}" autofocus autocomplete="tel" inputmode="numeric" maxlength="11" />
                        </div>
                        <div class="wm-field">
                            <input type="text" id="wm-otp" name="otp" class="wm-input" placeholder="${msg('otpPlaceholder')}"
                                   maxlength="8" autocomplete="one-time-code" inputmode="numeric" />
                            <button type="button" id="wm-send-btn" class="wm-suffix-btn" data-label="${msg('doGetVerificationCode')}">${msg("doGetVerificationCode")}</button>
                        </div>
                        <p id="wm-send-hint" class="wm-send-hint"></p>
                        <button type="submit" class="wm-btn wm-btn--primary wm-sink">${msg("doVerify")}</button>
                    </form>
                </div>
                <p class="wm-agreement">
                    ${msg("agreePre")}
                    <a href="https://meet.we-meet.online/conditions-utilisation" target="_blank" rel="noopener">${msg("termsLink")}</a>
                    ${msg("agreeAnd")}
                    <a href="https://meet.we-meet.online/mentions-legales" target="_blank" rel="noopener">${msg("privacyLink")}</a>
                </p>
            </div>
        </div>

        <script src="${url.resourcesPath}/js/unified-otp.js"></script>
        <script>
        // 语言切换：写 KEYCLOAK_LOCALE cookie + 原样 GET 重载当前 URL（不走 KC 的
        // client_data 重启链，避开自定义 authenticator 下 redirect_uri 校验 400）。
        (function () {
            var s = document.getElementById("wm-locale-select");
            if (!s) return;
            s.addEventListener("change", function () {
                document.cookie = "KEYCLOAK_LOCALE=" + encodeURIComponent(s.value) +
                    ";path=/;max-age=2592000;samesite=lax";
                // 用 assign(pathname+search) 强制 GET，避免 reload() 在 POST 重渲染页重放表单
                window.location.assign(window.location.pathname + window.location.search);
            });
        })();
        </script>

    </#if>

</@layout.registrationLayout>
