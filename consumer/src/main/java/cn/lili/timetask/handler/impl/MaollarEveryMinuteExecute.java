package cn.lili.timetask.handler.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.cache.Cache;
import cn.lili.cache.CachePrefix;
import cn.lili.timetask.handler.EveryMinuteExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Maollar 每分钟定时任务：同步 $MAO 代币实时价格
 */
@Slf4j
@Component
public class MaollarEveryMinuteExecute implements EveryMinuteExecute {

    @Autowired
    private Cache cache;

    @Value("${lili.maollar.solana.mint:GH9mvRrmoXhZktsr7tJdQcoGTEqV4PVq7tAm67pRdW7c}")
    private String maoTokenMint;

    private static final String DEX_SCREENER_API = "https://api.dexscreener.com/latest/dex/tokens/";

    @Override
    public void execute() {
        try {
            log.debug("【Maollar 价格同步】开始抓取 $MAO 实时价格...");

            // 1. 调用 DexScreener API 获取代币信息
            String result = HttpUtil.get(DEX_SCREENER_API + maoTokenMint);
            JSONObject json = JSONUtil.parseObj(result);
            JSONArray pairs = json.getJSONArray("pairs");

            if (pairs != null && !pairs.isEmpty()) {
                // 2. 获取第一个交易对的价格 (通常是流动性最大的)
                JSONObject primaryPair = pairs.getJSONObject(0);
                String priceUsd = primaryPair.getStr("priceUsd");

                if (priceUsd != null) {
                    // 3. 存入 Redis，有效期 10 分钟 (实际每分钟更新)
                    cache.put(CachePrefix.MAO_PRICE.getPrefix(), priceUsd, 10L, TimeUnit.MINUTES);
                    log.info("【Maollar 价格同步】成功同步 $MAO 价格: ${}", priceUsd);
                }
            } else {
                log.warn("【Maollar 价格同步】未找到 $MAO 交易对信息，请检查 Mint 地址是否正确或是否已开盘。");
            }
        } catch (Exception e) {
            log.error("【Maollar 价格同步】异常: ", e);
        }
    }
}
