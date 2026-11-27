package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.Validators;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.entity.StorageCabinet;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.mapper.StorageCabinetMapper;
import com.qingyan.hazmat.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 存放柜（§5）：列表 / 新增 / 维护。柜内剧毒批次守卫（§2 storage_cabinet 注）：
 * 柜内有在库剧毒（category=4）批次时禁改柜类型、禁取消双锁（4101）。
 */
@RestController
@RequestMapping("/chem/cabinet")
public class CabinetController {

    private final StorageCabinetMapper cabinetMapper;
    private final ChemInfoMapper chemInfoMapper;

    public CabinetController(StorageCabinetMapper cabinetMapper, ChemInfoMapper chemInfoMapper) {
        this.cabinetMapper = cabinetMapper;
        this.chemInfoMapper = chemInfoMapper;
    }

    /** 柜位列表（全量，登录即可）。 */
    @GetMapping
    public Result<List<StorageCabinet>> list() {
        return Result.ok(cabinetMapper.selectAll());
    }

    /** 新增柜位（编码必填，唯一索引兜底）。 */
    @PostMapping
    @RequirePerm("hzm:cabinet:maint")
    public Result<Void> add(@RequestBody StorageCabinet cabinet) {
        Validators.requiredLen(cabinet.getCode(), 32, "柜位编码");
        Validators.maxLen(cabinet.getName(), 100, "柜位名称");
        Validators.maxLen(cabinet.getLocation(), 100, "位置");
        if (cabinet.getType() == null) {
            cabinet.setType(6); // 默认通用柜
        }
        Validators.range(cabinet.getType(), 1, 9, "柜位类型");
        if (cabinet.getDualLock() == null) {
            cabinet.setDualLock(0);
        }
        cabinetMapper.insert(cabinet);
        return Result.ok();
    }

    /** 维护柜位：柜内有在库剧毒批次时禁改类型/取消双锁（4101）。 */
    @PutMapping
    @RequirePerm("hzm:cabinet:maint")
    public Result<Void> edit(@RequestBody StorageCabinet cabinet) {
        if (cabinet.getId() == null) {
            throw new BizException(4101, "缺少柜位 ID");
        }
        Validators.maxLen(cabinet.getName(), 100, "柜位名称");
        Validators.maxLen(cabinet.getLocation(), 100, "位置");
        if (cabinet.getType() != null) {
            Validators.range(cabinet.getType(), 1, 9, "柜位类型");
        }
        StorageCabinet existing = cabinetMapper.selectById(cabinet.getId());
        if (existing == null) {
            throw new BizException(4101, "柜位不存在");
        }
        boolean typeChanged = cabinet.getType() != null && !cabinet.getType().equals(existing.getType());
        boolean dualLockRemoved = cabinet.getDualLock() != null && cabinet.getDualLock() == 0
                && existing.getDualLock() != null && existing.getDualLock() == 1;
        if ((typeChanged || dualLockRemoved) && hasLethalBatch(cabinet.getId())) {
            throw new BizException(4101, "柜内存在在库剧毒批次，禁改柜类型或取消双锁");
        }
        cabinetMapper.update(cabinet);
        return Result.ok();
    }

    /** 柜内在库批次是否含剧毒（category=4；R3 数据源复用 selectCabinetMates）。 */
    private boolean hasLethalBatch(Long cabinetId) {
        List<ChemInfo> mates = chemInfoMapper.selectCabinetMates(cabinetId);
        return mates.stream().anyMatch(m -> m.getCategory() != null && m.getCategory() == 4);
    }
}
