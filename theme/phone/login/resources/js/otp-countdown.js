// 验证码页「重新发送」按钮 60s 倒计时。进入本页说明验证码刚发出，
// 故加载即冷却；倒计时结束按钮恢复可点，点击提交 wm-resend-form 触发重发。
// 防御式：元素不存在直接退出，不影响页面其余部分。
(function () {
  var btn = document.getElementById('wm-resend-btn');
  if (!btn) return;
  var label = btn.getAttribute('data-label') || '重新发送';
  var left = 60;
  function tick() {
    if (left <= 0) {
      btn.disabled = false;
      btn.textContent = label;
      return;
    }
    btn.disabled = true;
    btn.textContent = left + 's';
    left -= 1;
    window.setTimeout(tick, 1000);
  }
  tick();
})();
