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
 * 工作流引擎模块
 * <p>
 * 提供可视化工作流的定义、执行、状态持久化、断点续跑、审批暂停/恢复等能力。
 * 工作流由节点（Node）和边（Edge）组成，
 * 节点间通过共享变量表（WorkflowState.variables）传递数据。
 *
 * <h2>一、工作流定义结构（JSON）</h2>
 * <pre>{@code
 * {
 *   "name": "demo-workflow",
 *   "description": "工作流描述",
 *   "nodes": [ {节点定义} ],
 *   "edges": [ {边定义} ],
 *   "stateConfig": { "persistEnabled": true, "ttlHours": 48 },
 *   "errorStrategy": "STOP | SKIP | RETRY",
 *   "maxRetries": 0,
 *   "nodeTimeoutSeconds": 120
 * }
 * }</pre>
 *
 * <h3>错误策略</h3>
 * <ul>
 *   <li><b>STOP</b>：节点失败时停止整个工作流（默认）</li>
 *   <li><b>SKIP</b>：节点失败时跳过，继续执行后续节点</li>
 *   <li><b>RETRY</b>：节点失败时按 maxRetries 重试</li>
 * </ul>
 *
 * <h2>二、节点类型详解</h2>
 *
 * <h3>1. START — 开始节点</h3>
 * <p>工作流入口，每个工作流必须有且仅有一个 START 节点。</p>
 * <pre>{@code
 * {
 *   "id": "start", "name": "开始", "type": "START",
 *   "config": { "initialPrompt": "初始化提示词文本" }
 * }
 * }</pre>
 * <p>执行后自动将 initialPrompt 写入变量 {@code prompt}，供下游节点引用。</p>
 *
 * <h3>2. END — 结束节点</h3>
 * <p>工作流出口，汇总其前驱节点的输出作为工作流最终输出。无需额外配置。</p>
 *
 * <h3>3. AGENT — 智能体节点</h3>
 * <p>委托 AgentEngine 执行 AI 任务，支持超时控制和并行执行。</p>
 * <pre>{@code
 * {
 *   "id": "agent1", "name": "分析", "type": "AGENT",
 *   "config": {
 *     "agentCode": "default-agent",
 *     "sysPrompt": "基于以下内容分析：${prompt}，分数：${score}",
 *     "maxIterations": 3,
 *     "toolkitRefs": ["search-tool"]
 *   }
 * }
 * }</pre>
 * <p><b>sysPrompt 支持 ${var} 模板替换</b>，变量引用从工作流状态取值。
 * 若未配置 sysPrompt，退回使用 {@code prompt} 变量（START 节点设置），再退回父请求 input。</p>
 * <p>执行后将输出文本存入 {@code nodeId.output}，finalPayload 每项存入 {@code nodeId.key}。</p>
 *
 * <h3>4. CONDITION — 条件分支节点</h3>
 * <p>根据条件表达式选择分支，支持 branches 映射和条件边两种路由方式。</p>
 * <pre>{@code
 * {
 *   "id": "check", "name": "成绩判定", "type": "CONDITION",
 *   "config": {
 *     "conditionExpression": "${score}",
 *     "branches": { "PASS": "node-pass", "FAIL": "node-fail" }
 *   }
 * }
 * }</pre>
 * <p>也可通过条件边（EdgeType.CONDITIONAL）的 conditionExpression 和 conditionLabel 路由。</p>
 *
 * <h3>5. TRANSFORM — 数据变换节点（无需 Agent）</h3>
 * <p>支持字符串变换、数学运算、模板渲染，纯本地计算。</p>
 * <pre>{@code
 * {
 *   "id": "calc", "name": "计算总价", "type": "TRANSFORM",
 *   "config": {
 *     "transformType": "MATH",
 *     "transformConfig": { "expression": "${price} * ${quantity}" },
 *     "outputVar": "total"
 *   }
 * }
 * }</pre>
 * <p><b>支持的 transformType：</b></p>
 * <ul>
 *   <li><b>SUBSTRING</b>：截取子串，config: {start, end?}</li>
 *   <li><b>REVERSE</b>：字符串逆序</li>
 *   <li><b>UPPER / LOWER</b>：大小写转换</li>
 *   <li><b>TRIM</b>：去首尾空白</li>
 *   <li><b>REPLACE</b>：替换，config: {pattern, replacement}</li>
 *   <li><b>CONCAT</b>：拼接，config: {prefix?, suffix?}</li>
 *   <li><b>TEMPLATE</b>：模板渲染，config: {template: "Hello ${name}"}</li>
 *   <li><b>LENGTH</b>：取字符串长度</li>
 *   <li><b>MATH</b>：数学运算（+,-,*,/,%），config: {expression: "${x} * ${y}"}，基于 SpEL 求值</li>
 * </ul>
 * <p>输入源优先级：inputMappings → inputVar → 变量"input"。
 * 输出写入：outputVar（自定义变量名）+ nodeId.output + nodeId.result。</p>
 *
 * <h3>6. SCRIPT — 脚本/表达式节点</h3>
 * <p>支持 SpEL、JavaScript、Groovy 三种脚本引擎。</p>
 * <pre>{@code
 * {
 *   "id": "script1", "name": "表达式计算", "type": "SCRIPT",
 *   "config": {
 *     "scriptType": "SPEL",
 *     "expression": "#price * #quantity + #tax"
 *   }
 * }
 * }</pre>
 * <p><b>scriptType：</b></p>
 * <ul>
 *   <li><b>SPEL</b>：Spring Expression Language，变量用 #varName 访问，Bean 用 @beanName 访问。config 用 "expression"</li>
 *   <li><b>JS</b>：JavaScript（通过 ScriptEngine），工作流变量直接作为脚本变量。config 用 "script"</li>
 *   <li><b>GROOVY</b>：Groovy 脚本。config 用 "script"</li>
 * </ul>
 * <p>输出写入：outputVar + nodeId.output。</p>
 *
 * <h3>7. HTTP — HTTP 请求节点</h3>
 * <p>发起 HTTP 请求，url/headers/body 均支持 ${var} 模板替换。</p>
 * <pre>{@code
 * {
 *   "id": "controller", "name": "调用接口", "type": "HTTP",
 *   "config": {
 *     "url": "https://api.example.com/data?q=${query}",
 *     "method": "GET",
 *     "timeout": 30,
 *     "headers": { "Authorization": "Bearer ${token}" },
 *     "body": "{\"keyword\": \"${keyword}\"}"
 *   }
 * }
 * }</pre>
 *
 * <h3>8. ASSIGN — 变量赋值/聚合节点</h3>
 * <p>将常量、变量引用或 SpEL 表达式的结果赋值给新变量，适合数据聚合和初始化。</p>
 * <pre>{@code
 * {
 *   "id": "assign1", "name": "聚合数据", "type": "ASSIGN",
 *   "config": {
 *     "assignments": {
 *       "fullName": "Alice",
 *       "prevResult": "${step1.output}",
 *       "total": "#{${price} + ${tax}}"
 *     }
 *   }
 * }
 * }</pre>
 * <p><b>值表达式支持三种格式：</b></p>
 * <ul>
 *   <li>常量值：直接写文本，如 "hello"</li>
 *   <li>变量引用：${varName}，如 "${step1.output}"</li>
 *   <li>SpEL 表达式：#{expression}，如 "#{1 + 2}"，内部先替换 ${var} 再求值</li>
 * </ul>
 *
 * <h3>9. PARALLEL — 并行节点</h3>
 * <p>并行执行多个分支，按 joinType 汇总结果（ALL 等待全部 / ANY 任一完成）。</p>
 * <pre>{@code
 * { "id": "parallel1", "name": "并行处理", "type": "PARALLEL", "config": { "joinType": "ALL" } }
 * }</pre>
 *
 * <h3>10. LOOP — 循环节点</h3>
 * <p>对集合或固定次数循环执行子节点，支持 break 条件和最大迭代数限制。</p>
 *
 * <h3>11. SUBGRAPH — 子图节点</h3>
 * <p>嵌套执行另一个工作流定义，实现工作流复用。</p>
 *
 * <h3>12. APPROVAL — 审批节点</h3>
 * <p>发起人工审批请求，工作流暂停等待审批结果（异步回调模式）。</p>
 * <pre>{@code
 * {
 *   "id": "approve1", "name": "人工确认", "type": "APPROVAL",
 *   "approvalConfig": {
 *     "reason": "请确认操作结果",
 *     "options": ["通过", "拒绝"],
 *     "inputFields": ["comment"],
 *     "timeoutSeconds": 300
 *   }
 * }
 * }</pre>
 * <p><b>approvalConfig 位于节点顶层字段（与 config 平级）</b>。
 * 任何节点（含 AGENT/TRANSFORM 等）也可通过节点顶层 approvalConfig 实现前置审批，
 * 审批通过后才会执行节点本身。审批模式为异步回调，不阻塞线程。</p>
 *
 * <h2>三、边类型与路由</h2>
 *
 * <h3>EdgeType.NORMAL — 普通边</h3>
 * <p>顺序流转，拓扑排序后按序执行。</p>
 *
 * <h3>EdgeType.CONDITIONAL — 条件边</h3>
 * <p>CONDITION 节点根据条件表达式求值结果选择匹配的边。</p>
 * <pre>{@code
 * {
 *   "sourceId": "check", "targetId": "pass-node", "type": "CONDITIONAL",
 *   "conditionExpression": "${score} >= 60", "conditionLabel": "通过"
 * }
 * }</pre>
 *
 * <h3>EdgeType.PARALLEL — 并行边</h3>
 * <p>PARALLEL 节点的分支边，标识并行执行路径。</p>
 *
 * <h2>四、节点间传值机制（核心）</h2>
 * <p>
 * 所有节点共享一个 {@code WorkflowState.variables}（ConcurrentHashMap），
 * 通过 Blackboard 模式间接通信：节点执行后将结果写入共享变量表，
 * 下游节点通过变量引用读取。节点之间不直接传值。
 * </p>
 *
 * <h3>4.1 变量写入（上游产出）</h3>
 * <table border="1">
 *   <tr><th>来源</th><th>变量名</th><th>说明</th></tr>
 *   <tr><td>initialVariables</td><td>原始 key</td><td>请求中的初始变量直接注入，如 score=45</td></tr>
 *   <tr><td>START 节点</td><td>prompt</td><td>initialPrompt 配置值写入 prompt 变量</td></tr>
 *   <tr><td>AGENT 节点</td><td>nodeId.output</td><td>输出文本（String）</td></tr>
 *   <tr><td>AGENT 节点</td><td>nodeId.{payloadKey}</td><td>finalPayload 每项按 nodeId.key 存储</td></tr>
 *   <tr><td>TRANSFORM 节点</td><td>outputVar / nodeId.output / nodeId.result</td><td>自定义变量名 + 默认输出 + 原始结果</td></tr>
 *   <tr><td>SCRIPT 节点</td><td>outputVar / nodeId.output</td><td>自定义变量名 + 默认输出</td></tr>
 *   <tr><td>ASSIGN 节点</td><td>assignments 的 key</td><td>直接以赋值映射的 key 作为变量名</td></tr>
 *   <tr><td>outputMappings</td><td>映射的 value</td><td>将结果字段映射到指定变量名</td></tr>
 * </table>
 *
 * <h3>4.2 变量读取（下游消费）</h3>
 * <table border="1">
 *   <tr><th>方式</th><th>语法</th><th>适用节点</th><th>说明</th></tr>
 *   <tr><td>模板替换</td><td>${varName}</td><td>AGENT(sysPrompt)、HTTP(url/header/body)、ASSIGN</td><td>正则替换，未找到的变量替换为空串</td></tr>
 *   <tr><td>inputMappings</td><td>{"param": "${varName}"}</td><td>所有节点</td><td>显式指定输入参数来源</td></tr>
 *   <tr><td>默认快照</td><td>—</td><td>所有节点</td><td>无 inputMappings 时，取所有变量快照（排除 _ 前缀）</td></tr>
 *   <tr><td>SpEL 变量</td><td>#varName</td><td>SCRIPT(SPEL)</td><td>工作流变量作为 SpEL 上下文变量</td></tr>
 *   <tr><td>JS/Groovy 变量</td><td>varName</td><td>SCRIPT(JS/GROOVY)</td><td>工作流变量直接注入为脚本局部变量</td></tr>
 * </table>
 *
 * <h3>4.3 变量命名规则</h3>
 * <ul>
 *   <li><b>简单变量</b>：initialVariables 的 key、START 的 prompt、ASSIGN 的 key、outputVar 值</li>
 *   <li><b>节点输出变量</b>：{@code nodeId.output}（字符串输出）、{@code nodeId.result}（原始结果）</li>
 *   <li><b>节点 payload 变量</b>：{@code nodeId.payloadKey}（AGENT finalPayload 的每项）</li>
 *   <li><b>内部变量</b>：以 {@code _} 前缀，如 _nodeTimeoutSeconds，不出现在节点默认输入快照中</li>
 *   <li><b>模板引用</b>：${varName} 支持简单名和带点号名（如 ${nodeId.output}），正则 \${(\w[\w.]*)}</li>
 * </ul>
 *
 * <h3>4.4 传值数据流示意</h3>
 * <pre>{@code
 *                    ┌─────────────────────────────────┐
 *                    │   WorkflowState.variables       │
 *                    │   (共享 ConcurrentHashMap)       │
 *                    │                                 │
 *   initialVariables ┼─→ score=45                      │
 *   START            ─┼─→ prompt="..."                  │
 *   AGENT 执行后     ─┼─→ nodeId.output="..."           │
 *   TRANSFORM 执行后 ─┼─→ outputVar=result              │
 *                    └──────────────┬──────────────────┘
 *                                   │
 *              ┌────────────────────┼────────────────────┐
 *              ▼                    ▼                    ▼
 *        resolveNodeInput    resolveTemplateString   buildNodeRequest
 *        (inputMappings)     (${var} 替换)           (sysPrompt 模板替换)
 * }</pre>
 *
 * <h2>五、完整配置示例</h2>
 *
 * <h3>示例1：非 Agent 线性传值（START → TRANSFORM → TRANSFORM → END）</h3>
 * <pre>{@code
 * {
 *   "definition": {
 *     "name": "abc-transform-demo",
 *     "nodes": [
 *       { "id": "a", "name": "起点", "type": "START",
 *         "config": { "initialPrompt": "订单价格计算" } },
 *       { "id": "b", "name": "计算总价", "type": "TRANSFORM",
 *         "config": {
 *           "transformType": "MATH",
 *           "transformConfig": { "expression": "${price} * ${quantity}" },
 *           "outputVar": "total"
 *         } },
 *       { "id": "c", "name": "格式化摘要", "type": "TRANSFORM",
 *         "config": {
 *           "transformType": "TEMPLATE",
 *           "transformConfig": { "template": "订单总额：${total}元（单价${price}×数量${quantity}）" },
 *           "outputVar": "summary"
 *         } },
 *       { "id": "end", "name": "结束", "type": "END" }
 *     ],
 *     "edges": [
 *       { "id": "e1", "sourceId": "a", "targetId": "b", "type": "NORMAL" },
 *       { "id": "e2", "sourceId": "b", "targetId": "c", "type": "NORMAL" },
 *       { "id": "e3", "sourceId": "c", "targetId": "end", "type": "NORMAL" }
 *     ],
 *     "stateConfig": { "persistEnabled": true },
 *     "errorStrategy": "STOP"
 *   },
 *   "initialVariables": { "price": 128, "quantity": 3 }
 * }
 * }</pre>
 * <p>执行链路：score/quantity 注入 → b 计算 128*3=384 存入 total → c 模板渲染 "订单总额：384元..."</p>
 *
 * <h3>示例2：Agent 节点间传值（START → AGENT → AGENT → END）</h3>
 * <pre>{@code
 * {
 *   "definition": {
 *     "name": "agent-passing-demo",
 *     "nodes": [
 *       { "id": "a", "name": "起点", "type": "START",
 *         "config": { "initialPrompt": "生成AI主题" } },
 *       { "id": "b", "name": "展开", "type": "AGENT",
 *         "config": { "agentCode": "default-agent", "sysPrompt": "基于主题展开论述：${prompt}" } },
 *       { "id": "c", "name": "总结", "type": "AGENT",
 *         "config": { "agentCode": "default-agent", "sysPrompt": "总结以下内容：${b.output}" } },
 *       { "id": "end", "name": "结束", "type": "END" }
 *     ],
 *     "edges": [
 *       { "id": "e1", "sourceId": "a", "targetId": "b", "type": "NORMAL" },
 *       { "id": "e2", "sourceId": "b", "targetId": "c", "type": "NORMAL" },
 *       { "id": "e3", "sourceId": "c", "targetId": "end", "type": "NORMAL" }
 *     ]
 *   }
 * }
 * }</pre>
 * <p>执行链路：START 写入 prompt → b 的 sysPrompt 替换 ${prompt} → b 输出存入 b.output →
 * c 的 sysPrompt 替换 ${b.output} → c 输出存入 c.output → END 汇总输出</p>
 *
 * <h2>六、执行架构要点</h2>
 * <ul>
 *   <li><b>拓扑排序</b>：基于 Kahn 算法（BFS），同一对 source→target 的重复边只计一次入度</li>
 *   <li><b>线程池</b>：parallelExecutor 用于 AGENT 节点异步执行和 PARALLEL/LOOP 并行分支</li>
 *   <li><b>状态持久化</b>：通过 WorkflowStateStore（内存/Redis）持久化，支持断点续跑</li>
 *   <li><b>审批暂停/恢复</b>：异步回调模式，PAUSED 状态 + pausedNodeId + pendingRequestId，
 *       审批完成通过 ApprovalResolvedEvent 事件触发恢复</li>
 *   <li><b>执行历史</b>：通过 WorkflowExecutionHistoryService 记录每次执行/暂停/恢复/完成</li>
 * </ul>
 *
 * @author yangqiong
 */
package com.yangqiongai.ai.workflow;
