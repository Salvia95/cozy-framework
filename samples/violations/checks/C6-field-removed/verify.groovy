// C6: v1 샘플의 필드(customer)가 현재 이벤트 클래스에 없다 (필드 삭제)
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C6] src/test/resources/events/OrderPlaced/v1.json") : "build.log에 기대한 메시지가 없습니다: [C6] src/test/resources/events/OrderPlaced/v1.json"
assert log.contains("필드 'customer'가 현재 OrderPlaced에 없습니다") : "build.log에 기대한 메시지가 없습니다: 필드 'customer'가 현재 OrderPlaced에 없습니다"
assert log.contains("eventSamplesAreCompatible") : "build.log에 기대한 메시지가 없습니다: eventSamplesAreCompatible"
assert log.contains("Tests run: 5, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
