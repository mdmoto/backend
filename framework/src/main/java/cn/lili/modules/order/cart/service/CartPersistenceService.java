package cn.lili.modules.order.cart.service;

import cn.lili.modules.order.cart.entity.dto.TradeDTO;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;

/**
 * 购物车持久化业务层
 *
 * @author Chopper
 * @since 2024-03-11
 */
public interface CartPersistenceService {

    /**
     * 读取当前会员购物原始数据
     *
     * @param checkedWay 购物车类型
     * @return 交易完成后的数据模型
     */
    TradeDTO readDTO(CartTypeEnum checkedWay);

    /**
     * 重新写入
     *
     * @param tradeDTO 购物车构建器最终要构建的成品
     */
    void resetTradeDTO(TradeDTO tradeDTO);

    /**
     * 清除选中的购物车数据
     */
    void clean();
}
