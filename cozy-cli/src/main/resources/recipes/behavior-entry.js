  window.Alpine.data('{{component}}', function () {
    var root = null;
    return {
      init: function () {
        root = this.$root;
      }
      // cozy:slot behavior-{{component}} — 상태는 속성, 로직은 메서드로 쓰세요. 템플릿은 x-data로 {{component}}를 붙이고 x-on, x-text에는 이름만 씁니다. 서버 값은 root.dataset에서 읽고, 서버에 요청하지 않습니다(htmx가 합니다)
    };
  });
