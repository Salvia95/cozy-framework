/*
 * Cozy: htmx 전역 설정
 * - 변경 요청에 CSRF 헤더를 넣는다 (layout의 meta 태그)
 * - 4xx/5xx 응답과 연결 실패를 토스트로 알린다
 * - 요청 중이면 화면 맨 위에 진행 막대를 보인다
 * - 서버는 HX-Trigger: {"cozy:toast": {"message": "...", "kind": "success"}}로 토스트를 띄울 수 있다
 * - data-cozy-confirm이 있는 폼은 제출 전에 확인을 받는다 (앱 메뉴의 종료, 업데이트)
 * - 아이콘 목록(/cozy/dev/catalog/icons): data-cozy-icon-filter 입력으로 거르고, 칸을 누르면 data-copy를 복사한다
 * - 검증 오류(.is-invalid)가 있으면 첫 칸으로 포커스를 옮긴다 (페이지 로드, htmx 교체 뒤)
 * - 만들기·수정 폼(form-page 패턴 안의 POST 폼)에 바뀐 입력이 있으면 떠날 때 브라우저가 확인한다
 * - 카탈로그 미리보기(iframe[data-cozy-autosize])는 내용 높이에 맞춘다
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
    el.className = 'toast show cozy-toast' + (kind === 'critical' || kind === 'danger' ? ' cozy-toast-critical' : '');
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
    toast(status >= 500 ? '서버 오류가 발생했습니다 (' + status + ')' : '요청을 처리할 수 없습니다 (' + status + ')', 'critical');
  });
  document.addEventListener('htmx:sendError', function () {
    toast('서버에 연결할 수 없습니다. 앱이 실행 중인지 확인하세요.', 'critical');
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

  document.addEventListener('input', function (event) {
    var input = event.target;
    var scope = input && input.getAttribute && input.getAttribute('data-cozy-icon-filter');
    if (!scope) { return; }
    var query = input.value.trim().toLowerCase();
    document.querySelectorAll(scope + ' [data-name]').forEach(function (cell) {
      cell.hidden = query !== '' && cell.getAttribute('data-name').indexOf(query) < 0;
    });
  });
  document.addEventListener('click', function (event) {
    var cell = event.target.closest && event.target.closest('[data-copy]');
    if (!cell || !navigator.clipboard) { return; }
    navigator.clipboard.writeText(cell.getAttribute('data-copy')).then(function () {
      toast(cell.getAttribute('data-copy') + ' 복사했습니다', 'success');
    });
  });

  // 검증 오류: 첫 칸으로 포커스 (입력하는 중에는 검사하지 않고, 제출 뒤 서버가 돌려준 오류로 간다)
  function focusFirstError(root) {
    // 카탈로그 미리보기(iframe) 안에서는 옮기지 않는다: 바깥 화면이 그 칸까지 스크롤된다
    if (window.self !== window.top) { return; }
    var invalid = (root || document).querySelector('.is-invalid');
    if (invalid && typeof invalid.focus === 'function') {
      invalid.focus();
    }
  }

  // 저장하지 않은 입력: form-page 패턴(.cozy-width-form) 안의 POST 폼만 지킨다
  var dirty = false;
  function guarded(el) {
    var form = el && el.closest && el.closest('form');
    return form && form.method === 'post' && form.closest('.cozy-width-form') ? form : null;
  }
  document.addEventListener('input', function (event) {
    if (guarded(event.target)) { dirty = true; }
  });
  document.addEventListener('change', function (event) {
    if (guarded(event.target)) { dirty = true; }
  });
  document.addEventListener('submit', function (event) {
    if (guarded(event.target)) { dirty = false; }
  });
  window.addEventListener('beforeunload', function (event) {
    if (dirty) {
      event.preventDefault();
      event.returnValue = '';
    }
  });

  // htmx가 오류 폼을 돌려주면(outerHTML 교체) 그 폼의 첫 오류 칸으로. 입력은 아직 저장되지 않았다
  document.addEventListener('htmx:afterSettle', function () {
    var invalid = document.querySelector('.is-invalid');
    if (invalid) {
      if (guarded(invalid)) { dirty = true; }
      focusFirstError(document);
    }
  });

  // 미리보기 높이: 처음 그릴 때와, 나중에 내용이 바뀔 때(글꼴, 아이콘, htmx 갱신)마다 맞춘다
  function autosize(frame) {
    try {
      var doc = frame.contentDocument;
      if (!doc || !doc.documentElement) { return; }
      var fit = function () { frame.style.height = Math.max(64, doc.documentElement.scrollHeight) + 'px'; };
      fit();
      var View = frame.contentWindow && frame.contentWindow.ResizeObserver;
      if (View && !frame.cozyObserved && doc.body) {
        frame.cozyObserved = true;
        new View(fit).observe(doc.body);
      }
    } catch (e) { /* 다른 출처면 그대로 둔다 */ }
  }
  document.addEventListener('load', function (event) {
    var frame = event.target;
    if (frame && frame.tagName === 'IFRAME' && frame.hasAttribute('data-cozy-autosize')) { autosize(frame); }
  }, true);

  document.addEventListener('DOMContentLoaded', function () {
    focusFirstError(document);
    document.querySelectorAll('iframe[data-cozy-autosize]').forEach(autosize);
  });

  window.Cozy = { toast: toast };
})();
