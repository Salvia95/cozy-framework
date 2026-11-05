def log = new File(basedir, 'build.log').text
assert log.contains('cozy-enforce') : 'cozy-parent의 enforcer 실행이 앱에 상속되지 않았다'
assert !log.contains('cozy-version-consistency') : '프레임워크 전용 규칙이 앱에 상속됐다'
