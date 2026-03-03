package cn.lili.modules.goods.service;

import cn.lili.modules.goods.entity.dos.ProductTranslation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 商品多语言翻译业务层
 *
 * @author MaoMall
 * @since 2026-03-03
 */
public interface ProductTranslationService extends IService<ProductTranslation> {

    /**
     * 根据商品ID获取翻译列表
     * 
     * @param spuId 商品ID
     * @return 翻译列表
     */
    List<ProductTranslation> listBySpuId(String spuId);
}
