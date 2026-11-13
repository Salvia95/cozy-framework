/* memo 모듈의 Alpine 컴포넌트: 서버를 거치지 않는 화면 안의 상태(펼치기, 선택 수, 입력 미리보기 등). 레이아웃이 모든 화면에 싣는다.
   - 템플릿은 x-data="이름"으로 붙이고, 이벤트와 값은 x-on:click="메서드", x-text="값"처럼 이름만 쓴다(CSP 빌드, R16).
   - 서버 값은 x-data 요소의 data-*로 받는다(init에서 잡은 root.dataset). 서버를 거치는 일(조회, 저장)은 htmx가 한다.
   - 이름의 앞은 모듈 이름(memo)이다. 새 컴포넌트는 cozy add behavior memo <이름>이 이 파일 끝에 더한다. */
document.addEventListener('alpine:init', function () {
  // 본문 글자 수: 쓰는 동안 "120 / 5000자"를 보인다. 한도(data-limit)는 MemoForm의 @Size와 같고, 넘었는지는 서버가 검증한다
  window.Alpine.data('memoBodyCount', function () {
    var root = null;
    return {
      length: 0,
      limit: 0,
      init: function () {
        root = this.$root;
        this.limit = parseInt(root.dataset.limit, 10) || 0;
        var area = root.querySelector('textarea');
        this.length = area ? area.value.length : 0;
      },
      /** 감싼 요소의 x-on:input: 본문 칸이 바뀌었다 */
      count: function (event) {
        if (event.target.tagName === 'TEXTAREA') {
          this.length = event.target.value.length;
        }
      },
      get label() {
        var text = this.length.toLocaleString('ko-KR') + ' / ' + this.limit.toLocaleString('ko-KR') + '자';
        return (this.limit > 0 && this.length > this.limit) ? text + ' · 한도를 넘었습니다' : text;
      }
    };
  });
});
