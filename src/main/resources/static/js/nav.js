(function(){
  // 사이트 전체 공통 네비게이션. 새 기능 추가 시 이 배열에만 항목을 추가하면 모든 페이지에 반영된다.
  const NAV_ITEMS = [
    { type: 'link', href: '/mms', label: 'MMS' },
    { type: 'link', href: '/news', label: 'AI 뉴스' },
    { type: 'dropdown', label: '지도', items: [
        { href: '/map', label: '지도 홈' },
        { href: '/map/food', label: '맛집 지도' },
        { href: '/map/estate', label: '부동산 시세', soon: true }
      ] },
    { type: 'soon', label: '웹 게임' }
  ];

  const path = location.pathname;
  const isOn = href => path === href || path.startsWith(href + '/');

  function renderDesktopItem(item){
    if (item.type === 'link') {
      return '<a class="nav-link ' + (isOn(item.href) ? 'on' : '') + '" href="' + item.href + '">' + item.label + '</a>';
    }
    if (item.type === 'soon') {
      return '<span class="nav-link soon">' + item.label + '<span class="soon-badge">준비중</span></span>';
    }
    if (item.type === 'dropdown') {
      const open = item.items.some(i => !i.soon && isOn(i.href));
      const menu = item.items.map(i => i.soon
        ? '<span>' + i.label + '<span class="soon-badge">준비중</span></span>'
        : '<a class="' + (isOn(i.href) ? 'on' : '') + '" href="' + i.href + '">' + i.label + '</a>'
      ).join('');
      return '<div class="nav-dropdown' + (open ? ' open' : '') + '">'
        + '<button class="nav-link dd-trigger" type="button">' + item.label
        + '<svg viewBox="0 0 12 8" fill="none"><path d="M1 1l5 5 5-5" stroke="currentColor" stroke-width="1.6"/></svg></button>'
        + '<div class="dd-menu">' + menu + '</div></div>';
    }
    return '';
  }

  function renderMobileItem(item){
    if (item.type === 'link') {
      return '<a class="' + (isOn(item.href) ? 'on' : '') + '" href="' + item.href + '">' + item.label + '</a>';
    }
    if (item.type === 'soon') {
      return '<span class="soon">' + item.label + ' · 준비중</span>';
    }
    if (item.type === 'dropdown') {
      const isOpen = item.items.some(i => !i.soon && isOn(i.href));
      const sub = item.items.map(i => i.soon
        ? '<span class="soon sub">' + i.label + ' · 준비중</span>'
        : '<a class="sub ' + (isOn(i.href) ? 'on' : '') + '" href="' + i.href + '">' + i.label + '</a>'
      ).join('');
      return '<details' + (isOpen ? ' open' : '') + '><summary>' + item.label + '</summary>' + sub + '</details>';
    }
    return '';
  }

  const html = '<header class="topnav">'
    + '<div class="topnav-inner">'
    + '<a class="brand" href="/"><span class="brand-mark">JH</span><span class="brand-text">hunit<small>portfolio</small></span></a>'
    + '<nav class="nav-desktop">' + NAV_ITEMS.map(renderDesktopItem).join('') + '</nav>'
    + '<div class="nav-icons">'
    + '<a class="icon-btn" href="https://github.com/simjihun" target="_blank" rel="noopener" title="GitHub">'
    + '<svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2 .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg></a>'
    + '<a class="icon-btn tistory" href="https://hunit.tistory.com" target="_blank" rel="noopener" title="Tistory">T</a>'
    + '<button class="hamburger" id="navHam" aria-label="메뉴 열기" aria-expanded="false">☰</button>'
    + '</div></div>'
    + '<div class="nav-mobile" id="navMobile">' + NAV_ITEMS.map(renderMobileItem).join('') + '</div>'
    + '</header>';

  function init(){
    const mount = document.getElementById('site-nav');
    if (!mount) return;
    mount.outerHTML = html;

    document.querySelectorAll('.nav-dropdown').forEach(dd => {
      const trigger = dd.querySelector('.dd-trigger');
      trigger.addEventListener('click', e => {
        e.stopPropagation();
        const wasOpen = dd.classList.contains('open');
        document.querySelectorAll('.nav-dropdown.open').forEach(x => x.classList.remove('open'));
        if (!wasOpen) dd.classList.add('open');
      });
    });
    document.addEventListener('click', () => {
      document.querySelectorAll('.nav-dropdown.open').forEach(x => x.classList.remove('open'));
    });

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
