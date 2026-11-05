def log = new File(basedir, 'build.log').text
assert log.contains('BannedDependencies') : 'enforcer BannedDependencies 규칙이 아닌 다른 이유로 실패했다'
assert log.contains('com.h2database:h2') : '금지 대상 com.h2database:h2 이 메시지에 없다'
