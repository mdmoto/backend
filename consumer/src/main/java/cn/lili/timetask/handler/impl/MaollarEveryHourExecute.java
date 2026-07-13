package cn.lili.timetask.handler.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.order.order.service.OrderService;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import cn.lili.modules.system.service.MaollarTierService;
import cn.lili.timetask.handler.EveryHourExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Autowired
    private StripePaymentSnapshotService stripePaymentSnapshotService;

    @Value("${lili.maollar.solana.gateway-url}")
    private String gatewayUrl;

    @Value("${lili.maollar.solana.gateway-secret}")
    private String gatewaySecret;

    @Value("${lili.maollar.solana.report-gmv-enabled:false}")
    private Boolean reportGmvEnabled;

    @Override
    public void execute() {
        if (!Boolean.TRUE.equals(reportGmvEnabled)) {
            log.info("【Maollar 心跳】GMV 链上上报未启用，跳过本次任务。");
            return;
        }

        log.info("【Maollar 心跳】开始上报每小时 GMV 数据...");
        try {
            // 1. 获取基于 Stripe 真实收款的全局销售额 (USD)
            double totalSalesUSD = stripePaymentSnapshotService.getCompletedTotalSalesUSD();
            long hourBucket = System.currentTimeMillis() / 3600000L;

            // 3. 构造请求体
            Map<String, Object> body = new HashMap<>();
            body.put("type", "REPORT_GMV");
            body.put("request_id", "REPORT_GMV_" + hourBucket);
            body.put("total_gmv_usd", totalSalesUSD);
            body.put("audit_hash", buildGmvAuditHash(hourBucket, totalSalesUSD));
            body.put("audit_scope", "STRIPE_COMPLETED_TOTAL_SALES_USD");

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

    private String buildGmvAuditHash(long hourBucket, double totalSalesUSD) {
        String normalizedSales = BigDecimal.valueOf(totalSalesUSD)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return SecureUtil.sha256("MAOLLAR_GMV_REPORT_V1|REPORT_GMV_" + hourBucket + "|" + normalizedSales);
    }
}
