package com.wanyuecity.energy.workflow.service;

import com.wanyuecity.energy.core.BizException;
import java.util.Collection;
import java.util.Objects;

public final class SmsPhoneComparison {
    private SmsPhoneComparison() {}
    public static String normalize(String phone) {
        String result = phone == null ? "" : phone.replaceAll("[\\s()-]", "");
        if (result.matches("(?:\\+86|0086|86)1\\d{10}")) result = result.substring(result.length()-11);
        return result;
    }
    public static String snapshot(Collection<String> phones) {
        return phones.stream().map(SmsPhoneComparison::normalize).filter(p -> !p.isBlank()).distinct().sorted().collect(java.util.stream.Collectors.joining("、"));
    }
    public static boolean differs(String phone, String snapshot) {
        return !normalize(phone).equals(snapshot);
    }
    public static boolean validate(String phone, String source, String confirmedSource, Boolean decision) {
        if (!normalize(phone).matches("\\+?\\d{7,15}")) throw BizException.badRequest("请填写有效的联系人电话");
        if (!differs(phone, source)) return false;
        if (!Objects.equals(source, confirmedSource)) throw BizException.badRequest("原系统手机号已变化，请重新执行变更检查并确认短信通知号码");
        if (decision == null) throw BizException.badRequest("联系电话与原系统不一致，请确认是否修改短信通知号码");
        return decision;
    }
}
