package io.salvia95.cozy.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteConnection;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.salvia95.cozy.sqlite.internal.CozySqliteTransactionManager;
import io.salvia95.cozy.sqlite.testapp.SampleMapper;

/**
 * 쓰기 트랜잭션은 IMMEDIATE, 읽기 전용은 DEFERRED.
 */
class TransactionModeTest {

	static ConfigurableApplicationContext context;

	@BeforeAll
	static void start() {
		context = SqliteTestSupport.start(SqliteTestSupport.newDataDir("txmode"),
				"spring.datasource.hikari.data-source-properties[busy_timeout]=1000");
	}

	@AfterAll
	static void stop() {
		context.close();
	}

	@Test
	void cozyTransactionManagerIsUsed() {
		assertThat(context.getBean(PlatformTransactionManager.class)).isInstanceOf(CozySqliteTransactionManager.class);
	}

	@Test
	void writeTransactionStartsImmediateAndConnectionIsResetAfterwards() {
		AtomicReference<SQLiteConnection> used = new AtomicReference<>();
		AtomicReference<TransactionMode> during = new AtomicReference<>();
		write().executeWithoutResult((status) -> {
			SQLiteConnection sqlite = currentSqliteConnection();
			used.set(sqlite);
			during.set(sqlite.getCurrentTransactionMode());
			context.getBean(SampleMapper.class).insertName("immediate");
		});
		assertThat(during.get()).isEqualTo(TransactionMode.IMMEDIATE);
		assertThat(used.get().getConnectionConfig().getTransactionMode()).isEqualTo(TransactionMode.DEFERRED);

		AtomicReference<TransactionMode> readOnlyMode = new AtomicReference<>();
		readOnly().executeWithoutResult((status) -> readOnlyMode.set(currentSqliteConnection().getCurrentTransactionMode()));
		assertThat(readOnlyMode.get()).isEqualTo(TransactionMode.DEFERRED);
	}

	@Test
	void writeTransactionsQueueInsteadOfFailing() throws Exception {
		SampleMapper mapper = context.getBean(SampleMapper.class);
		CountDownLatch holding = new CountDownLatch(1);
		CompletableFuture<Void> first = CompletableFuture.runAsync(() -> write().executeWithoutResult((status) -> {
			mapper.count();
			holding.countDown();
			sleep(Duration.ofMillis(400));
			mapper.insertName("first");
		}));
		assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
		long started = System.nanoTime();
		write().executeWithoutResult((status) -> {
			mapper.count();
			mapper.insertName("second");
		});
		first.get(5, TimeUnit.SECONDS);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).as("두 번째 쓰기 트랜잭션은 첫 번째가 끝날 때까지 기다린다")
			.isGreaterThan(Duration.ofMillis(200));
	}

	@Test
	void readOnlyTransactionDoesNotBlockWriters() throws Exception {
		SampleMapper mapper = context.getBean(SampleMapper.class);
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> readOnly().executeWithoutResult((status) -> {
			mapper.count();
			reading.countDown();
			await(release);
		}));
		assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
		long started = System.nanoTime();
		write().executeWithoutResult((status) -> mapper.insertName("while-reading"));
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
		release.countDown();
		reader.get(5, TimeUnit.SECONDS);
	}

	@Test
	void writeTransactionThatCannotGetTheLockFailsWithBusyException() throws Exception {
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> write().executeWithoutResult((status) -> {
			holding.countDown();
			await(release);
		}));
		assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
		try {
			assertThatThrownBy(() -> write().executeWithoutResult((status) -> {
			})).isInstanceOf(CozyDatabaseBusyException.class);
		}
		finally {
			release.countDown();
			holder.get(5, TimeUnit.SECONDS);
		}
		// 실패한 연결이 풀에 돌아가도 이후 트랜잭션이 정상이어야 한다 (풀의 모든 연결을 여러 번 돈다)
		SampleMapper mapper = context.getBean(SampleMapper.class);
		for (int i = 0; i < 12; i++) {
			int n = i;
			write().executeWithoutResult((status) -> mapper.insertName("after-busy-" + n));
			readOnly().executeWithoutResult((status) -> mapper.count());
		}
	}

	private static TransactionTemplate write() {
		return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
	}

	private static TransactionTemplate readOnly() {
		TransactionTemplate template = write();
		template.setReadOnly(true);
		return template;
	}

	private static SQLiteConnection currentSqliteConnection() {
		DataSource dataSource = context.getBean(DataSource.class);
		Connection connection = DataSourceUtils.getConnection(dataSource);
		try {
			return connection.unwrap(SQLiteConnection.class);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
