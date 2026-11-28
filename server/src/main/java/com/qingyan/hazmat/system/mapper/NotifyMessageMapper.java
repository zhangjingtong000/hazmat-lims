package com.qingyan.hazmat.system.mapper;

import com.qingyan.hazmat.system.entity.NotifyMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 站内信 Mapper：定向通知的写入与已读回执。 */
public interface NotifyMessageMapper {

    int insert(NotifyMessage message);

    /** 我的消息（含全员广播）：target_user_id = 我 OR IS NULL。 */
    List<NotifyMessage> selectMine(@Param("uid") long uid,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    long countMine(@Param("uid") long uid);

    long countUnread(@Param("uid") long uid);

    int markRead(@Param("id") Long id, @Param("uid") long uid);

    int markAllRead(@Param("uid") long uid);

    /** 同类同业务对象在 since 之后是否已投递（定时扫描幂等：不重复轰炸；口径由调用方给时间）。 */
    long countRecent(@Param("category") String category,
                     @Param("bizType") String bizType,
                     @Param("bizId") Long bizId,
                     @Param("since") java.time.LocalDateTime since);
}
