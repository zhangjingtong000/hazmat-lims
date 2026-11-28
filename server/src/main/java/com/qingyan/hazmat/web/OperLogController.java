package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.security.RequirePerm;
import com.qingyan.hazmat.system.entity.OperLog;
import com.qingyan.hazmat.system.mapper.OperLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志查询页（管理员）。
 */
@RestController
@RequestMapping("/system/operlog")
public class OperLogController {

    private final OperLogMapper operLogMapper;

    public OperLogController(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @GetMapping("/list")
    @RequirePerm("system:operlog:list")
    public Result<TableData<OperLog>> list(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(defaultValue = "") String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<OperLog> records = operLogMapper.selectPage(keyword, status, offset, size);
        return Result.ok(TableData.of(records, operLogMapper.countPage(keyword, status)));
    }
}
