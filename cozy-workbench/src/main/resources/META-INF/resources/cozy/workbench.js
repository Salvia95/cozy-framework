/* 워크벤치(/cozy/dev/workbench)의 Alpine 컴포넌트. 템플릿이 x-data와 x-on으로 붙이고, 여기서는 등록만 한다.
   앱 화면에도 쓰는 동작(칸 접기, 복사, 검색 칸 단축키)은 cozy-ui의 cozy 컴포넌트(cozy.js)다. 레이아웃이 이 파일을 Alpine보다 먼저 싣는다.

   - workbenchPicker: 미리보기의 요소와 구조 트리를 잇는다. 미리보기의 요소에는 data-cozy-src="템플릿:줄"이 있고(SourceLocationDialect),
     구조 트리의 줄에는 시작·끝 줄과 삽입 대상이 있다. 요소를 누르면 화면 템플릿에서의 위치, 감싸는 구조, 에이전트에게 넘길 글을 보인다.
     htmx가 바꾸지 않는 칸 묶음(.cozy-workbench-panes)에 있고, 바뀌는 칸의 요소는 부를 때마다 x-ref로 찾는다.
     미리보기(iframe) 안의 문서는 Alpine이 닿지 않으므로 그 문서와 창에만 이벤트를 직접 단다.
   - workbenchCompare: 상태 비교 창. 고른 상태만 보이고, 창이 열려 있을 때 그 미리보기를 불러온다.
   CSP 빌드는 iframe 요소에서 식을 실행하지 않으므로, iframe의 load와 src는 감싸는 요소와 메서드가 다룬다.
   - workbenchThumb: 고정 크기로 그린 축소 미리보기를 칸 폭에 맞게 줄인다(--cozy-thumb-w는 그린 폭).
   - workbenchCommand: 코드 생성 폼. 모듈을 고르면 그 모듈의 선택지만 보이고, 아이콘 칸은 적은 아이콘을 앞에 그린다. 결과가 있으면 결과로 옮긴다. */
(function () {
  'use strict';

  function toast(message, kind) {
    if (window.Cozy && window.Cozy.toast) {
      window.Cozy.toast(message, kind);
    }
  }

  function workbenchPicker() {
    var self = null;
    // 고른 요소(미리보기 문서의 요소)와 복사할 글. 반응형 상태에 두지 않는다
    var selectedElements = [];
    var copies = { location: '', context: '' };
    // 이벤트를 단 미리보기 문서
    var attached = new WeakSet();
    // 미리보기 틀의 크기가 바뀌면(창 크기, 칸 접기) 선택 표시를 다시 놓는다
    var resizer = window.ResizeObserver ? new ResizeObserver(function () { place(refs().selected, selectedElements); }) : null;
    var observed = null;
    var keptScroll = null;

    function refs() {
      return self.$refs;
    }

    /** 고르기를 쓰는 보기인가 (샘플 데이터 보기에는 속성 칸이 없다) */
    function active() {
      var r = refs();
      return !!(r.frame && r.selection && r.props && r.pick);
    }

    function selection() {
      return refs().selection.dataset;
    }

    function template() {
      return selection().template;
    }

    function rows() {
      return Array.prototype.slice.call(self.$root.querySelectorAll('#workbench-side [data-cozy-line]'));
    }

    /** 요소의 data-cozy-src: {template, line} */
    function source(element) {
      var value = element.getAttribute ? element.getAttribute('data-cozy-src') : null;
      if (!value) {
        return null;
      }
      var at = value.lastIndexOf(':');
      return { template: value.slice(0, at), line: Number(value.slice(at + 1)) };
    }

    /** 자신이나 조상 가운데 조건에 맞는 첫 위치 */
    function nearest(element, matches) {
      for (var node = element; node && node.nodeType === 1; node = node.parentElement) {
        var found = source(node);
        if (found && matches(found)) {
          return { source: found, element: node };
        }
      }
      return null;
    }

    function number(row, name) {
      return Number(row.dataset[name]);
    }

    /** 줄을 감싸는 구조의 줄들 (바깥에서 안으로) */
    function enclosing(all, line) {
      return all.filter(function (row) {
        return number(row, 'cozyLine') <= line && line <= number(row, 'cozyEnd');
      });
    }

    /** 구조의 줄 하나와 그것을 감싸는 줄들 (같은 줄에서 시작하는 더 안쪽 줄은 뺀다) */
    function enclosingRow(all, row) {
      var index = all.indexOf(row);
      return enclosing(all, number(row, 'cozyLine')).filter(function (other) {
        return all.indexOf(other) <= index;
      });
    }

    /** 프레임워크나 다른 모듈의 템플릿이 그린 요소: 그 템플릿을 넣은 구조의 줄 */
    function insertionOf(all, own, after) {
      var candidates = all.filter(function (row) {
        return row.dataset.cozyTarget === own.template;
      });
      for (var i = 0; i < candidates.length; i++) {
        if (number(candidates[i], 'cozyLine') >= after) {
          return candidates[i];
        }
      }
      return candidates.length ? candidates[0] : null;
    }

    /** 그 템플릿이 그린 요소 가운데 가장 바깥의 것들 */
    function rootsOf(target) {
      var doc = previewDocument();
      if (!doc) {
        return [];
      }
      var roots = [];
      Array.prototype.forEach.call(doc.querySelectorAll('[data-cozy-src^="' + target + ':"]'), function (candidate) {
        var parentSource = candidate.parentElement ? source(candidate.parentElement) : null;
        if (!parentSource || parentSource.template !== target) {
          roots.push(candidate);
        }
      });
      return roots;
    }

    /** 앱 요소(줄) 바로 아래의 삽입 가운데 요소를 담거나 요소 앞에 있는 마지막 것. 없으면 첫 삽입 */
    function insertionUnder(all, line, element) {
      var depth = enclosing(all, line).length;
      var candidates = all.filter(function (row) {
        return row.dataset.cozyTarget && number(row, 'cozyLine') > line && enclosingRow(all, row).length === depth + 1;
      });
      var chosen = null;
      candidates.forEach(function (row) {
        var near = rootsOf(row.dataset.cozyTarget).some(function (root) {
          return root.contains(element) || (root.compareDocumentPosition(element) & Node.DOCUMENT_POSITION_FOLLOWING);
        });
        if (near) {
          chosen = row;
        }
      });
      return chosen || (candidates.length ? candidates[0] : null);
    }

    function describe(element) {
      var text = '<' + element.tagName.toLowerCase();
      if (element.id) {
        text += ' id="' + element.id + '"';
      }
      var classes = (element.getAttribute('class') || '').trim();
      if (classes) {
        text += ' class="' + classes.split(/\s+/).slice(0, 4).join(' ') + '"';
      }
      text += '>';
      var content = (element.textContent || '').replace(/\s+/g, ' ').trim();
      if (content) {
        text += ' "' + (content.length > 60 ? content.slice(0, 60) + '…' : content) + '"';
      }
      return text;
    }

    function place(box, elements) {
      var frame = refs().frame;
      if (!box) {
        return;
      }
      if (!elements.length || !frame) {
        box.hidden = true;
        return;
      }
      var left = Infinity;
      var top = Infinity;
      var right = -Infinity;
      var bottom = -Infinity;
      elements.forEach(function (element) {
        var rect = element.getBoundingClientRect();
        left = Math.min(left, rect.left);
        top = Math.min(top, rect.top);
        right = Math.max(right, rect.right);
        bottom = Math.max(bottom, rect.bottom);
      });
      left = Math.max(left, 0);
      top = Math.max(top, 0);
      right = Math.min(right, frame.clientWidth);
      bottom = Math.min(bottom, frame.clientHeight);
      if (right <= left || bottom <= top) {
        box.hidden = true;
        return;
      }
      box.style.left = (frame.offsetLeft + frame.clientLeft + left) + 'px';
      box.style.top = (frame.offsetTop + frame.clientTop + top) + 'px';
      box.style.width = (right - left) + 'px';
      box.style.height = (bottom - top) + 'px';
      box.hidden = false;
    }

    function hide(box) {
      if (box) {
        box.hidden = true;
      }
    }

    /** 오른쪽 칸의 속성 표를 바꾼다. 처음 표(화면의 속성)는 그 칸에 기억해 둔다 */
    function showProps(nodes) {
      var box = refs().props;
      while (box.firstChild) {
        box.removeChild(box.firstChild);
      }
      nodes.forEach(function (node) {
        box.appendChild(node);
      });
      // 복제한 표의 매개변수 폼(hx-post)을 htmx에 알린다
      if (window.htmx) {
        window.htmx.process(box);
      }
    }

    function screenProps() {
      var box = refs().props;
      if (!box.cozyScreenProps) {
        box.cozyScreenProps = Array.prototype.slice.call(box.children);
      }
      return box.cozyScreenProps;
    }

    /** 구조의 줄에 딸린 속성 표의 사본. 상위 경로를 채우고, 다른 템플릿이 그린 요소면 그 위치를 덧붙인다 */
    function propsOf(row, names, rendered, guessed) {
      var found = row.closest('li').querySelector('[data-cozy-workbench-node-props]');
      var copy = Array.prototype.map.call(found.children, function (child) {
        return child.cloneNode(true);
      });
      copy.forEach(function (node) {
        var path = node.querySelector('[data-cozy-prop="path"]');
        if (path) {
          path.textContent = names.length > 1 ? names.slice(0, -1).join(' › ') : '없음';
        }
        var body = node.querySelector('tbody');
        if (body && rendered) {
          var extra = document.createElement('tr');
          var head = document.createElement('th');
          head.scope = 'row';
          head.textContent = '렌더링한 템플릿';
          var cell = document.createElement('td');
          var code = document.createElement('code');
          code.textContent = rendered;
          cell.appendChild(code);
          if (guessed) {
            cell.appendChild(document.createTextNode(' 삽입 위치는 추정'));
          }
          extra.appendChild(head);
          extra.appendChild(cell);
          body.appendChild(extra);
        }
      });
      return copy;
    }

    function highlight(all, row) {
      all.forEach(function (other) {
        other.classList.toggle('cozy-workbench-node-selected', other === row);
      });
    }

    /**
     * 고른 것을 보인다.
     * line: 화면 템플릿의 줄 (모르면 null), chain: 감싸는 구조의 줄들, framework: 요소를 실제로 그린 다른 템플릿의 위치 (없으면 null)
     */
    function show(all, elements, line, chain, framework, description, guessed) {
      var r = refs();
      var data = selection();
      var templateFile = 'src/main/resources/templates/' + data.template + '.html';
      selectedElements = elements;
      place(r.selected, elements);
      highlight(all, chain.length ? chain[chain.length - 1] : null);
      if (chain.length) {
        var innermost = chain[chain.length - 1];
        for (var folded = innermost.parentElement; folded; folded = folded.parentElement) {
          if (folded.tagName === 'DETAILS') {
            folded.open = true;
          }
        }
        innermost.scrollIntoView({ block: 'nearest' });
      }
      var where = (line !== null) ? templateFile + ':' + line
          : 'templates/' + framework.template + '.html:' + framework.line + ' (화면 템플릿 외부)';
      var names = chain.map(function (row) {
        return row.dataset.cozyName;
      });
      var codes = chain.map(function (row) {
        return row.dataset.cozyCode;
      });
      var rendered = framework ? 'templates/' + framework.template + '.html:' + framework.line : '';
      showProps(chain.length ? propsOf(chain[chain.length - 1], names, rendered, guessed) : screenProps());
      r.hint.hidden = true;
      // 구조에 없는 요소는 속성 표가 없으므로 위치를 글로 보인다
      r.chain.textContent = names.length ? '' : '구조에 없는 요소 · ' + where;

      var lines = ['Screen: ' + data.screen + ' (state: ' + data.state + ')'];
      if (description) {
        lines.push('Element: ' + description);
      }
      lines.push('Source: ' + where);
      if (codes.length) {
        lines.push('Inside: ' + codes.join(' > '));
      }
      if (rendered) {
        lines.push('Rendered by: ' + rendered + ' (framework or another module; edit Source, not this file)');
      }
      if (guessed) {
        lines.push('Note: Source is the nearest insertion in the screen template and may be one level off.');
      }
      if (data.specFile) {
        lines.push('Spec: ' + data.specFile);
      }
      lines.push('Playbook: ' + data.playbook);
      copies = { location: where, context: lines.join('\n') };
      r.context.textContent = copies.context;
      self.canCopy = true;
    }

    function selectElement(element) {
      var all = rows();
      var screenTemplate = template();
      var app = nearest(element, function (found) {
        return found.template === screenTemplate;
      });
      var own = nearest(element, function () {
        return true;
      });
      if (!own) {
        return;
      }
      if (own.source.template === screenTemplate) {
        show(all, [own.element], own.source.line, enclosing(all, own.source.line), null, describe(element), false);
        return;
      }
      // 다른 템플릿이 그린 요소: 가까운 조상부터, 화면 템플릿이 넣은 템플릿을 찾는다 (패턴 안의 컴포넌트는 패턴의 줄로 간다)
      var row = null;
      for (var node = own.element; node && node.nodeType === 1 && !row; node = node.parentElement) {
        var found = source(node);
        if (found && found.template === screenTemplate) {
          break;
        }
        if (found) {
          row = insertionOf(all, found, app ? app.source.line : 0);
        }
      }
      var guessed = false;
      if (!row && app) {
        // 넣은 템플릿이 다시 넣은 템플릿이 그린 요소(패턴 안의 제목 등): 가까운 앱 요소 바로 아래의 삽입 가운데에서 고른다
        row = insertionUnder(all, app.source.line, element);
        guessed = row !== null;
      }
      if (row) {
        show(all, [element], number(row, 'cozyLine'), enclosingRow(all, row), own.source, describe(element), guessed);
      }
      else {
        show(all, [element], null, [], own.source, describe(element), false);
      }
    }

    function selectRow(row) {
      var all = rows();
      var elements = elementsOf(row);
      show(all, elements, number(row, 'cozyLine'), enclosingRow(all, row), null, elements.length ? describe(elements[0]) : '', false);
    }

    /** 구조의 줄이 미리보기에서 차지하는 요소들 */
    function elementsOf(row) {
      var doc = previewDocument();
      if (!doc) {
        return [];
      }
      var elements = row.dataset.cozyTarget ? rootsOf(row.dataset.cozyTarget) : [];
      if (!elements.length) {
        elements = Array.prototype.slice.call(doc.querySelectorAll('[data-cozy-src="' + template() + ':' + number(row, 'cozyLine') + '"]'));
      }
      return elements;
    }

    /** 고른 것을 지운다. 미리보기가 다시 그려지면 고른 요소가 사라지기 때문이다 */
    function clear(message) {
      var r = refs();
      selectedElements = [];
      hide(r.selected);
      hide(r.hover);
      highlight(rows(), null);
      showProps(screenProps());
      r.hint.hidden = false;
      r.chain.textContent = message || '';
      r.context.textContent = '요소를 선택하면 여기에 표시됩니다.';
      copies = { location: '', context: '' };
      self.canCopy = false;
    }

    function previewPath() {
      return refs().frame.getAttribute('src').split('?')[0];
    }

    /** 틀 안의 문서가 미리보기인가. 앱 화면은 틀 안에 넣을 수 없어서, 미리보기 밖으로 나가면 읽지 못하는 문서가 된다 */
    function previewDocument() {
      var frame = refs().frame;
      try {
        var doc = frame && frame.contentDocument;
        if (doc && doc.documentElement && frame.contentWindow.location.pathname.indexOf(previewPath()) >= 0) {
          return doc;
        }
      }
      catch (error) {
        // 읽지 못하는 문서
      }
      return null;
    }

    /** 매개변수를 고친 뒤(?node=줄) 다시 고를 구조의 줄 */
    function wantedRow() {
      var line = selection().node;
      return line ? rows().find(function (row) {
        return row.dataset.cozyLine === line && row.querySelector('.cozy-workbench-label');
      }) : null;
    }

    /** 고친 결과(매개변수나 화면 안의 동작의 변경 파일, 적용 결과)가 있으면 보이게 하고 포커스를 옮긴다. 칸이 세로로 쌓인 좁은 화면에서는 아래에 있다 */
    function revealResult() {
      var result = refs().argResult || refs().attachResult;
      if (result) {
        result.scrollIntoView({ block: 'start' });
        result.focus({ preventScroll: true });
      }
    }

    /** 클립보드를 쓸 수 없으면 글을 펼쳐 골라 둔다 (Ctrl+C로 복사) */
    function showText() {
      var r = refs();
      r.contextDetails.open = true;
      var range = document.createRange();
      range.selectNodeContents(r.context);
      var picked = window.getSelection();
      picked.removeAllRanges();
      picked.addRange(range);
    }

    function inspectorScroll() {
      return self.$root.querySelector('#workbench-inspector .cozy-workbench-scroll');
    }

    return {
      canCopy: false,

      init: function () {
        self = this;
      },

      destroy: function () {
        if (resizer) {
          resizer.disconnect();
        }
      },

      /** 오른쪽 칸이 처음 그려지거나 htmx가 바꿨을 때(x-init): 이전 선택을 지우고, 고칠 줄이 있으면 다시 고른다 */
      restore: function () {
        if (!active()) {
          return;
        }
        selectedElements = [];
        copies = { location: '', context: '' };
        this.canCopy = false;
        hide(refs().selected);
        hide(refs().hover);
        var row = wantedRow();
        if (row) {
          selectRow(row);
        }
        revealResult();
        var doc = previewDocument();
        if (doc && doc.readyState === 'complete') {
          this.attach();
        }
      },

      /** 미리보기가 그려졌을 때(감싸는 요소의 x-on:load.capture): 그 문서에 고르기를 단다 */
      attach: function (event) {
        if (!active()) {
          return;
        }
        var r = refs();
        var frame = r.frame;
        if (event && event.target !== frame) {
          return;
        }
        if (resizer && observed !== frame) {
          if (observed) {
            resizer.unobserve(observed);
          }
          resizer.observe(frame);
          observed = frame;
        }
        var doc = previewDocument();
        if (!doc) {
          // 고르기를 끄고 링크를 눌러 미리보기 밖으로 나갔다: 처음 미리보기로 되돌린다
          clear(r.chain.dataset.left);
          frame.setAttribute('src', frame.getAttribute('src'));
          return;
        }
        var row = wantedRow();
        if (row) {
          selectRow(row);
          revealResult();
        }
        else if (selectedElements.length) {
          clear('');
        }
        if (attached.has(doc)) {
          return;
        }
        attached.add(doc);
        var view = frame.contentWindow;
        doc.addEventListener('click', function (event) {
          if (!refs().pick || !refs().pick.checked) {
            return;
          }
          event.preventDefault();
          event.stopPropagation();
          selectElement(event.target);
        }, true);
        doc.addEventListener('mousemove', function (event) {
          place(refs().hover, refs().pick && refs().pick.checked ? [event.target] : []);
        }, true);
        doc.addEventListener('mouseleave', function () {
          hide(refs().hover);
        }, true);
        view.addEventListener('scroll', function () {
          hide(refs().hover);
          place(refs().selected, selectedElements);
        }, true);
      },

      /** 구조의 이름을 눌렀다: 접고 펴는 줄 안에 있어도 선택만 한다(x-on:click.prevent) */
      selectNode: function (event) {
        if (active()) {
          selectRow(event.currentTarget.closest('[data-cozy-line]'));
        }
      },

      hoverRow: function (event) {
        if (active()) {
          place(refs().hover, elementsOf(event.currentTarget));
        }
      },

      hideHover: function () {
        hide(refs().hover);
      },

      /** kind: location(경로) 또는 context(에이전트 전달 내용) */
      copyPicked: function (kind) {
        var text = copies[kind];
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(function () { toast('복사했습니다', 'success'); }, showText);
        }
        else {
          showText();
        }
      },

      /** 오른쪽 칸을 바꾸는 요청(샘플 데이터 저장, 행, 매개변수)은 그 칸의 스크롤 위치를 지킨다 */
      keepScroll: function (event) {
        var scroll = inspectorScroll();
        var target = event.detail && event.detail.target;
        keptScroll = (scroll && target && target.id === 'workbench-inspector') ? scroll.scrollTop : null;
      },

      restoreScroll: function () {
        var scroll = inspectorScroll();
        // 고친 결과(변경 파일, 적용 결과)가 있으면 지킨 위치가 아니라 그 결과를 보인다
        if (refs().argResult || refs().attachResult) {
          keptScroll = null;
          revealResult();
          return;
        }
        if (keptScroll !== null && scroll) {
          scroll.scrollTop = keptScroll;
        }
        keptScroll = null;
      }
    };
  }

  function workbenchCompare() {
    return {
      picked: [],
      width: 'full',
      limit: 4,
      total: 0,
      opened: false,

      init: function () {
        var root = this.$root;
        var boxes = Array.prototype.slice.call(root.querySelectorAll('.cozy-workbench-compare-picks input[type="checkbox"]'));
        this.picked = boxes.filter(function (box) { return box.checked; }).map(function (box) { return box.value; });
        this.total = boxes.length;
        this.limit = parseInt(root.dataset.limit, 10) || 4;
        var width = root.querySelector('input[type="radio"]:checked');
        this.width = width ? width.value : 'full';
        if (root.dataset.open === 'true') {
          // 개요의 "상태 비교"로 왔다: 창을 열어 둔다
          this.$nextTick(function () {
            var button = document.querySelector('[data-bs-target="#' + root.id + '"]');
            if (button) {
              button.click();
            }
          });
        }
      },

      /** 창이 열렸다(x-on:shown-bs-modal.dot) */
      open: function () {
        this.opened = true;
      },

      get full() {
        return this.picked.length >= this.limit;
      },

      get noteShown() {
        return this.full && this.total > this.limit;
      },

      get nothingPicked() {
        return this.picked.length === 0;
      },

      get thumbClass() {
        return 'cozy-workbench-thumb-' + this.width;
      },

      /** 다 고른 동안에는 고르지 않은 상태를 막는다 */
      blocked: function (box) {
        return this.full && this.picked.indexOf(box.value) < 0;
      },

      shows: function (card) {
        return this.picked.indexOf(card.dataset.state) >= 0;
      },

      /** 창이 열려 있으면 고른 상태의 미리보기를 불러온다(x-effect). 한 번 불러온 것은 그대로 둔다.
          CSP 빌드는 iframe에서 식을 실행하지 않으므로 src는 여기서 정한다 */
      loadPicked: function () {
        if (!this.opened) {
          return;
        }
        var picked = this.picked.slice();
        Array.prototype.forEach.call(this.$root.querySelectorAll('[data-state] iframe[data-src]'), function (frame) {
          if (picked.indexOf(frame.closest('[data-state]').dataset.state) >= 0 && !frame.getAttribute('src')) {
            frame.setAttribute('src', frame.dataset.src);
          }
        });
      },

      /** 카드를 누르면 고른 너비로 그 상태를 연다 */
      hrefOf: function (link) {
        if (!link.cozyHref) {
          link.cozyHref = link.getAttribute('href');
        }
        var url = new URL(link.cozyHref, window.location.href);
        url.searchParams.set('w', this.width);
        return url.pathname + url.search;
      }
    };
  }

  function workbenchThumb() {
    var observer = null;
    return {
      init: function () {
        var thumb = this.$root;
        if (!window.ResizeObserver) {
          return;
        }
        observer = new ResizeObserver(function () {
          var width = parseFloat(getComputedStyle(thumb).getPropertyValue('--cozy-thumb-w')) || 1280;
          // 그린 크기보다 키우지 않는다(카드가 넓으면 가운데에 그린 크기 그대로)
          thumb.style.setProperty('--cozy-thumb-scale', String(Math.min(1, thumb.clientWidth / width)));
        });
        observer.observe(thumb);
      },

      destroy: function () {
        if (observer) {
          observer.disconnect();
        }
      }
    };
  }

  function workbenchCommand() {
    function filterByModule(form) {
      var choice = form.querySelector('[data-module-choice]');
      if (!choice) {
        return;
      }
      var module = choice.value.trim();
      Array.prototype.forEach.call(form.querySelectorAll('optgroup[data-cozy-module]'), function (group) {
        var other = module !== '' && group.dataset.cozyModule !== module;
        group.hidden = other;
        group.disabled = other;
      });
      Array.prototype.forEach.call(form.querySelectorAll('select'), function (select) {
        var chosen = select.selectedOptions[0];
        if (chosen && chosen.parentElement.disabled) {
          // 다른 모듈의 값이 골라져 있으면 보이는 첫 값으로 바꾼다
          var first = Array.prototype.find.call(select.options, function (option) {
            return !option.parentElement.disabled;
          });
          select.value = first ? first.value : '';
        }
      });
    }

    function previewIcon(input) {
      var list = document.getElementById('command-icons');
      var use = input.closest('.cozy-workbench-icon-input').querySelector('svg use');
      var href = use.getAttribute('href');
      var sprite = href.substring(0, href.indexOf('#') + 1);
      var name = input.value.trim();
      var known = list && Array.prototype.some.call(list.options, function (option) { return option.value === name; });
      use.setAttribute('href', sprite + 'tabler-' + (known ? name : 'question-mark'));
    }

    return {
      init: function () {
        var form = this.$refs.form;
        if (form) {
          filterByModule(form);
        }
        // 변경 파일 확인이나 적용의 결과: htmx가 이 열을 바꾼 뒤에도 결과를 보인다 (행 버튼의 응답에는 결과가 없다)
        if (this.$refs.result) {
          this.$refs.result.scrollIntoView({ block: 'start' });
        }
      },

      /** 폼의 칸이 바뀌었다(x-on:input, x-on:change) */
      edited: function (event) {
        var target = event.target;
        if (target.hasAttribute('data-module-choice')) {
          filterByModule(this.$refs.form);
        }
        if (target.closest('.cozy-workbench-icon-input')) {
          previewIcon(target);
        }
      }
    };
  }

  document.addEventListener('alpine:init', function () {
    window.Alpine.data('workbenchPicker', workbenchPicker);
    window.Alpine.data('workbenchCompare', workbenchCompare);
    window.Alpine.data('workbenchThumb', workbenchThumb);
    window.Alpine.data('workbenchCommand', workbenchCommand);
  });
})();
