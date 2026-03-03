package cn.lili.listener;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.modules.goods.entity.dos.Goods;
import cn.lili.modules.goods.entity.dos.ProductTranslation;
import cn.lili.modules.goods.service.GoodsService;
import cn.lili.modules.goods.service.ProductTranslationService;
import cn.lili.rocketmq.tags.GoodsTagsEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商品多语言翻译监听器
 * 使用 Google Gemini 1.5 Flash 引擎进行国际化重写与翻译
 *
 * @author MaoMall
 * @since 2026-03-03
 */
@Component
@Slf4j
@RocketMQMessageListener(topic = "${lili.data.rocketmq.goods-topic}", consumerGroup = "product-translation-group")
public class ProductTranslationListener implements RocketMQListener<MessageExt> {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private ProductTranslationService productTranslationService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${lili.data.rocketmq.goods-topic}")
    private String goodsTopic;

    @Value("${lili.ai.api-key:PLACE_STUB}")
    private String aiApiKey;

    @Value("${lili.ai.api-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String aiApiUrl;

    @Override
    public void onMessage(MessageExt messageExt) {
        if (GoodsTagsEnum.GENERATOR_GOODS_INDEX.name().equals(messageExt.getTags())) {
            if ("true".equals(messageExt.getUserProperty("fromTranslation"))) {
                return;
            }

            String goodsId = new String(messageExt.getBody());
            log.info("触发多语言翻译流水线: {}", goodsId);
            Goods goods = goodsService.getById(goodsId);
            if (goods != null) {
                translateAndSave(goods);
            }
        }
    }

    private void translateAndSave(Goods goods) {
        try {
            // 构建重写提示词
            String prompt = String.format(
                    "You are a Silicon Valley Web3 e-commerce copywriter. Rewrite and translate the product title and description into 8 languages: en, ja, ko, ar, es, fr, th, de. "
                            +
                            "Tone: Minimalist, professional, and tech-forward. " +
                            "Source - Title: [%s], Description: [%s]. " +
                            "Output STRICTLY as a JSON object with 8 keys (en, ja, ko, ar, es, fr, th, de), each containing 'title' and 'description'.",
                    goods.getGoodsName(), goods.getIntro());

            // Gemini API 请求结构
            JSONObject requestBody = JSONUtil.createObj()
                    .set("contents", JSONUtil.createArray()
                            .add(JSONUtil.createObj().set("parts", JSONUtil.createArray()
                                    .add(JSONUtil.createObj().set("text", prompt)))))
                    .set("generationConfig", JSONUtil.createObj().set("responseMimeType", "application/json"));

            String urlWithKey = aiApiUrl + "?key=" + aiApiKey;

            log.info("正在发送翻译请求到 Gemini API...");
            String response = HttpRequest.post(urlWithKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .timeout(60000)
                    .execute().body();

            JSONObject jsonResponse = JSONUtil.parseObj(response);

            // 解析 Gemini 响应格式: candidates[0].content.parts[0].text
            String content = jsonResponse.getByPath("candidates[0].content.parts[0].text", String.class);
            if (CharSequenceUtil.isEmpty(content)) {
                log.error("Gemini 响应解析失败 or 内容为空. 原始响应: {}", response);
                return;
            }

            JSONObject translations = JSONUtil.parseObj(content);

            List<ProductTranslation> translationList = new ArrayList<>();
            for (Map.Entry<String, Object> entry : translations.entrySet()) {
                String lang = entry.getKey();
                JSONObject data = (JSONObject) entry.getValue();
                ProductTranslation pt = new ProductTranslation(
                        goods.getId(),
                        lang,
                        data.getStr("title"),
                        data.getStr("description"));
                translationList.add(pt);
            }

            if (!translationList.isEmpty()) {
                productTranslationService.remove(new LambdaQueryWrapper<ProductTranslation>()
                        .eq(ProductTranslation::getSpuId, goods.getId()));

                productTranslationService.saveBatch(translationList);
                log.info("商品 {} 的 8 国语言 AI 翻译已持久化完成", goods.getId());

                // 触发二次消息以刷新 ES 索引数据
                org.springframework.messaging.Message<String> message = MessageBuilder.withPayload(goods.getId())
                        .setHeader("fromTranslation", "true")
                        .build();
                rocketMQTemplate.send(goodsTopic + ":" + GoodsTagsEnum.GENERATOR_GOODS_INDEX.name(), message);
            }

        } catch (Exception e) {
            log.error("AI 翻译流水线异常 (Goods: {})", goods.getId(), e);
        }
    }
}
