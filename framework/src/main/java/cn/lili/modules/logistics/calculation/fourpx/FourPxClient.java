package cn.lili.modules.logistics.calculation.fourpx;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.properties.FourPxProperties;
import cn.lili.modules.logistics.calculation.LogisticsEstimateRequest;
import cn.lili.modules.logistics.calculation.LogisticsQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 4PX(FOP) 运费试算客户端实现。
 */
@Slf4j
@Component
public class FourPxClient {

    @Autowired
    private FourPxProperties fourPxProperties;

    private static final String METHOD_DS = "ds.xms.estimated_cost.get";
    private static final String VERSION = "1.0.0";

    public List<LogisticsQuote> estimateCost(LogisticsEstimateRequest request) {
        if (fourPxProperties == null
                || fourPxProperties.getAppKey() == null
                || fourPxProperties.getAppKey().isBlank()
                || fourPxProperties.getAppSecret() == null
                || fourPxProperties.getAppSecret().isBlank()
                || fourPxProperties.getAppKey().contains("YOUR_APP_KEY")) {
            log.warn("4PX AppKey is not configured, skipping API call.");
            return new ArrayList<>();
        }

        try {
            // 1. 业务参数 (Business Parameters) - 使用 LinkedHashMap 保证顺序一致性
            java.util.LinkedHashMap<String, Object> bizParams = new java.util.LinkedHashMap<>();
            bizParams.put("request_no", ""); // 试算时传空字符串
            bizParams.put("country_code", request.getCountryCode());
            // 4PX 此接口通常使用克(g)为单位，数值建议转为字符串以保证签名一致
            bizParams.put("weight", String.valueOf((int) (request.getTotalWeightKg() * 1000)));
            // 如果请求中没有尺寸，传默认值
            bizParams.put("length", String.valueOf(request.getLengthCm() != null ? request.getLengthCm().intValue() : 10));
            bizParams.put("width", String.valueOf(request.getWidthCm() != null ? request.getWidthCm().intValue() : 10));
            bizParams.put("height", String.valueOf(request.getHeightCm() != null ? request.getHeightCm().intValue() : 10));
            bizParams.put("cargocode", "P"); // 默认 Parcel

            String body = JSONUtil.toJsonStr(bizParams);
            String timestamp = String.valueOf(System.currentTimeMillis());

            // 2. 签名计算 (Signature Algorithm: MD5(sorted(key+value) + body + secret))
            java.util.TreeMap<String, String> signParams = new java.util.TreeMap<>();
            signParams.put("app_key", fourPxProperties.getAppKey());
            signParams.put("format", "json");
            signParams.put("method", METHOD_DS);
            signParams.put("timestamp", timestamp);
            signParams.put("v", VERSION);

            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, String> entry : signParams.entrySet()) {
                sb.append(entry.getKey()).append(entry.getValue());
            }
            sb.append(body);
            sb.append(fourPxProperties.getAppSecret());

            String sign = SecureUtil.md5(sb.toString());

            // 3. 构建请求 URL
            String url = String.format("%s/router/api/service?method=%s&app_key=%s&v=%s&timestamp=%s&format=json&sign=%s",
                    fourPxProperties.getBaseUrl(), METHOD_DS, fourPxProperties.getAppKey(), VERSION, timestamp, sign);

            log.debug("4PX estimate request country={}, weight_g={}, size_cm={}x{}x{}",
                    request.getCountryCode(),
                    bizParams.get("weight"),
                    bizParams.get("length"),
                    bizParams.get("width"),
                    bizParams.get("height"));

            String response = HttpUtil.post(url, body);
            log.debug("4PX estimate response: {}", response);

            JSONObject respJson = JSONUtil.parseObj(response);
            // 4PX 的结果码：result为1表示成功
            if (!"1".equals(respJson.getStr("result"))) {
                log.error("4PX API Error: {}", response);
                return new ArrayList<>();
            }

            // 4. 处理结果集
            // 注意：4PX 返回的数据在 "data" 字段下，是一个字符串形式的 JSON 数组
            String dataStr = respJson.getStr("data");
            JSONArray data = JSONUtil.parseArray(dataStr);
            List<LogisticsQuote> quotes = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    LogisticsQuote quote = new LogisticsQuote();
                    // 4PX 返回字段映射
                    quote.setServiceCode(item.getStr("logistics_product_code"));
                    quote.setServiceName("4PX - " + item.getStr("logistics_product_code"));
                    quote.setAmount(item.getDouble("lump_sum_fee")); // 总费用
                    quotes.add(quote);
                }
            }
            return quotes;

        } catch (Exception e) {
            log.error("Failed to estimate cost via 4PX", e);
            throw new ServiceException("4PX 运费试算接口异常：" + e.getMessage());
        }
    }
}
