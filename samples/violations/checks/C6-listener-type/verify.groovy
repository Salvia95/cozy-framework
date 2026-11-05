// C6: 리스너 파라미터가 CozyEvent가 아니다
def log = new File(basedir, "build.log").getText("UTF-8")
assert log.contains("[C6] it.cozy.app.shipping.ShippingListener#on") : "build.log에 기대한 메시지가 없습니다: [C6] it.cozy.app.shipping.ShippingListener#on"
assert log.contains("eventListenersReceiveCozyEvents") : "build.log에 기대한 메시지가 없습니다: eventListenersReceiveCozyEvents"
assert log.contains("Tests run: 4, Failures: 1, Errors: 0, Skipped: 0") : "다른 검사까지 실패했거나 검사가 돌지 않았습니다"
return true
