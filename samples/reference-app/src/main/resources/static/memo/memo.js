/* memo 모듈의 화면 안 동작. cozy가 동작 명세(behaviors/memo.yml)에서 만든 파일이라 손으로 고치지 않는다.
   동작은 cozy add behavior memo <동작> …로 바꾸고, 다시 만들 때는 cozy sync behaviors를 쓴다.
   cozy:behaviors spec=a487e432878c815c9ec9d13d2f094d5d65d4f2282a779fefe95f979b7f20e193 body=283779aa4038dba01293572240d625c9aed97dd62bda0b793bc8214b54041c89 */
document.addEventListener('alpine:init', function () {
  function readNumber(root, name, fallback) {
    var value = parseFloat(root.dataset[name]);
    return isNaN(value) ? fallback : value;
  }

  function copy(value) {
    return Array.isArray(value) ? value.slice() : value;
  }

  function eventValue(event) {
    var target = event && event.target;
    return (target && target.value !== undefined && target.value !== null) ? String(target.value) : '';
  }

  function within(event, root, selector) {
    var target = event && event.target;
    var element = (target && target.closest) ? target.closest(selector) : null;
    return element !== null && root.contains(element);
  }

  function lengthOf(value) {
    return (value === null || value === undefined) ? 0 : String(value).length;
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

  // memoBodyCount: 본문을 쓰는 동안 글자 수와 한도를 보인다
  // cozy:component memoBodyCount members=length,limit,over,text,label,count data=length,limit actions=count emits= listens=
  window.Alpine.data('memoBodyCount', function () {
    var root = null;
    var initial = {};
    return {
      length: 0,
      limit: 0,
      init: function () {
        root = this.$root;
        this.length = readNumber(root, 'length', 0);
        initial.length = copy(this.length);
        this.limit = readNumber(root, 'limit', 0);
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
});
