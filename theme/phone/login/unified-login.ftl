<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg" alt="we-meet" />
        <span class="wm-page-title">${msg("loginTitle")}</span>

    <#elseif section = "form">

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
                          data-otp-len="6"
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
                        <#-- 默认禁用，unified-otp.js 在「手机号合法 + 验证码填满」时点亮。
                             直接写在 HTML 里而不是等 JS 加载后再禁用，避免闪一下可点状态；
                             本页的「获取验证码」本来就依赖 JS，无脚本环境下走不通登录流程。 -->
                        <button type="submit" id="wm-submit-btn" class="wm-btn wm-btn--primary wm-sink" disabled>${msg("doVerify")}</button>
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

    </#if>

</@layout.registrationLayout>
