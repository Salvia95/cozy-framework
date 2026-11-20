package io.salvia95.cozy.cli.internal.channel;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.salvia95.cozy.cli.internal.CliException;
import io.salvia95.cozy.cli.internal.Json;
import io.salvia95.cozy.cli.internal.channel.ChannelIndex.Release;

/**
 * {@code cozy upgrade --check}(별칭 {@code cozy update}): 채널 색인을 읽어 지금 판과 새 판을 비교해 보인다. 아무것도 바꾸지 않는다.
 * 그리고 다른 cozy 명령 뒤에 하루에 한 번 하는 새 판 알림.
 */
public final class UpdateCommands {

	/** 마지막으로 확인한 날. 사용자 설정 파일 옆에 둔다 */
	public static final String STATE_FILE = "update-check.properties";

	private UpdateCommands() {
	}

	/**
	 * 새 판을 비교한 결과.
	 *
	 * @param current 지금 판
	 * @param latest 채널의 최신 판. 채널에 릴리스가 없으면 {@code null}
	 * @param newer 지금 판보다 새 판. 새 판부터
	 * @param upgrade {@code cozy upgrade}가 올릴 판(주 버전이 같은 가장 새 판). 없으면 {@code null}
	 */
	public record Plan(Version current, Version latest, List<Release> newer, Version upgrade) {

		public static Plan of(Version current, ChannelIndex index) {
			List<Release> newer = index.releases().stream().filter(release -> release.version().compareTo(current) > 0).toList();
			Version upgrade = newer.stream()
				.map(Release::version)
				.filter(version -> version.major() == current.major())
				.findFirst()
				.orElse(null);
			Version latest = index.releases().isEmpty() ? null : index.releases().getFirst().version();
			return new Plan(current, latest, newer, upgrade);
		}

		/** 주 버전이 바뀌어 {@code --to}로 적어야 올리는 최신 판. 없으면 {@code null} */
		public Version breakingLatest() {
			return (this.latest != null && this.latest.major() > this.current.major()) ? this.latest : null;
		}
	}

	/**
	 * @param to 적은 판({@code --to}). 있으면 그 판까지의 새 판과 그 판으로 올리는 명령을 보인다. 낮은 판이나 채널에 없는 판은
	 * {@code cozy upgrade}처럼 거절한다
	 */
	public static void check(Version to, Map<String, String> env, boolean windows, LocalDate today, boolean json,
			ChannelClient client, PrintStream out) {
		Path config = UserConfig.file(env, windows);
		ChannelSettings settings = settings(env, config);
		Version current = Installed.require(env).release();
		ChannelIndex index = client.index(settings);
		Plan plan = Plan.of(current, index);
		if (to != null) {
			if (to.equals(current)) {
				out.println("[cozy] 지금 판입니다: " + current + ".");
				return;
			}
			Release target = UpgradeCommands.target(current, to, index, out);
			List<Release> upTo = plan.newer().stream().filter((release) -> release.version().compareTo(target.version()) <= 0).toList();
			plan = new Plan(current, plan.latest(), upTo, plan.upgrade());
		}
		try {
			record(config, today, plan.latest());
		}
		catch (CliException ex) {
			// 확인한 날을 적지 못하면 다음 명령의 알림이 한 번 더 확인할 뿐이다
		}
		if (json) {
			out.print(Json.write(json(plan, settings, (to != null) ? to : plan.upgrade())));
			return;
		}
		out.println("지금 판:  " + current);
		out.println("최신 판:  " + ((plan.latest() == null) ? "없음" : plan.latest()));
		out.println("채널:     " + settings.address());
		if (plan.latest() == null) {
			out.println("[cozy] 채널에 릴리스가 없습니다. 관리자가 cozy channel add로 릴리스를 더합니다.");
			return;
		}
		if (plan.newer().isEmpty()) {
			if (plan.latest().compareTo(current) < 0) {
				out.println("[cozy] 채널의 최신 판(" + plan.latest() + ")이 지금 판(" + current + ")보다 낮습니다. 낮은 판으로는 올리지 않습니다. "
						+ "채널 주소가 맞는지 cozy channel show로 확인합니다.");
			}
			else {
				out.println("[cozy] 최신 판입니다.");
			}
			return;
		}
		out.println();
		out.println("새 판 " + plan.newer().size() + "개");
		releaseLines(plan.newer(), out);
		out.println();
		if (to != null) {
			out.println("[cozy] 이 판으로 올리는 명령: cozy upgrade --to " + to);
			return;
		}
		if (plan.upgrade() != null) {
			out.println("[cozy] cozy upgrade로 " + plan.upgrade() + "까지 올립니다.");
		}
		if (plan.breakingLatest() != null) {
			out.println("[cozy] 호환이 깨지는 판(" + plan.breakingLatest() + ")은 앱에서 돌릴 명령을 보고 준비가 되면 판을 적어 올립니다: "
					+ "cozy upgrade --to " + plan.breakingLatest());
		}
	}

	/** 정한 채널. 정하지 않았으면 판단할 수 없으므로 실패한다 */
	static ChannelSettings settings(Map<String, String> env, Path config) {
		ChannelSettings settings = ChannelSettings.resolve(env, config);
		if (!settings.configured()) {
			throw new CliException("채널을 정하지 않았습니다. 업데이트를 받을 곳을 cozy channel set <주소>로 정합니다(파일 서버, 웹 서버, "
					+ "GitLab, GitHub의 " + ChannelIndex.FILE_NAME + "). 설정 파일: " + config);
		}
		return settings;
	}

	/** 판마다 한 줄(판, 날짜, 호환 깨짐, 요약)과 앱에서 돌릴 명령 */
	static void releaseLines(List<Release> releases, PrintStream out) {
		for (Release release : releases) {
			out.println("  " + release.version() + " (" + release.date() + ")  " + (release.breaking() ? "[호환 깨짐] " : "")
					+ release.summary());
			if (!release.appSteps().isEmpty()) {
				out.println("      앱에서 돌릴 명령: " + String.join(", ", release.appSteps()));
			}
		}
	}

	private static Map<String, Object> json(Plan plan, ChannelSettings settings, Version target) {
		List<Object> releases = new ArrayList<>();
		for (Release release : plan.newer()) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("version", release.version().toString());
			map.put("date", release.date().toString());
			map.put("breaking", release.breaking());
			map.put("summary", release.summary());
			map.put("appSteps", release.appSteps());
			releases.add(map);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("current", plan.current().toString());
		root.put("latest", (plan.latest() == null) ? null : plan.latest().toString());
		root.put("channel", settings.address());
		root.put("upToDate", plan.newer().isEmpty());
		root.put("upgrade", (plan.upgrade() == null) ? null : plan.upgrade().toString());
		root.put("breaking", (plan.breakingLatest() == null) ? null : plan.breakingLatest().toString());
		root.put("target", (target == null) ? null : target.toString());
		root.put("releases", releases);
		return root;
	}

	/**
	 * 하루에 한 번 채널을 확인하고 새 판이 있으면 한 줄을 보인다. 채널을 정하지 않았거나 지금 판이 릴리스 판이 아니면 확인하지 않는다. 확인이
	 * 실패하거나 {@link ChannelClient#requestTimeout()} 안에 끝나지 않아도 아무것도 보이지 않고, 다음 날 다시 확인한다.
	 *
	 * @param clients 확인할 때만 만든다(Windows는 인증서 저장소를 읽는다)
	 */
	public static void notice(Map<String, String> env, boolean windows, LocalDate today, Supplier<ChannelClient> clients,
			PrintStream err) {
		try {
			Path config = UserConfig.file(env, windows);
			ChannelSettings settings = ChannelSettings.resolve(env, config);
			Installed installed = Installed.find(env);
			Version current = (installed == null) ? null : installed.release();
			if (!settings.configured() || current == null) {
				return;
			}
			Path stateFile = config.resolveSibling(STATE_FILE);
			if (today.toString().equals(UserConfig.read(stateFile).getProperty("checked"))) {
				return;
			}
			record(config, today, null);
			ChannelClient client = clients.get();
			CompletableFuture<ChannelIndex> fetch = new CompletableFuture<>();
			Thread thread = new Thread(() -> {
				try {
					fetch.complete(client.index(settings));
				}
				catch (Throwable ex) {
					fetch.completeExceptionally(ex);
				}
			}, "cozy-update-check");
			thread.setDaemon(true);
			thread.start();
			Plan plan = Plan.of(current, fetch.get(client.requestTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS));
			record(config, today, plan.latest());
			if (plan.upgrade() != null) {
				err.println("[cozy] 새 버전이 있습니다: " + plan.upgrade() + ". cozy upgrade로 올립니다 (바뀌는 것: cozy upgrade --check)");
			}
			else if (plan.breakingLatest() != null) {
				err.println("[cozy] 호환이 깨지는 새 버전이 있습니다: " + plan.breakingLatest() + ". 바뀌는 것과 앱에서 돌릴 명령은 cozy upgrade "
						+ "--check로 봅니다");
			}
		}
		catch (Exception ex) {
			// 알림은 명령의 결과를 바꾸지 않는다. 확인하지 못한 것은 다음 날 다시 확인한다
		}
	}

	private static void record(Path config, LocalDate today, Version latest) {
		Path stateFile = config.resolveSibling(STATE_FILE);
		Properties state = UserConfig.read(stateFile);
		state.setProperty("checked", today.toString());
		if (latest != null) {
			state.setProperty("latest", latest.toString());
		}
		UserConfig.write(stateFile, state, "cozy upgrade --check와 새 판 알림이 마지막으로 채널을 확인한 날");
	}
}
