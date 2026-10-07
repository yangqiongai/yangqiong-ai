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
package com.yangqiongai.ai.workflow.config;

/**
 * 工作流JSON Schema定义
 * <p>
 * 描述 WorkflowDefinition 的JSON 结构，用于：
 * 1. 后端校验前端提交的工作流 JSON
 * 2. 前端 TypeScript 类型定义的参考
 * 3. API 文档中描述工作流定义格式
 * </p>
 * @author yangqiong
 */
public final class WorkflowJsonSchema {

    private WorkflowJsonSchema() {
    }

    /**
     * 工作流定义完整 JSON Schema
     */
    public static final String WORKFLOW_DEFINITION_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "title": "WorkflowDefinition",
              "description": "工作流定义，包含节点、边、状态配置和错误策略",
              "type": "object",
              "required": ["name", "nodes", "edges"],
              "properties": {
                "name": {
                  "type": "string",
                  "description": "工作流名称，唯一标识",
                  "minLength": 1,
                  "maxLength": 128
                },
                "description": {
                  "type": "string",
                  "description": "工作流描述"
                },
                "nodes": {
                  "type": "array",
                  "description": "节点列表",
                  "items": {
                    "$ref": "#/definitions/WorkflowNode"
                  },
                  "minItems": 1
                },
                "edges": {
                  "type": "array",
                  "description": "边列表",
                  "items": {
                    "$ref": "#/definitions/WorkflowEdge"
                  }
                },
                "stateConfig": {
                  "$ref": "#/definitions/StateConfig"
                },
                "errorStrategy": {
                  "$ref": "#/definitions/ErrorStrategy"
                },
                "maxRetries": {
                  "type": "integer",
                  "description": "最大重试次数",
                  "minimum": 0,
                  "default": 0
                },
                "nodeTimeoutSeconds": {
                  "type": "integer",
                  "description": "节点超时时间（秒）",
                  "minimum": 1,
                  "default": 120
                }
              },
              "definitions": {
                "ErrorStrategy": {
                  "type": "string",
                  "description": "错误策略",
                  "enum": ["STOP", "SKIP", "RETRY"],
                  "default": "STOP"
                },
                "StateConfig": {
                  "type": "object",
                  "description": "状态配置",
                  "properties": {
                    "persistEnabled": {
                      "type": "boolean",
                      "description": "是否持久化状态",
                      "default": true
                    },
                    "ttlHours": {
                      "type": "integer",
                      "description": "状态TTL（小时）",
                      "minimum": 1,
                      "default": 48
                    }
                  }
                },
                "NodeType": {
                  "type": "string",
                  "description": "节点类型",
                  "enum": ["AGENT", "CONDITION", "PARALLEL", "LOOP", "SUBGRAPH", "TRANSFORM", "SCRIPT", "HTTP", "ASSIGN", "APPROVAL", "NOTIFY", "TIME_CONTROL", "START", "END"]
                },
                "EdgeType": {
                  "type": "string",
                  "description": "边类型",
                  "enum": ["NORMAL", "CONDITIONAL", "PARALLEL"],
                  "default": "NORMAL"
                },
                "NodePosition": {
                  "type": "object",
                  "description": "节点位置信息（前端渲染用途）",
                  "properties": {
                    "x": {
                      "type": "number",
                      "description": "X坐标"
                    },
                    "y": {
                      "type": "number",
                      "description": "Y坐标"
                    }
                  }
                },
                "WorkflowNode": {
                  "type": "object",
                  "description": "工作流节点",
                  "required": ["id", "name", "type"],
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "节点ID，工作流内唯一",
                      "minLength": 1
                    },
                    "name": {
                      "type": "string",
                      "description": "节点名称",
                      "minLength": 1
                    },
                    "type": {
                      "$ref": "#/definitions/NodeType"
                    },
                    "config": {
                      "type": "object",
                      "description": "节点配置，不同类型节点有不同的配置项",
                      "properties": {
                        "agentCode": {
                          "type": "string",
                          "description": "[AGENT] 任务编码"
                        },
                        "sysPrompt": {
                          "type": "string",
                          "description": "[AGENT] 系统提示词"
                        },
                        "maxIterations": {
                          "type": "integer",
                          "description": "[AGENT/LOOP] 最大迭代次数",
                          "minimum": 1
                        },
                        "toolkitRefs": {
                          "type": "array",
                          "description": "[AGENT] 工具包引用列表",
                          "items": {
                            "type": "string"
                          }
                        },
                        "conditionExpression": {
                          "type": "string",
                          "description": "[CONDITION] 条件表达式，如 ${reportType}"
                        },
                        "branches": {
                          "type": "object",
                          "description": "[CONDITION] 分支映射，key为条件值，value为目标节点ID",
                          "additionalProperties": {
                            "type": "string"
                          }
                        },
                        "branchCount": {
                          "type": "integer",
                          "description": "[PARALLEL] 分支数量",
                          "minimum": 2
                        },
                        "joinType": {
                          "type": "string",
                          "description": "[PARALLEL] 汇聚类型",
                          "enum": ["ALL", "ANY"],
                          "default": "ALL"
                        },
                        "exitCondition": {
                          "type": "string",
                          "description": "[LOOP] 退出条件表达式，如 ${qualityScore} >= 80"
                        },
                        "subNodes": {
                          "type": "array",
                          "description": "[LOOP] 子节点列表",
                          "items": {
                            "$ref": "#/definitions/WorkflowNode"
                          }
                        },
                        "workflowRef": {
                          "type": "string",
                          "description": "[SUBGRAPH] 引用的工作流定义名称"
                        },
                        "transformType": {
                          "type": "string",
                          "description": "[TRANSFORM] 变换类型",
                          "enum": ["SUBSTRING", "REVERSE", "UPPER", "LOWER", "TRIM", "REPLACE", "CONCAT", "TEMPLATE", "LENGTH", "MATH"]
                        },
                        "transformConfig": {
                          "type": "object",
                          "description": "[TRANSFORM] 变换配置"
                        },
                        "inputVar": {
                          "type": "string",
                          "description": "[TRANSFORM/SCRIPT] 输入变量名"
                        },
                        "outputVar": {
                          "type": "string",
                          "description": "[TRANSFORM/SCRIPT/HTTP] 输出变量名"
                        },
                        "scriptType": {
                          "type": "string",
                          "description": "[SCRIPT] 脚本类型",
                          "enum": ["SPEL", "JS", "GROOVY"]
                        },
                        "expression": {
                          "type": "string",
                          "description": "[SCRIPT] 表达式或脚本内容"
                        },
                        "url": {
                          "type": "string",
                          "description": "[HTTP] 请求URL"
                        },
                        "method": {
                          "type": "string",
                          "description": "[HTTP] 请求方法",
                          "enum": ["GET", "POST", "PUT", "DELETE"]
                        },
                        "headers": {
                          "type": "object",
                          "description": "[HTTP] 请求头",
                          "additionalProperties": {
                            "type": "string"
                          }
                        },
                        "body": {
                          "type": "string",
                          "description": "[HTTP] 请求体"
                        },
                        "timeout": {
                          "type": "integer",
                          "description": "[HTTP] 超时时间（毫秒）",
                          "minimum": 1
                        },
                        "assignments": {
                          "type": "object",
                          "description": "[ASSIGN] 变量赋值映射，key为变量名，value为值或表达式",
                          "additionalProperties": {
                            "type": "string"
                          }
                        }
                      }
                    },
                    "approvalConfig": {
                      "$ref": "#/definitions/NodeApprovalConfig"
                    },
                    "position": {
                      "$ref": "#/definitions/NodePosition"
                    }
                  }
                },
                "NodeApprovalConfig": {
                  "type": "object",
                  "description": "节点审批配置，APPROVAL节点必填，其他节点可选（配置后节点执行前触发审批）",
                  "properties": {
                    "reason": {
                      "type": "string",
                      "description": "审批原因说明"
                    },
                    "options": {
                      "type": "array",
                      "description": "审批选项列表，审批人从中选择一个",
                      "items": {
                        "type": "string"
                      }
                    },
                    "inputFields": {
                      "type": "array",
                      "description": "需审批人填写的字段名列表",
                      "items": {
                        "type": "string"
                      }
                    },
                    "timeoutSeconds": {
                      "type": "integer",
                      "description": "审批超时时间（秒）",
                      "minimum": 1,
                      "default": 300
                    },
                    "rejectBehavior": {
                      "type": "string",
                      "description": "审批拒绝后的行为",
                      "enum": ["FAIL", "SKIP", "RETRY"],
                      "default": "FAIL"
                    }
                  }
                },
                "WorkflowEdge": {
                  "type": "object",
                  "description": "工作流边",
                  "required": ["id", "sourceId", "targetId"],
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "边ID，工作流内唯一",
                      "minLength": 1
                    },
                    "sourceId": {
                      "type": "string",
                      "description": "源节点ID",
                      "minLength": 1
                    },
                    "targetId": {
                      "type": "string",
                      "description": "目标节点ID",
                      "minLength": 1
                    },
                    "type": {
                      "$ref": "#/definitions/EdgeType"
                    },
                    "conditionExpression": {
                      "type": "string",
                      "description": "条件表达式（条件边专用），如 research、simple"
                    },
                    "conditionLabel": {
                      "type": "string",
                      "description": "条件标签（前端显示用），如 调研报告、简单报告"
                    }
                  }
                },
                "ExecutionStatus": {
                  "type": "string",
                  "description": "执行状态",
                  "enum": ["PENDING", "RUNNING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED"]
                },
                "NodeExecutionStatus": {
                  "type": "object",
                  "description": "节点执行状态",
                  "required": ["nodeId", "status"],
                  "properties": {
                    "nodeId": {
                      "type": "string",
                      "description": "节点ID"
                    },
                    "nodeName": {
                      "type": "string",
                      "description": "节点名称"
                    },
                    "status": {
                      "$ref": "#/definitions/ExecutionStatus"
                    },
                    "output": {
                      "type": "object",
                      "description": "节点输出结果（AgentResult）",
                      "properties": {
                        "output": {
                          "type": "string",
                          "description": "输出文本"
                        },
                        "success": {
                          "type": "boolean",
                          "description": "是否成功"
                        },
                        "errorMessage": {
                          "type": "string",
                          "description": "错误信息"
                        }
                      }
                    },
                    "startTime": {
                      "type": "integer",
                      "description": "开始时间（时间戳毫秒）"
                    },
                    "endTime": {
                      "type": "integer",
                      "description": "结束时间（时间戳毫秒）"
                    },
                    "errorMessage": {
                      "type": "string",
                      "description": "错误信息"
                    },
                    "iterationCount": {
                      "type": "integer",
                      "description": "迭代次数（循环节点专用）",
                      "minimum": 0
                    },
                    "retryCount": {
                      "type": "integer",
                      "description": "重试次数（RETRY策略专用）",
                      "minimum": 0
                    }
                  }
                },
                "WorkflowState": {
                  "type": "object",
                  "description": "工作流状态",
                  "required": ["instanceId", "definitionName", "status"],
                  "properties": {
                    "instanceId": {
                      "type": "string",
                      "description": "工作流实例ID"
                    },
                    "definitionName": {
                      "type": "string",
                      "description": "工作流定义名称"
                    },
                    "status": {
                      "$ref": "#/definitions/ExecutionStatus"
                    },
                    "nodeStates": {
                      "type": "object",
                      "description": "节点执行状态 Map<nodeId, NodeExecutionStatus>",
                      "additionalProperties": {
                        "$ref": "#/definitions/NodeExecutionStatus"
                      }
                    },
                    "variables": {
                      "type": "object",
                      "description": "工作流变量",
                      "additionalProperties": {}
                    },
                    "definitionSnapshot": {
                      "type": "string",
                      "description": "工作流定义快照（JSON，用于恢复）"
                    },
                    "createTime": {
                      "type": "integer",
                      "description": "创建时间（时间戳毫秒）"
                    },
                    "updateTime": {
                      "type": "integer",
                      "description": "更新时间（时间戳毫秒）"
                    },
                    "definitionVersion": {
                      "type": "integer",
                      "description": "执行时的定义版本号"
                    },
                    "cancelRequested": {
                      "type": "boolean",
                      "description": "是否请求取消"
                    }
                  }
                }
              }
            }
            """;

    /**
     * 节点配置属性说明（按节点类型分组）
     */
    public static final String NODE_CONFIG_DESCRIPTION = """
            节点配置（config）属性说明：

            [AGENT] Agent节点
            - agentCode: string, 任务编码
            - sysPrompt: string, 系统提示词
            - maxIterations: integer, 最大迭代次数
            - toolkitRefs: string[], 工具包引用列表

            [CONDITION] 条件分支节点
            - conditionExpression: string, 条件表达式，如 ${reportType}
            - branches: object, 分支映射，key为条件值，value为目标节点ID

            [PARALLEL] 并行网关节点
            - branchCount: integer, 分支数量
            - joinType: string, 汇聚类型（ALL/ANY）

            [LOOP] 循环节点
            - exitCondition: string, 退出条件表达式，如 ${qualityScore} >= 80
            - maxIterations: integer, 最大迭代次数
            - subNodes: WorkflowNode[], 子节点列表

            [SUBGRAPH] 子图节点
            - workflowRef: string, 引用的工作流定义名称

            [TRANSFORM] 数据变换节点
            - transformType: string, 变换类型（SUBSTRING/REVERSE/UPPER/LOWER/TRIM/REPLACE/CONCAT/TEMPLATE/LENGTH/MATH）
            - transformConfig: object, 变换配置
            - inputVar: string, 输入变量名
            - outputVar: string, 输出变量名

            [SCRIPT] 脚本/表达式节点
            - scriptType: string, 脚本类型（SPEL/JS/GROOVY）
            - expression: string, 表达式或脚本内容
            - inputVar: string, 输入变量名
            - outputVar: string, 输出变量名

            [HTTP] HTTP请求节点
            - url: string, 请求URL
            - method: string, 请求方法（GET/POST/PUT/DELETE）
            - headers: object, 请求头
            - body: string, 请求体
            - timeout: integer, 超时时间（毫秒）
            - outputVar: string, 输出变量名

            [ASSIGN] 变量赋值节点
            - assignments: object, 变量赋值映射，key为变量名，value为值或表达式（支持常量、${var}变量引用、#{expr} SpEL表达式）

            [APPROVAL] 审批节点
            - approvalConfig: NodeApprovalConfig, 审批配置（必填）

            [NOTIFY] 通知节点
            - channelId: string, 渠道配置ID（必填，使用集成渠道管理中已配置的渠道ID）
            - channelType: string, 渠道类型（DINGTALK/FEISHU/WECOM/EMAIL/WEBHOOK），可选
            - title: string, 通知标题（支持${var}变量模板）
            - content: string, 通知内容（支持${var}变量模板，必填）
            - level: string, 通知级别（INFO/WARN/ERROR），默认INFO
            - override: object, 覆盖渠道已配置参数（如接收人to，值支持${var}变量模板）
            - async: boolean, 是否异步发送（默认false；异步时不产生notifyOutput结果变量）
            - ignoreFailure: boolean, 发送失败是否忽略（默认true，保证通知失败不影响主流程）

            [TIME_CONTROL] 时间控制节点
            - timeControlConfig: NodeTimeControlConfig, 时间控制配置（必填）

            节点审批配置（approvalConfig）属性说明：
            - reason: string, 审批原因说明
            - options: string[], 审批选项列表
            - inputFields: string[], 需审批人填写的字段名列表
            - timeoutSeconds: integer, 审批超时时间（秒），默认300
            - rejectBehavior: string, 拒绝行为（FAIL/SKIP/RETRY），默认FAIL

            时间控制配置（timeControlConfig）属性说明：
            - timeType: string, 时间模式（DELAY延迟秒数/COUNTDOWN倒计时/SPECIFIC具体时间/PERIODIC周期性时间/CRON cron表达式），默认DELAY
            - delaySeconds: integer, 延迟秒数（DELAY模式）
            - countdownMinutes: integer, 倒计时分钟数（COUNTDOWN模式）
            - countdownSeconds: integer, 倒计时秒数（COUNTDOWN模式）
            - specificTime: string, 具体时间，格式yyyy-MM-dd HH:mm:ss（SPECIFIC模式）
            - cronExpression: string, cron表达式，支持5段"分 时 日 月 周"与6段"秒 分 时 日 月 周"，如每15分钟：0 0/15 * * * *（CRON模式）
            - periodType: string, 周期类型（DAILY每天/WEEKLY每周/MONTHLY每月），默认DAILY（PERIODIC模式）
            - periodTime: string, 周期执行时间点，格式HH:mm（PERIODIC模式）
            - periodWeekdays: integer[], 每周执行日，1=周一至7=周日（WEEKLY模式）
            - periodDayOfMonth: integer, 每月几号执行，1-31（MONTHLY模式）
            - workdayOnly: boolean, 是否仅在工作日（周一至周五）执行，默认false（仅PERIODIC模式生效）
            """;
}
