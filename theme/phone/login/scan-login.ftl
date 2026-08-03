<#import "template.ftl" as layout>
<#-- 静态资源版本号，见 theme.properties 里 wmAssetVersion 的说明 -->
<#assign wmV = properties.wmAssetVersion!"0">
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg?v=${wmV}" alt="we-meet" />
        <span class="wm-page-title">${msg("scanTitle")}</span>

    <#elseif section = "form">

        <#if message?has_content && message.type = "error">
            <div class="wm-alert wm-alert--error">${kcSanitize(message.summary)?no_esc}</div>
        </#if>

        <#if qrImage?? && qrImage?has_content>
            <div id="wm-scan" class="wm-scan" data-interval="3000">
                <div class="wm-qr-box">
                    <img class="wm-qr" src="${qrImage}" alt="QR" width="240" height="240" />
                    <#if (scanStatus!'') == 'scanned'>
                        <div class="wm-qr-overlay">
                            <div class="wm-qr-ok">✓ ${msg("scanScanned")}</div>
                            <#if scannedName?? && scannedName?has_content>
                                <div class="wm-qr-sub">${scannedName}</div>
                            </#if>
                            <div class="wm-qr-sub">${msg("scanAwaitConfirm")}</div>
                        </div>
                    </#if>
                </div>
                <p class="wm-helper">${msg("scanOpenApp")}</p>
            </div>
            <script src="${url.resourcesPath}/js/scan-poll.js?v=${wmV}"></script>
        <#else>
            <p class="wm-helper">${msg("qrGenFailed")}</p>
        </#if>

        <#if auth?? && auth.showTryAnotherWayLink()>
            <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" class="wm-try-another">
                <input type="hidden" name="tryAnotherWay" value="on"/>
                <a href="#" onclick="document.getElementById('kc-select-try-another-way-form').submit();return false;">${msg("tryAnotherWayWm")} ›</a>
            </form>
        </#if>

    </#if>

</@layout.registrationLayout>
