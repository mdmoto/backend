package cn.lili.modules.order.cart.service;

import cn.lili.cache.Cache;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.context.UserContext;
import cn.lili.modules.member.service.MemberAddressService;
import cn.lili.modules.order.cart.entity.dto.TradeDTO;
import cn.lili.modules.order.cart.entity.enums.CartTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 购物车持久化业务层实现
 *
 * @author Chopper
 * @since 2024-03-11
 */
@Service
public class CartPersistenceServiceImpl implements CartPersistenceService {

    @Autowired
    private Cache<Object> cache;

    @Autowired
    private MemberAddressService memberAddressService;

    @Override
    public TradeDTO readDTO(CartTypeEnum checkedWay) {
        TradeDTO tradeDTO = (TradeDTO) cache.get(this.getOriginKey(checkedWay));
        if (tradeDTO == null) {
            tradeDTO = new TradeDTO(checkedWay);
            AuthUser currentUser = UserContext.getCurrentUser();
            if (currentUser != null) {
                tradeDTO.setMemberId(currentUser.getId());
                tradeDTO.setMemberName(currentUser.getUsername());
            }
        }
        if (tradeDTO.getMemberAddress() == null) {
            tradeDTO.setMemberAddress(this.memberAddressService.getDefaultMemberAddress());
        }
        return tradeDTO;
    }

    @Override
    public void resetTradeDTO(TradeDTO tradeDTO) {
        cache.put(this.getOriginKey(tradeDTO.getCartTypeEnum()), tradeDTO);
    }

    @Override
    public void clean() {
        cache.remove(this.getOriginKey(CartTypeEnum.CART));
    }

    /**
     * 读取当前会员购物原始数据key
     *
     * @param cartTypeEnum 获取方式
     * @return 当前会员购物原始数据key
     */
    private String getOriginKey(CartTypeEnum cartTypeEnum) {
        if (cartTypeEnum != null) {
            AuthUser currentUser = UserContext.getCurrentUser();
            if (currentUser != null) {
                return cartTypeEnum.getPrefix() + currentUser.getId();
            }
        }
        throw new ServiceException(ResultCode.USER_NOT_EXIST);
    }
}
