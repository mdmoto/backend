package cn.lili.modules.order.cart.render.impl;

import cn.lili.common.utils.CurrencyUtil;
import cn.lili.modules.member.entity.dos.MemberAddress;
import cn.lili.modules.order.cart.entity.dto.TradeDTO;
import cn.lili.modules.order.cart.entity.enums.DeliveryMethodEnum;
import cn.lili.modules.order.cart.entity.enums.RenderStepEnums;
import cn.lili.modules.order.cart.entity.vo.CartSkuVO;
import cn.lili.modules.order.cart.render.CartRenderStep;
import cn.lili.modules.store.entity.dos.FreightTemplateChild;
import cn.lili.modules.store.entity.dos.StoreAddress;
import cn.lili.modules.store.entity.dto.FreightTemplateChildDTO;
import cn.lili.modules.store.entity.enums.FreightTemplateEnum;
import cn.lili.modules.store.entity.vos.FreightTemplateVO;
import cn.lili.modules.store.service.FreightTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * sku 运费计算
 *
 * @author Chopper
 * @since 2020-07-02 14:47
 */
@Service
public class SkuFreightRender implements CartRenderStep {

    @Autowired
    private FreightTemplateService freightTemplateService;

    @Autowired
    private cn.lili.modules.logistics.calculation.LogisticsCalculationService logisticsCalculationService;

    @Override
    public RenderStepEnums step() {
        return RenderStepEnums.SKU_FREIGHT;
    }

    @Override
    public void render(TradeDTO tradeDTO) {
        List<CartSkuVO> cartSkuVOS = tradeDTO.getCheckedSkuList();
        //会员收货地址问题处理
        MemberAddress memberAddress = tradeDTO.getMemberAddress();
        StoreAddress storeAddress = tradeDTO.getStoreAddress();
        //如果收货地址为空，则抛出异常
        if (memberAddress == null && storeAddress == null) {
            return;
        }

        // 选择物流时候计算价格
        if (DeliveryMethodEnum.LOGISTICS.name().equals(tradeDTO.getCartList().get(0).getDeliveryMethod())) {
            if (memberAddress != null) {
                // 如果是非中国地址，尝试优先通过 4PX 接口获取运费预估
                if (!"CN".equals(memberAddress.getCountryCode())) {
                    calculateInternationalFreight(tradeDTO);
                    // 如果 4PX 成功获取到了报价信息，则认为已处理完成（业务上优先以 4PX 为准）
                    if (tradeDTO.getLogisticsQuotes() != null && !tradeDTO.getLogisticsQuotes().isEmpty()) {
                        return;
                    }
                }

                // 运费分组信息 (按运费模板分组)
                Map<String, List<String>> freightGroups = freightTemplateGrouping(cartSkuVOS);

                // 循环各个运费模板组
                for (Map.Entry<String, List<String>> freightTemplateGroup : freightGroups.entrySet()) {
                    List<String> skuIds = freightTemplateGroup.getValue();
                    List<CartSkuVO> currentCartSkus = cartSkuVOS.stream()
                            .filter(item -> skuIds.contains(item.getGoodsSku().getId()))
                            .collect(Collectors.toList());

                    // 寻找对应对商品运费计算模版
                    FreightTemplateVO freightTemplate = freightTemplateService.getFreightTemplate(freightTemplateGroup.getKey());

                    if (freightTemplate != null && freightTemplate.getFreightTemplateChildList() != null && !freightTemplate.getFreightTemplateChildList().isEmpty()) {
                        // 店铺模版免运费则跳过
                        if (FreightTemplateEnum.FREE.name().equals(freightTemplate.getPricingMethod())) {
                            continue;
                        }

                        // 运费模版匹配：尝试匹配地址路径中的任何一个 ID (国家、省、市均可)
                        FreightTemplateChild freightTemplateChild = null;
                        String[] addressIds = memberAddress.getConsigneeAddressIdPath().split(",");
                        for (FreightTemplateChild templateChild : freightTemplate.getFreightTemplateChildList()) {
                            for (String addressId : addressIds) {
                                if (templateChild.getAreaId().contains(addressId)) {
                                    freightTemplateChild = templateChild;
                                    break;
                                }
                            }
                            if (freightTemplateChild != null) {
                                break;
                            }
                        }

                        // 处理匹配结果
                        if (freightTemplateChild == null) {
                            // 如果是国内地址或者没有任何补偿，则标记不支持配送
                            if (tradeDTO.getNotSupportFreight() == null) {
                                tradeDTO.setNotSupportFreight(new ArrayList<>());
                            }
                            tradeDTO.getNotSupportFreight().addAll(currentCartSkus);
                            continue;
                        }

                        // 匹配成功，开始计算
                        FreightTemplateChildDTO freightTemplateChildDTO = new FreightTemplateChildDTO(freightTemplateChild);
                        freightTemplateChildDTO.setPricingMethod(freightTemplate.getPricingMethod());

                        // 计算计费用基数 (重量或件数)
                        Double count = currentCartSkus.stream().mapToDouble(item ->
                                FreightTemplateEnum.NUM.name().equals(freightTemplateChildDTO.getPricingMethod()) ?
                                        item.getNum().doubleValue() :
                                        CurrencyUtil.mul(item.getNum(), item.getGoodsSku().getWeight())
                        ).sum();

                        // 计算运费
                        Double countFreight = countFreight(count, freightTemplateChildDTO);

                        // 写入 SKU 运费分摊
                        resetFreightPrice(FreightTemplateEnum.valueOf(freightTemplateChildDTO.getPricingMethod()), count, countFreight, currentCartSkus);
                    }
                }
            }
        } else {
            //自提清空不配送商品
            tradeDTO.setNotSupportFreight(null);
        }
    }


    /**
     * sku运费写入
     *
     * @param freightTemplateEnum 运费计算模式
     * @param count               计费基数总数
     * @param countFreight        总运费
     * @param cartSkuVOS          与运费相关的购物车商品
     */
    private void resetFreightPrice(FreightTemplateEnum freightTemplateEnum, Double count, Double countFreight, List<CartSkuVO> cartSkuVOS) {

        //剩余运费 默认等于总运费
        Double surplusFreightPrice = countFreight;

        //当前下标
        int index = 1;
        for (CartSkuVO cartSkuVO : cartSkuVOS) {
            //如果是最后一个 则将剩余运费直接赋值
            //PS: 循环中避免百分比累加不等于100%，所以最后一个运费不以比例计算，直接将剩余运费赋值
            if (index == cartSkuVOS.size()) {
                cartSkuVO.getPriceDetailDTO().setFreightPrice(surplusFreightPrice);
                break;
            }

            Double freightPrice = freightTemplateEnum == FreightTemplateEnum.NUM ?
                    CurrencyUtil.mul(countFreight, CurrencyUtil.div(cartSkuVO.getNum(), count)) :
                    CurrencyUtil.mul(countFreight,
                            CurrencyUtil.div(CurrencyUtil.mul(cartSkuVO.getNum(), cartSkuVO.getGoodsSku().getWeight()), count));

            //剩余运费=总运费-当前循环的商品运费
            surplusFreightPrice = CurrencyUtil.sub(surplusFreightPrice, freightPrice);

            cartSkuVO.getPriceDetailDTO().setFreightPrice(freightPrice);
            index++;
        }
    }

    /**
     * 运费模版分组
     *
     * @param cartSkuVOS 购物车商品
     * @return map<运费模版id ， List < skuid>>
     */
    private Map<String, List<String>> freightTemplateGrouping(List<CartSkuVO> cartSkuVOS) {
        Map<String, List<String>> map = new HashMap<>();
        //循环渲染购物车商品运费价格
        for (CartSkuVO cartSkuVO : cartSkuVOS) {
            ////免运费判定
            String freightTemplateId = cartSkuVO.getGoodsSku().getFreightTemplateId();
            if (Boolean.TRUE.equals(cartSkuVO.getIsFreeFreight()) || freightTemplateId == null) {
                continue;
            }
            //包含 则value值中写入sku标识，否则直接写入新的对象，key为模版id，value为new arraylist
            if (map.containsKey(freightTemplateId)) {
                map.get(freightTemplateId).add(cartSkuVO.getGoodsSku().getId());
            } else {
                List<String> skuIdsList = new ArrayList<>();
                skuIdsList.add(cartSkuVO.getGoodsSku().getId());
                map.put(freightTemplateId, skuIdsList);
            }
        }
        return map;
    }


    /**
     * 计算运费
     *
     * @param count    重量/件
     * @param template 计算模版
     * @return 运费
     */
    private Double countFreight(Double count, FreightTemplateChildDTO template) {
        try {
            Double finalFreight = template.getFirstPrice();
            //不满首重 / 首件
            if (template.getFirstCompany() >= count) {
                return finalFreight;
            }
            //如果续重/续件，费用不为空，则返回
            if (template.getContinuedCompany() == 0 || template.getContinuedPrice() == 0) {
                return finalFreight;
            }

            //计算 续重 / 续件 价格
            Double continuedCount = count - template.getFirstCompany();
            return CurrencyUtil.add(finalFreight,
                    CurrencyUtil.mul(Math.ceil(continuedCount / template.getContinuedCompany()), template.getContinuedPrice()));
        } catch (Exception e) {
            return 0D;
        }
    }

    /**
     * 计算国际运费（通过外部 API 如 4PX）
     */
    private void calculateInternationalFreight(TradeDTO tradeDTO) {
        MemberAddress address = tradeDTO.getMemberAddress();
        List<CartSkuVO> checkedSkus = tradeDTO.getCheckedSkuList();
        if (checkedSkus.isEmpty()) {
            return;
        }

        cn.lili.modules.logistics.calculation.LogisticsEstimateRequest request = new cn.lili.modules.logistics.calculation.LogisticsEstimateRequest();
        request.setCountryCode(address.getCountryCode());
        request.setPostalCode(address.getPostalCode());
        request.setCity(address.getCity());
        request.setState(address.getProvince());

        List<cn.lili.modules.logistics.calculation.LogisticsEstimateSkuLine> lines = new ArrayList<>();
        double totalWeight = 0;
        double maxLength = 0;
        double maxWidth = 0;
        double totalHeight = 0;

        for (CartSkuVO sku : checkedSkus) {
            cn.lili.modules.logistics.calculation.LogisticsEstimateSkuLine line = new cn.lili.modules.logistics.calculation.LogisticsEstimateSkuLine();
            line.setSkuId(sku.getGoodsSku().getId());
            line.setName(sku.getGoodsSku().getGoodsName());
            line.setQuantity(sku.getNum());
            line.setWeightKg(sku.getGoodsSku().getWeight());
            lines.add(line);

            // 累加总重量
            totalWeight = CurrencyUtil.add(totalWeight, CurrencyUtil.mul(sku.getGoodsSku().getWeight(), sku.getNum()));

            // 聚合尺寸 (简易算法：长宽取最大，高度根据件数累加)
            Double l = sku.getGoodsSku().getGoodsLength() != null ? sku.getGoodsSku().getGoodsLength() : 10D;
            Double w = sku.getGoodsSku().getGoodsWidth() != null ? sku.getGoodsSku().getGoodsWidth() : 10D;
            Double h = sku.getGoodsSku().getGoodsHeight() != null ? sku.getGoodsSku().getGoodsHeight() : 10D;

            maxLength = Math.max(maxLength, l);
            maxWidth = Math.max(maxWidth, w);
            totalHeight = CurrencyUtil.add(totalHeight, CurrencyUtil.mul(h, sku.getNum()));
        }

        request.setSkuLines(lines);
        request.setTotalWeightKg(totalWeight);
        request.setLengthCm(maxLength);
        request.setWidthCm(maxWidth);
        request.setHeightCm(totalHeight);

        try {
            List<cn.lili.modules.logistics.calculation.LogisticsQuote> quotes = logisticsCalculationService.estimate(request);
            tradeDTO.setLogisticsQuotes(quotes);

            if (quotes != null && !quotes.isEmpty()) {
                // 默认选择第一个渠道的报价作为预估运费
                Double estimatesFreight = quotes.get(0).getAmount();
                // 将总运费分摊到各个 SKU 上，保证 TradeDTO.priceDetailDTO 计算准确
                resetFreightPrice(FreightTemplateEnum.NUM, (double) checkedSkus.size(), estimatesFreight, checkedSkus);
            }
        } catch (Exception e) {
            // 如果试算失败，可以记录日志或抛出异常，这里选择记录日志并跳过运费计算（或者设为不支持配送）
            tradeDTO.setNotSupportFreight(checkedSkus);
        }
    }


}
