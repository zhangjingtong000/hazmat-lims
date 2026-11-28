package com.qingyan.hazmat.system;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.system.entity.NotifyMessage;
import com.qingyan.hazmat.system.mapper.NotifyMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 站内信服务（主动通知通道）：业务事件与定时扫描统一经此投递，
 * 前端顶栏铃铛按未读数轮询（GET /system/notify/unread-count）。
 *
 * <p>投递幂等：同一业务对象在窗口期内只投一次（countRecent 守卫），
 * 避免定时任务每轮重复轰炸（运维噪声也是缺陷）。
 */
@Service
public class NotifyService {

    private final NotifyMessageMapper mapper;

    public NotifyService(NotifyMessageMapper mapper) {
        this.mapper = mapper;
    }

    /** 投递一条站内信；duplicateHours>0 时窗口期内同对象不重复投递。 */
    @Transactional
    public boolean push(Long targetUserId, String category, String title, String content,
                        String bizType, Long bizId, int duplicateHours) {
        if (title == null || title.isBlank()) {
            return false;
        }
        if (duplicateHours > 0 && bizType != null && bizId != null
                && mapper.countRecent(category, bizType, bizId, LocalDateTime.now().minusHours(duplicateHours)) > 0) {
            return false;
        }
        NotifyMessage message = new NotifyMessage();
        message.setTargetUserId(targetUserId);
        message.setCategory(category);
        message.setTitle(title.length() > 120 ? title.substring(0, 120) : title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        mapper.insert(message);
        return true;
    }

    public Map<String, Object> list(long uid, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        List<NotifyMessage> records = mapper.selectMine(uid, (p - 1) * s, s);
        return Map.of("total", mapper.countMine(uid), "records", records);
    }

    public long unread(long uid) {
        return mapper.countUnread(uid);
    }

    @Transactional
    public void markRead(Long id, long uid) {
        if (id == null) {
            throw new BizException(4101, "缺少消息 ID");
        }
        if (mapper.markRead(id, uid) == 0) {
            throw new BizException(4101, "消息不存在或无权操作");
        }
    }

    @Transactional
    public int markAllRead(long uid) {
        return mapper.markAllRead(uid);
    }
}
