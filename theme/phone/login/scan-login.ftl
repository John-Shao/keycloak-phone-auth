<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg" alt="we-meet" />
        <span class="wm-page-title">扫码登录</span>

    <#elseif section = "form">

        <#if message?has_content && message.type = "error">
            <div class="wm-alert wm-alert--error">${kcSanitize(message.summary)?no_esc}</div>
        </#if>

        <#if qrImage?? && qrImage?has_content>
            <div id="wm-scan" class="wm-scan" data-interval="3000">
                <div class="wm-qr-box">
                    <img class="wm-qr" src="${qrImage}" alt="登录二维码" width="240" height="240" />
                    <#if (scanStatus!'') == 'scanned'>
                        <div class="wm-qr-overlay">
                            <div class="wm-qr-ok">✓ 已扫码</div>
                            <#if scannedName?? && scannedName?has_content>
                                <div class="wm-qr-sub">${scannedName}</div>
                            </#if>
                            <div class="wm-qr-sub">请在 App 上确认登录</div>
                        </div>
                    </#if>
                </div>
                <p class="wm-helper">打开 we-meet App，扫一扫上方二维码登录</p>
            </div>
            <script src="${url.resourcesPath}/js/scan-poll.js"></script>
        <#else>
            <p class="wm-helper">二维码生成失败，请刷新页面重试。</p>
        </#if>

        <#if auth?? && auth.showTryAnotherWayLink()>
            <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" class="wm-try-another">
                <input type="hidden" name="tryAnotherWay" value="on"/>
                <a href="#" onclick="document.getElementById('kc-select-try-another-way-form').submit();return false;">换一种登录方式 ›</a>
            </form>
        </#if>

    </#if>

</@layout.registrationLayout>
