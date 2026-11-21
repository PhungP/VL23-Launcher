// Đổi theme HUD bằng nút [1] [2] [3] hoặc phím 1, 2, 3. Chỉ đổi class trên #hud: không tải lại trang,
// không đụng tới ảnh xe nên xe không nhúc nhích. Có nhớ lựa chọn lần trước; ?theme=2 mở thẳng theme 2.
(function () {
  var hud = document.getElementById('hud');
  var names = { 1: 'theme-su35', 2: 'theme-orange', 3: 'theme-cyberpunk' };
  var buttons = document.querySelectorAll('.theme-switch button');

  function set(n) {
    n = names[n] ? n : 1;
    hud.className = names[n];
    hud.setAttribute('data-theme', n);
    buttons.forEach(function (b) { b.classList.toggle('on', b.getAttribute('data-t') === String(n)); });
    try { localStorage.setItem('hudTheme', String(n)); } catch (e) {}
  }

  buttons.forEach(function (b) {
    b.addEventListener('click', function () { set(parseInt(b.getAttribute('data-t'), 10)); });
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === '1' || e.key === '2' || e.key === '3') set(parseInt(e.key, 10));
  });

  var q = /[?&]theme=([123])/.exec(location.search);
  var saved = null;
  try { saved = localStorage.getItem('hudTheme'); } catch (e) {}
  set(q ? parseInt(q[1], 10) : parseInt(saved || '1', 10));
})();
