package cn.lili.modules.member.serviceimpl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.lili.common.vo.PageVO;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.entity.dos.MemberPointsHistory;
import cn.lili.modules.member.entity.enums.PointTypeEnum;
import cn.lili.modules.member.entity.vo.MemberPointsHistoryVO;
import cn.lili.modules.member.mapper.MemberPointsHistoryMapper;
import cn.lili.modules.member.service.MemberPointsHistoryService;
import cn.lili.modules.member.service.MemberService;
import cn.lili.mybatis.util.PageUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员积分历史业务层实现
 *
 * @author Bulbasaur
 * @since 2020-02-25 14:10:16
 */
@Service
public class MemberPointsHistoryServiceImpl extends ServiceImpl<MemberPointsHistoryMapper, MemberPointsHistory>
        implements MemberPointsHistoryService {

    @Autowired
    private MemberService memberService;

    @Override
    public MemberPointsHistoryVO getMemberPointsHistoryVO(String memberId) {
        // 获取会员喵币历史
        Member member = memberService.getById(memberId);
        MemberPointsHistoryVO memberPointsHistoryVO = new MemberPointsHistoryVO();
        if (member != null) {
            memberPointsHistoryVO.setPoint(member.getPoint());
            memberPointsHistoryVO.setTotalPoint(member.getTotalPoint());
            return memberPointsHistoryVO;
        }
        return new MemberPointsHistoryVO();
    }

    @Override
    public IPage<MemberPointsHistory> MemberPointsHistoryList(PageVO page, String memberId, String memberName) {
        LambdaQueryWrapper<MemberPointsHistory> lambdaQueryWrapper = new LambdaQueryWrapper<MemberPointsHistory>()
                .eq(CharSequenceUtil.isNotEmpty(memberId), MemberPointsHistory::getMemberId, memberId)
                .like(CharSequenceUtil.isNotEmpty(memberName), MemberPointsHistory::getMemberName, memberName);
        // 如果排序为空，则默认创建时间倒序
        if (CharSequenceUtil.isEmpty(page.getSort())) {
            page.setSort("createTime");
            page.setOrder("desc");
        }
        return this.page(PageUtil.initPage(page), lambdaQueryWrapper);
    }

    @Override
    public double getUnsettledLiability() {
        QueryWrapper<MemberPointsHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_settled", false);
        queryWrapper.select("IFNULL(sum(fund_reserve), 0) as totalLiability");
        Map<String, Object> result = this.getMap(queryWrapper);
        if (result != null && result.get("totalLiability") != null) {
            return new java.math.BigDecimal(result.get("totalLiability").toString()).doubleValue();
        }
        return 0.0;
    }

    @Override
    public boolean save(MemberPointsHistory entity) {
        // P3 Fix: Use unified Merkle leaf utility for audit reproducibility
        entity.setMerkleHash(cn.lili.modules.member.audit.MaocoinMerkleLeaf.computeLeafHash(entity));
        return super.save(entity);
    }

    @Override
    public String getLatestMerkleRoot() {
        QueryWrapper<MemberPointsHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("merkle_hash");
        queryWrapper.orderByAsc("create_time");
        List<MemberPointsHistory> list = this.list(queryWrapper);
        if (list == null || list.isEmpty()) {
            return "";
        }

        List<String> hashes = list.stream()
                .map(MemberPointsHistory::getMerkleHash)
                .filter(h -> h != null && !h.isEmpty())
                .collect(Collectors.toList());

        if (hashes.isEmpty()) {
            return "";
        }

        return buildMerkleRoot(hashes);
    }

    private String buildMerkleRoot(List<String> hashes) {
        if (hashes.size() == 1) {
            return hashes.get(0);
        }

        List<String> nextLevel = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
            nextLevel.add(cn.hutool.crypto.digest.DigestUtil.sha256Hex(left + right));
        }
        return buildMerkleRoot(nextLevel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pointExchangeCallback(String memberId, Long points, String txHash) {
        // P0 Fix: Use txHash as bizId for strict idempotency
        String bizId = "DEDUCT_CALLBACK_" + txHash;
        boolean result = memberService.updateMemberPoint(points, PointTypeEnum.REDUCE.name(), memberId, "DApp 链上兑换扣减",
                bizId, java.math.BigDecimal.ZERO);


        if (result) {
            // 标记刚才生成的历史记录为“已确权”
            QueryWrapper<MemberPointsHistory> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("member_id", memberId)
                    .eq("point_type", PointTypeEnum.REDUCE.name())
                    .eq("content", "DApp 链上兑换扣减")
                    .orderByDesc("create_time")
                    .last("limit 1");
            MemberPointsHistory history = this.getOne(queryWrapper);
            if (history != null) {
                history.setIsConfirmed(true);
                this.updateById(history);
            }
        }
        return result;
    }

}