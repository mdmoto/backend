package cn.lili.modules.system.service;

import cn.lili.modules.system.entity.dos.FxRateSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 汇率快照 Service
 */
public interface FxRateSnapshotService extends IService<FxRateSnapshot> {

    /**
     * 刷新汇率快照 (从 OER 获取)
     */
    void refreshRates();

    /**
     * 获取当前汇率表
     * @param base 基准币种
     * @return 汇率 Map
     */
    Map<String, Double> getRates(String base);

    /**
     * 获取支持的币种列表
     */
    List<String> getSupportedCurrencies();
}
