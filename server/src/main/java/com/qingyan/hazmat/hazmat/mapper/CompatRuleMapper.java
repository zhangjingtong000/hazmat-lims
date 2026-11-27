package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.CompatRule;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 禁配规则 + 入库单持久层。 */
public interface CompatRuleMapper {

    int insertPair(CompatRule rule);

    List<CompatRule> selectAll();

    void deletePair(@Param("groupA") String groupA, @Param("groupB") String groupB);
}
