/* task 모듈의 Alpine 컴포넌트: 서버를 거치지 않는 화면 안의 상태(펼치기, 선택 수, 입력 미리보기 등). 레이아웃이 모든 화면에 싣는다.
   - 템플릿은 x-data="이름"으로 붙이고, 이벤트와 값은 x-on:click="메서드", x-text="값"처럼 이름만 쓴다(CSP 빌드, R16).
   - 서버 값은 x-data 요소의 data-*로 받는다(init에서 잡은 root.dataset). 서버를 거치는 일(조회, 저장)은 htmx가 한다.
   - 이름의 앞은 모듈 이름(task)이다. 새 컴포넌트는 cozy add behavior task <이름>이 이 파일 끝에 더한다. */
document.addEventListener('alpine:init', function () {
  // 상세의 안내를 펼치고 접으며 제목의 글자 수를 보인다
  window.Alpine.data('taskPanel', function () {
    var root = null;
    return {
      open: false,
      title: '',
      init: function () {
        root = this.$root;
        this.title = root.dataset.title;
      },
      get label() {
        return this.open ? '안내 접기' : '안내 펼치기';
      },
      get size() {
        return this.title.length;
      },
      get summary() {
        return '제목 ' + this.size + '자';
      },
      toggle: function () {
        this.open = !this.open;
      }
    };
  });
});
