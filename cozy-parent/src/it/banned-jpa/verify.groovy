def log = new File(basedir, 'build.log').text
assert log.contains('BannedDependencies') : 'enforcer BannedDependencies 규칙이 아닌 다른 이유로 실패했다'
assert log.contains('org.springframework.boot:spring-boot-starter-data-jpa') : '금지 대상 org.springframework.boot:spring-boot-starter-data-jpa 이 메시지에 없다'
