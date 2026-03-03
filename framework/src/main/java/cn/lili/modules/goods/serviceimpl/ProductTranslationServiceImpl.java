package cn.lili.modules.goods.serviceimpl;

import cn.lili.modules.goods.entity.dos.ProductTranslation;
import cn.lili.modules.goods.mapper.ProductTranslationMapper;
import cn.lili.modules.goods.service.ProductTranslationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品多语言翻译业务层实现
 *
 * @author MaoMall
 * @since 2026-03-03
 */
@Service
public class ProductTranslationServiceImpl extends ServiceImpl<ProductTranslationMapper, ProductTranslation>
        implements ProductTranslationService {

    @Override
    public List<ProductTranslation> listBySpuId(String spuId) {
        return this.list(new LambdaQueryWrapper<ProductTranslation>().eq(ProductTranslation::getSpuId, spuId));
    }
}
