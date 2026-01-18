package cn.lili.modules.email.impl;

import cn.hutool.core.util.StrUtil;
import cn.lili.cache.Cache;
import cn.lili.cache.CachePrefix;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.utils.CommonUtil;
import cn.lili.modules.email.EmailUtil;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.EmailSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import cn.lili.modules.verification.entity.enums.VerificationEnums;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 邮箱工具实现类
 *
 * @author Maollar
 * @version v1.0
 * @since 2025/01/XX
 */
@Component
@Slf4j
public class EmailUtilImpl implements EmailUtil {

    @Autowired
    private Cache cache;
    @Autowired
    private SettingService settingService;

    /**
     * 获取配置好的JavaMailSender
     */
    private JavaMailSender getMailSender() {
        // 获取邮箱配置
        Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
        if (setting == null || StrUtil.isBlank(setting.getSettingValue())) {
            throw new ServiceException(ResultCode.EMAIL_SETTING_ERROR);
        }

        EmailSetting emailSetting;
        try {
            emailSetting = new Gson().fromJson(setting.getSettingValue(), EmailSetting.class);
        } catch (Exception e) {
            log.error("解析邮箱配置失败", e);
            throw new ServiceException(ResultCode.EMAIL_SETTING_ERROR);
        }

        if (emailSetting == null || StrUtil.isBlank(emailSetting.getHost()) ||
                StrUtil.isBlank(emailSetting.getUsername()) || StrUtil.isBlank(emailSetting.getPassword())) {
            throw new ServiceException(ResultCode.EMAIL_SETTING_ERROR);
        }

        // 创建JavaMailSender实例
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(emailSetting.getHost());
        mailSender.setPort(465); // QQ邮箱使用465端口
        mailSender.setUsername(emailSetting.getUsername());
        mailSender.setPassword(emailSetting.getPassword());

        // 配置SMTP属性
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.trust", emailSetting.getHost());
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.debug", "false");

        return mailSender;
    }

    @Override
    public void sendEmailCode(String email, VerificationEnums verificationEnums, String uuid) {
        // 生成验证码
        String code = CommonUtil.getRandomNum();

        // 准备邮件内容
        String subject = "验证码";
        String content = "您的验证码是：" + code + "，有效期5分钟，请勿泄露给他人。";

        // 根据场景调整邮件主题和内容
        switch (verificationEnums) {
            case LOGIN:
                subject = "登录验证码";
                content = "您的登录验证码是：" + code + "，有效期5分钟，请勿泄露给他人。";
                break;
            case REGISTER:
            case BIND_MOBILE:
                subject = "注册验证码";
                content = "您的注册验证码是：" + code + "，有效期5分钟，请勿泄露给他人。";
                break;
            case FIND_USER:
            case UPDATE_PASSWORD:
                subject = "找回密码验证码";
                content = "您的找回密码验证码是：" + code + "，有效期5分钟，请勿泄露给他人。";
                break;
            case WALLET_PASSWORD:
                subject = "支付密码验证码";
                content = "您的支付密码验证码是：" + code + "，有效期5分钟，请勿泄露给他人。";
                break;
            default:
                subject = "验证码";
                content = "您的验证码是：" + code + "，有效期5分钟，请勿泄露给他人。";
        }

        // 发送邮件
        sendEmail(email, subject, content);

        // 缓存验证码（5分钟有效期）
        cache.put(CachePrefix.EMAIL_CODE.getPrefix() + verificationEnums.name() + ":" + email, code, 300L);
        log.info("发送邮箱验证码成功: email={}, code={}, scene={}", email, code, verificationEnums);
    }

    @Override
    public boolean verifyCode(String email, VerificationEnums verificationEnums, String uuid, String code) {
        Object cacheCode = cache.get(CachePrefix.EMAIL_CODE.getPrefix() + verificationEnums.name() + ":" + email);
        if (cacheCode == null || StrUtil.isBlank(cacheCode.toString())) {
            throw new ServiceException(ResultCode.VERIFICATION_CODE_INVALID);
        }
        if (!cacheCode.toString().equals(code)) {
            throw new ServiceException(ResultCode.VERIFICATION_SMS_CHECKED_ERROR);
        }
        cache.remove(CachePrefix.EMAIL_CODE.getPrefix() + verificationEnums.name() + ":" + email);
        return true;
    }

    @Override
    public void sendTestEmail(String toEmail, String subject, String content) {
        sendEmail(toEmail, subject, content);
    }

    /**
     * 发送邮件的通用方法
     */
    private void sendEmail(String toEmail, String subject, String content) {
        try {
            JavaMailSender mailSender = getMailSender();
            Setting setting = settingService.get(SettingEnum.EMAIL_SETTING.name());
            EmailSetting emailSetting = new Gson().fromJson(setting.getSettingValue(), EmailSetting.class);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailSetting.getUsername());
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);

            mailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", toEmail, subject);
        } catch (Exception e) {
            log.error("邮件发送失败: to={}, subject={}", toEmail, subject, e);
            throw new ServiceException(ResultCode.EMAIL_SEND_ERROR);
        }
    }
}

