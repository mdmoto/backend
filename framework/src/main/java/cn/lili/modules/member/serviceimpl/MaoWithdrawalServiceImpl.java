package cn.lili.modules.member.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${lili.maollar.solana.gateway-url:}")
    private String gatewayUrl;

    @Value("${lili.maollar.solana.gateway-secret:}")
    private String gatewaySecret;

    @Value("${lili.maollar.solana.daily-max-issuance:1000.0}")
    private Double dailyMaxIssuance;

    @Value("${lili.maollar.solana.exchange-enabled:false}")
    private Boolean exchangeEnabled;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaoWithdrawalLog applyWithdrawal(Long points, String solanaWalletAddress) {
        if (!Boolean.TRUE.equals(exchangeEnabled)) {
            throw new ServiceException(ResultCode.ERROR, "$MAO 兑换暂未开放，请等待主网合约完成部署与权限交割");
        }
        if (points == null || points <= 0 || CharSequenceUtil.isBlank(solanaWalletAddress)) {
            throw new ServiceException(ResultCode.PARAMS_ERROR);
        }

        String memberId = UserContext.getCurrentUser().getId();
        Member member = memberService.getById(memberId);

        // 合规性校验：大额或敏感操作需要 KYC 实名认证
        if (member == null) {
            throw new ServiceException(ResultCode.USER_NOT_EXIST);
        }
        
        // 开启 KYC 强制校验 (Phase 4 合规化)
        if (!Boolean.TRUE.equals(member.getIsKycVerified())) {
            log.warn("用户 {} 尝试兑换 $MAO 但未通过 KYC 认证", memberId);
            throw new ServiceException(ResultCode.ERROR, "请先完成实名认证 (KYC) 后再申请兑换奖励");
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
    public void executeWithdrawal(String withdrawalLogId) {
        // 1. 原子性状态更新 (CAS 锁)，防止并发重复执行
        // 只有当状态为 NONE 时，才将其更新为 PENDING，成功更新的线程才拥有执行权
        boolean claimed = this.update(new LambdaUpdateWrapper<MaoWithdrawalLog>()
                .set(MaoWithdrawalLog::getMaoIssueStatus, MaoIssueStatusEnum.PENDING.name())
                .eq(MaoWithdrawalLog::getId, withdrawalLogId)
                .eq(MaoWithdrawalLog::getMaoIssueStatus, MaoIssueStatusEnum.NONE.name()));

        if (!claimed) {
            log.warn("提现任务 {} 正在处理中或已被处理，跳过重复执行", withdrawalLogId);
            return;
        }

        MaoWithdrawalLog withdrawalLog = this.getById(withdrawalLogId);
        if (withdrawalLog == null) {
            return;
        }

        try {
            ensureDailyLimit(withdrawalLog);
            ensureGatewayConfigured();
            deductWithdrawalPoints(withdrawalLog);
        } catch (ServiceException e) {
            persistWithdrawalFailure(withdrawalLog.getId(), e.getMsg());
            throw e;
        }

        // 调用 Cloudflare Worker 签名网关 (带 HMAC 校验)
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "WITHDRAW"); 
            body.put("request_id", withdrawalLog.getId()); // 增加幂等 ID (Nonce)
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
                persistWithdrawalSuccess(withdrawalLog.getId(), json.getStr("txHash"));
            } else {
                throw new Exception("链上执行失败: " + json.getStr("message"));
            }
        } catch (Exception e) {
            log.error("【链上结果待核验】$MAO 兑换已扣分，但链上返回不确定，禁止自动退分: withdrawalLogId={}, message={}",
                    withdrawalLog.getId(), e.getMessage());
            persistWithdrawalChainUnknown(withdrawalLog.getId(), e.getMessage());
            throw new ServiceException(ResultCode.ERROR, "兑换已提交但链上结果待核验，请联系客服处理：" + e.getMessage());
        }
    }

    private void ensureDailyLimit(MaoWithdrawalLog withdrawalLog) {
        double dailyReserved = getDailyReservedIssued(withdrawalLog.getId());
        if (dailyReserved + withdrawalLog.getMaoIssuedAmount().doubleValue() > dailyMaxIssuance) {
            log.error("【熔断告警】当日 $MAO 发放量已达上限，停止自动发放。");
            throw new ServiceException(ResultCode.ERROR, "系统提现额度已达今日上限");
        }
    }

    private void ensureGatewayConfigured() {
        if (CharSequenceUtil.isBlank(gatewayUrl) || CharSequenceUtil.isBlank(gatewaySecret)) {
            throw new ServiceException(ResultCode.ERROR, "Cloudflare Worker 网关未配置");
        }
    }

    private void deductWithdrawalPoints(MaoWithdrawalLog withdrawalLog) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            boolean deductResult = memberService.updateMemberPoint(withdrawalLog.getPoints(), PointTypeEnum.REDUCE.name(),
                    withdrawalLog.getMemberId(), "兑换 $MAO 购物奖励", "DEDUCT_WITHDRAW_" + withdrawalLog.getId(), java.math.BigDecimal.ZERO);
            if (!deductResult) {
                throw new ServiceException(ResultCode.USER_POINTS_ERROR);
            }
        });
    }

    private void persistWithdrawalSuccess(String withdrawalLogId, String txHash) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            MaoWithdrawalLog toUpdate = new MaoWithdrawalLog();
            toUpdate.setId(withdrawalLogId);
            toUpdate.setMaoTxHash(txHash);
            toUpdate.setMaoIssueStatus(MaoIssueStatusEnum.SUCCESS.name());
            toUpdate.setErrorLog(null);
            this.updateById(toUpdate);
        });
    }

    private void persistWithdrawalFailure(String withdrawalLogId, String errorMessage) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            MaoWithdrawalLog toUpdate = new MaoWithdrawalLog();
            toUpdate.setId(withdrawalLogId);
            toUpdate.setMaoIssueStatus(MaoIssueStatusEnum.FAILED.name());
            toUpdate.setErrorLog(errorMessage);
            this.updateById(toUpdate);
        });
    }

    private void persistWithdrawalChainUnknown(String withdrawalLogId, String errorMessage) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            MaoWithdrawalLog toUpdate = new MaoWithdrawalLog();
            toUpdate.setId(withdrawalLogId);
            toUpdate.setMaoIssueStatus(MaoIssueStatusEnum.CHAIN_UNKNOWN.name());
            toUpdate.setErrorLog(errorMessage);
            this.updateById(toUpdate);
        });
    }

    @Override
    public double getDailyTotalIssued() {
        QueryWrapper<MaoWithdrawalLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mao_issue_status", MaoIssueStatusEnum.SUCCESS.name());
        queryWrapper.ge("create_time", new Date(System.currentTimeMillis() - 86400000L));
        return getIssuedSum(queryWrapper);
    }

    private double getDailyReservedIssued(String excludeWithdrawalLogId) {
        QueryWrapper<MaoWithdrawalLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("mao_issue_status", MaoIssueStatusEnum.SUCCESS.name(),
                MaoIssueStatusEnum.PENDING.name(), MaoIssueStatusEnum.CHAIN_UNKNOWN.name());
        queryWrapper.ge("create_time", new Date(System.currentTimeMillis() - 86400000L));
        if (CharSequenceUtil.isNotBlank(excludeWithdrawalLogId)) {
            queryWrapper.ne("id", excludeWithdrawalLogId);
        }
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
