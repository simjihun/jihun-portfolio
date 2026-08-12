/**
 * 비공개 회원 영역 공통 상단 네비게이션.
 * #private-nav 엘리먼트가 있는 페이지(/mypage, /admin, /admin/*)에서 공통으로 사용한다.
 * /api/auth/me로 로그인 상태를 확인해 비로그인 시 /login으로 리다이렉트하고,
 * 이름·권한(ROLE_ADMIN 여부)에 따라 메뉴를 다르게 그린다.
 *
 * 새 관리자 전용 기능 페이지를 추가할 때는 PRIVATE_NAV_ITEMS 배열에 한 줄만 추가하면
 * 모든 비공개 페이지의 메뉴에 자동으로 반영된다 (공개 사이트의 nav.js NAV_ITEMS와 동일한 패턴).
 */
const PRIVATE_NAV_ITEMS = [
  // { label: '기능 이름', path: '/admin/기능영문', adminOnly: true }
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
    const isOn = (p) => path === p || (p !== '/mypage' && path.startsWith(p));

    const links = [
      { label: '마이페이지', path: '/mypage', adminOnly: false },
      ...(isAdmin ? [{ label: '관리자', path: '/admin', adminOnly: true }] : []),
      ...PRIVATE_NAV_ITEMS.filter(item => !item.adminOnly || isAdmin)
    ];

    const linksHtml = links.map(l =>
      `<a class="priv-link ${isOn(l.path) ? 'on' : ''}" href="${l.path}">${l.label}</a>`
    ).join('');

    el.innerHTML = `
      <div class="priv-header">
        <div class="priv-left">
          <span class="priv-brand">🔒 hunit Private</span>
          ${linksHtml}
        </div>
        <div class="priv-right">
          <span class="priv-who">${escapeHtml(me.name || me.username)}님</span>
          <form method="post" action="/logout" style="margin:0"><button class="priv-logout" type="submit">로그아웃</button></form>
        </div>
      </div>`;
  }

  function escapeHtml(s) {
    return (s ?? '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  init();
})();
