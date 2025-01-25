package io.hhplus.tdd.point;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService;

    @Test
    void 동시에_여러_사용자가_포인트를_충전할_수_있다() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    pointService.transaction(userId, 1000, TransactionType.CHARGE);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();
        executorService.shutdown();

        for (int i = 0; i < threadCount; i++) {
            assertEquals(1000, pointService.point(i).point());
        }
    }

    @Test
    void 동일한_사용자의_포인트_충전은_순차적으로_처리한다() throws InterruptedException {
        long userId = 1000L;
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.transaction(userId, 1000, TransactionType.CHARGE);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();
        executorService.shutdown();

        assertEquals(10000, pointService.point(userId).point());
    }

    @Test
    void 동일한_사용자의_포인트_사용을_순차적으로_처리한다() throws InterruptedException {
        long userId = 1001L;
        pointService.transaction(userId, 10000, TransactionType.CHARGE);

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.transaction(userId, 2000, TransactionType.USE);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals(0, pointService.point(userId).point());
    }

    @Test
    void 포인트_사용시_부족할_경우_사용이_불가능하다() throws InterruptedException {
        long userId = 1002L;
        pointService.transaction(userId, 5000, TransactionType.CHARGE);

        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.transaction(userId, 2000, TransactionType.USE);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals(2, successCount.get());
        assertEquals(1000, pointService.point(userId).point());
    }

    @Test
    void 포인트_충전_한도초과시_최대금액까지만_충전된다() throws InterruptedException {
        long userId = 1003L;
        int threadCount = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.transaction(userId, 100000, TransactionType.CHARGE);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        UserPoint finalPoint = pointService.point(userId);
        assertEquals(1000000, finalPoint.point());
    }

    @Test
    void 충전과_사용을_동시에_처리할_수_있다() throws InterruptedException {
        long userId = 1004L;
        int threadCount = 10;
        pointService.transaction(userId, 5000, TransactionType.CHARGE);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger chargeCount = new AtomicInteger(0);
        AtomicInteger useCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final boolean isCharge = i % 2 == 0;
            executorService.submit(() -> {
                try {
                    if (isCharge) {
                        pointService.transaction(userId, 1000, TransactionType.CHARGE);
                        chargeCount.incrementAndGet();
                    } else {
                        pointService.transaction(userId, 500, TransactionType.USE);
                        useCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals(5, chargeCount.get());
        assertEquals(5, useCount.get());
        assertEquals(7500, pointService.point(userId).point());

        List<PointHistory> histories = pointService.history(userId);
        assertEquals(11, histories.size());
    }


}
