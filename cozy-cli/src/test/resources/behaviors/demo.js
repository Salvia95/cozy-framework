/* demo 모듈의 화면 안 동작. cozy가 동작 명세(behaviors/demo.yml)에서 만든 파일이라 손으로 고치지 않는다.
   동작은 cozy add behavior demo <동작> …로 바꾸고, 다시 만들 때는 cozy sync behaviors를 쓴다.
   cozy:behaviors spec=0b753ad282cbb638a6020747915f375ab96cd1199d6b244b21ce771a73321f49 body=882a8fe09afd8a00562972a0d4952ac67c4ee0c990a0cb6f3c5a8ea653714045 */
document.addEventListener('alpine:init', function () {
  function readBool(root, name, fallback) {
    var value = root.dataset[name];
    return (value === undefined) ? fallback : value === 'true';
  }

  function readNumber(root, name, fallback) {
    var value = parseFloat(root.dataset[name]);
    return isNaN(value) ? fallback : value;
  }

  function readList(root, name, fallback) {
    var value = root.dataset[name];
    if (value === undefined) {
      return copy(fallback);
    }
    return value.split(/\s+/).filter(function (item) {
      return item !== '';
    });
  }

  function copy(value) {
    return Array.isArray(value) ? value.slice() : value;
  }

  function eventValue(event) {
    var target = event && event.target;
    return (target && target.value !== undefined && target.value !== null) ? String(target.value) : '';
  }

  function eventChecked(event) {
    return !!(event && event.target && event.target.checked);
  }

  function eventData(event, root, name) {
    var element = event && event.target;
    while (element && element.nodeType === 1) {
      if (element.dataset && element.dataset[name] !== undefined) {
        return element.dataset[name];
      }
      if (element === root) {
        break;
      }
      element = element.parentElement;
    }
    return '';
  }

  function eventDetail(event, name) {
    return (event && event.detail && event.detail[name] !== undefined) ? event.detail[name] : '';
  }

  function within(event, root, selector) {
    var target = event && event.target;
    var element = (target && target.closest) ? target.closest(selector) : null;
    return element !== null && root.contains(element);
  }

  function toNumber(value) {
    var number = parseFloat(value);
    return isNaN(number) ? 0 : number;
  }

  function isEmpty(value) {
    return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0);
  }

  function countOf(value) {
    return Array.isArray(value) ? value.length : 0;
  }

  function lengthOf(value) {
    return (value === null || value === undefined) ? 0 : String(value).length;
  }

  function includes(list, value) {
    return Array.isArray(list) && list.indexOf(value) >= 0;
  }

  function show(value, locale) {
    if (Array.isArray(value)) {
      return value.join(', ');
    }
    if (typeof value === 'number' && locale) {
      return value.toLocaleString(locale);
    }
    return (value === null || value === undefined) ? '' : String(value);
  }

  function withItem(list, value) {
    return includes(list, value) ? list.slice() : list.concat([value]);
  }

  function withoutItem(list, value) {
    return list.filter(function (item) {
      return item !== value;
    });
  }

  function toggledItem(list, value) {
    return includes(list, value) ? withoutItem(list, value) : withItem(list, value);
  }

  function listen(listeners, name, handler) {
    window.addEventListener(name, handler);
    listeners.push([name, handler]);
  }

  function unlisten(listeners) {
    listeners.forEach(function (entry) {
      window.removeEventListener(entry[0], entry[1]);
    });
    listeners.length = 0;
  }

  // demoBodyCount: 본문을 쓰는 동안 글자 수와 한도를 보인다
  // cozy:component demoBodyCount members=length,limit,over,text,label,count data=length,limit
  window.Alpine.data('demoBodyCount', function () {
    var root = null;
    var initial = {};
    return {
      length: 0,
      limit: 5000,
      init: function () {
        root = this.$root;
        this.length = readNumber(root, 'length', 0);
        initial.length = copy(this.length);
        this.limit = readNumber(root, 'limit', 5000);
        initial.limit = copy(this.limit);
      },
      get over() {
        return ((this.limit > 0) && (this.length > this.limit));
      },
      get text() {
        return (show(this.length, 'ko-KR') + ' / ' + show(this.limit, 'ko-KR') + '자');
      },
      get label() {
        return (this.over ? (show(this.text, null) + ' · 한도를 넘었습니다') : this.text);
      },
      count: function (event) {
        if (within(event, root, 'textarea')) {
          this.length = lengthOf(eventValue(event));
        }
      }
    };
  });

  // demoSelection: 고른 행 수에 따라 묶음 버튼을 켠다
  // cozy:component demoSelection members=ids,picked,none,summary,pick,add,drop,clear data=
  window.Alpine.data('demoSelection', function () {
    var root = null;
    var initial = {};
    var listeners = [];
    return {
      ids: [],
      init: function () {
        root = this.$root;
        initial.ids = copy(this.ids);
        var self = this;
        listen(listeners, 'demo:archived', function (event) {
          self.clear(event);
        });
      },
      destroy: function () {
        unlisten(listeners);
      },
      get picked() {
        return countOf(this.ids);
      },
      get none() {
        return isEmpty(this.ids);
      },
      get summary() {
        return (show(this.picked, null) + '개 고름: ' + show(this.ids, null));
      },
      pick: function (event) {
        this.ids = toggledItem(this.ids, eventData(event, root, 'id'));
        this.$dispatch('demo:selection-changed', { ids: copy(this.ids), picked: copy(this.picked) });
      },
      add: function (event) {
        this.ids = withItem(this.ids, eventData(event, root, 'id'));
      },
      drop: function (event) {
        this.ids = withoutItem(this.ids, eventData(event, root, 'id'));
      },
      clear: function (event) {
        this.ids = copy(initial.ids);
        this.$dispatch('demo:selection-changed', { ids: copy(this.ids), picked: copy(this.picked) });
      }
    };
  });

  // demoPanel: 탭, 펼침, 수량, 동의, 메모를 고른다
  // cozy:component demoPanel members=open,tab,quantity,agreed,note,tags,onList,notList,many,few,under,tagged,closed,ready,noteEmpty,braces,toggleOpen,showTab,more,less,jump,agree,write,setQuantity,fromDetail,clearNote data=open,tags
  window.Alpine.data('demoPanel', function () {
    var root = null;
    var initial = {};
    return {
      open: true,
      tab: 'list',
      quantity: 1,
      agreed: false,
      note: '',
      tags: ['a', 'b'],
      init: function () {
        root = this.$root;
        this.open = readBool(root, 'open', true);
        initial.open = copy(this.open);
        initial.tab = copy(this.tab);
        initial.quantity = copy(this.quantity);
        initial.agreed = copy(this.agreed);
        initial.note = copy(this.note);
        this.tags = readList(root, 'tags', ['a', 'b']);
        initial.tags = copy(this.tags);
      },
      get onList() {
        return (this.tab === 'list');
      },
      get notList() {
        return (this.tab !== 'list');
      },
      get many() {
        return (this.quantity >= 10);
      },
      get few() {
        return (this.quantity <= 2);
      },
      get under() {
        return (this.quantity < 5);
      },
      get tagged() {
        return includes(this.tags, 'a');
      },
      get closed() {
        return !this.open;
      },
      get ready() {
        return (this.agreed || this.many);
      },
      get noteEmpty() {
        return isEmpty(this.note);
      },
      get braces() {
        return ('{' + show(this.quantity, null) + '}');
      },
      toggleOpen: function (event) {
        this.open = !this.open;
      },
      showTab: function (event) {
        this.tab = eventData(event, root, 'tab');
      },
      more: function (event) {
        this.quantity = this.quantity + 1;
      },
      less: function (event) {
        if ((this.quantity > 1)) {
          this.quantity = this.quantity - 1;
        }
      },
      jump: function (event) {
        this.quantity = this.quantity + 10;
      },
      agree: function (event) {
        this.agreed = eventChecked(event);
      },
      write: function (event) {
        this.note = eventValue(event);
      },
      setQuantity: function (event) {
        this.quantity = toNumber(eventValue(event));
      },
      fromDetail: function (event) {
        this.note = eventDetail(event, 'message');
      },
      clearNote: function (event) {
        this.note = copy(initial.note);
      }
    };
  });
});
