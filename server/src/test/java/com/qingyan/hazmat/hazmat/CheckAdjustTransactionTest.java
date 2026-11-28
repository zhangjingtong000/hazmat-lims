package com.qingyan.hazmat.hazmat;

import com.qingyan.hazmat.common.BizException;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import com.qingyan.hazmat.hazmat.service.CheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 调账拒绝时"需重盘"标记必须真正落库（H2 真实落库，不走 mock）。
 *
 * <p>调账发现批次已封存时，服务先标记差异行"需重盘"再抛 4101。若标记与异常
 * 处在同一个事务里，异常会把标记一起回滚——返回给调用方的提示说"已标记需重盘"，
 * 库里却什么都没留下，重盘的人看不到这条差异。这里断言的是<b>库里的值</b>，
 * 不是"方法被调用过"。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:hazmat-it;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.schedule.enabled=false"
})
class CheckAdjustTransactionTest {

    @Autowired
    private CheckService checkService;

    @Autowired
    private ChemInfoMapper chemMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void 批次封存后调账拒绝_需重盘标记仍留在库里() {
        String tag = String.valueOf(System.nanoTime());
        long chemId = insertChemical(tag);
        long batchId = insertBatch(chemId, tag);
        long checkerOne = insertUser(tag + "-one");
        long checkerTwo = insertUser(tag + "-two");
        long checkId = insertCheck(tag, checkerOne, checkerTwo);
        long diffId = insertDiff(checkId, batchId);
        long adjuster = grantAdjustPermission(tag);

        BizException ex = assertThrows(BizException.class,
                () -> checkService.adjust(checkId, diffId, adjuster));
        assertEquals(4101, ex.getCode());

        String remark = jdbc.queryForObject(
                "SELECT remark FROM chem_check_diff WHERE id = ?", String.class, diffId);
        assertEquals("需重盘", remark, "拒绝调账后必须能在库里读到标记（REQUIRES_NEW 提交）");
    }

    private long insertChemical(String tag) {
        jdbc.update("""
                INSERT INTO chem_info(code, name, cas_no, category, compat_group, unit, stock_qty, status)
                VALUES (?, '盘点调账事务测试品', '900-00-3', 1, 'GEN', '瓶', 2, 0)
                """, "T-CHK-" + tag);
        return jdbc.queryForObject("SELECT MAX(id) FROM chem_info WHERE code = ?", Long.class, "T-CHK-" + tag);
    }

    private long insertBatch(long chemId, String tag) {
        jdbc.update("""
                INSERT INTO chem_batch(chemical_id, lot_no, qty_in, qty_left, expiry_date, status)
                VALUES (?, ?, 2, 2, ?, 3)
                """, chemId, "LOT-CHK-" + tag, LocalDate.now().plusDays(30));
        return jdbc.queryForObject("SELECT MAX(id) FROM chem_batch WHERE chemical_id = ?", Long.class, chemId);
    }

    private long insertUser(String name) {
        jdbc.update("INSERT INTO sys_user(username, password, nickname) VALUES (?, 'x', '')", name);
        return jdbc.queryForObject("SELECT MAX(id) FROM sys_user WHERE username = ?", Long.class, name);
    }

    private long insertCheck(String tag, long checkerOne, long checkerTwo) {
        jdbc.update("""
                INSERT INTO chem_check(code, checker_one_id, checker_two_id, diff_count, result)
                VALUES (?, ?, ?, 1, 2)
                """, "PD-IT-" + tag, checkerOne, checkerTwo);
        return jdbc.queryForObject("SELECT MAX(id) FROM chem_check WHERE code = ?", Long.class, "PD-IT-" + tag);
    }

    private long insertDiff(long checkId, long batchId) {
        jdbc.update("""
                INSERT INTO chem_check_diff(check_id, batch_id, book_qty, actual_qty, diff_qty, remark)
                VALUES (?, ?, 2, 1, -1, '')
                """, checkId, batchId);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM chem_check_diff WHERE check_id = ?", Long.class, checkId);
    }

    /** 建角色 → 关联盘点调账权限菜单 → 关联用户，使 countPerm 命中。 */
    private long grantAdjustPermission(String tag) {
        long userId = insertUser("it-adjuster-" + tag);
        Long menuId = jdbc.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = 'hzm:check:adjust' ORDER BY id DESC LIMIT 1", Long.class);
        jdbc.update("INSERT INTO sys_role(role_name, role_key) VALUES ('盘点调账员', ?)", "it_adjust_" + tag);
        Long roleId = jdbc.queryForObject("SELECT MAX(id) FROM sys_role", Long.class);
        jdbc.update("INSERT INTO sys_role_menu(role_id, menu_id) VALUES (?, ?)", roleId, menuId);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }
}
