package cn.lili.modules.goods.service;

import cn.lili.modules.goods.entity.dos.Goods;

/**
 * 商品AI上下文业务层
 */
public interface AiContextService {

    /**
     * 为指定商品生成并持久化AI上下文
     * @param goodsId 商品ID
     */
    void generateContext(String goodsId);

    /**
     * 根据商品对象生成AI上下文（用于预览或即时AI调用）
     * @param goods 商品对象
     * @return JSON 字符串
     */
    String getContextJson(Goods goods);
}
