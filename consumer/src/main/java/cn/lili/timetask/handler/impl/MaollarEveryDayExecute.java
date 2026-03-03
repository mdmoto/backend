package cn.lili.timetask.handler.impl;

import cn.lili.modules.system.service.ExchangeRateProvider;
import cn.lili.modules.system.service.impl.OpenExchangeRatesProvider;
import cn.lili.timetask.handler.EveryDayExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maollar 每日定时任务：刷新汇率库 (OER)
 */
@Slf4j
@Component
public class MaollarEveryDayExecute implements EveryDayExecute {

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    @Override
    public void execute() {
        log.info("【Maollar 心跳】开始执行每日强制汇率同步任务...");
        if (exchangeRateProvider instanceof OpenExchangeRatesProvider) {
            ((OpenExchangeRatesProvider) exchangeRateProvider).refreshRatesIfExpired(true);
            log.info("【Maollar 心跳】每日汇率同步完成。");
        }
    }
}
