package cn.lili.controller.promotion;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.vo.PageVO;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.promotion.entity.dos.PointsGoods;
import cn.lili.modules.promotion.entity.dos.PointsGoodsCategory;
import cn.lili.modules.promotion.entity.dto.search.PointsGoodsSearchParams;
import cn.lili.modules.promotion.entity.vos.PointsGoodsVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家端,积分商品接口 (已弃用，切换至 $MAO 经济模型)
 *
 * @author paulG
 * @since 2021/1/19
 **/
@RestController
@Api(tags = "买家端,喵币商品接口")
@RequestMapping("/buyer/promotion/pointsGoods")
public class PointsGoodsBuyerController {

    @GetMapping
    @ApiOperation(value = "分页获取喵币商品")
    public ResultMessage<IPage<PointsGoods>> getPointsGoodsPage(PointsGoodsSearchParams searchParams, PageVO page) {
        throw new ServiceException(ResultCode.PROMOTION_STATUS_END);
    }

    @GetMapping("/category")
    @ApiOperation(value = "获取喵币商品分类分页")
    public ResultMessage<IPage<PointsGoodsCategory>> page(String name, PageVO page) {
        throw new ServiceException(ResultCode.PROMOTION_STATUS_END);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取喵币活动商品")
    @ApiImplicitParam(name = "id", value = "喵币商品ID", required = true, paramType = "path")
    public ResultMessage<PointsGoodsVO> getPointsGoodsPage(@PathVariable String id) {
        throw new ServiceException(ResultCode.PROMOTION_STATUS_END);
    }

}
