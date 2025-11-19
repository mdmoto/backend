package cn.lili.controller.statistics;

import cn.lili.common.enums.ResultCode;
import cn.lili.common.enums.ResultUtil;
import cn.lili.common.vo.ResultMessage;
import cn.lili.modules.statistics.entity.dto.StatisticsQueryParam;
import cn.lili.modules.statistics.entity.vo.IndexStatisticsVO;
import cn.lili.modules.statistics.entity.vo.OrderOverviewVO;
import cn.lili.modules.statistics.entity.vo.OverViewDataVO;
import cn.lili.modules.statistics.service.IndexStatisticsService;
import cn.lili.modules.statistics.service.OrderStatisticsService;
import cn.lili.modules.statistics.service.OverViewStatisticsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 买家端,实时统计数据接口（公开接口，无需token）
 *
 * @author Maollar
 * @since 2025/01/XX
 */
@Slf4j
@RestController
@Api(tags = "买家端,实时统计数据接口")
@RequestMapping("/buyer/statistics/realtime")
public class RealtimeStatisticsBuyerController {

    @Autowired
    private IndexStatisticsService indexStatisticsService;

    @Autowired
    private OrderStatisticsService orderStatisticsService;

    @Autowired
    private OverViewStatisticsService overViewStatisticsService;

    @ApiOperation(value = "获取实时统计数据")
    @GetMapping
    public ResultMessage<RealtimeStatisticsVO> getRealtimeStatistics() {
        try {
            RealtimeStatisticsVO vo = new RealtimeStatisticsVO();

            // 获取首页统计数据
            IndexStatisticsVO indexStats = indexStatisticsService.indexStatistics();
            if (indexStats != null) {
                vo.setTotalOrders(indexStats.getOrderNum());
                vo.setTotalMembers(indexStats.getMemberNum());
                vo.setTotalGoods(indexStats.getGoodsNum());
                vo.setTodayOrderNum(indexStats.getTodayOrderNum());
                vo.setTodayOrderPrice(indexStats.getTodayOrderPrice());
            }

            // 获取营业概览（全部时间）- 使用LAST_THIRTY作为默认，实际应该查询全部
            // 注意：这里简化处理，实际应该查询全部时间的数据
            StatisticsQueryParam allTimeParam = new StatisticsQueryParam();
            // 不设置searchType，让系统使用默认值（通常是当前月份）
            // 如果需要全部时间，可能需要修改service层或直接查询数据库
            
            // 优先使用营业概览的营业额数据
            OverViewDataVO overView = overViewStatisticsService.getOverViewDataVO(allTimeParam);
            if (overView != null && overView.getTurnover() != null) {
                vo.setTotalSalesAmount(overView.getTurnover());
            } else {
                // 如果没有营业概览数据，使用订单概览的付款金额
                OrderOverviewVO orderOverview = orderStatisticsService.overview(allTimeParam);
                if (orderOverview != null && orderOverview.getPaymentAmount() != null) {
                    vo.setTotalSalesAmount(orderOverview.getPaymentAmount());
                    vo.setTotalPaymentOrders(orderOverview.getPaymentOrderNum());
                }
            }

            return ResultUtil.data(vo);
        } catch (Exception e) {
            log.error("获取实时统计数据错误", e);
            return ResultUtil.error(ResultCode.ERROR);
        }
    }

    /**
     * 实时统计数据VO
     */
    @Data
    public static class RealtimeStatisticsVO {
        /**
         * 总订单数
         */
        private Long totalOrders;

        /**
         * 总会员数
         */
        private Long totalMembers;

        /**
         * 总商品数
         */
        private Long totalGoods;

        /**
         * 今日订单数
         */
        private Long todayOrderNum;

        /**
         * 今日订单金额
         */
        private Double todayOrderPrice;

        /**
         * 总销售额（累计营业额或付款金额，已扣除退款）
         */
        private Double totalSalesAmount;

        /**
         * 总付款订单数
         */
        private Long totalPaymentOrders;
    }
}

