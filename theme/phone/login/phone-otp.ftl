<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg" alt="we-meet" />
        <span class="wm-page-title">输入验证码</span>

    <#elseif section = "form">

        <#assign p = (phone!'')>
        <#if p?length == 11>
            <#assign shownPhone = p?substring(0,3) + "****" + p?substring(7)>
        <#else>
            <#assign shownPhone = p>
        </#if>
        <p class="wm-subtitle">验证码已发送至 <strong>+86 ${shownPhone}</strong></p>

        <#if resent?? && resent>
            <div class="wm-alert wm-alert--success">验证码已重新发送</div>
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
                       placeholder="请输入验证码"
                       maxlength="8"
                       autofocus
                       autocomplete="one-time-code"
                       inputmode="numeric" />
                <button type="submit"
                        form="wm-resend-form"
                        id="wm-resend-btn"
                        class="wm-suffix-btn"
                        data-label="重新发送">重新发送</button>
            </div>

            <button type="submit" class="wm-btn wm-btn--primary">验证登录</button>
        </form>

        <p class="wm-helper">
            <a class="wm-link" href="${url.loginRestartFlowUrl}">‹ 重新输入手机号</a>
        </p>

        <script src="${url.resourcesPath}/js/otp-countdown.js"></script>

    </#if>

</@layout.registrationLayout>
