(function(){
  // 사이트 전체 공통 네비게이션. 새 기능 추가 시 이 배열에만 항목을 추가하면 모든 페이지에 반영된다.
  // 순서 = 메인화면 기능 섹션 순서와 동일하게 유지한다.
  // type:'dropdown'은 상위 링크(href) 자체로도 이동 가능하면서, 하위 분류(children)를 함께 노출한다.
  const NAV_ITEMS = [
    { type: 'link', href: '/news', label: 'AI 뉴스' },
    { type: 'link', href: '/stock', label: 'AI 주식' },
    { type: 'link', href: '/map', label: '지도 API' },
    { type: 'link', href: '/mms', label: 'MMS' },
    { type: 'dropdown', href: '/game', label: '웹 게임', children: [
      { href: '/game', label: '웹게임 (숫자야구·발리볼)' },
      { href: '/game/board', label: '보드게임 (오목·장기)' },
      { href: '/cardgame', label: '카드게임 (프리셀·클론다이크)' }
    ] }
  ];

  const path = location.pathname;
  const isOn = href => path === href || path.startsWith(href + '/');
  const isOnAny = item => item.type === 'dropdown'
    ? item.children.some(c => isOn(c.href)) || isOn(item.href)
    : isOn(item.href);

  function renderDesktopItem(item){
    if (item.type === 'link') {
      return '<a class="nav-link ' + (isOn(item.href) ? 'on' : '') + '" href="' + item.href + '">' + item.label + '</a>';
    }
    if (item.type === 'dropdown') {
      const children = item.children.map(c =>
        '<a class="nav-dropdown-link ' + (isOn(c.href) ? 'on' : '') + '" href="' + c.href + '">' + c.label + '</a>'
      ).join('');
      return '<div class="nav-dropdown">'
        + '<a class="nav-link ' + (isOnAny(item) ? 'on' : '') + '" href="' + item.href + '">' + item.label + ' <span class="nav-caret">▾</span></a>'
        + '<div class="nav-dropdown-menu">' + children + '</div>'
        + '</div>';
    }
    if (item.type === 'soon') {
      return '<span class="nav-link soon">' + item.label + '<span class="soon-badge">준비중</span></span>';
    }
    return '';
  }

  function renderMobileItem(item){
    if (item.type === 'link') {
      return '<a class="' + (isOn(item.href) ? 'on' : '') + '" href="' + item.href + '">' + item.label + '</a>';
    }
    if (item.type === 'dropdown') {
      const children = item.children.map(c =>
        '<a class="nav-mobile-sub ' + (isOn(c.href) ? 'on' : '') + '" href="' + c.href + '">' + c.label + '</a>'
      ).join('');
      return '<a class="' + (isOnAny(item) ? 'on' : '') + '" href="' + item.href + '">' + item.label + '</a>' + children;
    }
    if (item.type === 'soon') {
      return '<span class="soon">' + item.label + ' · 준비중</span>';
    }
    return '';
  }

  // 드롭다운은 base.css를 건드리지 않고 여기서 스코프된 스타일을 함께 주입한다.
  const dropdownStyle = '<style>'
    + '.nav-dropdown{position:relative;display:inline-flex}'
    + '.nav-dropdown .nav-caret{font-size:10px;opacity:.7;margin-left:2px}'
    + '.nav-dropdown-menu{position:absolute;top:100%;left:0;min-width:220px;background:var(--panel-2,#161d2e);border:1px solid var(--line,#2a3348);border-radius:10px;padding:6px;display:none;flex-direction:column;gap:2px;box-shadow:0 10px 24px rgba(0,0,0,.35);z-index:50}'
    + '.nav-dropdown:hover .nav-dropdown-menu,.nav-dropdown:focus-within .nav-dropdown-menu{display:flex}'
    + '.nav-dropdown-link{padding:8px 10px;border-radius:7px;color:var(--mut,#9aa4bb);font-size:13.5px;white-space:nowrap;text-decoration:none}'
    + '.nav-dropdown-link:hover{background:rgba(242,169,59,.12);color:var(--ink,#eef1f8)}'
    + '.nav-dropdown-link.on{color:var(--amber,#F2A93B);font-weight:600}'
    + '.nav-mobile-sub{display:block;padding-left:22px !important;font-size:13px;opacity:.85}'
    + '</style>';

  // 구조: 로고 - 햄버거(모바일) - 데스크톱 메뉴(flex:1) - 아이콘그룹(margin-left:auto로 항상 우단)
  const html = dropdownStyle + '<header class="topnav">'
    + '<div class="topnav-inner">'
    + '<a class="brand" href="/"><span class="brand-mark">JH</span><span class="brand-text">hunit<small>portfolio</small></span></a>'
    + '<button class="hamburger" id="navHam" aria-label="메뉴 열기" aria-expanded="false">☰</button>'
    + '<nav class="nav-desktop">' + NAV_ITEMS.map(renderDesktopItem).join('') + '</nav>'
    + '<div class="nav-icons">'
    + '<a class="icon-btn" href="https://github.com/simjihun" target="_blank" rel="noopener" title="GitHub">'
    + '<svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg></a>'
    + '<a class="icon-btn tistory" href="https://hunit.tistory.com" target="_blank" rel="noopener" title="Tistory">T</a>'
    + '</div></div>'
    + '<div class="nav-mobile" id="navMobile">' + NAV_ITEMS.map(renderMobileItem).join('') + '</div>'
    + '</header>';

  function init(){
    const mount = document.getElementById('site-nav');
    if (!mount) return;
    mount.outerHTML = html;

    const ham = document.getElementById('navHam');
    const mobile = document.getElementById('navMobile');
    ham.addEventListener('click', () => {
      const isOpen = mobile.classList.toggle('open');
      ham.setAttribute('aria-expanded', isOpen);
      ham.textContent = isOpen ? '✕' : '☰';
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
