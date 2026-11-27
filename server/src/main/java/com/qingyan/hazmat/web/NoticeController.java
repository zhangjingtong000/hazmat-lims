package com.qingyan.hazmat.web;

import com.qingyan.hazmat.common.Result;
import com.qingyan.hazmat.common.TableData;
import com.qingyan.hazmat.security.RequirePerm;
import com.qingyan.hazmat.system.entity.Notice;
import com.qingyan.hazmat.system.mapper.NoticeMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 模块范式 specimen：标准业务 CRUD 的参考实现，
 * 生成器按此模式产出各系统的业务模块。
 */
@RestController
@RequestMapping("/biz/notice")
public class NoticeController {

    private final NoticeMapper noticeMapper;

    public NoticeController(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @GetMapping("/list")
    @RequirePerm("biz:notice:list")
    public Result<TableData<Notice> > list(@RequestParam(defaultValue = "") String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<Notice> records = noticeMapper.selectPage(keyword, offset, size);
        return Result.ok(TableData.of(records, noticeMapper.countPage(keyword)));
    }

    @PostMapping
    @RequirePerm("biz:notice:add")
    public Result<Void> add(@RequestBody Notice notice, HttpServletRequest request) {
        notice.setCreateBy((String) request.getAttribute("username"));
        if (notice.getStatus() == null) {
            notice.setStatus("0");
        }
        noticeMapper.insert(notice);
        return Result.ok();
    }

    @PutMapping
    @RequirePerm("biz:notice:edit")
    public Result<Void> edit(@RequestBody Notice notice) {
        noticeMapper.update(notice);
        return Result.ok();
    }

    @DeleteMapping("/{ids}")
    @RequirePerm("biz:notice:remove")
    public Result<Void> remove(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
        noticeMapper.deleteByIds(idList);
        return Result.ok();
    }
}
