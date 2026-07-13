package cn.lili.timetask.handler.impl.maollar;

import cn.lili.modules.member.entity.dos.MaoAuditAnchor;
import cn.lili.modules.member.service.MaoAuditAnchorService;
import cn.lili.timetask.handler.EveryDayExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Maollar 每日审计锚定任务
 */
@Slf4j
@Component
public class MaollarAuditAnchorEveryDayExecute implements EveryDayExecute {

    @Autowired
    private MaoAuditAnchorService maoAuditAnchorService;

    @Override
    public void execute() {
        LocalDate anchorDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("【Maollar 审计锚】开始生成 UTC 日期 {} 的公开审计锚。", anchorDate);
        MaoAuditAnchor anchor = maoAuditAnchorService.createDailyAnchor(anchorDate);
        log.info("【Maollar 审计锚】完成 anchorKey={}, status={}, root={}, txHash={}",
                anchor.getAnchorKey(), anchor.getAnchorStatus(), anchor.getMerkleRoot(), anchor.getAnchorTxHash());
    }
}
