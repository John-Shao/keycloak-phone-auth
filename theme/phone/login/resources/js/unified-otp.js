// 单页手机登录「获取验证码」：AJAX 直发后端（页面不刷新）+ 60s 倒计时。
// 文案由 ftl 通过 data-* 传入（i18n，跟随 Keycloak locale）；本脚本不含硬编码文案。
// form-urlencoded 简单请求免 CORS 预检，后端 /api/keycloak-sms/otp/send/ 响应 ACAO:*。
(function () {
  var form = document.getElementById('wm-login-form');
  if (!form) return;
  var sendUrl = form.getAttribute('data-send');
  var phoneEl = document.getElementById('wm-phone');
  var otpEl = document.getElementById('wm-otp');
  var btn = document.getElementById('wm-send-btn');
  var hint = document.getElementById('wm-send-hint');
  if (!sendUrl || !phoneEl || !btn) return;

  var t = {
    label: btn.getAttribute('data-label') || '',
    sending: form.getAttribute('data-sending') || '',
    resend: form.getAttribute('data-resend') || '{0}s',
    sent: form.getAttribute('data-sent') || '',
    invalid: form.getAttribute('data-invalid') || '',
    fail: form.getAttribute('data-fail') || '',
    neterr: form.getAttribute('data-neterr') || ''
  };
  var left = 0;

  function setHint(msg, isErr) {
    if (!hint) return;
    hint.textContent = msg || '';
    hint.className = 'wm-send-hint' + (isErr ? ' wm-send-hint--err' : '');
  }
  function tick() {
    if (left <= 0) { btn.disabled = false; btn.textContent = t.label; return; }
    btn.disabled = true;
    btn.textContent = t.resend.replace('{0}', left);
    left -= 1;
    window.setTimeout(tick, 1000);
  }

  btn.addEventListener('click', function () {
    var phone = (phoneEl.value || '').trim();
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      setHint(t.invalid, true);
      phoneEl.focus();
      return;
    }
    btn.disabled = true;
    btn.textContent = t.sending;
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
        setHint(t.sent, false);
        if (otpEl) otpEl.focus();
      } else {
        btn.disabled = false;
        btn.textContent = t.label;
        setHint((res.d && res.d.error) || t.fail, true);
      }
    }).catch(function () {
      btn.disabled = false;
      btn.textContent = t.label;
      setHint(t.neterr, true);
    });
  });
})();
