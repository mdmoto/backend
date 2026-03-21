package cn.lili.modules.logistics.calculation.impl;

import cn.lili.modules.logistics.calculation.LogisticsCalculationService;
import cn.lili.modules.logistics.calculation.LogisticsEstimateRequest;
import cn.lili.modules.logistics.calculation.LogisticsQuote;
import cn.lili.modules.logistics.calculation.fourpx.FourPxClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 运费试算服务占位实现。
 *
 * 说明：当前默认走 4PX 客户端（未接入时会抛 UnsupportedOperationException）。
 * 后续可按系统配置选择不同渠道，并在此处增加降级策略（例如：返回运费模板/固定运费）。
 */
@Service
public class LogisticsCalculationServiceImpl implements LogisticsCalculationService {

    @Autowired
    private FourPxClient fourPxClient;

    @Override
    public List<LogisticsQuote> estimate(LogisticsEstimateRequest request) {
        return fourPxClient.estimateCost(request);
    }
}

