// 생성 결과 확인 (persistence=false, events=false).
// basedir은 IT 폴더(projects/basic)일 수도, 생성된 앱 폴더일 수도 있다
def app = new File(basedir, "project/demo-basic").isDirectory() ? new File(basedir, "project/demo-basic") : basedir
println "[cozy] 생성된 앱: " + app
def file = { String path -> new File(app, path) }
def pkg = "src/main/java/com/example/demo/basic/home"
assert file(".gitignore").isFile() : ".gitignore가 없습니다"
assert file("AGENTS.md").text.contains("mvn -o verify") : "AGENTS.md가 없습니다"
assert file("src/main/resources/application.yml").getText("UTF-8").contains('name: "데모 업무 도구"') : "appName이 들어가지 않았습니다"
assert file("target/dist/demo-basic-update.json").isFile() : "-Pdist 매니페스트가 없습니다"
assert file("target/dist/image/demo-basic").isDirectory() : "app-image가 없습니다"
assert file("$pkg/internal/MemoryNoteStore.java").exists() == !false
assert file("$pkg/internal/SqliteNoteStore.java").exists() == false
assert file("src/main/resources/db/migration").exists() == false
assert file("$pkg/api/NoteAdded.java").exists() == false
assert file("src/test/resources/events/NoteAdded/v1.json").exists() == false
def pom = file("pom.xml").text
assert pom.contains("cozy-starter-events") == false
assert pom.contains("cozy-starter-sqlite") == (false && !false)
return true
