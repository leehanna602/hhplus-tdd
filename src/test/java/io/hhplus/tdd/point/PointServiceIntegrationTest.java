package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PointServiceIntegrationTest {

    @Autowired
    private UserPointTable userPointTable;

    @Autowired
    private PointService pointService;

    @Test
    @DisplayName("동시에 포인트 충전/사용 요청 성공")
    void concurrent_chargeAndUsePoint_success() throws InterruptedException, ExecutionException {
        long userId = 1L;
        int threadCount = 10;
        long initialPoint = 1000L;
        long chargeAmount = 100L;
        long useAmount = 50L;

        userPointTable.insertOrUpdate(userId, initialPoint);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount / 2; i++) {
            tasks.add(() -> {
                pointService.transaction(userId, chargeAmount, TransactionType.CHARGE);
                return null;
            });
            tasks.add(() -> {
                pointService.transaction(userId, useAmount, TransactionType.USE);
                return null;
            });
        }

        List<Future<Void>> futures = executorService.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }

        executorService.shutdown();

        UserPoint finalUserPoint = pointService.point(userId);
        long expectedFinalPoint = initialPoint + (chargeAmount - useAmount) * (threadCount / 2);
        List<PointHistory> histories = pointService.history(userId);

        assertEquals(expectedFinalPoint, finalUserPoint.point());
        assertEquals(threadCount, histories.size());
        assertEquals(TransactionType.CHARGE, histories.get(1).type());
        assertEquals(TransactionType.USE, histories.get(2).type());
    }


    @Test
    @DisplayName("동시에 포인트 충전 요청 성공")
    public void concurrent_chargePoint_success() throws InterruptedException, ExecutionException {
        long userId = 1L;
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<UserPoint>> tasks = new ArrayList<>();

        // 각 스레드가 1000 포인트씩 충전
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> pointService.transaction(userId, 1000, TransactionType.CHARGE));
        }

        // 모든 작업을 병렬로 실행
        List<Future<UserPoint>> results = executorService.invokeAll(tasks);

        // 결과 검증
        for (Future<UserPoint> result : results) {
            assertNotNull(result.get());
        }

        // 최종 잔고 검증
        UserPoint finalPoint = pointService.point(userId);
        assertEquals(10000, finalPoint.point());
    }


    @Test
    @DisplayName("동시에 포인트 충전 요청 한도 초과로 실패")
    public void concurrent_chargePoint_exceedingMaximum() throws InterruptedException {
        long userId = 1L;
        int threadCount = 11;
        long chargeAmount = 100000L;
        long maxPointAllowed = 1000000L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<UserPoint>> tasks = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 각 스레드가 1000 포인트씩 충전
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    UserPoint result = pointService.transaction(userId, chargeAmount, TransactionType.CHARGE);
                    successCount.incrementAndGet();
                    return result;
                } catch (IllegalArgumentException e) {
                    failCount.incrementAndGet();
                    return null;
                }
            });
        }

        // 모든 작업을 병렬로 실행
        List<Future<UserPoint>> results = executorService.invokeAll(tasks);
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(1, TimeUnit.MINUTES));

        // 결과 검증
        UserPoint finalPoint = pointService.point(userId);
        assertEquals(maxPointAllowed, finalPoint.point(), "최종 포인트가 최대 한도와 일치해야 합니다.");
        assertEquals(10, successCount.get(), "성공한 충전 횟수가 예상과 다릅니다.");
        assertEquals(1, failCount.get(), "실패한 충전 횟수가 예상과 다릅니다.");

        List<PointHistory> histories = pointService.history(userId);
        assertEquals(10, histories.size(), "포인트 충전 내역 개수가 예상과 다릅니다.");

        for (PointHistory history : histories) {
            assertEquals(TransactionType.CHARGE, history.type(), "모든 내역이 충전이어야 합니다.");
            assertEquals(chargeAmount, history.amount(), "충전 금액이 일치해야 합니다.");
        }
    }


    @Test
    @DisplayName("동시에 포인트 사용 요청 성공")
    public void concurrent_usePoint_success() throws InterruptedException, ExecutionException {
        long userId = 1L;
        int threadCount = 10;

        pointService.transaction(userId, 5000, TransactionType.CHARGE);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<UserPoint>> tasks = new ArrayList<>();

        // 각 스레드가 100 포인트씩 차감
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> pointService.transaction(userId, 100, TransactionType.USE));
        }

        // 모든 작업을 병렬로 실행
        List<Future<UserPoint>> results = executorService.invokeAll(tasks);

        // 결과 검증
        for (Future<UserPoint> result : results) {
            assertNotNull(result.get());
        }

        // 최종 잔고 검증
        UserPoint finalPoint = pointService.point(userId);
        assertEquals(4000, finalPoint.point());
    }


    @Test
    @DisplayName("동시에 포인트 사용 요청시 잔액 부족으로 실패")
    public void concurrent_usePoint_insufficientBalance() throws InterruptedException {
        long userId = 1L;
        int threadCount = 12;
        long initialPoint = 1000L;
        long useAmount = 100L;

        pointService.transaction(userId, initialPoint, TransactionType.CHARGE);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Callable<UserPoint>> tasks = new ArrayList<>();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // 각 스레드가 100 포인트씩 차감
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    UserPoint result = pointService.transaction(userId, useAmount, TransactionType.USE);
                    successCount.incrementAndGet();
                    return result;
                } catch (IllegalArgumentException e) {
                    failCount.incrementAndGet();
                    return null;
                }
            });
        }

        // 모든 작업을 병렬로 실행
        List<Future<UserPoint>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();
        assertTrue(executorService.awaitTermination(1, TimeUnit.MINUTES));

        // 결과 검증
        UserPoint finalPoint = pointService.point(userId);
        long expectedSuccessCount = initialPoint / useAmount;
        long expectedRemainingBalance = initialPoint % useAmount;

        assertEquals(expectedRemainingBalance, finalPoint.point(), "최종 포인트가 예상과 다릅니다.");
        assertEquals(expectedSuccessCount, successCount.get(), "성공한 사용 횟수가 예상과 다릅니다.");
        assertEquals(threadCount - expectedSuccessCount, failCount.get(), "실패한 사용 횟수가 예상과 다릅니다.");

        List<PointHistory> histories = pointService.history(userId);
        assertEquals(expectedSuccessCount + 1, histories.size(), "포인트 사용 내역 개수가 예상과 다릅니다.");

        for (int i = 1; i < histories.size(); i++) {
            PointHistory history = histories.get(i);
            assertEquals(TransactionType.USE, history.type(), "모든 내역이 사용이어야 합니다.");
            assertEquals(useAmount, history.amount(), "사용 금액이 일치해야 합니다.");
        }
    }

}
