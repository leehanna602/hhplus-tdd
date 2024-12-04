package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final Lock lock = new ReentrantLock();
    private static final long maxPoint = 1000000;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint point(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> history(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint transaction(long id, long amount, TransactionType type) {
        lock.lock();
        try {
            UserPoint userPoint = userPointTable.selectById(id);
            long currentAmount = userPoint.point();
            validateTransaction(currentAmount, amount, type);

            long newAmount = calculateNewAmount(currentAmount, amount, type);
            userPoint = userPointTable.insertOrUpdate(id, newAmount);
            pointHistoryTable.insert(id, amount, type, System.currentTimeMillis());
            return userPoint;

        } finally {
            lock.unlock();
        }
    }

    private void validateTransaction(long currentAmount, long amount, TransactionType type) {
        switch (type) {
            case CHARGE:
                if (currentAmount + amount > maxPoint) {
                    throw new IllegalArgumentException("충전 가능한 총액을 초과하였습니다.");
                }
                break;
            case USE:
                if (currentAmount < amount) {
                    throw new IllegalArgumentException("사용 가능한 포인트가 부족합니다.");
                }
                break;
            default:
                throw new IllegalArgumentException("유효하지 않은 타입입니다.");
        }
    }

    private long calculateNewAmount(long currentAmount, long amount, TransactionType type) {
        return switch (type) {
            case CHARGE -> currentAmount + amount;
            case USE -> currentAmount - amount;
        };
    }

}
