<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg" alt="we-meet" />
        <span class="wm-page-title">手机号登录</span>

    <#elseif section = "form">

        <#if message?has_content && message.type = "error">
            <div class="wm-alert wm-alert--error">${kcSanitize(message.summary)?no_esc}</div>
        </#if>

        <form id="kc-phone-input-form" class="wm-form" action="${url.loginAction}" method="post">
            <div class="wm-field">
                <span class="wm-prefix">+86</span>
                <input type="tel"
                       id="phone"
                       name="phone"
                       class="wm-input"
                       value="${(phone!'')}"
                       placeholder="请输入手机号"
                       autofocus
                       autocomplete="tel"
                       inputmode="numeric"
                       maxlength="11" />
            </div>

            <button type="submit" class="wm-btn wm-btn--primary">获取验证码</button>
        </form>

        <p class="wm-helper">未注册的手机号将自动创建 we-meet 账号</p>

        <p class="wm-agreement">
            登录即代表同意
            <a href="https://meet.we-meet.online/conditions-utilisation" target="_blank" rel="noopener">《用户协议》</a>
            和
            <a href="https://meet.we-meet.online/mentions-legales" target="_blank" rel="noopener">《隐私政策》</a>
        </p>

    </#if>

</@layout.registrationLayout>
