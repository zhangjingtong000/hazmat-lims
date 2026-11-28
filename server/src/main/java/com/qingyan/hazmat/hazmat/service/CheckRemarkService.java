package com.qingyan.hazmat.hazmat.service;

import com.qingyan.hazmat.hazmat.mapper.ChemCheckDiffMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 盘点差异行标记的独立事务写入。
 *
 * <p>调账被拒绝时（批次已封存/不存在）要先把差异行标记为"需重盘"再抛业务异常。
 * 若标记与拒绝同处一个事务，异常回滚会把标记一起抹掉——调用方看到"已标记"的提示，
 * 库里却没有标记。故标记走 {@link Propagation#REQUIRES_NEW}：外层事务挂起，
 * 标记单独提交，随后抛出的异常只回滚外层。
 *
 * <p>必须是独立的 Spring bean：同类内部自调用不会经过事务代理，注解不生效。
 */
@Service
public class CheckRemarkService {

    public static final String REMARK_RECHECK = "需重盘";

    private final ChemCheckDiffMapper diffMapper;

    public CheckRemarkService(ChemCheckDiffMapper diffMapper) {
        this.diffMapper = diffMapper;
    }

    /** 在新事务中把差异行标为"需重盘"并提交（不受调用方事务回滚影响）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRecheck(long diffId) {
        diffMapper.setRemark(diffId, REMARK_RECHECK);
    }
}
