<#import "template.ftl" as layout>
<#-- 静态资源版本号，见 theme.properties 里 wmAssetVersion 的说明 -->
<#assign wmV = properties.wmAssetVersion!"0">
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg?v=${wmV}" alt="we-meet" />
        <span class="wm-page-title">${msg("phoneTitle")}</span>

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
                       placeholder="${msg('phonePlaceholder')}"
                       autofocus
                       autocomplete="tel"
                       inputmode="numeric"
                       maxlength="11" />
            </div>

            <button type="submit" class="wm-btn wm-btn--primary">${msg("doGetVerificationCode")}</button>
        </form>

        <p class="wm-helper">${msg("autoRegisterHint")}</p>

        <p class="wm-agreement">
            ${msg("agreePre")}
            <a href="https://meet.we-meet.online/conditions-utilisation" target="_blank" rel="noopener">${msg("termsLink")}</a>
            ${msg("agreeAnd")}
            <a href="https://meet.we-meet.online/mentions-legales" target="_blank" rel="noopener">${msg("privacyLink")}</a>
        </p>

    </#if>

</@layout.registrationLayout>
