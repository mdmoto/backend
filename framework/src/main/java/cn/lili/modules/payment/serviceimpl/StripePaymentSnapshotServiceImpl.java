package cn.lili.modules.payment.serviceimpl;

import cn.lili.modules.payment.entity.StripePaymentSnapshot;
import cn.lili.modules.payment.mapper.StripePaymentSnapshotMapper;
import cn.lili.modules.payment.service.StripePaymentSnapshotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
                .eq(StripePaymentSnapshot::getPaymentStatus, "CONFIRMED"));
    }

    @Override
    public double getCompletedTotalSalesUSD() {
        // 求和已确认的真实净收款 (USD)
        QueryWrapper<StripePaymentSnapshot> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("sum(amount_net_usd) as totalSales");
        queryWrapper.eq("payment_status", "CONFIRMED");
        Map<String, Object> map = this.getMap(queryWrapper);
        if (map != null && map.get("totalSales") != null) {
            return Double.parseDouble(map.get("totalSales").toString());
        }
        return 0.0;
    }
}
