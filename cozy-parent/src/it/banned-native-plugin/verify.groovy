def log = new File(basedir, 'build.log').text
assert log.contains('BannedPlugins') : 'enforcer BannedPlugins 규칙이 아닌 다른 이유로 실패했다'
assert log.contains('org.graalvm.buildtools:native-maven-plugin') : '금지 대상 org.graalvm.buildtools:native-maven-plugin 이 메시지에 없다'
