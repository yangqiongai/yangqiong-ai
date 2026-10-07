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
/**
 * AI 通用审批模块
 * <p>
 * 提供三种审批方式，覆盖从静态强制审批到动态智能审批的全场景需求：
 * </p>
 * <ul>
 *   <li><b>静态注解</b>：通过 {@link com.yangqiongai.ai.approval.Suspendable} 注解在编码时声明强制审批</li>
 *   <li><b>编程式API</b>：通过 {@link com.yangqiongai.ai.approval.ApprovalGate#requestApproval} 在业务代码中动态发起审批</li>
 *   <li><b>LLM自主Tool</b>：通过 {@code HumanApprovalTool} 让Agent在ReAct推理中自主请求人工审批</li>
 * </ul>
 *
 * <h2>一、交互模式</h2>
 * <ul>
 *   <li><b>CONFIRM</b>（默认）：options和inputFields均为空，审批人只能通过/拒绝</li>
 *   <li><b>CHOICE</b>：设置options，审批人从选项中选择一个</li>
 *   <li><b>FORM</b>：设置inputFields，审批人填写表单字段</li>
 *   <li><b>CHOICE+FORM</b>：同时设置options和inputFields，审批人先选择再填表单</li>
 * </ul>
 *
 * <h2>二、前提条件</h2>
 * <ul>
 *   <li>方法所在类必须是Spring Bean（标注 {@code @Component}、{@code @Service} 等）</li>
 *   <li>调用前必须通过 {@code SessionContext.setSessionId()} 和 {@code SessionContext.setUserId()} 设置会话上下文</li>
 *   <li>未设置会话上下文时，审批会被跳过，方法直接执行</li>
 *   <li>Spring AOP 无法拦截类内部方法调用，需通过外部调用或拆分Bean触发</li>
 * </ul>
 *
 * <h2>三、使用示例</h2>
 *
 * <h3>1. 简单确认模式（CONFIRM）</h3>
 * <pre>{@code
 * @Component
 * public class DataDeleteService {
 *
 *     @Suspendable(reason = "删除操作需要人工确认", timeoutSeconds = 300)
 *     public String deleteData(String dataId) {
 *         // 审批通过后才会执行到这里
 *         return "已删除: " + dataId;
 *     }
 * }
 * }</pre>
 *
 * <h3>2. 多选项选择模式（CHOICE）</h3>
 * <pre>{@code
 * @Component
 * public class ConflictResolveService {
 *
 *     @Suspendable(reason = "发现冲突数据，请选择处理策略", options = {"覆盖", "跳过", "合并"})
 *     public String handleConflict(String dataId) {
 *         Map<String, Object> response = SessionContext.getApprovalResponse();
 *         String choice = (String) response.get("selectedOption");
 *         // 根据 choice 执行不同逻辑
 *         switch (choice) {
 *             case "覆盖" -> overwriteData(dataId);
 *             case "跳过" -> skipData(dataId);
 *             case "合并" -> mergeData(dataId);
 *         }
 *         return "处理完成: " + choice;
 *     }
 * }
 * }</pre>
 *
 * <h3>3. 表单输入模式（FORM）</h3>
 * <pre>{@code
 * @Component
 * public class EmailService {
 *
 *     @Suspendable(reason = "发送邮件前请确认", inputFields = {"cc", "priority"})
 *     public String sendEmail(String to, String subject) {
 *         Map<String, Object> response = SessionContext.getApprovalResponse();
 *         // FORM模式下，表单数据存储在 response.get("fields") 中
 *         Map<String, Object> fields = (Map<String, Object>) response.get("fields");
 *         String cc = (String) fields.get("cc");
 *         String priority = (String) fields.get("priority");
 *         // 发送邮件...
 *         return "已发送至 " + to + "，抄送 " + cc + "，优先级 " + priority;
 *     }
 * }
 * }</pre>
 *
 * <h3>4. 选择+表单组合模式（CHOICE+FORM）</h3>
 * <pre>{@code
 * @Component
 * public class DataImportService {
 *
 *     @Suspendable(
 *         reason = "导入数据存在重复，请选择处理方式",
 *         options = {"覆盖已有数据", "保留已有数据", "取消导入"},
 *         inputFields = {"remark"},
 *         timeoutSeconds = 600
 *     )
 *     public String importData(String fileName) {
 *         Map<String, Object> response = SessionContext.getApprovalResponse();
 *         String decision = (String) response.get("selectedOption");
 *         Map<String, Object> fields = (Map<String, Object>) response.get("fields");
 *         String remark = (String) fields.get("remark");
 *         // 根据 decision 和 remark 执行导入...
 *         return "导入完成: " + decision + "，备注: " + remark;
 *     }
 * }
 * }</pre>
 *
 * <h3>5. 与 @AgentTool 组合使用（Agent工具方法审批）</h3>
 * <pre>{@code
 * @Component
 * public class DangerousTool implements Tool {
 *
 *     @Suspendable(reason = "数据删除操作需要人工确认", timeoutSeconds = 300)
 *     @AgentTool("删除指定ID的数据，需要人工审批确认后才会执行")
 *     public String deleteData(String dataId) {
 *         return "已删除数据: " + dataId;
 *     }
 * }
 * }</pre>
 *
 * <h3>6. 普通业务方法审批（非Tool方法）</h3>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @Suspendable(reason = "大额订单需要主管审批", options = {"批准", "拒绝", "修改金额"})
 *     public Order createOrder(OrderRequest request) {
 *         Map<String, Object> response = SessionContext.getApprovalResponse();
 *         String decision = (String) response.get("selectedOption");
 *         if ("修改金额".equals(decision)) {
 *             // 审批人要求修改金额的逻辑...
 *         }
 *         return order;
 *     }
 * }
 * }</pre>
 *
 * <h3>7. 调用方设置会话上下文</h3>
 * <pre>{@code
 * // 调用 @Suspendable 方法前必须设置会话上下文
 * SessionContext.setSessionId("session-123");
 * SessionContext.setUserId("user-456");
 * try {
 *     String result = orderService.createOrder(request);
 * } catch (ApprovalRejectedException e) {
 *     // 审批被拒绝或超时
 *     log.warn("订单审批未通过: {}", e.getMessage());
 * } finally {
 *     SessionContext.clear();
 * }
 * }</pre>
 *
 * <h3>8. 编程式动态审批（不使用注解，通过ApprovalGate手动发起）</h3>
 * <p>
 * 当需要根据运行时条件动态决定是否审批、提供什么选项时，可直接调用
 * {@link com.yangqiongai.ai.approval.ApprovalGate#requestApproval(ApprovalRequest)}，无需标注 @Suspendable。
 * </p>
 * <pre>{@code
 * @Service
 * public class RiskControlService {
 *
 *     @Autowired
 *     private ApprovalGate approvalGate;
 *
 *     public String transferMoney(String from, String to, BigDecimal amount) {
 *         // 根据金额动态决定是否需要审批
 *         if (amount.compareTo(new BigDecimal("10000")) <= 0) {
 *             return doTransfer(from, to, amount);
 *         }
 *
 *         // 大额转账动态发起审批，运行时构建选项和表单
 *         ApprovalResponse resp = approvalGate.requestApproval(
 *             ApprovalRequest.builder()
 *                 .sessionId(SessionContext.getSessionId())
 *                 .userId(SessionContext.getUserId())
 *                 .resourceType("TRANSFER")
 *                 .targetName("transferMoney")
 *                 .reason("大额转账审批：金额" + amount + "元，收款人" + to)
 *                 .options(List.of("批准转账", "拒绝转账", "需二次确认"))
 *                 .inputFields(List.of("auditRemark"))
 *                 .timeout(Duration.ofMinutes(10))
 *                 .build()
 *         );
 *
 *         if (resp.isApproved()) {
 *             String decision = resp.getSelectedOption();
 *             String remark = resp.getField("auditRemark");
 *             if ("批准转账".equals(decision)) {
 *                 return doTransfer(from, to, amount);
 *             }
 *             return "转账已取消：" + remark;
 *         } else if (resp.isRejected()) {
 *             return "转账被拒绝：" + resp.getRejectReason();
 *         } else {
 *             return "转账审批超时";
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>四、审批响应数据格式</h2>
 * <p>
 * 审批通过后，审批人的选择和表单输入会以 JSON 格式存储，通过 {@code SessionContext.getApprovalResponse()} 获取：
 * </p>
 * <ul>
 *   <li><b>CONFIRM模式</b>：response 为 null 或空Map</li>
 *   <li><b>CHOICE模式</b>：{@code {"selectedOption": "覆盖"}}</li>
 *   <li><b>FORM模式</b>：{@code {"fields": {"cc": "admin@test.com", "priority": "高"}}}</li>
 *   <li><b>CHOICE+FORM模式</b>：{@code {"selectedOption": "合并", "fields": {"remark": "保留最新数据"}}}</li>
 * </ul>
 *
 * <h2>五、审批人操作接口</h2>
 * <ul>
 *   <li>{@code GET  /api/approval/pending} — 查看待审批列表</li>
 *   <li>{@code GET  /api/approval/{requestId}} — 查看审批详情（含inputSchema）</li>
 *   <li>{@code POST /api/approval/{requestId}/approve} — 审批通过，body可携带responsePayload</li>
 *   <li>{@code POST /api/approval/{requestId}/reject} — 审批拒绝，body携带reason</li>
 *   <li>{@code GET  /api/approval/stream/{sessionId}} — SSE订阅审批事件</li>
 * </ul>
 *
 * @author yangqiong
 */
package com.yangqiongai.ai.approval;
