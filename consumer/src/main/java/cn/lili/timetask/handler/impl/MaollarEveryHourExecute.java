package cn.lili.timetask.handler.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.system.service.MaollarTierService;
import cn.lili.timetask.handler.EveryHourExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Maollar 每小时定时任务：上报 GMV 到区块链网关
 */
@Slf4j
@Component
public class MaollarEveryHourExecute implements EveryHourExecute {

    @Autowired
    private OrderService orderService;

    @Autowired
    private MaollarTierService maollarTierService;

    @Value("${lili.maollar.solana.gateway-url}")
    private String gatewayUrl;

    @Value("${lili.maollar.solana.gateway-secret}")
    private String gatewaySecret;

    @Override
    public void execute() {
        log.info("【Maollar 心跳】开始上报每小时 GMV 数据...");
        try {
            // 1. 获取当前已完成订单的总成交额 (CNY)
            double totalSalesCNY = orderService.getCompletedTotalSales();

            // 2. 折算为 USD (使用带安全垫的汇率)
            double totalSalesUSD = maollarTierService.convertToUSD(totalSalesCNY, "CNY");

            // 3. 构造请求体
            Map<String, Object> body = new HashMap<>();
            body.put("type", "REPORT_GMV");
            body.put("total_gmv_usd", totalSalesUSD);

            String timestamp = String.valueOf(System.currentTimeMillis());
            String bodyStr = JSONUtil.toJsonStr(body);

            // 4. 生成 HMAC 签名
            String signature = SecureUtil.hmacSha256(gatewaySecret).digestHex(timestamp + bodyStr);

            // 5. 调用网关 (注意：这里使用 POST 到根地址，Worker 内部会根据 type 处理)
            String result = HttpUtil.createPost(gatewayUrl)
                    .header("X-MAO-Timestamp", timestamp)
                    .header("X-MAO-Signature", signature)
                    .body(bodyStr)
                    .execute()
                    .body();

            log.info("【Maollar 心跳】GMV 上报结果: {}", result);
        } catch (Exception e) {
            log.error("【Maollar 心跳】GMV 上报异常: ", e);
        }
    }
}
