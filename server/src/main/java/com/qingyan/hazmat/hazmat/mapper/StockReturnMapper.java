package com.qingyan.hazmat.hazmat.mapper;

import com.qingyan.hazmat.hazmat.entity.StockReturn;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 余量归还单持久层（状态推进一律条件更新）。 */
public interface StockReturnMapper {

    int insert(StockReturn stockReturn);

    StockReturn selectById(@Param("id") Long id);

    /** 当日已有序号（编码 GH-年月日-NNN 生成用）。 */
    int countToday(@Param("dayPrefix") String dayPrefix);

    /** 确认回库：0→1 并落 confirmer_id/confirm_time（条件更新）。 */
    int confirm(@Param("id") Long id, @Param("confirmerId") Long confirmerId);

    /** 某领用单的全部归还记录（含已拒绝——拒因留痕可审计）。 */
    List<StockReturn> selectByReq(@Param("reqId") Long reqId);

    /** 待确认归还单（审批中心第四类待办，联表带品名/申请人）。 */
    long countPending();

    List<StockReturn> selectPending();

    /** 拒绝：0→2 并落拒因（条件更新）。 */
    int reject(@Param("id") Long id, @Param("comment") String comment);
}
