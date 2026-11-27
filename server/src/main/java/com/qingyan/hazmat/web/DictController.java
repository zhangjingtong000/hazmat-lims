package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.security.RequirePerm;
import com.qingyan.hazmat.system.entity.DictItem;
import com.qingyan.hazmat.system.mapper.DictItemMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 基础字典（停用不删改）。 */
@RestController
@RequestMapping("/system/dict")
public class DictController {

    private final DictItemMapper dictMapper;

    public DictController(DictItemMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @GetMapping("/list")
    public Result<TableData<DictItem>> list(@RequestParam(required = false) String dictType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        List<DictItem> rows = dictMapper.selectPage(dictType, keyword, (page - 1) * size, size);
        return Result.ok(TableData.of(rows, dictMapper.countPage(dictType, keyword)));
    }

    @GetMapping("/by-type")
    public Result<List<DictItem>> byType(@RequestParam String dictType) {
        return Result.ok(dictMapper.selectByType(dictType));
    }

    @PostMapping
    @RequirePerm("hzm:compat:maint")
    public Result<DictItem> add(@RequestBody DictItem item) {
        if (item.getDictType() == null || item.getItemName() == null) {
            throw new com.qingyan.hazmat.common.BizException(4101, "字典类型与名称必填");
        }
        com.qingyan.hazmat.common.Validators.maxLen(item.getDictType(), 30, "字典类型");
        com.qingyan.hazmat.common.Validators.maxLen(item.getItemName(), 50, "字典名称");
        com.qingyan.hazmat.common.Validators.maxLen(item.getItemValue(), 50, "字典值");
        if (item.getStatus() == null) {
            item.setStatus(0);
        }
        if (item.getOrderNum() == null) {
            item.setOrderNum(0);
        }
        dictMapper.insert(item);
        return Result.ok(item);
    }

    @PutMapping
    @RequirePerm("hzm:compat:maint")
    public Result<Void> edit(@RequestBody DictItem item) {
        dictMapper.update(item);
        return Result.ok(null);
    }

    /** 停用不删改（设计原则：停用项退出新建表单，历史单据原值展示）。 */
    @PostMapping("/{id}/status")
    @RequirePerm("hzm:compat:maint")
    public Result<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dictMapper.updateStatus(id, body.get("status"));
        return Result.ok(null);
    }
}
