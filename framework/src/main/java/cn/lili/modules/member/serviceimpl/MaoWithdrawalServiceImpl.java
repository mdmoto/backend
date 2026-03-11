package cn.lili.modules.member.serviceimpl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.context.UserContext;
import cn.lili.modules.member.entity.dos.MaoWithdrawalLog;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.entity.enums.MaoIssueStatusEnum;
import cn.lili.modules.member.entity.enums.PointTypeEnum;
import cn.lili.modules.member.mapper.MaoWithdrawalLogMapper;
import cn.lili.modules.member.service.MaoWithdrawalService;
import cn.lili.modules.member.service.MemberService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * $MAO 提现业务层实现 (Cloudflare Worker 增强安全版)
 */
@Slf4j
@Service
public class MaoWithdrawalServiceImpl extends ServiceImpl<MaoWithdrawalLogMapper, MaoWithdrawalLog>
        implements MaoWithdrawalService {

    @Autowired
    private MemberService memberService;

    @Value("${lili.maollar.solana.gateway-url:}")
    private String gatewayUrl;

    @Value("${lili.maollar.solana.gateway-secret:}")
    private String gatewaySecret;

    @Value("${lili.maollar.solana.daily-max-issuance:1000.0}")
    private Double dailyMaxIssuance;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaoWithdrawalLog applyWithdrawal(Long points, String solanaWalletAddress) {
        String memberId = UserContext.getCurrentUser().getId();
        Member member = memberService.getById(memberId);

        // 核心变动：在 $MAO 经济模型下，我们优先鼓励钱包绑定
        // 如果需要 KYC 可以在未来作为大额兑换的要求，目前先放开
        if (member == null) {
            throw new ServiceException(ResultCode.USER_NOT_EXIST);
        }
        if (member.getPoint() < points) {
            throw new ServiceException(ResultCode.USER_POINTS_ERROR);
        }

        MaoWithdrawalLog withdrawalLog = new MaoWithdrawalLog();
        withdrawalLog.setMemberId(memberId);
        withdrawalLog.setPoints(points);
        // 修正：内部存储为 micro-coin (10^6)，发送给网关前需要换算为标准单位
        withdrawalLog.setMaoIssuedAmount(
                new BigDecimal(points).divide(BigDecimal.valueOf(1000000L), 6, java.math.RoundingMode.HALF_UP));
        withdrawalLog.setSolanaWalletAddress(solanaWalletAddress);
        withdrawalLog.setMaoIssueStatus(MaoIssueStatusEnum.NONE.name());
        this.save(withdrawalLog);

        return withdrawalLog;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeWithdrawal(String withdrawalLogId) {
        MaoWithdrawalLog withdrawalLog = this.getById(withdrawalLogId);
        if (withdrawalLog == null || !withdrawalLog.getMaoIssueStatus().equals(MaoIssueStatusEnum.NONE.name())) {
            return;
        }

        double dailyIssued = getDailyTotalIssued();
        if (dailyIssued + withdrawalLog.getMaoIssuedAmount().doubleValue() > dailyMaxIssuance) {
            log.error("【熔断告警】当日 $MAO 发放量已达上限，停止自动发放。");
            throw new ServiceException(ResultCode.ERROR, "系统提现额度已达今日上限");
        }

        // 开启积分扣除
        boolean deductResult = memberService.updateMemberPoint(withdrawalLog.getPoints(), PointTypeEnum.REDUCE.name(),
                withdrawalLog.getMemberId(), "$MAO 兑换扣除积分", 0.0);

        if (!deductResult) {
            withdrawalLog.setMaoIssueStatus(MaoIssueStatusEnum.FAILED.name());
            withdrawalLog.setErrorLog("积分不足扣减失败");
            this.updateById(withdrawalLog);
            throw new ServiceException(ResultCode.USER_POINTS_ERROR);
        }

        // 调用 Cloudflare Worker 签名网关 (带 HMAC 校验)
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "WITHDRAW"); // 显式声明提现类型
            body.put("amount", withdrawalLog.getMaoIssuedAmount().toPlainString());
            body.put("user_wallet_address", withdrawalLog.getSolanaWalletAddress());

            String timestamp = String.valueOf(System.currentTimeMillis());
            String bodyStr = JSONUtil.toJsonStr(body);
            // 生成动态 HMAC-SHA256 签名 (防重放)
            String signature = SecureUtil.hmacSha256(gatewaySecret).digestHex(timestamp + bodyStr);

            log.info("向 Cloudflare Worker 发送兑换请求: {}", gatewayUrl);

            String result = HttpUtil.createPost(gatewayUrl)
                    .header("X-MAO-Timestamp", timestamp)
                    .header("X-MAO-Signature", signature)
                    .body(bodyStr)
                    .timeout(30000) // 增加超时时间，链上交易可能较慢
                    .execute()
                    .body();

            log.info("Cloudflare Worker 响应结果: {}", result);
            JSONObject json = JSONUtil.parseObj(result);

            if (json.getBool("success")) {
                withdrawalLog.setMaoTxHash(json.getStr("txHash"));
                withdrawalLog.setMaoIssueStatus(MaoIssueStatusEnum.SUCCESS.name());
                this.updateById(withdrawalLog);
            } else {
                // 如果网关返回失败，抛出异常触发退分逻辑
                throw new Exception("链上执行失败: " + json.getStr("message"));
            }
        } catch (Exception e) {
            log.error("【后悔药】$MAO 兑换失败，正在执行积分退回: {}", e.getMessage());

            // 1. 尝试退回积分
            try {
                memberService.updateMemberPoint(withdrawalLog.getPoints(), PointTypeEnum.INCREASE.name(),
                        withdrawalLog.getMemberId(), "$MAO 兑换失败退回积分", 0.0);
            } catch (Exception re) {
                log.error("【严重异常】积分退回失败，用户 ID: {}, 积分量: {}", withdrawalLog.getMemberId(), withdrawalLog.getPoints());
            }

            // 2. 更新日志为失败状态
            withdrawalLog.setMaoIssueStatus(MaoIssueStatusEnum.FAILED.name());
            withdrawalLog.setErrorLog(e.getMessage());
            this.updateById(withdrawalLog);

            throw new ServiceException(ResultCode.ERROR, "兑换失败：" + e.getMessage());
        }
    }

    @Override
    public double getDailyTotalIssued() {
        QueryWrapper<MaoWithdrawalLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mao_issue_status", MaoIssueStatusEnum.SUCCESS.name());
        queryWrapper.ge("create_time", new Date(System.currentTimeMillis() - 86400000L));
        return getIssuedSum(queryWrapper);
    }

    @Override
    public double getTotalIssuedAmount() {
        QueryWrapper<MaoWithdrawalLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mao_issue_status", MaoIssueStatusEnum.SUCCESS.name());
        return getIssuedSum(queryWrapper);
    }

    private double getIssuedSum(QueryWrapper<MaoWithdrawalLog> queryWrapper) {
        queryWrapper.select("IFNULL(sum(mao_issued_amount), 0) as totalAmount");
        Map<String, Object> map = this.getMap(queryWrapper);
        if (map != null && map.get("totalAmount") != null) {
            return Double.parseDouble(map.get("totalAmount").toString());
        }
        return 0.0;
    }
}
