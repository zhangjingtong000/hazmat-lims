package com.qingyan.hazmat.hazmat;

import com.qingyan.hazmat.hazmat.entity.ChemBatch;
import com.qingyan.hazmat.hazmat.entity.ChemInfo;
import com.qingyan.hazmat.hazmat.mapper.ChemInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEFO 候选批次的效期过滤（H2 真实落库，不走 mock）。
 *
 * <p>过期批次在默认流程下仍是 {@code status=0}：没有调度任务把它置为 3，
 * 因此状态与余量条件都挡不住它。若 FEFO 只看状态和余量，效期最早的过期批次
 * 会排在队首被优先发出去。此测试构造"一个已过期 + 一个未过期"的同化学品批次，
 * 断言候选集只含未过期那一批。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:hazmat-it;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "app.schedule.enabled=false"
})
class FefoExpiryTest {

    @Autowired
    private ChemInfoMapper chemMapper;

    @Test
    void 过期批次不参与FEFO候选() {
        Long chemId = createChemical();
        ChemBatch expired = createBatch(chemId, "LOT-EXPIRED", "5", LocalDate.now().minusDays(1));
        ChemBatch fresh = createBatch(chemId, "LOT-FRESH", "5", LocalDate.now().plusDays(30));

        List<ChemBatch> candidates = chemMapper.selectFefoBatches(chemId);

        assertEquals(1, candidates.size(), "候选集只应含未过期批次，实际：" + lotNos(candidates));
        assertEquals(fresh.getLotNo(), candidates.get(0).getLotNo());
        assertTrue(candidates.stream().noneMatch(b -> b.getId().equals(expired.getId())),
                "已过期批次不得出现在 FEFO 候选中");
    }

    private static List<String> lotNos(List<ChemBatch> batches) {
        return batches.stream().map(ChemBatch::getLotNo).toList();
    }

    private Long createChemical() {
        ChemInfo info = new ChemInfo();
        info.setCode("T-FEFO-" + System.nanoTime());
        info.setName("FEFO 效期过滤测试品");
        info.setCasNo("900-00-1");
        info.setCategory(1);
        info.setCompatGroup("GEN");
        info.setUnit("瓶");
        info.setStockQty(new BigDecimal("10"));
        info.setStatus(0);
        chemMapper.insert(info);
        return info.getId();
    }

    private ChemBatch createBatch(Long chemId, String lotNo, String qty, LocalDate expiry) {
        ChemBatch batch = new ChemBatch();
        batch.setChemicalId(chemId);
        batch.setLotNo(lotNo);
        batch.setQtyIn(new BigDecimal(qty));
        batch.setQtyLeft(new BigDecimal(qty));
        batch.setExpiryDate(expiry);
        batch.setStatus(0);
        chemMapper.insertBatch(batch);
        return batch;
    }
}
