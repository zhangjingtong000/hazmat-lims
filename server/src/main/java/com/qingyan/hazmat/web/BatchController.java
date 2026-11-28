package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.service.CompatService;
import com.qingyan.hazmat.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 批次：列表（chemicalId 可空）/ 移柜（显式触发禁配校验，冲突 4301）。
 * 移柜条件更新（WHERE cabinet_id=原柜 AND status=0）affected=0 即拒 4101。
 */
@RestController
@RequestMapping("/chem/batch")
public class BatchController {

    private final ChemInfoMapper chemInfoMapper;
    private final CompatService compatService;

    public BatchController(ChemInfoMapper chemInfoMapper, CompatService compatService) {
        this.chemInfoMapper = chemInfoMapper;
        this.compatService = compatService;
    }

    /** 批次列表分页（chemicalId 可空；LEFT JOIN 补化学品名/柜位名；登录即可）。 */
    @GetMapping("/list")
    public Result<TableData<ChemBatch>> list(@RequestParam(required = false) Long chemicalId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<ChemBatch> records = chemInfoMapper.selectBatchPage(chemicalId, offset, size);
        return Result.ok(TableData.of(records, chemInfoMapper.countBatchPage(chemicalId)));
    }

    /** 移柜：body {cabinetId}——先禁配校验（冲突 4301），再条件更新（仅在库批次、锁原柜）。 */
    @PutMapping("/{id}/cabinet")
    @RequirePerm("hzm:batch:move")
    public Result<Void> moveCabinet(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object cabinetId = body.get("cabinetId");
        if (!(cabinetId instanceof Number n)) {
            throw new BizException(4101, "缺少目标柜位 ID");
        }
        ChemBatch batch = chemInfoMapper.selectBatch(id);
        if (batch == null) {
            throw new BizException(4101, "批次不存在");
        }
        if (batch.getStatus() == null || batch.getStatus() != 0) {
            throw new BizException(4101, "仅在库批次可移柜");
        }
        long target = n.longValue();
        if (batch.getCabinetId() != null && batch.getCabinetId() == target) {
            throw new BizException(4101, "目标柜位与当前柜位相同");
        }
        ChemInfo chem = chemInfoMapper.selectById(batch.getChemicalId());
        if (chem == null) {
            throw new BizException(4101, "化学品台账不存在");
        }
        compatService.checkCabinet(target, chem); // 冲突 4301
        if (chemInfoMapper.moveBatch(id, target, batch.getCabinetId()) == 0) {
            throw new BizException(4101, "批次状态或柜位已变化，请刷新");
        }
        return Result.ok();
    }
}
