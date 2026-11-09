// 생성 결과 확인 (persistence=true, events=true).
// basedir은 IT 폴더(projects/events)일 수도, 생성된 앱 폴더일 수도 있다
def app = new File(basedir, "project/demo-events").isDirectory() ? new File(basedir, "project/demo-events") : basedir
println "[cozy] 생성된 앱: " + app
def file = { String path -> new File(app, path) }
def pkg = "src/main/java/com/example/demo/events/home"
// 모듈 구조: api(화면), spi(계약), internal(구현), 템플릿 api/internal
["api/HomeController.java", "spi/HomeFacade.java", "spi/HomeFinder.java", "spi/dto/package-info.java",
 "internal/app/command/HomeFacadeImpl.java", "internal/app/query/HomeFinderImpl.java", "internal/domain/Note.java",
 "internal/infra/NoteStore.java", "internal/config/HomeConfiguration.java"].each {
  assert file("$pkg/" + it).isFile() : "모듈 구조 파일이 없습니다: " + it
}
assert file("src/main/resources/templates/home/api/index.html").isFile() : "화면 템플릿(home/api)이 없습니다"
assert file("src/main/resources/templates/home/internal/activity.html").isFile() : "조각 템플릿(home/internal)이 없습니다"
assert file(".gitignore").isFile() : ".gitignore가 없습니다"
assert file("AGENTS.md").text.contains("mvn -o verify") : "AGENTS.md가 없습니다"
assert file("AGENTS.md").text.contains("cozy-helper") : "AGENTS.md가 cozy-helper 스킬을 가리키지 않습니다"
assert file(".agents/skills/cozy-helper/SKILL.md").text.startsWith("---\nname: cozy-helper\n") : "cozy-helper 스킬이 없습니다"
assert file(".agents/skills/cozy-helper/references/add-query/PLAYBOOK.md").text.contains("LIMIT #{limit}") : "플레이북이 Velocity로 바뀌었습니다"
assert file("src/main/resources/application.yml").getText("UTF-8").contains('name: "데모 업무 도구"') : "appName이 들어가지 않았습니다"
assert file("target/dist/demo-events-update.json").isFile() : "-Pdist 매니페스트가 없습니다"
assert file("target/dist/image/demo-events").isDirectory() : "app-image가 없습니다"
def thirdParty = file("target/dist/image/demo-events/licenses/THIRD-PARTY.txt")
assert thirdParty.isFile() : "제3자 라이선스 목록이 없습니다"
assert thirdParty.getText("UTF-8").contains("org.springframework:spring-webmvc:")
assert thirdParty.getText("UTF-8").contains("(Apache-2.0) Cozy :: UI (io.salvia95.cozy:cozy-ui:") : "Cozy 모듈이 목록에 없습니다"
assert thirdParty.getText("UTF-8").contains("org.xerial:sqlite-jdbc:") == true
assert file("$pkg/internal/infra/MemoryNoteStore.java").exists() == !true
assert file("$pkg/internal/infra/SqliteNoteStore.java").exists() == true
assert file("src/main/resources/db/migration").exists() == true
assert file("$pkg/spi/event/NoteAdded.java").exists() == true
assert file("src/test/resources/events/NoteAdded/v1.json").exists() == true
def pom = file("pom.xml").text
assert pom.contains("cozy-starter-events") == true
assert pom.contains("cozy-starter-sqlite") == (true && !true)
return true
