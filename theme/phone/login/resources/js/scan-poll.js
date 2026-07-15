// 扫码登录页轮询：每 data-interval 毫秒整页 reload（GET），重入 Keycloak
// ScanAuthenticator.authenticate() 查后端扫码态。
//
// 为何用整页 GET reload 而非 fetch/AJAX：
//   - 无 session_code 轮转问题（每次 reload 都是新鲜的 GET 认证请求）；
//   - 无跨域 CORS（浏览器只跟 Keycloak 同源通信，后端由 KC 服务端查询）；
//   - 无双轮询者竞态（KC 服务端是唯一的后端轮询者）。
// confirmed 时 authenticate() 会 success()→302 跳走，reload 定时器随页面卸载失效。
// 防御式：容器不在直接退出。
(function () {
  var el = document.getElementById('wm-scan');
  if (!el) return;
  var ms = parseInt(el.getAttribute('data-interval') || '3000', 10);
  window.setTimeout(function () {
    window.location.reload();
  }, ms);
})();
