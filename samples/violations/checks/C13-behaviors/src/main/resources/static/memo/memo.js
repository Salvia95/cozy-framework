/* memo 모듈의 화면 안 동작. cozy가 동작 명세(behaviors/memo.yml)에서 만든 파일이라 손으로 고치지 않는다.
   동작은 cozy add behavior memo <동작> …로 바꾸고, 다시 만들 때는 cozy sync behaviors를 쓴다.
   cozy:behaviors spec=113fb47b98d0c4d36c3ea828654ba66c04a4a1b5889eed70e5fdde7797b8c00c body=5f18e1d47483954577d9968ac7402759ba04f4872460e63c619066b41fd96c1f */
document.addEventListener('alpine:init', function () {
  function copy(value) {
    return Array.isArray(value) ? value.slice() : value;
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

  function includes(list, value) {
    return Array.isArray(list) && list.indexOf(value) >= 0;
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

  // memoPanel: 패널을 펼치고 접는다
  // cozy:component memoPanel members=open,flip data= actions=flip emits= listens=
  window.Alpine.data('memoPanel', function () {
    var root = null;
    var initial = {};
    return {
      open: false,
      init: function () {
        root = this.$root;
        initial.open = copy(this.open);
      },
      flip: function (event) {
        this.open = !this.open;
      }
    };
  });

  // memoSelection: 고른 행을 센다
  // cozy:component memoSelection members=ids,pick,drop,clear data= actions=pick,drop,clear emits=memo:picked listens=memo:archived:clear
  window.Alpine.data('memoSelection', function () {
    var root = null;
    var initial = {};
    var listeners = [];
    return {
      ids: [],
      init: function () {
        root = this.$root;
        initial.ids = copy(this.ids);
        var self = this;
        listen(listeners, 'memo:archived', function (event) {
          self.clear(event);
        });
      },
      destroy: function () {
        unlisten(listeners);
      },
      pick: function (event) {
        this.ids = toggledItem(this.ids, eventData(event, root, 'id'));
        this.$dispatch('memo:picked', { ids: copy(this.ids) });
      },
      drop: function (event) {
        this.ids = copy(initial.ids);
      },
      clear: function (event) {
        this.ids = copy(initial.ids);
      }
    };
  });
});
