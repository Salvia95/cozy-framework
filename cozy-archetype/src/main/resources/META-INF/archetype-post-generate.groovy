// Cozy 아키타입 생성 후처리: 옵션 검사, 옵션별 파일 제거.
// 옵션이 맞지 않으면 생성한 폴더를 지우고 실패한다.
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator

def props = request.properties
Path root = Paths.get(request.outputDirectory, request.artifactId)

def fail = { String message ->
    if (Files.exists(root)) {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
    throw new IllegalArgumentException("[cozy] " + message)
}

def flag = { String name ->
    String value = props.getProperty(name)
    if (value != "true" && value != "false") {
        fail("${name}는 true 또는 false여야 합니다 (지금: ${value})")
    }
    return value == "true"
}

String appId = props.getProperty("appId")
if (!(appId ==~ /[a-z0-9]+(-[a-z0-9]+)*/)) {
    fail("appId '${appId}'는 영문 소문자·숫자와 단어 사이 하이픈만 씁니다 (예: voc-assistant). -DappId=로 지정하세요.")
}
String appName = props.getProperty("appName")
if (appName == null || appName.isBlank() || appName =~ /["\\<>&$#]/) {
    fail("appName '${appName}'에는 \" \\ < > & \$ #를 쓸 수 없습니다. POM과 application.yml에 그대로 들어갑니다.")
}
boolean persistence = flag("persistence")
boolean events = flag("events")
if (events && !persistence) {
    fail("events=true는 persistence=true가 필요합니다. 이벤트 발행 기록을 SQLite에 저장하기 때문입니다 (docs/conventions/events.md).")
}

Path java = root.resolve("src/main/java").resolve(props.getProperty("package").replace('.', '/'))
Path home = java.resolve("home")
Path resources = root.resolve("src/main/resources")

def remove = { Path path ->
    if (Files.exists(path)) {
        Files.walk(path).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}

if (persistence) {
    remove(home.resolve("internal/infra/MemoryNoteStore.java"))
}
else {
    ["NoteMapper.java", "NoteRow.java", "SqliteNoteStore.java"].each { remove(home.resolve("internal/infra").resolve(it)) }
    remove(resources.resolve("mapper"))
    remove(resources.resolve("db"))
}
if (!events) {
    remove(home.resolve("spi/event"))
    remove(home.resolve("internal/app/command/NoteAddedListener.java"))
    remove(root.resolve("src/test/resources/events"))
}

println "[cozy] ${appName} (${appId}) 생성: persistence=${persistence}, events=${events}"
println "[cozy] 다음: cd ${request.artifactId} && mvn verify. 구조는 cozy add·cozy link로 만든다. 규칙은 AGENTS.md에 있습니다."
