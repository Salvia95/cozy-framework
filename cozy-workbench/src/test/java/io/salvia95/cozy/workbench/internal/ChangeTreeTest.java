package io.salvia95.cozy.workbench.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 변경 파일을 모듈과 계층으로 */
class ChangeTreeTest {

	static WorkbenchEngine.Change create(String file) {
		return new WorkbenchEngine.Change("create", file, null);
	}

	static final String JAVA = "src/main/java/io/x/";

	@Test
	void anEventInTheEventsModuleKeepsItsSampleNextToIt() {
		List<WorkbenchEngine.Change> plan = List.of(create(JAVA + "events/package-info.java"),
				create(JAVA + "events/spi/task/package-info.java"), create(JAVA + "events/spi/task/TaskCreated.java"),
				create("src/test/resources/events/TaskCreated/v1.json"),
				new WorkbenchEngine.Change("edit", JAVA + "task/package-info.java", "allowedDependencies += \"events :: spi\""));
		List<ChangeTree.Module> tree = ChangeTree.of(plan, "io.x", Map.of());
		assertThat(tree).extracting(ChangeTree.Module::label).containsExactly("events 모듈", "task 모듈");
		assertThat(tree.getFirst().layers().stream().flatMap((l) -> l.files().stream()).map(ChangeTree.File::name))
			.as("이벤트와 샘플이 같은 모듈에 모인다").containsExactlyInAnyOrder("package-info.java", "package-info.java", "TaskCreated.java", "v1.json");
	}

	@Test
	void filesAreGroupedByModuleAndLayer() {
		List<WorkbenchEngine.Change> plan = List.of(create(JAVA + "task/spi/TaskFacade.java"),
				create(JAVA + "task/spi/dto/TaskItem.java"), create(JAVA + "task/internal/domain/Task.java"),
				create(JAVA + "task/internal/config/TaskConfiguration.java"), create(JAVA + "task/api/TaskController.java"),
				create(JAVA + "task/api/dto/TaskForm.java"), create(JAVA + "task/spi/event/TaskCreated.java"),
				create("src/test/resources/events/TaskCreated/v1.json"), create("src/main/resources/mapper/task/TaskMapper.xml"),
				create("src/main/resources/db/migration/V202611121042__task_create_task.sql"),
				create("src/main/resources/templates/task/api/list.html"), create("src/main/resources/templates/task/internal/fields.html"),
				create("src/main/resources/specs/task-list.yml"), create("src/test/java/io/x/task/TaskCrudTest.java"),
				new WorkbenchEngine.Change("unchanged", JAVA + "memo/package-info.java", "이미 있음"),
				new WorkbenchEngine.Change("edit", "src/main/resources/cozy/fragments.yml", "fragments += memo/pinned"),
				create("src/main/resources/templates/memo/spi/pinned.html"), new WorkbenchEngine.Change("edit", "pom.xml", null),
				create("src/test/resources/events/MemoPinned/v1.json"));
		List<ChangeTree.Module> tree = ChangeTree.of(plan, "io.x", Map.of("MemoPinned", "memo"));

		assertThat(tree).extracting(ChangeTree.Module::label).as("처음 나온 순서, 앱 공통은 끝").containsExactly("task 모듈", "memo 모듈", "앱 공통");
		ChangeTree.Module task = tree.getFirst();
		assertThat(task.layers()).extracting(ChangeTree.Layer::label).as("계층은 정한 순서")
			.containsExactly("공개 API", "내부 구현", "화면", "SQL", "화면 명세", "테스트");
		assertThat(files(task, "공개 API")).containsExactly("TaskFacade.java spi", "TaskItem.java spi/dto", "TaskCreated.java spi/event");
		assertThat(files(task, "내부 구현")).containsExactly("Task.java internal/domain", "TaskConfiguration.java internal/config");
		assertThat(files(task, "화면")).containsExactly("TaskController.java api", "TaskForm.java api/dto", "list.html templates/task/api",
				"fields.html templates/task/internal");
		assertThat(files(task, "SQL")).containsExactly("TaskMapper.xml mapper/task", "V202611121042__task_create_task.sql db/migration");
		assertThat(files(task, "화면 명세")).containsExactly("task-list.yml specs");
		assertThat(files(task, "테스트")).as("이벤트 샘플은 계획의 이벤트가 있는 모듈로")
			.containsExactly("v1.json events/TaskCreated", "TaskCrudTest.java ");
		assertThat(task.summary()).isEqualTo("생성 14");

		ChangeTree.Module memo = tree.get(1);
		assertThat(memo.layers()).extracting(ChangeTree.Layer::label).containsExactly("공개 API", "테스트", "설정");
		assertThat(files(memo, "설정")).containsExactly("package-info.java ");
		assertThat(memo.summary()).isEqualTo("생성 2 · 변경 없음 1");
		ChangeTree.Module app = tree.get(2);
		assertThat(app.module()).isEmpty();
		assertThat(files(app, "공개 API")).containsExactly("fragments.yml src/main/resources/cozy");
		assertThat(files(app, "설정")).containsExactly("pom.xml ");
	}

	@Test
	void theBehaviorSpecAndItsScriptBelongToTheirModule() {
		List<WorkbenchEngine.Change> plan = List.of(new WorkbenchEngine.Change("edit", "src/main/resources/behaviors/memo.yml", "계산 under"),
				new WorkbenchEngine.Change("edit", "src/main/resources/static/memo/memo.js", "동작 명세에서 만듦"),
				new WorkbenchEngine.Change("edit", "src/main/resources/templates/memo/internal/fields.html", "붙임"),
				new WorkbenchEngine.Change("edit", "src/main/resources/static/memo/logo.svg", null));
		List<ChangeTree.Module> tree = ChangeTree.of(plan, "io.x", Map.of());

		assertThat(tree).extracting(ChangeTree.Module::label).as("모듈의 static 안 다른 파일만 앱 공통").containsExactly("memo 모듈", "앱 공통");
		assertThat(tree.getFirst().layers()).extracting(ChangeTree.Layer::label).containsExactly("화면", "화면 안의 동작");
		assertThat(files(tree.getFirst(), "화면 안의 동작")).containsExactly("memo.yml behaviors", "memo.js static/memo");
	}

	static List<String> files(ChangeTree.Module module, String layer) {
		return module.layers()
			.stream()
			.filter((l) -> l.label().equals(layer))
			.findFirst()
			.orElseThrow()
			.files()
			.stream()
			.map((f) -> f.name() + " " + f.folder())
			.toList();
	}
}
