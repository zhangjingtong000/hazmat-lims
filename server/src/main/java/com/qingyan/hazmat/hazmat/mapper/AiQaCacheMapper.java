package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.common.ai.AiQaCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** AI 问答缓存持久层（uk_qhash 唯一键兜底并发重复写入，冲突由服务层静默忽略）。 */
@Mapper
public interface AiQaCacheMapper {

    AiQaCache selectByHash(@Param("questionHash") String questionHash);

    int insert(AiQaCache cache);
}
