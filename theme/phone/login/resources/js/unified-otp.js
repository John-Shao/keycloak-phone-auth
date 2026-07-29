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

  // ---- 登录按钮的点亮条件：手机号合法 + 验证码填满 ----
  // 位置在下面那条 return 之前：即使「获取验证码」这套因缺少节点没装上，
  // 按钮也不能永远卡在 HTML 里写死的 disabled 上。
  // 验证码位数跟随后端 authenticator 的 otp_length（部署脚本默认 6），由 ftl
  // 用 data-otp-len 传进来，改配置时两边一起改。
  var PHONE_RE = /^1[3-9]\d{9}$/;
  var submitBtn = document.getElementById('wm-submit-btn');
  var otpLen = parseInt(form.getAttribute('data-otp-len'), 10) || 6;
  var OTP_RE = new RegExp('^\\d{' + otpLen + '}$');

  function val(el) { return ((el && el.value) || '').trim(); }
  function syncSubmit() {
    if (!submitBtn) return;
    submitBtn.disabled = !(PHONE_RE.test(val(phoneEl)) && OTP_RE.test(val(otpEl)));
  }
  if (submitBtn) {
    // change 与 input 都听：短信验证码自动填充在个别内核里只发 change
    ['input', 'change'].forEach(function (ev) {
      if (phoneEl) phoneEl.addEventListener(ev, syncSubmit);
      if (otpEl) otpEl.addEventListener(ev, syncSubmit);
    });
    // 回车同样能提交表单，禁用按钮拦不住，这里再兜一道
    form.addEventListener('submit', function (e) {
      if (submitBtn.disabled) e.preventDefault();
    });
    // 服务端回填手机号（出错重渲染）时也要按当前值判定一次
    syncSubmit();
  }

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
