/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.agent.skill.security;

import com.yangqiongai.ai.agent.runtime.skill.AgentSkill;
import com.yangqiongai.ai.agent.skill.config.SkillProperties;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能安全扫描器
 * @author yangqiong
 */
@Component
public class SkillSecurityScanner {

    private static final Logger log = LoggerFactory.getLogger(SkillSecurityScanner.class);

    /**
     * 安全扫描规则定义列表（覆盖6大攻击类别，对齐 agentscope2.0 检测规则）
     */
    private static final List<SecurityRule> RULES = List.of(
            // EXFILTRATION 数据外泄
            new SecurityRule("EXFILTRATION", "exfil-curl-post",
                    "curl\\s+[^\\n]*\\s(-d|--data|-F|--data-binary)\\s",
                    Severity.HIGH, "检测到 curl POST 上传操作"),
            new SecurityRule("EXFILTRATION", "exfil-wget-post",
                    "wget\\s+(?:[^\\n]*\\s)?--post-data\\b",
                    Severity.HIGH, "检测到 wget --post-data 上传操作"),
            new SecurityRule("EXFILTRATION", "exfil-nc-pipe",
                    "\\b(cat|tar|gzip)\\s+[^\\n]*\\|\\s*nc\\s",
                    Severity.HIGH, "检测到通过 netcat 管道外传数据"),
            new SecurityRule("EXFILTRATION", "exfil-cloud-upload",
                    "aws\\s+s3\\s+cp|gsutil\\s+cp|az\\s+copy",
                    Severity.HIGH, "检测到云存储上传操作"),

            // INJECTION 注入攻击（使用 (?i) 大小写不敏感）
            new SecurityRule("INJECTION", "inj-ignore-prev",
                    "(?i)ignore\\s+(all\\s+)?(your\\s+)?previous\\s+instructions",
                    Severity.MEDIUM, "检测到提示词注入：忽略先前指令"),
            new SecurityRule("INJECTION", "inj-system-tag",
                    "(?i)<\\s*(system|admin)\\s*>",
                    Severity.MEDIUM, "检测到提示词注入：系统标签"),
            new SecurityRule("INJECTION", "inj-jailbreak",
                    "(?i)\\b(DAN|jailbreak|developer\\s+mode)\\b",
                    Severity.MEDIUM, "检测到提示词注入：越狱关键词"),
            new SecurityRule("INJECTION", "inj-sql",
                    "';.*DROP|';.*DELETE|UNION.*SELECT",
                    Severity.HIGH, "检测到 SQL 注入"),
            new SecurityRule("INJECTION", "inj-env-theft",
                    "\\$\\{.*ENV|process\\.env|os\\.environ",
                    Severity.MEDIUM, "检测到环境变量窃取"),

            // DESTRUCTIVE 破坏性操作
            new SecurityRule("DESTRUCTIVE", "dest-rm-rf-root",
                    "rm\\s+-rf?\\s+(--no-preserve-root\\s+)?/(\\s|$)",
                    Severity.CRITICAL, "检测到 rm -rf / 根目录删除"),
            new SecurityRule("DESTRUCTIVE", "dest-mkfs",
                    "\\bmkfs(\\.[a-z0-9]+)?\\s+/dev/",
                    Severity.CRITICAL, "检测到 mkfs 格式化磁盘设备"),
            new SecurityRule("DESTRUCTIVE", "dest-dd-dev",
                    "\\bdd\\s+[^\\n]*of=/dev/(sd|nvme|hd|xvd|disk)",
                    Severity.CRITICAL, "检测到 dd 写入裸磁盘设备"),
            new SecurityRule("DESTRUCTIVE", "dest-redirect-dev",
                    ">\\s*/dev/(sd|nvme|hd|xvd)[a-z0-9]*",
                    Severity.HIGH, "检测到重定向写入裸磁盘设备"),

            // PERSISTENCE 持久化后门
            new SecurityRule("PERSISTENCE", "pers-crontab-install",
                    "\\b(crontab\\s+-)|(echo\\s+[^\\n]*\\s+>>\\s*/etc/cron)",
                    Severity.HIGH, "检测到 crontab 计划任务安装"),
            new SecurityRule("PERSISTENCE", "pers-systemd-install",
                    "(systemctl\\s+enable\\s+|cp\\s+[^\\n]*\\.service\\s+/etc/systemd/system|/etc/systemd/system/[^\\s]+\\.service)",
                    Severity.HIGH, "检测到 systemd 服务持久化"),
            new SecurityRule("PERSISTENCE", "pers-rc-tamper",
                    "echo\\s+[^\\n]*>>\\s+~?/?(\\.bashrc|\\.zshrc|\\.profile)",
                    Severity.MEDIUM, "检测到 Shell RC 文件篡改"),

            // NETWORK 网络攻击
            new SecurityRule("NETWORK", "net-reverse-shell-bash",
                    "bash\\s+-i\\s+>&\\s*/dev/tcp/",
                    Severity.CRITICAL, "检测到 bash 反弹 Shell"),
            new SecurityRule("NETWORK", "net-nc-listen",
                    "\\bnc\\s+(?:[^\\n]*\\s)?-(l|lvp|nlvp)\\b",
                    Severity.HIGH, "检测到 netcat 监听端口"),
            new SecurityRule("NETWORK", "net-nc-exec",
                    "\\bnc\\s+(?:[^\\n]*\\s)?-e\\b",
                    Severity.CRITICAL, "检测到 netcat -e 远程命令执行"),

            // OBFUSCATION 代码混淆
            new SecurityRule("OBFUSCATION", "obf-base64-pipe-shell",
                    "base64\\s+(-d|--decode)\\b[^\\n]*\\|\\s*(bash|sh|zsh)",
                    Severity.HIGH, "检测到 base64 解码后管道执行 Shell"),
            new SecurityRule("OBFUSCATION", "obf-eval-curl",
                    "\\beval\\s+[^\\n]*\\$\\(\\s*(curl|wget)\\b",
                    Severity.CRITICAL, "检测到 eval $(curl) 远程代码执行"),
            new SecurityRule("OBFUSCATION", "obf-curl-pipe-shell",
                    "(curl|wget)\\s+[^\\n]*\\|\\s*(bash|sh|zsh)",
                    Severity.HIGH, "检测到 curl|bash 远程代码执行")
    );

    private final SkillProperties properties;

    public SkillSecurityScanner(SkillProperties properties) {
        this.properties = properties;
    }

    /**
     * 扫描技能内容中的安全风险
     * @param skill
     * @param trustLevel
     * @return
     */
    public SecurityScanResult scan(AgentSkill skill, String trustLevel) {
        if (skill == null) {
            return SecurityScanResult.allow();
        }

        if (!properties.getSecurity().isEnabled()) {
            return SecurityScanResult.allow();
        }

        String source = skill.getSource();
        if (source == null || !properties.getSecurity().getScanSources().contains(source)) {
            return SecurityScanResult.allow();
        }

        String content = skill.getSkillContent();
        if (content == null || content.isBlank()) {
            return SecurityScanResult.allow();
        }

        String skillId = skill.getName() + "_" + source;
        TrustLevel resolvedTrustLevel = resolveTrustLevel(trustLevel, source);

        ScanResult scanResult;
        try {
            scanResult = doScan(skill.getName(), content, skill.getResources());
        } catch (Exception e) {
            log.warn("安全扫描异常，默认允许注册: skillId={}, error={}", skillId, e.getMessage());
            return SecurityScanResult.allow();
        }

        Verdict verdict = scanResult.verdict;
        String reportText = scanResult.reportText;
        boolean allowed = shouldAllow(resolvedTrustLevel, verdict);

        List<SecurityWarning> warnings = new ArrayList<>();
        for (Finding f : scanResult.findings) {
            warnings.add(new SecurityWarning(f.category, f.severity.name(), f.description));
        }

        if (verdict == Verdict.SAFE) {
            log.debug("技能安全扫描通过: skillId={}", skillId);
            return SecurityScanResult.allow();
        }

        if (verdict == Verdict.CAUTION) {
            log.warn("技能安全扫描发现风险(CAUTION)，允许注册: skillId={}, warningCount={}, report={}",
                    skillId, warnings.size(), reportText);
            return new SecurityScanResult(true, false, warnings, reportText);
        }

        if (allowed) {
            log.warn("技能安全扫描发现危险但信任等级允许: skillId={}, trustLevel={}, verdict={}, warnings={}",
                    skillId, resolvedTrustLevel, verdict, warnings.size());
            return new SecurityScanResult(true, false, warnings, reportText);
        }

        log.warn("技能安全扫描未通过，跳过注册: skillId={}, trustLevel={}, verdict={}, warningCount={}",
                skillId, resolvedTrustLevel, verdict, warnings.size());
        return new SecurityScanResult(false, true, warnings, reportText);
    }

    /**
     * 兼容旧接口的扫描方法
     * @param skill
     * @return
     */
    public SecurityScanResult scan(AgentSkill skill) {
        return scan(skill, null);
    }

    /**
     * 执行安全扫描，匹配所有规则并计算最终判定结果
     * @param name
     * @param content
     * @param resources
     * @return
     */
    private ScanResult doScan(String name, String content, Map<String, String> resources) {
        List<Finding> findings = new ArrayList<>();
        scanText(content, findings);
        if (resources != null) {
            for (Map.Entry<String, String> entry : resources.entrySet()) {
                String value = entry.getValue();
                if (value != null && !value.isBlank()) {
                    scanText(value, findings);
                }
            }
        }

        Verdict verdict = computeVerdict(findings);
        String reportText = buildReport(name, findings, verdict);
        return new ScanResult(findings, verdict, reportText);
    }

    /**
     * 在文本中匹配所有安全规则，报告全部匹配项
     * @param text
     * @param findings
     */
    private void scanText(String text, List<Finding> findings) {
        for (SecurityRule rule : RULES) {
            Matcher m = rule.pattern.matcher(text);
            while (m.find()) {
                String matchText = text.substring(m.start(), Math.min(m.end(), m.start() + 100));
                findings.add(new Finding(rule.category, rule.ruleId, rule.severity, rule.description, matchText));
            }
        }
    }

    /**
     * 根据规则严重度计算判定结果：CRITICAL/HIGH 为 DANGEROUS，MEDIUM/LOW 为 CAUTION
     * @param findings
     * @return
     */
    private Verdict computeVerdict(List<Finding> findings) {
        if (findings.isEmpty()) {
            return Verdict.SAFE;
        }
        for (Finding f : findings) {
            if (f.severity == Severity.CRITICAL || f.severity == Severity.HIGH) {
                return Verdict.DANGEROUS;
            }
        }
        return Verdict.CAUTION;
    }

    /**
     * 构建扫描报告文本
     * @param skillName
     * @param findings
     * @param verdict
     * @return
     */
    private String buildReport(String skillName, List<Finding> findings, Verdict verdict) {
        StringBuilder sb = new StringBuilder();
        sb.append("Skill: ").append(skillName).append("\n");
        sb.append("Verdict: ").append(verdict).append("\n");
        sb.append("Findings: ").append(findings.size()).append("\n");
        for (Finding f : findings) {
            sb.append("[").append(f.category).append("] ")
                    .append("[").append(f.severity).append("] ")
                    .append(f.description)
                    .append(" (matched: ").append(f.matchText).append(")\n");
        }
        return sb.toString();
    }

    /**
     * 根据信任等级和判定结果决定是否允许注册
     * <p>对齐 agentscope2.0 安全策略：
     * BUILTIN 总是允许；TRUSTED/AGENT_CREATED 允许 SAFE 和 CAUTION；
     * COMMUNITY 仅允许 SAFE</p>
     * @param trustLevel
     * @param verdict
     * @return
     */
    private boolean shouldAllow(TrustLevel trustLevel, Verdict verdict) {
        if (trustLevel == TrustLevel.BUILTIN) {
            return true;
        }
        if (trustLevel == TrustLevel.TRUSTED || trustLevel == TrustLevel.AGENT_CREATED) {
            return verdict == Verdict.SAFE || verdict == Verdict.CAUTION;
        }
        // COMMUNITY 仅允许 SAFE
        return verdict == Verdict.SAFE;
    }

    /**
     * 解析信任等级
     * @param trustLevel
     * @param source
     * @return
     */
    private TrustLevel resolveTrustLevel(String trustLevel, String source) {
        if (trustLevel != null && !trustLevel.isBlank()) {
            try {
                return TrustLevel.valueOf(trustLevel);
            } catch (IllegalArgumentException e) {
                log.warn("无法识别的信任等级: {}, 使用默认值", trustLevel);
            }
        }
        if ("BUILTIN".equals(source)) {
            return TrustLevel.BUILTIN;
        }
        try {
            return TrustLevel.valueOf(properties.getSecurity().getDefaultTrustLevel());
        } catch (IllegalArgumentException e) {
            return TrustLevel.COMMUNITY;
        }
    }

    /**
     * 安全判定结果
     */
    public enum Verdict {

        /**
         * 安全
         */
        SAFE,

        /**
         * 警告
         */
        CAUTION,

        /**
         * 危险
         */
        DANGEROUS
    }

    /**
     * 严重度等级
     */
    private enum Severity {

        /**
         * 低风险
         */
        LOW,

        /**
         * 中风险
         */
        MEDIUM,

        /**
         * 高风险
         */
        HIGH,

        /**
         * 严重风险
         */
        CRITICAL
    }

    /**
     * 安全扫描规则定义
     */
    private static class SecurityRule {

        /**
         * 攻击类别
         */
        private final String category;

        /**
         * 规则唯一标识
         */
        private final String ruleId;

        /**
         * 编译后的正则表达式（大小写敏感，INJECTION 类规则在正则中内嵌 (?i)）
         */
        private final Pattern pattern;

        /**
         * 严重度（直接使用规则定义的等级，不进行后续升级）
         */
        private final Severity severity;

        /**
         * 规则描述
         */
        private final String description;

        SecurityRule(String category, String ruleId, String regex, Severity severity, String description) {
            this.category = category;
            this.ruleId = ruleId;
            this.pattern = Pattern.compile(regex);
            this.severity = severity;
            this.description = description;
        }
    }

    /**
     * 扫描发现的单项安全问题
     */
    private static class Finding {

        /**
         * 攻击类别
         */
        private final String category;

        /**
         * 规则标识
         */
        private final String ruleId;

        /**
         * 严重度
         */
        private final Severity severity;

        /**
         * 问题描述
         */
        private final String description;

        /**
         * 匹配到的文本片段
         */
        private final String matchText;

        Finding(String category, String ruleId, Severity severity, String description, String matchText) {
            this.category = category;
            this.ruleId = ruleId;
            this.severity = severity;
            this.description = description;
            this.matchText = matchText;
        }
    }

    /**
     * 内部扫描结果
     */
    private static class ScanResult {

        /**
         * 发现的问题列表
         */
        private final List<Finding> findings;

        /**
         * 判定结果
         */
        private final Verdict verdict;

        /**
         * 扫描报告文本
         */
        private final String reportText;

        ScanResult(List<Finding> findings, Verdict verdict, String reportText) {
            this.findings = findings;
            this.verdict = verdict;
            this.reportText = reportText;
        }
    }

    /**
     * 安全扫描结果
     */
    public static class SecurityScanResult {

        /**
         * 是否允许注册
         */
        private final boolean allowed;

        /**
         * 是否被阻断（DANGEROUS且信任等级不允许）
         */
        private final boolean blocked;

        /**
         * 安全警告列表
         */
        private final List<SecurityWarning> warnings;

        /**
         * 扫描报告文本
         */
        private final String reportText;

        SecurityScanResult(boolean allowed, boolean blocked, List<SecurityWarning> warnings, String reportText) {
            this.allowed = allowed;
            this.blocked = blocked;
            this.warnings = warnings;
            this.reportText = reportText;
        }

        public static SecurityScanResult allow() {
            return new SecurityScanResult(true, false, List.of(), null);
        }

        /**
         * 是否允许注册
         * @return
         */
        public boolean isAllowed() {
            return allowed;
        }

        /**
         * 是否被阻断
         * @return
         */
        public boolean isBlocked() {
            return blocked;
        }

        /**
         * 是否安全（无警告）
         * @return
         */
        public boolean isSafe() {
            return warnings == null || warnings.isEmpty();
        }

        public List<SecurityWarning> getWarnings() {
            return warnings;
        }

        public String getReportText() {
            return reportText;
        }
    }

    /**
     * 安全警告
     */
    public static class SecurityWarning {

        /**
         * 攻击类别
         */
        private final String category;

        /**
         * 严重度
         */
        private final String severity;

        /**
         * 警告描述
         */
        private final String message;

        SecurityWarning(String category, String severity, String message) {
            this.category = category;
            this.severity = severity;
            this.message = message;
        }

        public String getCategory() {
            return category;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }
    }
}
