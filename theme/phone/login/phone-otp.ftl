<#import "template.ftl" as layout>
<#-- 静态资源版本号，见 theme.properties 里 wmAssetVersion 的说明 -->
<#assign wmV = properties.wmAssetVersion!"0">
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg?v=${wmV}" alt="we-meet" />
        <span class="wm-page-title">${msg("otpTitle")}</span>

    <#elseif section = "form">

        <#assign p = (phone!'')>
        <#if p?length == 11>
            <#assign shownPhone = p?substring(0,3) + "****" + p?substring(7)>
        <#else>
            <#assign shownPhone = p>
        </#if>
        <p class="wm-subtitle">${msg("otpSentTo")} <strong>+86 ${shownPhone}</strong></p>

        <#if resent?? && resent>
            <div class="wm-alert wm-alert--success">${msg("otpResent")}</div>
        </#if>
        <#if message?has_content && message.type = "error">
            <div class="wm-alert wm-alert--error">${kcSanitize(message.summary)?no_esc}</div>
        </#if>

        <!-- 重发表单：由验证码输入框内的后缀按钮通过 form= 属性提交 -->
        <form id="wm-resend-form" action="${url.loginAction}" method="post">
            <input type="hidden" name="action" value="resend" />
        </form>

        <form id="kc-otp-verify-form" class="wm-form" action="${url.loginAction}" method="post">
            <input type="hidden" name="action" value="verify" />

            <div class="wm-field">
                <input type="text"
                       id="otp"
                       name="otp"
                       class="wm-input"
                       placeholder="${msg('otpPlaceholder')}"
                       maxlength="8"
                       autofocus
                       autocomplete="one-time-code"
                       inputmode="numeric" />
                <button type="submit"
                        form="wm-resend-form"
                        id="wm-resend-btn"
                        class="wm-suffix-btn"
                        data-label="${msg('doResend')}">${msg("doResend")}</button>
            </div>

            <button type="submit" class="wm-btn wm-btn--primary">${msg("doVerify")}</button>
        </form>

        <p class="wm-helper">
            <a class="wm-link" href="${url.loginRestartFlowUrl}">‹ ${msg("reenterPhone")}</a>
        </p>

        <script src="${url.resourcesPath}/js/otp-countdown.js?v=${wmV}"></script>

    </#if>

</@layout.registrationLayout>
