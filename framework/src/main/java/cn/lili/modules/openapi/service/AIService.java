package cn.lili.modules.openapi.service;

public interface AIService {

    /**
     * 生成商品的 AI 总结，包括“喵币收益档位”和“投资建议”
     * @param goodsName 商品名称
     * @param price 价格
     * @return AI 生成的总结字符串
     */
    String generateGoodsSummary(String goodsName, Double price);
}
