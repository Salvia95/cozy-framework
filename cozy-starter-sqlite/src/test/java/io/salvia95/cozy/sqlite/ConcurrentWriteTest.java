package io.salvia95.cozy.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.context.ConfigurableApplicationContext;

import io.salvia95.cozy.sqlite.testapp.SampleMapper;
import io.salvia95.cozy.sqlite.testapp.SampleService;

/**
 * 스레드 8개가 동시에 써도 데이터가 사라지지 않는다.
 */
class ConcurrentWriteTest {

	static final int THREADS = 8;

	static final int WRITES_PER_THREAD = 250;

	static ConfigurableApplicationContext context;

	@BeforeAll
	static void start() {
		context = SqliteTestSupport.start(SqliteTestSupport.newDataDir("concurrent"));
	}

	@AfterAll
	static void stop() {
		context.close();
	}

	@Test
	void eightWritersWithWriteOnlyTransactions() throws Exception {
		SampleService service = context.getBean(SampleService.class);
		assertNoLoss("write-only", (thread, i) -> service.insert("w-" + thread + "-" + i));
	}

	@Test
	void eightWritersWithReadThenWriteTransactions() throws Exception {
		SampleService service = context.getBean(SampleService.class);
		assertNoLoss("read-then-write", (thread, i) -> service.countThenInsert("rw-" + thread + "-" + i));
	}

	private void assertNoLoss(String label, BiConsumer<Integer, Integer> write) throws Exception {
		SampleMapper mapper = context.getBean(SampleMapper.class);
		long before = mapper.count();
		ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
		CountDownLatch go = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
			for (int t = 0; t < THREADS; t++) {
				int thread = t;
				futures.add(pool.submit((Callable<Void>) () -> {
					go.await();
					for (int i = 0; i < WRITES_PER_THREAD; i++) {
						try {
							write.accept(thread, i);
						}
						catch (RuntimeException ex) {
							failures.add(ex);
						}
					}
					return null;
				}));
			}
			go.countDown();
			for (Future<?> future : futures) {
				future.get();
			}
		}
		assertThat(failures).as("%s: 쓰기 실패 %d건, 첫 오류: %s", label, failures.size(),
				failures.isEmpty() ? "-" : failures.peek().toString()).isEmpty();
		assertThat(mapper.count() - before).isEqualTo((long) THREADS * WRITES_PER_THREAD);
	}
}
