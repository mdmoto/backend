package cn.lili.modules.logistics.calculation;

import java.util.List;

/**
 * 运费试算（用于下单页动态运费/渠道选择）。
 *
 * 说明：当前仅提供抽象接口，具体实现可对接 4PX / 菜鸟 / 燕文等渠道。
 */
public interface LogisticsCalculationService {

    /**
     * 试算运费（返回多个可选渠道报价）。
     */
    List<LogisticsQuote> estimate(LogisticsEstimateRequest request);
}

