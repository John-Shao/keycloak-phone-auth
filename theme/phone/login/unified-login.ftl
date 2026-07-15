<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=false; section>

    <#if section = "header">
        <img class="wm-logo" src="${url.resourcesPath}/img/logo.svg" alt="we-meet" />
        <span class="wm-page-title">登录 we-meet</span>

    <#elseif section = "form">

        <#if message?has_content && message.type = "error">
            <div class="wm-alert wm-alert--error">${kcSanitize(message.summary)?no_esc}</div>
        </#if>
        <#if resent?? && resent>
            <div class="wm-alert wm-alert--success">验证码已重新发送</div>
        </#if>

        <div class="wm-dual">
            <!-- 左：扫码登录 -->
            <div class="wm-col wm-col-scan">
                <div class="wm-col-title">扫码登录</div>
                <#if qrImage?? && qrImage?has_content>
                    <div id="wm-scan" class="wm-scan"
                         data-ready="${readyBase}/api/qr-login/ready/?token=${qrToken}"
                         data-interval="2500">
                        <div class="wm-qr-box">
                            <img class="wm-qr" src="${qrImage}" alt="登录二维码" width="200" height="200" />
                            <div id="wm-scan-overlay" class="wm-qr-overlay" style="display:none">
                                <div class="wm-qr-ok">✓ 已扫码</div>
                                <div class="wm-qr-sub">请在 App 上确认登录</div>
                            </div>
                        </div>
                        <p class="wm-helper">打开 we-meet App 扫一扫</p>
                    </div>
                    <form id="wm-qr-confirm-form" action="${url.loginAction}" method="post" style="display:none">
                        <input type="hidden" name="qrConfirm" value="1" />
                    </form>
                    <script src="${url.resourcesPath}/js/unified-poll.js"></script>
                <#else>
                    <p class="wm-helper">二维码生成失败，请刷新页面。</p>
                </#if>
            </div>

            <div class="wm-col-divider"></div>

            <!-- 右：手机号验证码登录 -->
            <div class="wm-col wm-col-phone">
                <div class="wm-col-title">手机号登录</div>
                <#if (phoneStep!'input') == 'otp'>
                    <form id="wm-otp-form" class="wm-form" action="${url.loginAction}" method="post">
                        <input type="hidden" name="action" value="verify" />
                        <div class="wm-field">
                            <input type="text" name="otp" class="wm-input" placeholder="请输入验证码"
                                   maxlength="8" autofocus autocomplete="one-time-code" inputmode="numeric" />
                        </div>
                        <button type="submit" class="wm-btn wm-btn--primary">验证登录</button>
                    </form>
                    <form action="${url.loginAction}" method="post" class="wm-resend-row">
                        <input type="hidden" name="action" value="resend" />
                        <button type="submit" class="wm-link-btn">重新发送验证码</button>
                    </form>
                    <p class="wm-helper">验证码已发送至 ${(phone!'')}</p>
                <#else>
                    <form id="wm-phone-form" class="wm-form" action="${url.loginAction}" method="post">
                        <div class="wm-field">
                            <span class="wm-prefix">+86</span>
                            <input type="tel" name="phone" class="wm-input" placeholder="请输入手机号"
                                   value="${(phone!'')}" autocomplete="tel" inputmode="numeric" maxlength="11" />
                        </div>
                        <button type="submit" class="wm-btn wm-btn--primary">获取验证码</button>
                    </form>
                </#if>
            </div>
        </div>

        <p class="wm-agreement">
            登录即代表同意
            <a href="https://meet.we-meet.online/conditions-utilisation" target="_blank" rel="noopener">《用户协议》</a>
            和
            <a href="https://meet.we-meet.online/mentions-legales" target="_blank" rel="noopener">《隐私政策》</a>
        </p>

    </#if>

</@layout.registrationLayout>
