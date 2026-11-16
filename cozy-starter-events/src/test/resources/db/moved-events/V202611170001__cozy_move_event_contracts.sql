-- 이벤트 클래스를 이벤트 계약 모듈(events)로 옮겨 클래스 이름이 바뀌었다 (cozy migrate events).
-- 저장된 발행 기록과 인박스의 클래스 이름을 새 이름으로 바꾼다. 미완료 발행 기록은 다음 기동 때 새 클래스로 다시 발행되고,
-- 이미 처리한 이벤트는 인박스가 계속 건너뛴다.

-- io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent → io.salvia95.cozy.events.testapp.sample.SampleEvent
UPDATE cozy_event_publication SET event_type = 'io.salvia95.cozy.events.testapp.sample.SampleEvent' WHERE event_type = 'io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent';
UPDATE cozy_event_publication SET listener_id = replace(listener_id, '(io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent)', '(io.salvia95.cozy.events.testapp.sample.SampleEvent)') WHERE instr(listener_id, '(io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent)') > 0;
UPDATE cozy_inbox SET listener_id = replace(listener_id, '(io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent)', '(io.salvia95.cozy.events.testapp.sample.SampleEvent)') WHERE instr(listener_id, '(io.salvia95.cozy.events.testapp.sample.spi.event.SampleEvent)') > 0;
