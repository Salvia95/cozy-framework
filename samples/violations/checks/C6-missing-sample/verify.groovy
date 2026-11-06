// C6: 이벤트에 직렬화 샘플이 없다
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C6] it.cozy.app.order.spi.event.OrderPlaced") : "build.log에 기대한 메시지가 없습니다: [C6] it.cozy.app.order.spi.event.OrderPlaced"
assert log.contains("v1.json") : "build.log에 기대한 메시지가 없습니다: v1.json"
assert log.contains("eventSamplesAreCompatible") : "build.log에 기대한 메시지가 없습니다: eventSamplesAreCompatible"
assert log.contains("Tests run: 5, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
