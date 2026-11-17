/* memo 모듈의 화면 안 동작. cozy가 동작 명세(behaviors/memo.yml)에서 만든 파일이라 손으로 고치지 않는다.
   동작은 cozy add behavior memo <동작> …로 바꾸고, 다시 만들 때는 cozy sync behaviors를 쓴다.
   cozy:behaviors spec=16f7d652c427d861b3a3926b639036c80053abe42b9bfe237070c87f306483c2 body=a527270ce676aed4edf65491fa03d4cb6efd6fdb8419a930b4351eed83977781 */
document.addEventListener('alpine:init', function () {
  function readBool(root, name, fallback) {
    var value = root.dataset[name];
    return (value === undefined) ? fallback : value === 'true';
  }

  function copy(value) {
    return Array.isArray(value) ? value.slice() : value;
  }

  // memoPinToggle: 고정 표시를 켜고 끈다
  window.Alpine.data('memoPinToggle', function () {
    var root = null;
    var initial = {};
    return {
      pinned: false,
      init: function () {
        root = this.$root;
        this.pinned = readBool(root, 'pinned', false);
        initial.pinned = copy(this.pinned);
      },
      flip: function (event) {
        this.pinned = !this.pinned;
        // 손으로 더한 줄: 명세에 없는 동작
        root.classList.toggle('pinned');
      }
    };
  });
});
