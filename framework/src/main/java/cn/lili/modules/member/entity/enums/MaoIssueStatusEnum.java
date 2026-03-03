package cn.lili.modules.member.entity.enums;

/**
 * $MAO 发放状态枚举
 */
public enum MaoIssueStatusEnum {
    /**
     * 初始状态
     */
    NONE("未处理"),
    /**
     * 处理中
     */
    PENDING("处理中"),
    /**
     * 成功
     */
    SUCCESS("成功"),
    /**
     * 失败
     */
    FAILED("失败");

    private final String description;

    MaoIssueStatusEnum(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
