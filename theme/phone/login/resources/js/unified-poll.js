// 双栏登录页「扫码列」轮询（阶段二）。
//
// 用 AJAX fetch 后端 /api/qr-login/ready/（只读 status），**不整页 reload** ——
// 否则会冲掉用户正在右列输入的手机号/验证码。confirmed 时才提交一次 qrConfirm
// 整表单，由 Keycloak 服务端查受保护的 authenticator-status 建浏览器 SSO 会话。
// ready 端点跨域可读（ACAO:*，只回 status、无 token、无 PII）。
// 防御式：容器/表单不在则直接退出。
(function () {
  var box = document.getElementById('wm-scan');
  if (!box) return;
  // 触摸设备上扫码列被 CSS 隐藏（扫自己屏幕没意义，见 login.css），此处同条件
  // 退出，别每 2.5s 空轮询一次 ready 端点。
  if (window.matchMedia &&
      window.matchMedia('(hover: none) and (pointer: coarse)').matches) {
    return;
  }
  var readyUrl = box.getAttribute('data-ready');
  var interval = parseInt(box.getAttribute('data-interval') || '2500', 10);
  var overlay = document.getElementById('wm-scan-overlay');
  var confirmForm = document.getElementById('wm-qr-confirm-form');
  if (!readyUrl || !confirmForm) return;
  var stopped = false;

  function poll() {
    if (stopped) return;
    fetch(readyUrl, { method: 'GET', credentials: 'omit', cache: 'no-store' })
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (data) {
        var st = data && data.status;
        if (st === 'confirmed') {
          stopped = true;
          confirmForm.submit(); // 整表单提交 → action() getUserById+setUser+success
          return;
        }
        if (st === 'scanned' && overlay) {
          overlay.style.display = 'flex';
        }
        window.setTimeout(poll, interval);
      })
      .catch(function () {
        window.setTimeout(poll, interval);
      });
  }
  window.setTimeout(poll, interval);
})();
