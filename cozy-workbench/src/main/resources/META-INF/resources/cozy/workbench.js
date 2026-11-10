/* 워크벤치(/cozy/dev/workbench): 복사 버튼, 그리고 미리보기의 요소와 구조 트리를 잇는다.
   미리보기의 요소에는 data-cozy-src="템플릿:줄"이 있고(SourceLocationDialect), 구조 트리의 줄에는 시작·끝 줄과 삽입 대상이 있다.
   요소를 누르면 화면 템플릿에서의 위치, 감싸는 구조, 에이전트에게 넘길 글을 보인다. */
(function () {
  'use strict';

  function copied() {
    if (window.Cozy && window.Cozy.toast) {
      window.Cozy.toast('복사했습니다', 'success');
    }
  }

  // 검사 화면의 고치는 명령 등: data-cozy-copy의 값을 복사한다
  document.querySelectorAll('[data-cozy-copy]').forEach(function (button) {
    button.addEventListener('click', function () {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(button.dataset.cozyCopy).then(copied, function () {});
      }
    });
  });

  var frame = document.querySelector('[data-cozy-workbench-frame]');
  var panel = document.querySelector('[data-cozy-workbench-selection]');
  if (!frame || !panel) {
    return;
  }
  var pick = panel.querySelector('[data-cozy-workbench-pick]');
  var hint = panel.querySelector('[data-cozy-workbench-hint]');
  var locationText = panel.querySelector('[data-cozy-workbench-location]');
  var chainText = panel.querySelector('[data-cozy-workbench-chain]');
  var contextText = panel.querySelector('[data-cozy-workbench-context]');
  var contextDetails = panel.querySelector('[data-cozy-workbench-context-details]');
  var copyButtons = panel.querySelectorAll('[data-cozy-workbench-copy]');
  var hoverBox = document.querySelector('[data-cozy-workbench-hover]');
  var selectedBox = document.querySelector('[data-cozy-workbench-selected]');
  var rows = Array.prototype.slice.call(document.querySelectorAll('[data-cozy-line]'));
  var template = panel.dataset.template;
  var templateFile = 'src/main/resources/templates/' + template + '.html';
  var selectedElements = [];
  var copies = { location: '', context: '' };

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
  function enclosing(line) {
    return rows.filter(function (row) {
      return number(row, 'cozyLine') <= line && line <= number(row, 'cozyEnd');
    });
  }

  /** 구조의 줄 하나와 그것을 감싸는 줄들 (같은 줄에서 시작하는 더 안쪽 줄은 뺀다) */
  function enclosingRow(row) {
    var index = rows.indexOf(row);
    return enclosing(number(row, 'cozyLine')).filter(function (other) {
      return rows.indexOf(other) <= index;
    });
  }

  /** 프레임워크나 다른 모듈의 템플릿이 그린 요소: 그 템플릿을 넣은 구조의 줄 */
  function insertionOf(own, after) {
    var candidates = rows.filter(function (row) {
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
  function insertionUnder(line, element) {
    var depth = enclosing(line).length;
    var candidates = rows.filter(function (row) {
      return row.dataset.cozyTarget && number(row, 'cozyLine') > line && enclosingRow(row).length === depth + 1;
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
    if (!elements.length) {
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

  /**
   * 고른 것을 보인다.
   * line: 화면 템플릿의 줄 (모르면 null), chain: 감싸는 구조의 줄들, framework: 요소를 실제로 그린 다른 템플릿의 위치 (없으면 null)
   */
  function show(elements, line, chain, framework, description, guessed) {
    selectedElements = elements;
    place(selectedBox, elements);
    rows.forEach(function (row) {
      row.classList.remove('cozy-workbench-node-selected');
    });
    if (chain.length) {
      var innermost = chain[chain.length - 1];
      innermost.classList.add('cozy-workbench-node-selected');
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
    hint.hidden = true;
    locationText.textContent = where;
    locationText.hidden = false;
    chainText.textContent = (names.length ? names.join(' › ') : '구조에 없는 요소')
        + (rendered ? ' · 렌더링한 템플릿 ' + rendered : '') + (guessed ? ' · 삽입 위치는 추정' : '');

    var lines = ['Screen: ' + panel.dataset.screen + ' (state: ' + panel.dataset.state + ')'];
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
    if (panel.dataset.specFile) {
      lines.push('Spec: ' + panel.dataset.specFile);
    }
    lines.push('Playbook: ' + panel.dataset.playbook);
    copies.location = where;
    copies.context = lines.join('\n');
    contextText.textContent = copies.context;
    copyButtons.forEach(function (button) {
      button.disabled = false;
    });
  }

  function selectElement(element) {
    var app = nearest(element, function (found) {
      return found.template === template;
    });
    var own = nearest(element, function () {
      return true;
    });
    if (!own) {
      return;
    }
    if (own.source.template === template) {
      show([own.element], own.source.line, enclosing(own.source.line), null, describe(element), false);
      return;
    }
    // 다른 템플릿이 그린 요소: 가까운 조상부터, 화면 템플릿이 넣은 템플릿을 찾는다 (패턴 안의 컴포넌트는 패턴의 줄로 간다)
    var row = null;
    for (var node = own.element; node && node.nodeType === 1 && !row; node = node.parentElement) {
      var found = source(node);
      if (found && found.template === template) {
        break;
      }
      if (found) {
        row = insertionOf(found, app ? app.source.line : 0);
      }
    }
    var guessed = false;
    if (!row && app) {
      // 넣은 템플릿이 다시 넣은 템플릿이 그린 요소(패턴 안의 제목 등): 가까운 앱 요소 바로 아래의 삽입 가운데에서 고른다
      row = insertionUnder(app.source.line, element);
      guessed = row !== null;
    }
    if (row) {
      show([element], number(row, 'cozyLine'), enclosingRow(row), own.source, describe(element), guessed);
    }
    else {
      show([element], null, [], own.source, describe(element), false);
    }
  }

  function selectRow(row) {
    var doc = previewDocument();
    var line = number(row, 'cozyLine');
    var elements = [];
    if (doc) {
      if (row.dataset.cozyTarget) {
        elements = rootsOf(row.dataset.cozyTarget);
      }
      if (!elements.length) {
        elements = Array.prototype.slice.call(doc.querySelectorAll('[data-cozy-src="' + template + ':' + line + '"]'));
      }
    }
    show(elements, line, enclosingRow(row), null, elements.length ? describe(elements[0]) : '', false);
  }

  var previewUrl = frame.getAttribute('src');
  var previewPath = previewUrl.split('?')[0];

  /** 고른 것을 지운다. 미리보기가 다시 그려지면 고른 요소가 사라지기 때문이다 */
  function clear(message) {
    selectedElements = [];
    selectedBox.hidden = true;
    hoverBox.hidden = true;
    rows.forEach(function (row) {
      row.classList.remove('cozy-workbench-node-selected');
    });
    locationText.hidden = true;
    hint.hidden = false;
    chainText.textContent = message || '';
    contextText.textContent = '요소를 선택하면 여기에 표시됩니다.';
    copies = { location: '', context: '' };
    copyButtons.forEach(function (button) {
      button.disabled = true;
    });
  }

  /** 틀 안의 문서가 미리보기인가. 앱 화면은 틀 안에 넣을 수 없어서, 미리보기 밖으로 나가면 읽지 못하는 문서가 된다 */
  function previewDocument() {
    try {
      var doc = frame.contentDocument;
      if (doc && doc.documentElement && frame.contentWindow.location.pathname.indexOf(previewPath) >= 0) {
        return doc;
      }
    }
    catch (error) {
      // 읽지 못하는 문서
    }
    return null;
  }

  function attach() {
    var doc = previewDocument();
    if (!doc) {
      // 고르기를 끄고 링크를 눌러 미리보기 밖으로 나갔다: 처음 미리보기로 되돌린다
      clear(chainText.dataset.left);
      frame.setAttribute('src', previewUrl);
      return;
    }
    if (selectedElements.length) {
      clear('');
    }
    doc.addEventListener('click', function (event) {
      if (!pick.checked) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      selectElement(event.target);
    }, true);
    doc.addEventListener('mousemove', function (event) {
      place(hoverBox, pick.checked ? [event.target] : []);
    }, true);
    doc.addEventListener('mouseleave', function () {
      hoverBox.hidden = true;
    }, true);
    frame.contentWindow.addEventListener('scroll', function () {
      hoverBox.hidden = true;
      place(selectedBox, selectedElements);
    }, true);
  }

  frame.addEventListener('load', attach);
  if (previewDocument() && frame.contentDocument.readyState === 'complete') {
    attach();
  }
  window.addEventListener('resize', function () {
    place(selectedBox, selectedElements);
  });
  pick.addEventListener('change', function () {
    hoverBox.hidden = true;
  });
  document.querySelectorAll('[data-cozy-workbench-node]').forEach(function (button) {
    button.addEventListener('click', function () {
      selectRow(button.closest('[data-cozy-line]'));
    });
  });
  copyButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      var text = copies[button.dataset.cozyWorkbenchCopy];
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(copied, showText);
      }
      else {
        showText();
      }
    });
  });

  /** 클립보드를 쓸 수 없으면 글을 펼쳐 골라 둔다 (Ctrl+C로 복사) */
  function showText() {
    contextDetails.open = true;
    var range = document.createRange();
    range.selectNodeContents(contextText);
    var selection = window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
  }
})();
