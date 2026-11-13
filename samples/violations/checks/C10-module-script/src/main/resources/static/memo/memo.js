/* memo 모듈의 Alpine 컴포넌트. 메서드 사이의 쉼표가 빠졌다 */
document.addEventListener('alpine:init', function () {
  window.Alpine.data('memoCount', function () {
    return {
      count: 0,
      increase: function () { this.count++; }
      get label() { return this.count + '건'; }
    };
  });
});
