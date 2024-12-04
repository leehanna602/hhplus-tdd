package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    private PointService pointService;

    @BeforeEach
    void setUp() {
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    @DisplayName("특정 유저의 UserId로 포인트 조회 성공")
    void findPoint_byUserId_success() {
        UserPoint expectedUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        when(userPointTable.selectById(1L)).thenReturn(expectedUserPoint);

        UserPoint result = pointService.point(1L);

        assertEquals(expectedUserPoint, result);
        verify(userPointTable).selectById(1L);
    }

    @Test
    @DisplayName("특정 유저의 UserId로 포인트 충전/이용 내역 조회 성공")
    void findHistory_byUserId_success() {
        List<PointHistory> expectedPointHistoryList = Arrays.asList(
                new PointHistory(1L, 1L, 50L, TransactionType.CHARGE, System.currentTimeMillis()),
                new PointHistory(2L, 1L, 30L, TransactionType.USE, System.currentTimeMillis())
        );
        when(pointHistoryTable.selectAllByUserId(1L)).thenReturn(expectedPointHistoryList);

        List<PointHistory> result = pointService.history(1L);

        assertEquals(expectedPointHistoryList, result);
        verify(pointHistoryTable).selectAllByUserId(1L);
    }

    @Test
    @DisplayName("특정 유저의 포인트 충전 후 이력 저장 성공")
    void transaction_charge_success() {
        UserPoint initialUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        UserPoint expectedUserPoint = new UserPoint(1L, 150L, System.currentTimeMillis());
        when(userPointTable.selectById(1L)).thenReturn(initialUserPoint);
        when(userPointTable.insertOrUpdate(1L, 150)).thenReturn(expectedUserPoint);

        UserPoint result = pointService.transaction(1L, 50L, TransactionType.CHARGE);

        assertEquals(expectedUserPoint, result);
        verify(userPointTable).selectById(1L);
        verify(userPointTable).insertOrUpdate(1L, 150);
        verify(pointHistoryTable).insert(eq(1L), eq(50L), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("특정 유저의 포인트 사용 후 이력 저장 성공")
    void transaction_use_success() {
        UserPoint initialUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        UserPoint expectedUserPoint = new UserPoint(1L, 70L, System.currentTimeMillis());
        when(userPointTable.selectById(1L)).thenReturn(initialUserPoint);
        when(userPointTable.insertOrUpdate(1L, 70L)).thenReturn(expectedUserPoint);

        UserPoint result = pointService.transaction(1L, 30L, TransactionType.USE);

        assertEquals(expectedUserPoint, result);
        verify(userPointTable).selectById(1L);
        verify(userPointTable).insertOrUpdate(1L, 70);
        verify(pointHistoryTable).insert(eq(1L), eq(30L), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("특정 유저의 포인트 사용시 잔액 부족으로 에러발생")
    void transaction_use_insufficientBalance() {
        UserPoint initialUserPoint = new UserPoint(1L, 50L, System.currentTimeMillis());
        when(userPointTable.selectById(1L)).thenReturn(initialUserPoint);

        assertThrows(IllegalArgumentException.class, () -> pointService.transaction(1L, 100L, TransactionType.USE));
    }

    @Test
    @DisplayName("특정 유저의 포인트 충전시 충전 가능한 최대 포인트가 초과되어 에러 발생")
    void transaction_charge_exceedingMaximum() {
        UserPoint initialUserPoint = new UserPoint(1L, 100L, System.currentTimeMillis());
        when(userPointTable.selectById(1L)).thenReturn(initialUserPoint);

        assertThrows(IllegalArgumentException.class, () -> pointService.transaction(1L, 3000000L, TransactionType.CHARGE));
    }


}