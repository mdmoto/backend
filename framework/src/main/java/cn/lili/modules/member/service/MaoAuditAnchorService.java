package cn.lili.modules.member.service;

import cn.lili.modules.member.entity.dos.MaoAuditAnchor;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;

/**
 * MAO 猫币账本公开审计锚业务层
 */
public interface MaoAuditAnchorService extends IService<MaoAuditAnchor> {

    /**
     * 生成并尝试发布指定 UTC 日期的账本审计锚。
     *
     * @param anchorDate UTC 日期
     * @return 审计锚
     */
    MaoAuditAnchor createDailyAnchor(LocalDate anchorDate);

    /**
     * 获取最新审计锚。
     *
     * @return 最新审计锚
     */
    MaoAuditAnchor getLatestAnchor();

    /**
     * 获取最近审计锚。
     *
     * @param limit 数量上限
     * @return 审计锚列表
     */
    List<MaoAuditAnchor> getRecentAnchors(int limit);
}
