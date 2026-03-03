package cn.lili.modules.system.service;

import java.math.BigDecimal;

/**
 * 汇率提供者接口
 * 用于支持多法币与 USD 的实时折算，可切 Stripe 或 OER
 */
public interface ExchangeRateProvider {
    /**
     * 获取指定货币对 USD 的实时汇率
     * 
     * @param currencyCode 货币代码，如 "JPY", "CNY"
     * @return 1单位该法币 = 多少 USD
     */
    BigDecimal getRateToUsd(String currencyCode);
}
