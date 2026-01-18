package cn.lili.modules.email;

import cn.lili.modules.verification.entity.enums.VerificationEnums;

/**
 * 邮箱接口
 *
 * @author Maollar
 * @version v1.0
 * @since 2025/01/XX
 */
public interface EmailUtil {

    /**
     * 验证码发送
     *
     * @param email             邮箱地址
     * @param verificationEnums 验证码场景
     * @param uuid              用户标识uuid
     */
    void sendEmailCode(String email, VerificationEnums verificationEnums, String uuid);

    /**
     * 验证码验证
     *
     * @param email             邮箱地址
     * @param verificationEnums 验证码场景
     * @param uuid              用户标识uuid
     * @param code              待验证code
     * @return 操作状态
     */
    boolean verifyCode(String email, VerificationEnums verificationEnums, String uuid, String code);

    /**
     * 发送测试邮件
     *
     * @param toEmail 接收邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    void sendTestEmail(String toEmail, String subject, String content);
}

