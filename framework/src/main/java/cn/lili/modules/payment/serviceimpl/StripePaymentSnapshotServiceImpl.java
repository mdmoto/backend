package cn.lili.modules.payment.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.mapper.StripePaymentSnapshotMapper;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.Date;
import java.util.Map;

/**
 * Stripe 支付快照服务实现
 */
@Service
public class StripePaymentSnapshotServiceImpl extends ServiceImpl<StripePaymentSnapshotMapper, StripePaymentSnapshot> implements StripePaymentSnapshotService {

    @Override
    public StripePaymentSnapshot getConfirmedSnapshot(String orderSn) {
        return this.getOne(new LambdaQueryWrapper<StripePaymentSnapshot>()
                .eq(StripePaymentSnapshot::getOrderSn, orderSn)
                .eq(StripePaymentSnapshot::getPaymentStatus, "CONFIRMED")
                .isNotNull(StripePaymentSnapshot::getAmountNetUsd)
                .orderByDesc(StripePaymentSnapshot::getUpdateTime), false); // false = don't throw exception if multiple found
    }

    @Override
    public double getCompletedTotalSalesUSD() {
        return getCompletedTotalSalesAmountUSD().doubleValue();
    }

    @Override
    public BigDecimal getCompletedTotalSalesAmountUSD() {
        // 求和已确认的真实净收款 (USD)
        QueryWrapper<StripePaymentSnapshot> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(sum(amount_net_usd), 0) as totalSales");
        queryWrapper.eq("payment_status", "CONFIRMED");
        queryWrapper.gt("amount_net_usd", 0);
        return queryTotalSales(queryWrapper);
    }

    @Override
    public BigDecimal getCompletedTotalSalesAmountUSDUpTo(StripePaymentSnapshot snapshot) {
        if (snapshot == null || snapshot.getUpdateTime() == null) {
            return getCompletedTotalSalesAmountUSD();
        }

        QueryWrapper<StripePaymentSnapshot> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("IFNULL(sum(amount_net_usd), 0) as totalSales");
        queryWrapper.eq("payment_status", "CONFIRMED");
        queryWrapper.gt("amount_net_usd", 0);

        Date updateTime = snapshot.getUpdateTime();
        if (CharSequenceUtil.isNotBlank(snapshot.getId())) {
            queryWrapper.and(wrapper -> wrapper.lt("update_time", updateTime)
                    .or()
                    .eq("update_time", updateTime)
                    .le("id", snapshot.getId()));
        } else {
            queryWrapper.le("update_time", updateTime);
        }

        return queryTotalSales(queryWrapper);
    }

    private BigDecimal queryTotalSales(QueryWrapper<StripePaymentSnapshot> queryWrapper) {
        Map<String, Object> map = this.getMap(queryWrapper);
        if (map != null && map.get("totalSales") != null) {
            return new BigDecimal(map.get("totalSales").toString());
        }
        return BigDecimal.ZERO;
    }
}
