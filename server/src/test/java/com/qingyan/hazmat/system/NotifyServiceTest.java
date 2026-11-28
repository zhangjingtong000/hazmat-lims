package com.qingyan.hazmat.system;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.system.entity.NotifyMessage;
import com.qingyan.hazmat.system.mapper.NotifyMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内信服务单测：投递去重（同一业务对象窗口期内不重复）、超长标题截断、
 * 已读回执越权/不存在拒绝、未读数与分页口径。
 */
class NotifyServiceTest {

    private NotifyMessageMapper mapper;
    private NotifyService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(NotifyMessageMapper.class);
        service = new NotifyService(mapper);
    }

    @Test
    void 投递成功并截断超长标题() {
        when(mapper.countRecent(anyString(), anyString(), anyLong(), any())).thenReturn(0L);
        String longTitle = "批次已过期：".repeat(30);
        boolean pushed = service.push(7L, "expiry", longTitle, "内容", "chem_batch", 5L, 12);

        assertTrue(pushed);
        ArgumentCaptor<NotifyMessage> captor = ArgumentCaptor.forClass(NotifyMessage.class);
        verify(mapper).insert(captor.capture());
        NotifyMessage saved = captor.getValue();
        assertEquals(120, saved.getTitle().length(), "标题应截断到 120 字");
        assertEquals(7L, saved.getTargetUserId());
        assertEquals("chem_batch", saved.getBizType());
        assertEquals(5L, saved.getBizId());
    }

    @Test
    void 窗口期内同业务对象不重复投递() {
        when(mapper.countRecent(eq("expiry"), eq("chem_batch"), eq(5L), any())).thenReturn(1L);
        boolean pushed = service.push(null, "expiry", "批次已过期", "内容", "chem_batch", 5L, 12);
        assertFalse(pushed, "已投递过的对象应跳过");
        verify(mapper, never()).insert(any());
    }

    @Test
    void 无标题不投递() {
        assertFalse(service.push(null, "expiry", "  ", "内容", "chem_batch", 5L, 12));
        verify(mapper, never()).insert(any());
    }

    @Test
    void 未读数与分页口径() {
        when(mapper.countUnread(3L)).thenReturn(4L);
        when(mapper.countMine(3L)).thenReturn(9L);
        when(mapper.selectMine(eq(3L), eq(0), eq(10))).thenReturn(List.of(new NotifyMessage()));

        assertEquals(4L, service.unread(3L));
        Map<String, Object> page = service.list(3L, 1, 10);
        assertEquals(9L, page.get("total"));
        assertEquals(1, ((List<?>) page.get("records")).size());
    }

    @Test
    void 分页参数被夹取到合法区间() {
        when(mapper.countMine(1L)).thenReturn(0L);
        when(mapper.selectMine(eq(1L), eq(0), eq(1))).thenReturn(List.of());
        service.list(1L, 0, 999);
        verify(mapper).selectMine(1L, 0, 100);
    }

    @Test
    void 标记已读_不存在或越权时拒绝() {
        when(mapper.markRead(9L, 1L)).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> service.markRead(9L, 1L));
        assertEquals(4101, e.getCode());

        BizException missing = assertThrows(BizException.class, () -> service.markRead(null, 1L));
        assertEquals(4101, missing.getCode());
    }

    @Test
    void 全部已读返回更新行数() {
        when(mapper.markAllRead(1L)).thenReturn(3);
        assertEquals(3, service.markAllRead(1L));
    }
}
