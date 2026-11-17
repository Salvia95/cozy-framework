/*
 * Cozy: htmx 전역 설정
 * - 변경 요청에 CSRF 헤더를 넣는다 (layout의 meta 태그)
 * - 4xx/5xx 응답과 연결 실패를 토스트로 알린다
 * - 요청 중이면 화면 맨 위에 진행 막대를 보인다
 * - 서버는 HX-Trigger: {"cozy:toast": {"message": "...", "kind": "success"}}로 토스트를 띄울 수 있다
 * - 검증 오류(.is-invalid)가 있으면 첫 칸으로 포커스를 옮긴다 (페이지 로드, htmx 교체 뒤)
 * - 앱 모듈의 컴포넌트 묶음(/cozy/modules.js)에서 난 오류를 원래 파일과 줄로 알린다. 문법 오류면 묶음 전체가 실행되지 않으므로 토스트도 띄운다
 *
 * Alpine 컴포넌트 (화면 안의 상태. 서버를 거치는 동작은 htmx가 한다)
 * - Alpine은 CSP 빌드다. 템플릿은 x-data="이름"과 이 객체의 속성·메서드 이름만 쓴다 (docs/conventions/ui.md)
 * - 서버 값은 x-data 요소의 data-* 속성으로 받는다 (this.$root.dataset)
 * - cozyCopy: data-copy-text의 글자를 복사하고 토스트로 알린다. 버튼에 x-on:click="copy"
 * - cozyDisclosure: 이름 붙인 부분을 펼치고 접는다(toggle('이름'), isOpen('이름'), isClosed('이름')). data-disclosure-closed는 처음에 접힌
 *   부분, data-disclosure-remember는 접은 부분을 브라우저에 기억할 이름이다
 * - cozyFocusKey: 글을 적는 중이 아닐 때 data-focus-key(기본 /)를 누르면 그 칸으로 옮겨 간다. 칸에 x-on:keydown.window="focusOnKey"
 * - cozyConfirm: 폼을 내기 전에 data-confirm-message로 확인을 받는다. 폼에 x-on:submit="ask" (앱 메뉴의 종료, 업데이트)
 * - cozyDirtyForm: 안의 POST 폼에 저장하지 않은 입력이 있으면 떠날 때 브라우저가 확인한다 (form-page 패턴이 붙인다)
 * - cozyAutoHeight: 안의 iframe을 내용 높이에 맞춘다. iframe에는 식을 달 수 없어 감싸는 요소가 x-on:load.capture="fit"으로 받는다
 * - cozyIconList: 아이콘 목록(/cozy/dev/catalog/icons). 입력으로 이름을 거르고(filter), 칸을 누르면 data-copy-text를 복사한다(copy)
 * - 컴포넌트가 자기 요소의 data-*를 읽을 때는 init에서 $root를 잡아 둔다. 안쪽 요소의 다른 컴포넌트에서 부르면 $root가 그 컴포넌트다
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
  // 묶음의 줄 번호를 원래 파일의 줄로: data-cozy-parts는 "파일:묶음에서의 첫 줄;…"
  function moduleLine(parts, line) {
    var where = 'cozy/modules.js ' + line + '줄';
    (parts || '').split(';').forEach(function (part) {
      var colon = part.lastIndexOf(':');
      var first = parseInt(part.substring(colon + 1), 10);
      if (colon > 0 && line >= first) { where = part.substring(0, colon) + ' ' + (line - first + 1) + '줄'; }
    });
    return where;
  }

  window.addEventListener('error', function (event) {
    if (!event.filename || event.filename.indexOf('/cozy/modules.js') < 0) { return; }
    var script = document.querySelector('script[data-cozy-parts]');
    var where = moduleLine(script ? script.getAttribute('data-cozy-parts') : '', event.lineno);
    var syntax = !!(event.error && event.error.name === 'SyntaxError');
    console.error('[cozy] ' + where + ': ' + event.message
      + (syntax ? ' — 모듈 컴포넌트 묶음이 실행되지 않아 모든 모듈의 컴포넌트가 없습니다' : ''));
    if (syntax) {
      toast('화면 동작을 불러오지 못했습니다. 앱을 만든 사람에게 ' + where + '을 알려 주세요.', 'critical');
    }
  });

  document.addEventListener('cozy:toast', function (event) {
    toast(event.detail.message, event.detail.kind);
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

  // htmx가 오류 폼을 돌려주면(outerHTML 교체) 그 폼의 첫 오류 칸으로. 입력은 아직 저장되지 않았다(cozyDirtyForm이 기억한다)
  document.addEventListener('htmx:afterSettle', function () {
    if (document.querySelector('.is-invalid')) {
      focusFirstError(document);
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    focusFirstError(document);
  });

  // 미리보기 높이: 처음 그릴 때와, 나중에 내용이 바뀔 때(글꼴, 아이콘, htmx 갱신)마다 맞춘다. 새 문서마다 한 번 지켜본다.
  // Tabler는 body를 틀 높이(100%)로 두므로 그대로 재면 틀에 따라 다시 바뀌어 끝없이 돈다. 틀 안의 문서를 내용 높이로 두고(cozy-fit)
  // 문서 요소의 높이(틀 높이와 상관없다)에 틀의 테두리를 더한다
  function fitFrame(frame) {
    try {
      var doc = frame.contentDocument;
      // 주소가 있는 틀의 처음 빈 문서(about:blank)는 곧 바뀐다. 실제 문서는 load에서 다시 부른다
      if (!doc || !doc.documentElement || (doc.URL === 'about:blank' && frame.getAttribute('src'))) { return; }
      doc.documentElement.classList.add('cozy-fit');
      var fit = function () {
        var content = Math.ceil(doc.documentElement.getBoundingClientRect().height);
        var height = Math.max(64, content + frame.offsetHeight - frame.clientHeight) + 'px';
        if (frame.style.height !== height) { frame.style.height = height; }
      };
      fit();
      var View = frame.contentWindow && frame.contentWindow.ResizeObserver;
      if (View && frame.cozyObserved !== doc) {
        // 앞 문서를 지켜보던 것은 끊는다. 남겨 두면 떨어진 문서를 계속 지켜보며 그림마다 오류를 낸다
        if (frame.cozyObserver) { frame.cozyObserver.disconnect(); }
        frame.cozyObserved = doc;
        frame.cozyObserver = new View(fit);
        frame.cozyObserver.observe(doc.documentElement);
      }
    } catch (e) { /* 다른 출처면 그대로 둔다 */ }
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text);
    }
    return Promise.reject(new Error('clipboard'));
  }

  function readStored(key) {
    try {
      var value = JSON.parse(window.localStorage.getItem(key) || 'null');
      return Array.isArray(value) ? value : null;
    }
    catch (error) {
      // 저장소를 쓸 수 없거나 값이 깨졌으면 기억하지 않은 것으로 본다
      return null;
    }
  }

  function store(key, value) {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    }
    catch (error) {
      // 기억하지 못해도 펼치고 접기는 된다
    }
  }

  document.addEventListener('alpine:init', function () {
    // cozy:component cozyDisclosure members=toggle,isOpen,isClosed data=disclosure-closed,disclosure-remember (cozy/behaviors.yml)
    window.Alpine.data('cozyDisclosure', function () {
      // 안쪽 요소(다른 컴포넌트 안의 버튼)에서 부르면 $root가 그 컴포넌트다. 기억할 이름은 처음에 읽어 둔다
      var remember = null;
      return {
        closed: {},
        init: function () {
          var data = this.$root.dataset;
          remember = data.disclosureRemember || null;
          var names = (data.disclosureRemember && readStored(data.disclosureRemember))
              || (data.disclosureClosed || '').split(/\s+/).filter(Boolean);
          var closed = {};
          names.forEach(function (name) { closed[name] = true; });
          this.closed = closed;
        },
        isOpen: function (name) {
          return !this.closed[name];
        },
        isClosed: function (name) {
          return !!this.closed[name];
        },
        toggle: function (name) {
          this.closed[name] = !this.closed[name];
          if (remember) {
            var closed = this.closed;
            store(remember, Object.keys(closed).filter(function (n) { return closed[n]; }));
          }
        }
      };
    });

    // cozy:component cozyFocusKey members=focusOnKey data=focus-key (cozy/behaviors.yml)
    window.Alpine.data('cozyFocusKey', function () {
      return {
        focusOnKey: function (event) {
          var target = event.target;
          var typing = target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName);
          if (event.key === (this.$root.dataset.focusKey || '/') && !typing && !event.ctrlKey && !event.metaKey && !event.altKey) {
            event.preventDefault();
            this.$root.focus();
          }
        }
      };
    });

    // cozy:component cozyConfirm members=ask data=confirm-message (cozy/behaviors.yml)
    window.Alpine.data('cozyConfirm', function () {
      var root = null;
      return {
        init: function () {
          root = this.$root;
        },
        /** x-on:submit: 취소하면 내지 않고, 감싸는 요소(cozyDirtyForm 등)에도 제출을 알리지 않는다. htmx로 내는 폼은 hx-confirm을 쓴다 */
        ask: function (event) {
          var message = root.dataset.confirmMessage;
          if (message && !window.confirm(message)) {
            event.preventDefault();
            event.stopPropagation();
          }
        }
      };
    });

    // cozy:component cozyDirtyForm members=mark,clean,settled,warn data= (cozy/behaviors.yml)
    window.Alpine.data('cozyDirtyForm', function () {
      var root = null;
      var dirty = false;
      function guarded(el) {
        var form = el && el.closest && el.closest('form');
        return !!(form && form.method === 'post' && root.contains(form));
      }
      return {
        init: function () {
          root = this.$root;
        },
        /** x-on:input, x-on:change */
        mark: function (event) {
          if (guarded(event.target)) { dirty = true; }
        },
        /** x-on:submit: 내는 중이다. htmx도 제출을 받아 preventDefault하므로 그것으로 가리지 않는다(확인 창에서 취소한 제출은 여기 오지 않는다) */
        clean: function (event) {
          if (guarded(event.target)) { dirty = false; }
        },
        /** x-on:htmx:after-settle.camel: 서버가 오류 폼을 돌려주었으면 입력은 아직 저장되지 않았다 */
        settled: function () {
          if (guarded(root.querySelector('.is-invalid'))) { dirty = true; }
        },
        /** x-on:beforeunload.window */
        warn: function (event) {
          if (dirty) {
            event.preventDefault();
            event.returnValue = '';
          }
        }
      };
    });

    // cozy:component cozyAutoHeight members=fit data= (cozy/behaviors.yml)
    window.Alpine.data('cozyAutoHeight', function () {
      return {
        init: function () {
          Array.prototype.forEach.call(this.$root.querySelectorAll('iframe'), fitFrame);
        },
        /** 감싸는 요소의 x-on:load.capture (load는 올라오지 않는다) */
        fit: function (event) {
          if (event.target && event.target.tagName === 'IFRAME') { fitFrame(event.target); }
        }
      };
    });

    // cozy:component cozyIconList members=filter,copy data= (cozy/behaviors.yml)
    window.Alpine.data('cozyIconList', function () {
      var root = null;
      return {
        init: function () {
          root = this.$root;
        },
        /** 입력의 x-on:input: data-name에 글자가 든 칸만 보인다 */
        filter: function (event) {
          var query = event.target.value.trim().toLowerCase();
          Array.prototype.forEach.call(root.querySelectorAll('[data-name]'), function (cell) {
            cell.hidden = query !== '' && cell.dataset.name.indexOf(query) < 0;
          });
        },
        /** 목록의 x-on:click: 누른 칸의 data-copy-text를 복사한다 */
        copy: function (event) {
          var cell = event.target.closest('[data-copy-text]');
          if (!cell || !root.contains(cell)) { return; }
          var text = cell.dataset.copyText;
          copyText(text).then(function () {
            toast(text + ' 복사했습니다', 'success');
          }, function () {
            toast('복사하지 못했습니다. 글자를 직접 선택해 복사하세요.', 'critical');
          });
        }
      };
    });

    // cozy:component cozyCopy members=copy data=copy-text (cozy/behaviors.yml)
    window.Alpine.data('cozyCopy', function () {
      var root = null;
      return {
        init: function () {
          root = this.$root;
        },
        copy: function () {
          copyText(root.dataset.copyText || '').then(function () {
            toast('복사했습니다', 'success');
          }, function () {
            toast('복사하지 못했습니다. 글자를 직접 선택해 복사하세요.', 'critical');
          });
        }
      };
    });
  });

  window.Cozy = { toast: toast };
})();
