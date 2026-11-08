// 픽스처 준비 (각 픽스처의 setup.groovy가 부른다). 기본 앱(base/)을 복사하고 recipe.txt를 위에서부터 실행한다.
//   cozy <인자...>      cozy 명령. 실패하면 픽스처가 실패한다. 큰따옴표로 공백이 있는 인자를 묶는다
//   cozy! <인자...>     실패해야 하는 cozy 명령 (종료 코드 2). 파일이 하나도 바뀌지 않았는지도 확인한다
//   hand <폴더>         hand/<폴더>의 파일을 앱에 그대로 덮어쓴다 (에이전트가 빈칸을 채운 결과)
//   rerun               지금까지 실행한 cozy 명령을 다시 실행하고, 모두 "바뀐 것 없음"인지 확인한다 (멱등)
//   rerun <레시피>...    그 레시피(add 뒤의 이름)만 다시 실행한다. 빈칸을 채운 뒤에는 2단계(query, command)만 다시 실행할 수 있다
// 출력은 recipes.log에 남는다. 시각은 cozy.cli.now로 고정해 마이그레이션 이름이 늘 같다.
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

def app = basedir.toPath()
def home = Path.of(recipesHome)
def log = new StringBuilder()

def copyTree = { Path from, Path to ->
    Files.walk(from).withCloseable { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { file ->
            def target = to.resolve(from.relativize(file).toString())
            Files.createDirectories(target.parent)
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

def snapshot = {
    def files = [:]
    Files.walk(app.resolve("src")).withCloseable { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { files[app.relativize(it).toString()] = Files.readString(it) }
    }
    files
}

def tokens = { String line ->
    def out = []
    def m = line =~ /"([^"]*)"|(\S+)/
    while (m.find()) {
        out << (m.group(1) != null ? m.group(1) : m.group(2))
    }
    out
}

def cozy = { List<String> args ->
    def command = [System.getProperty("java.home") + "/bin/java", "-Dcozy.cli.now=202611081000", "-cp", cliDir + "/*",
                   "io.salvia95.cozy.cli.CozyCli"] + args + ["--repository", localRepositoryPath.toString()]
    def process = new ProcessBuilder(command).directory(app.toFile()).redirectErrorStream(true).start()
    def output = process.inputStream.getText("UTF-8")
    def code = process.waitFor()
    log << "\$ cozy " << args.join(" ") << "\n" << output << "(종료 코드 " << code << ")\n\n"
    [code, output]
}

copyTree(home.resolve("base"), app)
def done = []
new File(basedir, "recipe.txt").readLines("UTF-8").each { raw ->
    def line = raw.trim()
    if (line.isEmpty() || line.startsWith("#")) {
        return
    }
    def parts = tokens(line)
    switch (parts[0]) {
        case "cozy":
            def (code, output) = cozy(parts.drop(1))
            if (code != 0) {
                new File(basedir, "recipes.log").setText(log.toString(), "UTF-8")
                throw new IllegalStateException("cozy 명령이 실패했습니다: " + line + "\n" + output)
            }
            done << parts.drop(1)
            break
        case "cozy!":
            def before = snapshot()
            def (code, output) = cozy(parts.drop(1))
            if (code != 2 || snapshot() != before) {
                new File(basedir, "recipes.log").setText(log.toString(), "UTF-8")
                throw new IllegalStateException("실패하고 아무것도 바꾸지 않아야 합니다(종료 코드 " + code + "): " + line + "\n" + output)
            }
            break
        case "hand":
            copyTree(home.resolve("hand").resolve(parts[1]), app)
            log << "\$ hand " << parts[1] << "\n\n"
            break
        case "rerun":
            def before = snapshot()
            def only = parts.drop(1)
            done.findAll { only.isEmpty() || (it[0] == "add" && only.contains(it[1])) }.each { args ->
                def (code, output) = cozy(args)
                if (code != 0 || !output.contains("바뀐 것 없음")) {
                    new File(basedir, "recipes.log").setText(log.toString(), "UTF-8")
                    throw new IllegalStateException("다시 실행하면 바뀐 것이 없어야 합니다: cozy " + args.join(" ") + "\n" + output)
                }
            }
            if (snapshot() != before) {
                throw new IllegalStateException("다시 실행했더니 파일이 바뀌었습니다")
            }
            break
        default:
            throw new IllegalStateException("recipe.txt에서 모르는 줄입니다: " + line)
    }
}
new File(basedir, "recipes.log").setText(log.toString(), "UTF-8")
return true
