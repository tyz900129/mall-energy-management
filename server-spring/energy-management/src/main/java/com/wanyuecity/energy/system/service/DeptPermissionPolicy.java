package com.wanyuecity.energy.system.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 部门权限裁剪策略的纯逻辑：解析祖先链、选取最近策略、判定权限码是否保留。
 * 不依赖 Spring/JDBC，便于单独验证；数据加载见 {@link PermissionService}。
 *
 * @deprecated V107 起取消部门权限裁剪（角色/岗位授权为唯一权威来源），
 *             本类不再参与鉴权，仅保留历史逻辑与单测；不要再新增调用方。
 */
@Deprecated(forRemoval = false)
public final class DeptPermissionPolicy {

    public enum Mode { PREFIX, EXACT }

    /** 一条白名单规则：mode=PREFIX 按前缀匹配，mode=EXACT 按权限码精确匹配。 */
    public record Rule(Mode mode, String include, String exclude) {
        public boolean includes(String code) {
            if (code == null || include == null) return false;
            return mode == Mode.PREFIX ? code.startsWith(include) : code.equals(include);
        }

        public boolean excludes(String code) {
            if (code == null || exclude == null) return false;
            return mode == Mode.PREFIX ? code.startsWith(exclude) : code.equals(exclude);
        }
    }

    private DeptPermissionPolicy() {
    }

    /** 物化路径 "/9/6/" -> 最近优先的部门链 [self, 6, 9]；根节点 "/" 只返回 self。 */
    public static List<Long> selfFirstChain(Long selfId, String ancestors) {
        Set<Long> rootFirst = new LinkedHashSet<>();
        if (ancestors != null) {
            for (String part : ancestors.split("/")) {
                String value = part.trim();
                if (value.isEmpty()) continue;
                try {
                    rootFirst.add(Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    // 路径异常时跳过该段，不影响自身节点参与解析
                }
            }
        }
        if (selfId != null) rootFirst.add(selfId);
        List<Long> chain = new ArrayList<>(rootFirst);
        Collections.reverse(chain);
        return chain;
    }

    /** 沿链取最近一个"有启用策略"的部门，整组生效；都没有策略时返回空表（=不裁剪）。 */
    public static List<Rule> nearestRules(List<Long> selfFirstChain, Map<Long, List<Rule>> rulesByDept) {
        if (selfFirstChain == null || rulesByDept == null) return List.of();
        for (Long deptId : selfFirstChain) {
            List<Rule> rules = rulesByDept.get(deptId);
            if (rules != null && !rules.isEmpty()) return rules;
        }
        return List.of();
    }

    /** 保留条件：任一 include 命中且无 exclude 命中；规则为空表示不裁剪。 */
    public static boolean allows(String code, List<Rule> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean included = false;
        for (Rule rule : rules) {
            if (rule.excludes(code)) return false;
            if (rule.includes(code)) included = true;
        }
        return included;
    }

    /** 生成裁剪谓词；无策略时返回 null，调用方据此跳过过滤。 */
    public static Predicate<String> matcher(List<Rule> rules) {
        if (rules == null || rules.isEmpty()) return null;
        return code -> allows(code, rules);
    }
}
