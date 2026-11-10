package io.salvia95.cozy.cli.internal.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.rules.SlotRules;
import io.salvia95.cozy.rules.Violation;

/**
 * 레시피가 바꿀 파일 전체의 계획. 계획을 먼저 세우고, 하나라도 충돌하면 아무 파일도 쓰지 않는다.
 *
 * <ul>
 * <li>만들 파일: 없으면 만든다. 같은 내용이 이미 있으면 그대로 둔다. 다른 내용이 있으면 덮어쓰지 않고 실패한다.</li>
 * <li>바꿀 파일: 계획을 세울 때 읽은 내용이 그대로일 때만 바꾼다. 바꾼 결과가 지금과 같으면 그대로 둔다.</li>
 * <li>cozy가 통째로 가지는 파일(에이전트 스킬): 없으면 만들고 다르면 바꾸고, 남는 파일은 지운다.</li>
 * </ul>
 */
public final class Plan {

	/** 바뀜 하나 */
	public record Change(Action action, String file, String detail, String content) {
	}

	public enum Action {

		/** 새 파일 */
		CREATE("만듦"),

		/** 있는 파일에 덧붙임 */
		EDIT("바꿈"),

		/** 남는 파일을 지움 */
		DELETE("지움"),

		/** 이미 같은 내용이 있거나, 다르지만 바꾸지 않아 그대로 둠 */
		UNCHANGED("그대로");

		private final String label;

		Action(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	private final Path project;

	private final Map<String, Change> changes = new LinkedHashMap<>();

	private final Map<String, String> readContents = new LinkedHashMap<>();

	private boolean keptDifferent;

	public Plan(Path project) {
		this.project = project;
	}

	public Path project() {
		return this.project;
	}

	/** 프로젝트 기준 파일의 지금 내용. 없으면 {@code null}. 같은 계획 안에서 먼저 계획한 내용이 있으면 그것을 돌려준다 */
	public String current(String file) {
		Change planned = this.changes.get(file);
		if (planned != null && planned.content() != null) {
			return planned.content();
		}
		Path path = this.project.resolve(file);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			this.readContents.putIfAbsent(file, text);
			return text;
		}
		catch (IOException ex) {
			throw new CliException(file + "를 읽지 못했습니다: " + ex.getMessage());
		}
	}

	public boolean exists(String file) {
		return this.changes.containsKey(file) || Files.exists(this.project.resolve(file));
	}

	/** 새 파일. 이미 다른 내용으로 있으면 실패한다 */
	public Plan create(String file, String content) {
		String existing = current(file);
		if (existing != null) {
			if (!existing.equals(content)) {
				throw new CliException(file + "가 이미 있고 내용이 다릅니다. 덮어쓰지 않습니다. 이 파일을 지우거나 다른 이름을 쓰세요.");
			}
			this.changes.putIfAbsent(file, new Change(Action.UNCHANGED, file, "이미 같은 내용", null));
			return this;
		}
		this.changes.put(file, new Change(Action.CREATE, file, null, content));
		return this;
	}

	/** 있는 파일을 바꾼다. 바뀐 게 없으면 그대로 둔다 */
	public Plan edit(String file, String content, String detail) {
		String existing = current(file);
		if (existing == null) {
			throw new CliException(file + "가 없습니다.");
		}
		if (existing.equals(content)) {
			this.changes.putIfAbsent(file, new Change(Action.UNCHANGED, file, "이미 " + detail, null));
			return this;
		}
		Change planned = this.changes.get(file);
		Action action = (planned != null && planned.action() == Action.CREATE) ? Action.CREATE : Action.EDIT;
		String merged = (planned != null && planned.detail() != null && action == Action.EDIT) ? planned.detail() + "; " + detail
				: detail;
		this.changes.put(file, new Change(action, file, merged, content));
		return this;
	}

	/** 없으면 만들고, 다른 내용이면 바꾼다 */
	public Plan replace(String file, String content, String detail) {
		String existing = current(file);
		return (existing == null || existing.equals(content)) ? create(file, content) : edit(file, content, detail);
	}

	/** 있는 파일을 지운다 */
	public Plan delete(String file, String detail) {
		if (current(file) == null) {
			throw new CliException(file + "가 없습니다.");
		}
		this.changes.put(file, new Change(Action.DELETE, file, detail, null));
		return this;
	}

	/** 다른 내용이지만 바꾸지 않고 둔다 */
	public Plan keep(String file, String detail) {
		this.changes.put(file, new Change(Action.UNCHANGED, file, detail, null));
		this.keptDifferent = true;
		return this;
	}

	/** 다른 내용인데 그대로 둔 파일이 있는가 */
	public boolean keptDifferent() {
		return this.keptDifferent;
	}

	public List<Change> changes() {
		return List.copyOf(this.changes.values());
	}

	public boolean changesNothing() {
		return this.changes.values().stream().allMatch((c) -> c.action() == Action.UNCHANGED);
	}

	/** 바뀔 파일에 남는 빈칸 */
	public List<Violation> slots() {
		List<Violation> slots = new ArrayList<>();
		for (Change change : this.changes.values()) {
			if (change.action() == Action.DELETE) {
				continue;
			}
			String content = (change.content() != null) ? change.content() : current(change.file());
			if (content != null) {
				slots.addAll(SlotRules.scan(change.file(), content));
			}
		}
		return slots;
	}

	/**
	 * 계획대로 쓴다. 읽은 뒤에 바뀐 파일이 있으면 아무것도 쓰지 않고 실패한다. 쓰다가 실패하면 이미 쓴 파일을 되돌린다.
	 */
	public void apply() {
		for (Map.Entry<String, String> read : this.readContents.entrySet()) {
			Path path = this.project.resolve(read.getKey());
			try {
				if (!Files.isRegularFile(path) || !Files.readString(path, StandardCharsets.UTF_8).equals(read.getValue())) {
					throw new CliException(read.getKey() + "가 계획을 세우는 동안 바뀌었습니다. 다시 실행하세요.");
				}
			}
			catch (IOException ex) {
				throw new CliException(read.getKey() + "를 읽지 못했습니다: " + ex.getMessage());
			}
		}
		Map<Path, String> backups = new LinkedHashMap<>();
		List<Path> created = new ArrayList<>();
		try {
			for (Change change : this.changes.values()) {
				if (change.action() == Action.UNCHANGED) {
					continue;
				}
				Path path = this.project.resolve(change.file());
				if (Files.exists(path)) {
					backups.put(path, Files.readString(path, StandardCharsets.UTF_8));
				}
				else {
					created.add(path);
				}
				if (change.action() == Action.DELETE) {
					Files.delete(path);
					deleteIfEmpty(path.getParent());
					continue;
				}
				Files.createDirectories(path.getParent());
				Path temp = path.resolveSibling(path.getFileName() + ".cozy-tmp");
				Files.writeString(temp, change.content(), StandardCharsets.UTF_8);
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
		}
		catch (IOException ex) {
			for (Path path : created) {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ignored) {
					// 되돌리기는 최선만 한다
				}
			}
			backups.forEach((path, text) -> {
				try {
					Files.createDirectories(path.getParent());
					Files.writeString(path, text, StandardCharsets.UTF_8);
				}
				catch (IOException ignored) {
					// 되돌리기는 최선만 한다
				}
			});
			throw new CliException("파일을 쓰지 못해 되돌렸습니다: " + ex.getMessage());
		}
	}

	/** 파일을 지워 비게 된 폴더 하나를 지운다 */
	private static void deleteIfEmpty(Path dir) throws IOException {
		try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
			if (entries.findAny().isPresent()) {
				return;
			}
		}
		Files.delete(dir);
	}
}
