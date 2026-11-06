/*
 * Cozy: htmx 전역 설정 (PRD §6.4)
 * - 변경 요청에 CSRF 헤더를 넣는다 (layout의 meta 태그)
 * - 4xx/5xx 응답과 연결 실패를 토스트로 알린다
 * - 요청 중이면 화면 맨 위에 진행 막대를 보인다
 * - 서버는 HX-Trigger: {"cozy:toast": {"message": "...", "kind": "success"}}로 토스트를 띄울 수 있다
 * - data-cozy-confirm이 있는 폼은 제출 전에 확인을 받는다 (앱 메뉴의 종료, 업데이트)
 */
(function () {
  'use strict';

  function meta(name) {
    var el = document.querySelector('meta[name="' + name + '"]');
    return el ? el.getAttribute('content') : null;
  }

  function toast(message, kind) {
    var container = document.getElementById('cozy-toasts');
    if (!container) { return; }
    var el = document.createElement('div');
    el.className = 'toast show text-bg-' + (kind || 'info');
    el.setAttribute('role', 'status');
    var body = document.createElement('div');
    body.className = 'toast-body';
    body.textContent = message;
    el.appendChild(body);
    container.appendChild(el);
    setTimeout(function () { el.remove(); }, 5000);
  }

  var busy = 0;
  function setBusy(delta) {
    busy = Math.max(0, busy + delta);
    document.documentElement.classList.toggle('cozy-busy', busy > 0);
  }

  document.addEventListener('htmx:configRequest', function (event) {
    var header = meta('_csrf_header');
    var token = meta('_csrf');
    if (header && token && event.detail.verb !== 'get') {
      event.detail.headers[header] = token;
    }
  });
  document.addEventListener('htmx:beforeRequest', function () { setBusy(1); });
  document.addEventListener('htmx:afterRequest', function () { setBusy(-1); });
  document.addEventListener('htmx:responseError', function (event) {
    var status = event.detail.xhr.status;
    toast(status >= 500 ? '서버 오류가 발생했습니다 (' + status + ')' : '요청을 처리할 수 없습니다 (' + status + ')', 'danger');
  });
  document.addEventListener('htmx:sendError', function () {
    toast('서버에 연결할 수 없습니다. 앱이 실행 중인지 확인하세요.', 'danger');
  });
  document.addEventListener('cozy:toast', function (event) {
    toast(event.detail.message, event.detail.kind);
  });

  document.addEventListener('submit', function (event) {
    var form = event.target;
    var message = form && form.getAttribute && form.getAttribute('data-cozy-confirm');
    if (message && !window.confirm(message)) {
      event.preventDefault();
    }
  });

  window.Cozy = { toast: toast };
})();
