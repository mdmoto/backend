package cn.lili.timetask.handler.impl;

import cn.lili.modules.system.service.FxRateSnapshotService;
import cn.lili.timetask.handler.EveryHourExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 每小时更新一次展示用汇率 (OER)
 */
@Slf4j
@Component
public class FxRateEveryHourExecute implements EveryHourExecute {

    @Autowired
    private FxRateSnapshotService fxRateSnapshotService;

    @Override
    public void execute() {
        log.info("【FX 汇率定时刷新】开始...");
        try {
            fxRateSnapshotService.refreshRates();
        } catch (Exception e) {
            log.error("【FX 汇率定时刷新异常】: ", e);
        }
    }
}
