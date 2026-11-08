// 생성 결과 확인 (persistence=false, events=false).
// basedir은 IT 폴더(projects/basic)일 수도, 생성된 앱 폴더일 수도 있다
def app = new File(basedir, "project/demo-basic").isDirectory() ? new File(basedir, "project/demo-basic") : basedir
println "[cozy] 생성된 앱: " + app
def file = { String path -> new File(app, path) }
def pkg = "src/main/java/com/example/demo/basic/home"
// 모듈 구조 (ADR-0029): api(화면), spi(계약), internal(구현), 템플릿 api/internal
["api/HomeController.java", "spi/HomeFacade.java", "spi/HomeFinder.java", "spi/dto/package-info.java",
 "internal/app/command/HomeFacadeImpl.java", "internal/app/query/HomeFinderImpl.java", "internal/domain/Note.java",
 "internal/infra/NoteStore.java", "internal/config/HomeConfiguration.java"].each {
  assert file("$pkg/" + it).isFile() : "모듈 구조 파일이 없습니다: " + it
}
assert file("src/main/resources/templates/home/api/index.html").isFile() : "화면 템플릿(home/api)이 없습니다"
assert file("src/main/resources/templates/home/internal/activity.html").isFile() : "조각 템플릿(home/internal)이 없습니다"
assert file(".gitignore").isFile() : ".gitignore가 없습니다"
assert file("AGENTS.md").text.contains("mvn -o verify") : "AGENTS.md가 없습니다"
assert file("AGENTS.md").text.contains("cozy add crud <모듈>") : "AGENTS.md에 레시피 목록이 없습니다 (ADR-0032 §7)"
assert file("src/main/resources/application.yml").getText("UTF-8").contains('name: "데모 업무 도구"') : "appName이 들어가지 않았습니다"
assert file("target/dist/demo-basic-update.json").isFile() : "-Pdist 매니페스트가 없습니다"
assert file("target/dist/image/demo-basic").isDirectory() : "app-image가 없습니다"
def thirdParty = file("target/dist/image/demo-basic/licenses/THIRD-PARTY.txt")
assert thirdParty.isFile() : "제3자 라이선스 목록이 없습니다"
assert thirdParty.getText("UTF-8").contains("org.springframework:spring-webmvc:")
assert thirdParty.getText("UTF-8").contains("(Apache-2.0) Cozy :: UI (io.salvia95.cozy:cozy-ui:") : "Cozy 모듈이 목록에 없습니다"
assert thirdParty.getText("UTF-8").contains("org.xerial:sqlite-jdbc:") == false
assert file("$pkg/internal/infra/MemoryNoteStore.java").exists() == !false
assert file("$pkg/internal/infra/SqliteNoteStore.java").exists() == false
assert file("src/main/resources/db/migration").exists() == false
assert file("$pkg/spi/event/NoteAdded.java").exists() == false
assert file("src/test/resources/events/NoteAdded/v1.json").exists() == false
def pom = file("pom.xml").text
assert pom.contains("cozy-starter-events") == false
assert pom.contains("cozy-starter-sqlite") == (false && !false)
return true
