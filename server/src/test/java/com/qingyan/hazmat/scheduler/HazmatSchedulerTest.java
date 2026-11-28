package com.qingyan.hazmat.scheduler;

import com.qingyan.hazmat.security.LoginAttemptService;
import com.qingyan.hazmat.security.RateLimitFilter;
import com.qingyan.hazmat.system.NotifyService;
import com.qingyan.hazmat.system.mapper.OperLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务单测：效期/超期未入库扫描把命中记录投递为站内信（幂等去重由 NotifyService 保证），
 * 且**任何异常都不得抛出**（定时任务失败不能影响主流程）。
 */
class HazmatSchedulerTest {

    private JdbcTemplate jdbc;
    private NotifyService notifyService;
    private OperLogMapper operLogMapper;
    private RateLimitFilter rateLimitFilter;
    private LoginAttemptService loginAttemptService;
    private HazmatScheduler scheduler;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        notifyService = Mockito.mock(NotifyService.class);
        operLogMapper = Mockito.mock(OperLogMapper.class);
        rateLimitFilter = Mockito.mock(RateLimitFilter.class);
        loginAttemptService = Mockito.mock(LoginAttemptService.class);
        scheduler = new HazmatScheduler(jdbc, notifyService, operLogMapper, rateLimitFilter, loginAttemptService);
        ReflectionTestUtils.setField(scheduler, "expiryWarnDays", 30);
        ReflectionTestUtils.setField(scheduler, "approvedNotStockedDays", 7);
    }

    private Map<String, Object> row(long id, String lotNo) {
        return new java.util.LinkedHashMap<>(Map.of(
                "id", id, "lot_no", lotNo, "expiry_date", "2026-06-08", "chem_name", "浓硫酸"));
    }

    @Test
    void 效期扫描_过期与临近分别推送并写操作日志() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row(1L, "LOT-EXPIRED")), List.of(row(2L, "LOT-NEAR")));
        when(notifyService.push(isNull(), eq("expiry"), anyString(), anyString(),
                eq("chem_batch"), anyLong(), anyInt())).thenReturn(true);

        scheduler.scanExpiry();

        verify(notifyService, times(2)).push(isNull(), eq("expiry"), anyString(), anyString(),
                eq("chem_batch"), anyLong(), eq(12));
        verify(notifyService).push(isNull(), eq("expiry"), org.mockito.ArgumentMatchers.contains("已过期"),
                anyString(), eq("chem_batch"), eq(1L), eq(12));
        verify(notifyService).push(isNull(), eq("expiry"), org.mockito.ArgumentMatchers.contains("效期临近"),
                anyString(), eq("chem_batch"), eq(2L), eq(12));
        verify(operLogMapper, atLeastOnce()).insert(any());
    }

    @Test
    void 效期扫描_无命中时不写日志() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(), List.of());
        scheduler.scanExpiry();
        verify(notifyService, never()).push(any(), anyString(), anyString(), anyString(), any(), any(), anyInt());
        verify(operLogMapper, never()).insert(any());
    }

    @Test
    void 超期未入库扫描_命中即推送() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                new java.util.LinkedHashMap<>(Map.of(
                        "id", 9L, "code", "AP-20260901-001",
                        "chemical_name", "丙酮", "audit_time", "2026-09-01 10:00:00"))));
        when(notifyService.push(isNull(), eq("stockin"), anyString(), anyString(),
                eq("purchase_req"), eq(9L), eq(12))).thenReturn(true);

        scheduler.scanApprovedNotStockedIn();

        verify(notifyService).push(isNull(), eq("stockin"),
                org.mockito.ArgumentMatchers.contains("已批超期未入库"), anyString(),
                eq("purchase_req"), eq(9L), eq(12));
        verify(operLogMapper, atLeastOnce()).insert(any());
    }

    @Test
    void 扫描异常不抛出_仅记录错误日志() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenThrow(new IllegalStateException("SQL 语法错误"));
        assertDoesNotThrow(scheduler::scanExpiry, "定时任务异常不得冒泡影响主流程");
        assertDoesNotThrow(scheduler::scanApprovedNotStockedIn);
    }

    @Test
    void housekeeping清理两个内存计数表() {
        scheduler.housekeeping();
        verify(rateLimitFilter).sweep();
        verify(loginAttemptService).sweep();
    }
}
