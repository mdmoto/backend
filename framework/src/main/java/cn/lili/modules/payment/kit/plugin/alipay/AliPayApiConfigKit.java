package cn.lili.modules.payment.kit.plugin.alipay;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.common.enums.ResultCode;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.utils.SpringContextUtil;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.payment.AlipayPaymentSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import com.alipay.api.AlipayApiException;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * AliPayApiConfigKit
 *
 * @author Chopper
 * @since 2020-12-16 09:31
 */
public class AliPayApiConfigKit {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AliPayApiConfigKit.class);

    /**
     * 支付配置（已禁用缓存，每次重新构建）
     */
    static DefaultAlipayClient defaultAlipayClient = null; // 强制为null，确保每次都重新构建

    /**
     * 下次刷新时间（已禁用）
     */
    static Date nextRebuildDate = null; // 强制为null，确保每次都重新构建

    /**
     * 间隔时间（已禁用缓存）
     */
    static Long refreshInterval = 1000 * 60 * 1L;

    /**
     * 获取支付宝支付参数
     *
     * @return
     * @throws AlipayApiException
     */
    public static synchronized DefaultAlipayClient getAliPayApiConfig() throws AlipayApiException {
        log.error("========== 获取支付宝配置 - 强制重新构建 ==========");
        return rebuild(); // 强制每次重新构建以确保使用最新配置和私钥格式化
    }

    static DefaultAlipayClient rebuild() throws AlipayApiException {
        log.error("========== 开始重建支付宝配置 ==========");
        AlipayPaymentSetting setting;
        try {
            SettingService settingService = (SettingService) SpringContextUtil.getBean("settingServiceImpl");
            Setting systemSetting = settingService.get(SettingEnum.ALIPAY_PAYMENT.name());
            setting = JSONUtil.toBean(systemSetting.getSettingValue(), AlipayPaymentSetting.class);
            log.error("成功读取支付宝配置 - AppID: {}", setting.getAppId());
        } catch (Exception e) {
            log.error("读取支付宝配置失败", e);
            throw new ServiceException(ResultCode.PAY_NOT_SUPPORT);
        }
        CertAlipayRequest certAlipayRequest = new CertAlipayRequest();
        certAlipayRequest.setServerUrl("https://openapi.alipay.com/gateway.do");
        certAlipayRequest.setFormat("json");
        certAlipayRequest.setCharset("utf-8");
        certAlipayRequest.setSignType("RSA2");
        certAlipayRequest.setAppId(setting.getAppId());
        // 修复私钥格式：确保正确的PKCS8格式（包含换行符）
        String privateKey = setting.getPrivateKey();
        if (privateKey != null && !privateKey.trim().isEmpty()) {
            log.error("========== 原始私钥长度: {} ==========", privateKey.length());
            log.error("原始私钥前100字符: {}", privateKey.substring(0, Math.min(100, privateKey.length())));

            // 第一步：处理转义的换行符
            // JSON解析后，\\n 可能变成 \n（单个反斜杠+n）或保持为 \\n（两个反斜杠+n）
            // 需要处理所有可能的情况
            log.error("处理前 - 包含\\n: {}, 包含实际换行符: {}", privateKey.contains("\\n"), privateKey.contains("\n"));

            // 先处理 \\n（两个反斜杠+n，JSON字符串中的转义）
            while (privateKey.contains("\\\\n")) {
                privateKey = privateKey.replace("\\\\n", "\n");
            }
            // 再处理 \n（单个反斜杠+n，JSON解析后的转义）
            if (privateKey.contains("\\n")) {
                privateKey = privateKey.replace("\\n", "\n");
            }
            // 处理其他转义格式
            privateKey = privateKey.replace("\\r\\n", "\n");
            privateKey = privateKey.replace("\\r", "\n");

            log.error("处理后 - 私钥长度: {}, 包含换行符: {}", privateKey.length(), privateKey.contains("\n"));

            // SDK v4.40.572+ 支持两种格式：
            // 1. PEM格式（带BEGIN/END标记）
            // 2. 纯PKCS8 Base64格式（推荐）
            String base64Content;
            if (privateKey.contains("-----BEGIN PRIVATE KEY-----")
                    && privateKey.contains("-----END PRIVATE KEY-----")) {
                // PEM format - 提取Base64内容
                log.error("检测到PEM格式私钥，提取Base64内容");
                base64Content = privateKey
                        .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                        .replaceAll("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s+", "");
            } else {
                // 纯Base64格式 - 直接使用，仅移除空白字符
                log.error("检测到纯Base64格式私钥");
                base64Content = privateKey.replaceAll("\\s+", "");
            }

            // FIX: Re-declare for scope visibility (or rely on this being the only
            // definition if we move logic)
            // But since I need it later outside this block, I should actually just compute
            // it here and use it.
            // Wait, the block ENDS at line 156.
            // I will copy this extraction logic to the outer scope or simpler:
            // I'll leave this here and RE-COMPUTE it below if needed, OR just move the
            // "setPrivateKey" call INSIDE this if block.

            // Moving the setPrivateKey call INSIDE the if block is the cleanest way.

            log.error("提取的Base64内容长度: {}", base64Content.length());
            log.error("Base64内容前50字符: {}", base64Content.substring(0, Math.min(50, base64Content.length())));

            // 第四步：尝试两种格式
            // 格式1：多行格式（标准PKCS8格式，每64字符一行）
            StringBuilder multiLineFormatted = new StringBuilder();
            multiLineFormatted.append("-----BEGIN PRIVATE KEY-----\n");
            int lineCount = 0;
            for (int i = 0; i < base64Content.length(); i += 64) {
                int end = Math.min(i + 64, base64Content.length());
                multiLineFormatted.append(base64Content.substring(i, end));
                lineCount++;
                if (end < base64Content.length()) {
                    multiLineFormatted.append("\n");
                }
            }
            multiLineFormatted.append("\n-----END PRIVATE KEY-----\n");
            String multiLineKey = multiLineFormatted.toString();

            // 格式2：单行格式（支付宝SDK可能期望这种格式）
            String singleLineKey = "-----BEGIN PRIVATE KEY-----\n" + base64Content + "\n-----END PRIVATE KEY-----\n";

            log.error("多行格式长度: {}, 行数: {}", multiLineKey.length(), multiLineKey.split("\n").length);
            log.error("单行格式长度: {}, 行数: {}", singleLineKey.length(), singleLineKey.split("\n").length);

            // 尝试多行格式（标准PKCS8格式，每64字符一行）
            // 根据支付宝SDK文档，标准PKCS8格式应该是多行的
            privateKey = multiLineKey;
            log.error("使用多行格式私钥（标准PKCS8格式）");

            log.error("========== 格式化后的私钥长度: {}, 行数: {} ==========", privateKey.length(),
                    privateKey.split("\n").length);
            log.error("格式化后的私钥预览（前300字符）:\n{}", privateKey.substring(0, Math.min(300, privateKey.length())));

            // 验证私钥格式
            if (!privateKey.contains("-----BEGIN PRIVATE KEY-----")
                    || !privateKey.contains("-----END PRIVATE KEY-----")) {
                log.warn("私钥缺少BEGIN/END标记，尝试作为纯Base64处理");
                // do not throw exception, let downstream validation handle it
            }
        } else {
            log.error("私钥为空或未配置");
            throw new ServiceException(ResultCode.ALIPAY_NOT_SETTING);
        }

        log.error("========== 设置私钥到CertAlipayRequest，长度: {} ==========", privateKey.length());
        log.error("私钥前200字符:\n{}", privateKey.substring(0, Math.min(200, privateKey.length())));
        log.error("私钥后100字符:\n{}", privateKey.substring(Math.max(0, privateKey.length() - 100)));
        log.error("私钥行数: {}", privateKey.split("\n").length);
        log.error("私钥是否包含换行符: {}", privateKey.contains("\n"));

        // 验证私钥格式：尝试解析私钥以确保格式正确
        try {
            String base64Key = privateKey
                    .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                    .replaceAll("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            log.error("Base64内容长度: {}", base64Key.length());
            log.error("Base64前50字符: {}", base64Key.substring(0, Math.min(50, base64Key.length())));

            java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
            byte[] keyBytes = decoder.decode(base64Key);
            log.error("解码后的字节数组长度: {}", keyBytes.length);

            java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            java.security.PrivateKey testKey = keyFactory.generatePrivate(keySpec);
            log.error("✅ 私钥格式验证通过，可以正确解析");
        } catch (Exception e) {
            log.error("❌ 私钥格式验证失败: {}", e.getMessage(), e);
            log.error("❌ 私钥格式验证失败堆栈: ", e);
            throw new ServiceException(ResultCode.ALIPAY_NOT_SETTING);
        }

        // FIX: The SDK DefaultSigner expects raw Base64 key, not PEM with headers
        // Re-extract base64Content as it's out of scope here
        String base64Content = privateKey
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        certAlipayRequest.setPrivateKey(base64Content);
        log.error("设置到SDK的私钥(Base64)前50字符: {}", base64Content.substring(0, Math.min(50, base64Content.length())));
        log.error("私钥已设置到CertAlipayRequest，长度: {}", privateKey.length());

        // 再次验证设置后的私钥
        String setPrivateKey = certAlipayRequest.getPrivateKey();
        if (setPrivateKey != null) {
            log.error("从CertAlipayRequest读取的私钥长度: {}", setPrivateKey.length());
            log.error("私钥是否相同: {}", setPrivateKey.equals(privateKey));
        } else {
            log.error("⚠️ 从CertAlipayRequest读取的私钥为null");
        }
        certAlipayRequest.setCertPath(setting.getCertPath());
        certAlipayRequest.setAlipayPublicCertPath(setting.getAlipayPublicCertPath());
        certAlipayRequest.setRootCertPath(setting.getRootCertPath());

        // DEBUG: Verify file existence
        verifyFile("CertPath", setting.getCertPath());
        verifyFile("AlipayPublicCertPath", setting.getAlipayPublicCertPath());
        verifyFile("RootCertPath", setting.getRootCertPath());

        log.error("========== 创建DefaultAlipayClient ==========");
        defaultAlipayClient = new DefaultAlipayClient(certAlipayRequest);
        log.error("========== DefaultAlipayClient创建完成 ==========");
        nextRebuildDate = DateUtil.date(System.currentTimeMillis() + refreshInterval);
        return defaultAlipayClient;
    }

    private static void verifyFile(String name, String path) {
        try {
            if (path == null || path.trim().isEmpty()) {
                log.error("❌ {} is empty or null", name);
                return;
            }
            java.io.File file = new java.io.File(path);
            boolean exists = file.exists();
            boolean canRead = file.canRead();
            String msg = String.format("🔍 Checking %s: Path='%s', Exists=%s, CanRead=%s, AbsolutePath='%s'",
                    name, path, exists, canRead, file.getAbsolutePath());
            log.error(msg); // 使用log.error确保输出到日志
            if (!exists) {
                log.error("❌ {} FILE NOT FOUND at '{}'", name, path);
            } else if (!canRead) {
                log.error("❌ {} FILE EXISTS but CANNOT READ at '{}'", name, path);
            } else {
                log.error("✅ {} FILE EXISTS and READABLE at '{}'", name, path);
            }
        } catch (Exception e) {
            log.error("Error verifying file " + name, e);
        }
    }
}
