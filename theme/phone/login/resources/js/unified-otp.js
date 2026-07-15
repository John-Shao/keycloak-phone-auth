// 单页手机登录「获取验证码」：AJAX 直发后端（页面不刷新）+ 60s 倒计时。
//
// 用 form-urlencoded 简单请求（免 CORS 预检），后端 /api/keycloak-sms/otp/send/
// 响应 ACAO:*，只回 {success}/{error}。发码/校验的 OTP 生命周期都在后端；本页
// 只负责触发发码 + 倒计时，「验证登录」再整页提交由 Keycloak 认证器调后端校验。
// 防御式：表单/控件不在直接退出。
(function () {
  var form = document.getElementById('wm-login-form');
  if (!form) return;
  var sendUrl = form.getAttribute('data-send');
  var phoneEl = document.getElementById('wm-phone');
  var otpEl = document.getElementById('wm-otp');
  var btn = document.getElementById('wm-send-btn');
  var hint = document.getElementById('wm-send-hint');
  if (!sendUrl || !phoneEl || !btn) return;
  var left = 0;
  var label = btn.getAttribute('data-label') || '获取验证码';

  function setHint(msg, isErr) {
    if (!hint) return;
    hint.textContent = msg || '';
    hint.className = 'wm-send-hint' + (isErr ? ' wm-send-hint--err' : '');
  }
  function tick() {
    if (left <= 0) { btn.disabled = false; btn.textContent = label; return; }
    btn.disabled = true;
    btn.textContent = left + 's 后重发';
    left -= 1;
    window.setTimeout(tick, 1000);
  }

  btn.addEventListener('click', function () {
    var phone = (phoneEl.value || '').trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      setHint('请输入正确的手机号', true);
      phoneEl.focus();
      return;
    }
    btn.disabled = true;
    btn.textContent = '发送中…';
    setHint('', false);
    fetch(sendUrl, {
      method: 'POST',
      credentials: 'omit',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'phone=' + encodeURIComponent(phone)
    }).then(function (r) {
      return r.json()
        .then(function (d) { return { ok: r.ok, d: d }; })
        .catch(function () { return { ok: r.ok, d: {} }; });
    }).then(function (res) {
      if (res.ok && res.d && res.d.success) {
        left = 60;
        tick();
        setHint('验证码已发送', false);
        if (otpEl) otpEl.focus();
      } else {
        btn.disabled = false;
        btn.textContent = label;
        setHint((res.d && res.d.error) || '发送失败，请重试', true);
      }
    }).catch(function () {
      btn.disabled = false;
      btn.textContent = label;
      setHint('网络错误，请重试', true);
    });
  });
})();
