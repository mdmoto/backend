package cn.lili.modules.system.serviceimpl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.cache.Cache;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.modules.system.entity.dos.FxRateSnapshot;
import cn.lili.modules.system.mapper.FxRateSnapshotMapper;
import cn.lili.modules.system.service.FxRateSnapshotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 汇率快照 Service 实现
 */
@Slf4j
@Service
public class FxRateSnapshotServiceImpl extends ServiceImpl<FxRateSnapshotMapper, FxRateSnapshot> implements FxRateSnapshotService {

    @Autowired
    private Cache<Map<String, Double>> cache;

    @Value("${lili.maollar.oer.app-id:}")
    private String appId;

    private static final String API_URL = "https://openexchangerates.org/api/latest.json?app_id=";
    private static final String CACHE_KEY = "FX_RATES_USD";

    @Override
    public void refreshRates() {
        if (StrUtil.isEmpty(appId)) {
            log.error("【FX 汇率更新失败】OER App ID 未配置");
            throw new ServiceException(ResultCode.ERROR, "OER App ID is missing");
        }

        try {
            log.info("【FX 汇率更新】开始从 Open Exchange Rates 拉取汇率...");
            String result = HttpUtil.get(API_URL + appId);
            JSONObject json = JSONUtil.parseObj(result);
            JSONObject rates = json.getJSONObject("rates");
            Long timestamp = json.getLong("timestamp");

            if (rates != null && timestamp != null) {
                List<FxRateSnapshot> snapshots = new ArrayList<>();
                Map<String, Double> ratesMap = new HashMap<>();

                for (String symbol : rates.keySet()) {
                    BigDecimal rateValue = rates.getBigDecimal(symbol);
                    if (rateValue != null) {
                        FxRateSnapshot snapshot = new FxRateSnapshot();
                        snapshot.setBaseCurrency("USD");
                        snapshot.setQuoteCurrency(symbol);
                        snapshot.setExchangeRate(rateValue);
                        snapshot.setAsOfTs(timestamp);
                        snapshot.setSource("OER");
                        snapshots.add(snapshot);
                        
                        ratesMap.put(symbol, rateValue.doubleValue());
                    }
                }

                // 批量更新到数据库 (使用 UK 覆盖)
                this.saveOrUpdateBatch(snapshots);
                
                // 写入 Redis 缓存 (4 小时有效)
                cache.put(CACHE_KEY, ratesMap, 14400L);
                
                log.info("【FX 汇率更新成功】已同步 {} 个币种汇率", snapshots.size());
            } else {
                throw new Exception("OER response invalid");
            }
        } catch (Exception e) {
            log.error("【FX 汇率更新失败】无法从 OER 获取数据: ", e);
            // 这里不抛出异常给定时任务，仅记录日志，由 API 层决定 fallback
        }
    }

    @Override
    public Map<String, Double> getRates(String base) {
        if (!"USD".equalsIgnoreCase(base)) {
            // 目前仅支持 USD 作为基准
            throw new ServiceException(ResultCode.PARAMS_ERROR);
        }

        Map<String, Double> rates = (Map<String, Double>) cache.get(CACHE_KEY);
        if (rates == null) {
            log.warn("【FX 汇率缓存失效】尝试从数据库恢复...");
            List<FxRateSnapshot> list = this.list(new LambdaQueryWrapper<FxRateSnapshot>().eq(FxRateSnapshot::getBaseCurrency, "USD"));
            if (list != null && !list.isEmpty()) {
                rates = list.stream().collect(Collectors.toMap(FxRateSnapshot::getQuoteCurrency, s -> s.getExchangeRate().doubleValue()));
                cache.put(CACHE_KEY, rates, 14400L);
            }
        }

        if (rates == null) {
            log.error("【FX 汇率不可用】缓存与数据库均无记录");
            return null; // 用户要求如果失败返回“不可用”
        }

        return rates;
    }

    @Override
    public List<String> getSupportedCurrencies() {
        Map<String, Double> rates = getRates("USD");
        if (rates != null) {
            return new ArrayList<>(rates.keySet());
        }
        return Arrays.asList("USD", "CNY", "JPY", "EUR"); // 兜底返回常用币种
    }
}
