/**
 * 비공개 회원 영역 공통 상단 네비게이션.
 * #private-nav 엘리먼트가 있는 페이지(/mypage, /admin, /admin/*)에서 공통으로 사용한다.
 * /api/auth/me로 로그인 상태를 확인해 비로그인 시 /login으로 리다이렉트한다.
 *
 * 왼쪽(priv-left) = 로고(클릭 시 /mypage 대시보드로 이동) + PRIVATE_NAV_ITEMS(실제 기능 메뉴).
 * 오른쪽(priv-right) = "{이름}님" 드롭다운 — 계정설정/관리자 페이지(관리자만)/로그아웃.
 *
 * 새 기능 페이지를 왼쪽 메뉴에 상시 노출하고 싶으면 PRIVATE_NAV_ITEMS 배열에 한 줄만 추가하면
 * 모든 비공개 페이지의 메뉴에 자동으로 반영된다 (공개 사이트의 nav.js NAV_ITEMS와 동일한 패턴).
 */
const PRIVATE_NAV_ITEMS = [
  { label: 'TOSS 주식', path: '/admin/toss-stock', adminOnly: true }
];

(function () {
  async function init() {
    const el = document.getElementById('private-nav');
    if (!el) return;

    let me;
    try {
      me = await fetch('/api/auth/me').then(r => r.json());
    } catch (e) {
      me = { authenticated: false };
    }
    if (!me.authenticated) {
      location.href = '/login';
      return;
    }

    const isAdmin = (me.roles || []).includes('ROLE_ADMIN');
    const path = location.pathname;
    const isOn = (p) => path === p || path.startsWith(p);

    const leftLinks = PRIVATE_NAV_ITEMS.filter(item => !item.adminOnly || isAdmin);
    const leftHtml = leftLinks.map(l =>
      `<a class="priv-link ${isOn(l.path) ? 'on' : ''}" href="${l.path}">${l.label}</a>`
    ).join('');

    el.innerHTML = `
      <div class="priv-header">
        <div class="priv-left">
          <a class="priv-brand" href="/mypage" title="대시보드로 이동">🔒 hunit Private</a>
          ${leftHtml}
        </div>
        <div class="priv-right">
          <div class="priv-dropdown" id="privDropdown">
            <button class="priv-dropdown-btn" type="button" onclick="togglePrivDropdown(event)">
              ${escapeHtml(me.name || me.username)}님 <span class="caret">▾</span>
            </button>
            <div class="priv-dropdown-menu">
              <a class="priv-dropdown-item" href="/mypage/settings">계정 설정</a>
              ${isAdmin ? '<a class="priv-dropdown-item" href="/admin">관리자 페이지</a>' : ''}
              <div class="priv-dropdown-divider"></div>
              <form method="post" action="/logout" style="margin:0">
                <button class="priv-dropdown-item" type="submit">로그아웃</button>
              </form>
            </div>
          </div>
        </div>
      </div>`;

    document.addEventListener('click', (e) => {
      const dd = document.getElementById('privDropdown');
      if (dd && dd.classList.contains('open') && !dd.contains(e.target)) dd.classList.remove('open');
    });
  }

  function escapeHtml(s) {
    return (s ?? '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  window.togglePrivDropdown = function (e) {
    e.stopPropagation();
    document.getElementById('privDropdown').classList.toggle('open');
  };

  init();
})();
