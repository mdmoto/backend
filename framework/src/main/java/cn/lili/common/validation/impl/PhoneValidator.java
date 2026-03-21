package cn.lili.common.validation.impl;

import cn.lili.common.validation.Phone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 手机号校验
 *
 * @author Bulbasaur
 * @since 2021/7/9 1:42 上午
 */
public class PhoneValidator implements ConstraintValidator<Phone, String> {

    /**
     * 兼容国内手机号与国际 E.164（允许 + 前缀），并允许输入中包含空格/短横线/括号。
     * - 国内：0? + 1[3-9] + 9位
     * - 国际：+?[1-9]\d{6,14}
     */
    private static final Pattern cnPattern = Pattern.compile("^0?(13[0-9]|14[0-9]|15[0-9]|16[0-9]|17[0-9]|18[0-9]|19[0-9])[0-9]{8}$");
    private static final Pattern e164Pattern = Pattern.compile("^\\+?[1-9]\\d{6,14}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        normalized = normalized.replaceAll("[\\s\\-()]", "");
        Matcher cn = cnPattern.matcher(normalized);
        if (cn.matches()) {
            return true;
        }
        Matcher e164 = e164Pattern.matcher(normalized);
        return e164.matches();
    }

    @Override
    public void initialize(Phone constraintAnnotation) {

    }
}
