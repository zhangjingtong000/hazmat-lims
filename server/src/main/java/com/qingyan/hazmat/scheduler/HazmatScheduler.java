package com.qingyan.hazmat.scheduler;

import com.qingyan.hazmat.security.LoginAttemptService;
import com.qingyan.hazmat.security.RateLimitFilter;
import com.qingyan.hazmat.system.NotifyService;
import com.qingyan.hazmat.system.entity.OperLog;
import com.qingyan.hazmat.system.mapper.OperLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 定时任务：把"靠人被动查询"的监管口径变成主动扫描 + 站内信推送。
 *
 * <ul>
 *   <li>{@code scanExpiry}：批次效期扫描——已过期（只推送提醒，不自动封存）与临近预警
 *       （默认 30 天，取台账 expiry_warn_days 的最小值兜底）分别推送</li>
 *   <li>{@code scanApprovedNotStockedIn}：已批超 7 天未入库（申购预警口径）推送，
 *       让"该入库的没人管"不再只出现在看板上</li>
 *   <li>{@code housekeeping}：内存计数表清理（限流/登录失败窗口，防长期运行内存增长）</li>
 * </ul>
 *
 * <p>幂等：同类同业务对象 12 小时内只投递一次（NotifyService.duplicateHours），
 * 任务重启/多轮扫描不会重复轰炸。可经 app.schedule.enabled=false 整体关闭（单测/离线演示）。
 */
@Component
@ConditionalOnProperty(prefix = "app.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HazmatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HazmatScheduler.class);

    private final JdbcTemplate jdbc;
    private final NotifyService notifyService;
    private final OperLogMapper operLogMapper;
    private final RateLimitFilter rateLimitFilter;
    private final LoginAttemptService loginAttemptService;

    @Value("${app.schedule.expiry-warn-days:30}")
    private int expiryWarnDays;

    @Value("${app.schedule.approved-not-stocked-days:7}")
    private int approvedNotStockedDays;

    public HazmatScheduler(JdbcTemplate jdbc, NotifyService notifyService, OperLogMapper operLogMapper,
                           RateLimitFilter rateLimitFilter, LoginAttemptService loginAttemptService) {
        this.jdbc = jdbc;
        this.notifyService = notifyService;
        this.operLogMapper = operLogMapper;
        this.rateLimitFilter = rateLimitFilter;
        this.loginAttemptService = loginAttemptService;
    }

    /** 效期扫描：默认启动 90s 后首跑，之后每 30 分钟一次（首跑避开种子注入窗口）。 */
    @Scheduled(initialDelayString = "${app.schedule.initial-delay-ms:90000}",
            fixedDelayString = "${app.schedule.scan-interval-ms:1800000}")
    public void scanExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate warnLine = today.plusDays(expiryWarnDays);
        try {
            List<Map<String, Object>> expired = jdbc.queryForList("""
                    SELECT b.id, b.lot_no, b.expiry_date, i.name AS chem_name
                    FROM chem_batch b JOIN chem_info i ON i.id = b.chemical_id
                    WHERE b.status = 0 AND b.qty_left > 0 AND b.expiry_date IS NOT NULL
                      AND b.expiry_date < ?
                    """, today);
            int expiredPushed = 0;
            for (Map<String, Object> row : expired) {
                boolean pushed = notifyService.push(null, "expiry",
                        "批次已过期：" + row.get("chem_name") + " / " + row.get("lot_no"),
                        "效期 " + row.get("expiry_date") + " 已过，按制度应立即封存并禁止发放；"
                                + "当前系统未接自动封存，请核对后由管理员在库中置 status=3，再走废弃处置流程。",
                        "chem_batch", asLong(row.get("id")), 12);
                expiredPushed += pushed ? 1 : 0;
            }
            List<Map<String, Object>> nearExpiry = jdbc.queryForList("""
                    SELECT b.id, b.lot_no, b.expiry_date, i.name AS chem_name
                    FROM chem_batch b JOIN chem_info i ON i.id = b.chemical_id
                    WHERE b.status = 0 AND b.qty_left > 0 AND b.expiry_date IS NOT NULL
                      AND b.expiry_date >= ? AND b.expiry_date <= ?
                    """, today, warnLine);
            int nearPushed = 0;
            for (Map<String, Object> row : nearExpiry) {
                boolean pushed = notifyService.push(null, "expiry",
                        "效期临近：" + row.get("chem_name") + " / " + row.get("lot_no"),
                        "效期 " + row.get("expiry_date") + " 在 " + expiryWarnDays
                                + " 天内到期，请优先安排使用（先到期先出）。",
                        "chem_batch", asLong(row.get("id")), 12);
                nearPushed += pushed ? 1 : 0;
            }
            if (expiredPushed + nearPushed > 0) {
                log.info("[schedule] 效期扫描：过期 {} 条、临近 {} 条（新增站内信 {} 条）",
                        expired.size(), nearExpiry.size(), expiredPushed + nearPushed);
                record("效期扫描", "过期 " + expired.size() + " / 临近 " + nearExpiry.size()
                        + "，推送 " + (expiredPushed + nearPushed) + " 条");
            }
        } catch (Exception e) {
            log.error("[schedule] 效期扫描失败（不影响主流程）", e);
        }
    }

    /** 已批超期未入库扫描：每 30 分钟一次（与效期扫描同频，首跑错开 30s）。 */
    @Scheduled(initialDelayString = "${app.schedule.initial-delay-ms:90000}",
            fixedDelayString = "${app.schedule.scan-interval-ms:1800000}")
    public void scanApprovedNotStockedIn() {
        try {
            LocalDate line = LocalDate.now().minusDays(approvedNotStockedDays);
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, code, chemical_name, audit_time
                    FROM purchase_req
                    WHERE status = 2 AND (in_qty IS NULL OR in_qty = 0)
                      AND audit_time IS NOT NULL AND CAST(audit_time AS DATE) <= ?
                    """, line);
            int pushed = 0;
            for (Map<String, Object> row : rows) {
                boolean ok = notifyService.push(null, "stockin",
                        "已批超期未入库：" + row.get("code") + " / " + row.get("chemical_name"),
                        "申购于 " + row.get("audit_time") + " 批准，至今未入库（超 "
                                + approvedNotStockedDays + " 天）；请核查供应商到货情况，"
                                + "逾期可由申请人作废并重新申购。",
                        "purchase_req", asLong(row.get("id")), 12);
                pushed += ok ? 1 : 0;
            }
            if (pushed > 0) {
                log.info("[schedule] 超期未入库扫描：命中 {} 条，推送 {} 条", rows.size(), pushed);
                record("超期未入库扫描", "命中 " + rows.size() + "，推送 " + pushed + " 条");
            }
        } catch (Exception e) {
            log.error("[schedule] 超期未入库扫描失败（不影响主流程）", e);
        }
    }

    /** 内存计数清理：每 10 分钟一次。 */
    @Scheduled(initialDelayString = "60000", fixedDelayString = "600000")
    public void housekeeping() {
        rateLimitFilter.sweep();
        loginAttemptService.sweep();
    }

    private void record(String action, String detail) {
        try {
            OperLog log = new OperLog();
            log.setModule("定时任务");
            log.setAction(action);
            log.setOperator("system");
            log.setStatus("成功");
            operLogMapper.insert(log);
        } catch (Exception ignore) {
            // 留痕失败不阻断扫描
        }
    }

    private Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
