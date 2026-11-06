// 생성 결과 확인 (persistence=true, events=false).
// basedir은 IT 폴더(projects/persistence)일 수도, 생성된 앱 폴더일 수도 있다
def app = new File(basedir, "project/demo-persistence").isDirectory() ? new File(basedir, "project/demo-persistence") : basedir
println "[cozy] 생성된 앱: " + app
def file = { String path -> new File(app, path) }
def pkg = "src/main/java/com/example/demo/persistence/home"
assert file(".gitignore").isFile() : ".gitignore가 없습니다"
assert file("AGENTS.md").text.contains("mvn -o verify") : "AGENTS.md가 없습니다"
assert file("src/main/resources/application.yml").getText("UTF-8").contains('name: "데모 업무 도구"') : "appName이 들어가지 않았습니다"
assert file("target/dist/demo-persistence-update.json").isFile() : "-Pdist 매니페스트가 없습니다"
assert file("target/dist/image/demo-persistence").isDirectory() : "app-image가 없습니다"
def thirdParty = file("target/dist/image/demo-persistence/licenses/THIRD-PARTY.txt")
assert thirdParty.isFile() : "제3자 라이선스 목록이 없습니다"
assert thirdParty.getText("UTF-8").contains("org.springframework:spring-webmvc:")
assert thirdParty.getText("UTF-8").contains("(Apache-2.0) Cozy :: UI (io.salvia95.cozy:cozy-ui:") : "Cozy 모듈이 목록에 없습니다"
assert thirdParty.getText("UTF-8").contains("org.xerial:sqlite-jdbc:") == true
assert file("$pkg/internal/MemoryNoteStore.java").exists() == !true
assert file("$pkg/internal/SqliteNoteStore.java").exists() == true
assert file("src/main/resources/db/migration").exists() == true
assert file("$pkg/api/NoteAdded.java").exists() == false
assert file("src/test/resources/events/NoteAdded/v1.json").exists() == false
def pom = file("pom.xml").text
assert pom.contains("cozy-starter-events") == false
assert pom.contains("cozy-starter-sqlite") == (true && !false)
return true
