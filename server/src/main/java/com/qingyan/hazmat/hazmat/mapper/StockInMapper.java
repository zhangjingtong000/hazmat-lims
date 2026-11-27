package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.StockIn;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 入库单持久层。 */
public interface StockInMapper {

    int insert(StockIn stockIn);

    StockIn selectById(@Param("id") Long id);

    int countToday(@Param("dayPrefix") String dayPrefix);

    /** 第二签（剧毒两步签）：0待验收→1已入库（条件更新）。 */
    int countersign(@Param("id") Long id, @Param("checkerId") Long checkerId);

    /** 拒收：0待验收→2已拒收终态（条件更新，与 countersign 并发互斥）。 */
    int reject(@Param("id") Long id, @Param("reason") String reason);

    /** 验收完成落账收尾（一步双签 submit 与剧毒 countersign 共用）：0待验收→1已入库并回写复核人与入库时间（条件更新）。 */
    int complete(@Param("id") Long id, @Param("checkerId") Long checkerId);

    /** 该申购单累计已入量（对账读侧；写侧走 purchase_req.in_qty 条件更新）。 */
    java.math.BigDecimal sumInByReq(@Param("reqId") Long reqId);

    // ── 列表分页（控制器层扩展，LEFT JOIN 补化学品名/验收人昵称）──

    /** 入库单列表分页：status 可空；keyword 匹配编码/批号。 */
    List<StockIn> selectPage(@Param("keyword") String keyword,
                             @Param("status") Integer status,
                             @Param("offset") int offset,
                             @Param("size") int size);

    long countPage(@Param("keyword") String keyword,
                   @Param("status") Integer status);
}
