<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        ${msg("doGetVerificationCode")}

    <#elseif section = "form">
        <form id="kc-phone-input-form"
              action="${url.loginAction}"
              method="post">

            <div class="${properties.kcFormGroupClass!}">
                <label for="phone" class="${properties.kcLabelClass!}">
                    手机号
                </label>
                <input type="tel"
                       id="phone"
                       name="phone"
                       class="${properties.kcInputClass!}"
                       value="${(phone!'')}"
                       placeholder="请输入手机号"
                       autofocus
                       autocomplete="tel" />
            </div>

            <#if message?has_content && message.type = "error">
                <div class="${properties.kcAlertClass!} pf-m-danger ${properties.kcAlertErrorClass!}">
                    <div class="pf-v5-c-alert__icon">
                        <i class="pficon-error-circle-o" aria-hidden="true"></i>
                    </div>
                    <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                       type="submit"
                       value="${msg("doGetVerificationCode")}" />
            </div>

        </form>
    </#if>

</@layout.registrationLayout>
