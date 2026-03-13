<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>

    <#if section = "header">
        ${msg("doVerify")}

    <#elseif section = "info">
        验证码已发送至 <strong>${(phone!'')}</strong>，10 分钟内有效

    <#elseif section = "form">

        <#if resent?? && resent>
            <div class="${properties.kcAlertClass!} pf-m-success">
                <span class="${properties.kcAlertTitleClass!}">${msg("otp.sent")}</span>
            </div>
        </#if>

        <#if message?has_content && message.type = "error">
            <div class="${properties.kcAlertClass!} pf-m-danger ${properties.kcAlertErrorClass!}">
                <div class="pf-v5-c-alert__icon">
                    <i class="pficon-error-circle-o" aria-hidden="true"></i>
                </div>
                <span class="${properties.kcAlertTitleClass!}">${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <!-- OTP verify form -->
        <form id="kc-otp-verify-form"
              action="${url.loginAction}"
              method="post">
            <input type="hidden" name="action" value="verify" />

            <div class="${properties.kcFormGroupClass!}">
                <label for="otp" class="${properties.kcLabelClass!}">验证码</label>
                <input type="text"
                       id="otp"
                       name="otp"
                       class="${properties.kcInputClass!}"
                       placeholder="请输入验证码"
                       maxlength="8"
                       autofocus
                       autocomplete="one-time-code"
                       inputmode="numeric" />
            </div>

            <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!}">
                <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}"
                       type="submit"
                       value="${msg("doVerify")}" />
            </div>
        </form>

        <!-- Resend form -->
        <form action="${url.loginAction}" method="post" style="margin-top:0.75rem;">
            <input type="hidden" name="action" value="resend" />
            <button type="submit"
                    class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!}">
                ${msg("doResend")}
            </button>
        </form>

    </#if>

</@layout.registrationLayout>
