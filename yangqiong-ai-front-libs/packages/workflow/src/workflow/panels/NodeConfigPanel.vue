<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { NodeType, NodeApprovalConfig, TimeControlConfig, WorkflowDefinition, WorkflowNodeExecData } from '../types/workflow'

const props = defineProps<{
  nodeId: string
  nodeName: string
  nodeType: NodeType
  config: Record<string, any>
  inputMappings?: Record<string, string>
  outputMappings?: Record<string, string>
  approvalConfig?: NodeApprovalConfig
  timeControlConfig?: TimeControlConfig
  timeoutSeconds?: number
  maxRetries?: number
  nodeOptions?: Array<{ id: string; name: string }>
  agentOptions?: Array<{ code: string; name: string }>
  /** 集成渠道下拉选项，供通知节点选择 */
  channelOptions?: Array<{ id: string; name: string; type?: string }>
  definition?: WorkflowDefinition
  /** 获取画布当前最新定义（props.definition 挂载后不回传更新，试运行必须用实时定义） */
  getDefinition?: () => WorkflowDefinition
  /** 本次工作流执行后节点的输入/输出详情 */
  execData?: WorkflowNodeExecData
  /** 是否启用企业版专属能力（审批节点等），非企业版时审批配置提示不可用 */
  enterpriseFeatures?: boolean
  readonly: boolean
}>()

const emit = defineEmits<{
  (e: 'update', data: { name: string; config: Record<string, any>; inputMappings?: Record<string, string>; outputMappings?: Record<string, string>; approvalConfig?: NodeApprovalConfig; timeControlConfig?: TimeControlConfig; timeoutSeconds?: number; maxRetries?: number }): void
  (e: 'delete'): void
}>()

function cloneApprovalConfig(cfg?: NodeApprovalConfig): NodeApprovalConfig {
  return {
    reason: cfg?.reason || '',
    options: cfg?.options ? [...cfg.options] : [],
    inputFields: cfg?.inputFields ? [...cfg.inputFields] : [],
    timeoutSeconds: cfg?.timeoutSeconds ?? 300,
    rejectBehavior: cfg?.rejectBehavior || 'FAIL',
  }
}

function cloneTimeControlConfig(cfg?: TimeControlConfig): TimeControlConfig {
  return {
    timeType: cfg?.timeType || 'DELAY',
    delaySeconds: cfg?.delaySeconds ?? 10,
    countdownMinutes: cfg?.countdownMinutes ?? 0,
    countdownSeconds: cfg?.countdownSeconds ?? 30,
    // 后端格式"yyyy-MM-dd HH:mm:ss"转datetime-local所需的"T"分隔格式
    specificTime: cfg?.specificTime ? cfg.specificTime.replace(' ', 'T') : '',
    cronExpression: cfg?.cronExpression || '0/15 * * * *',
    periodType: cfg?.periodType || 'DAILY',
    periodTime: cfg?.periodTime || '09:00',
    periodWeekdays: cfg?.periodWeekdays ? [...cfg.periodWeekdays] : [1, 2, 3, 4, 5],
    periodDayOfMonth: cfg?.periodDayOfMonth ?? 1,
    workdayOnly: cfg?.workdayOnly ?? false,
  }
}

const formData = reactive({
  name: props.nodeName || '',
  config: { ...props.config } as Record<string, any>,
  inputMappings: { ...(props.inputMappings || {}) } as Record<string, string>,
  outputMappings: { ...(props.outputMappings || {}) } as Record<string, string>,
  approvalConfig: cloneApprovalConfig(props.approvalConfig) as NodeApprovalConfig,
  approvalEnabled: !!props.approvalConfig,
  timeControlConfig: cloneTimeControlConfig(props.timeControlConfig) as TimeControlConfig,
  timeoutSeconds: props.timeoutSeconds as number | undefined,
  maxRetries: props.maxRetries as number | undefined,
})

expandTransformConfig(formData.config)
initLoopMode(formData.config)

watch(
  () => [props.nodeId, props.nodeName, props.config, props.inputMappings, props.outputMappings, props.approvalConfig, props.timeControlConfig, props.timeoutSeconds, props.maxRetries],
  () => {
    formData.name = props.nodeName || ''
    formData.config = { ...props.config }
    expandTransformConfig(formData.config)
    initLoopMode(formData.config)
    formData.inputMappings = { ...(props.inputMappings || {}) }
    formData.outputMappings = { ...(props.outputMappings || {}) }
    formData.approvalConfig = cloneApprovalConfig(props.approvalConfig)
    formData.approvalEnabled = !!props.approvalConfig
    formData.timeControlConfig = cloneTimeControlConfig(props.timeControlConfig)
    formData.timeoutSeconds = props.timeoutSeconds
    formData.maxRetries = props.maxRetries
  },
)

// 后端嵌套transformConfig展开为面板扁平键（加载时调用，供v-model双向绑定）
function expandTransformConfig(config: Record<string, any>) {
  const nested = config.transformConfig
  if (nested && typeof nested === 'object') {
    for (const [key, value] of Object.entries(nested)) {
      const flatKey = `transformConfig${key.charAt(0).toUpperCase()}${key.slice(1)}`
      if (config[flatKey] === undefined) {
        config[flatKey] = value
      }
    }
  }
}

// 面板扁平键压缩为后端嵌套transformConfig（提交前调用，仅处理副本不回改表单）
function compressTransformConfig(config: Record<string, any>) {
  const nested: Record<string, unknown> = {
    ...(config.transformConfig && typeof config.transformConfig === 'object' ? config.transformConfig : {}),
  }
  const prefix = 'transformConfig'
  for (const key of Object.keys(config)) {
    if (key.length > prefix.length && key.startsWith(prefix)) {
      const name = key.charAt(prefix.length).toLowerCase() + key.slice(prefix.length + 1)
      if (config[key] !== undefined && config[key] !== '') {
        nested[name] = config[key]
      }
      delete config[key]
    }
  }
  if (Object.keys(nested).length > 0) {
    config.transformConfig = nested
  } else {
    delete config.transformConfig
  }
}

// LOOP面板辅助字段：根据配置判断循环模式（condition条件循环 / iteration数组遍历），_loopMode不写入工作流定义
function initLoopMode(config: Record<string, any>) {
  if (config._loopMode !== undefined) return
  const iterateOver = config.iterateOver
  config._loopMode = iterateOver != null && String(iterateOver).trim() !== '' ? 'iteration' : 'condition'
}

// LOOP模式切换时清理另一模式的配置字段，避免后端按残留字段误判循环模式
function switchLoopMode() {
  const mode = formData.config._loopMode
  if (mode === 'iteration') {
    delete formData.config.exitCondition
  } else {
    delete formData.config.iterateOver
    delete formData.config.currentItemVar
    delete formData.config.currentIndexVar
  }
  handleUpdate()
}

const nodeTypeLabel = computed(() => {
  const labels: Record<NodeType, string> = {
    AGENT: 'Agent 节点',
    CONDITION: '条件分支',
    PARALLEL: '并行网关',
    LOOP: '循环节点',
    SUBGRAPH: '子图节点',
    TRANSFORM: '数据变换',
    SCRIPT: '脚本/表达式',
    HTTP: 'HTTP 请求',
    ASSIGN: '变量赋值',
    APPROVAL: '审批节点',
    NOTIFY: '通知节点',
    TIME_CONTROL: '时间控制',
    START: '开始节点',
    END: '结束节点',
  }
  return labels[props.nodeType] || props.nodeType
})

// 通知级别选项
const notifyLevels = [
  { value: 'INFO', label: '提示' },
  { value: 'WARN', label: '警告' },
  { value: 'ERROR', label: '严重' },
]

// 通知节点参数覆盖条目（键 -> 值，值支持 ${var} 变量模板）
const notifyOverrideEntries = computed(() => {
  return Object.entries(parseStringMapValue(formData.config.override)).map(([key, value]) => ({ key, value }))
})

function addNotifyOverride() {
  const override = parseStringMapValue(formData.config.override)
  override[genKey('param')] = ''
  formData.config.override = override
  handleUpdate()
}

function removeNotifyOverride(index: number) {
  const entries = Object.entries(parseStringMapValue(formData.config.override))
  entries.splice(index, 1)
  formData.config.override = Object.fromEntries(entries)
  handleUpdate()
}

function updateNotifyOverride(index: number, field: 'key' | 'value', val: string) {
  const entries = Object.entries(parseStringMapValue(formData.config.override))
  if (field === 'key') {
    entries[index] = [uniqueKey(val, entries.map(([k]) => k), index), entries[index][1]]
  } else {
    entries[index][1] = val
  }
  formData.config.override = Object.fromEntries(entries)
  handleUpdate()
}

// 切换通知渠道，联动渠道类型回显（渠道选项未覆盖当前值时保留显示）
const resolvedChannelOptions = computed(() => {
  const options = props.channelOptions ?? []
  const current = formData.config.channelId as string | undefined
  if (current && !options.some((opt) => opt.id === current)) {
    return [...options, { id: current, name: `#${current}`, type: formData.config.channelType }]
  }
  return options
})

function updateNotifyChannel(id: string) {
  if (id === '') {
    delete formData.config.channelId
    delete formData.config.channelType
  } else {
    formData.config.channelId = id
    const matched = (props.channelOptions ?? []).find((opt) => opt.id === id)
    if (matched?.type) {
      formData.config.channelType = matched.type
    }
  }
  handleUpdate()
}

const rejectBehaviors = [
  { value: 'FAIL', label: '失败（停止工作流）' },
  { value: 'SKIP', label: '跳过（继续后续节点）' },
  { value: 'RETRY', label: '重试（重新执行节点）' },
]

// 时间控制节点选项（前2项为社区可用的时间模式，后3项为企业版专属的高级时间模式）
const timeTypes = [
  { value: 'DELAY', label: '延迟执行（几秒后继续）' },
  { value: 'COUNTDOWN', label: '倒计时（分+秒后继续）' },
  { value: 'SPECIFIC', label: '具体时间（等待至指定时刻）' },
  { value: 'PERIODIC', label: '周期性时间（等待至下一个周期点）' },
  { value: 'CRON', label: 'Cron 表达式（支持每N分钟等高频间隔）' },
]

// 下拉分组：时间模式（社区可用）与高级时间模式（企业版专属）
const basicTimeTypes = timeTypes.slice(0, 2)
const advancedTimeTypes = timeTypes.slice(2)

// 高级时间模式（具体时间/周期性时间/Cron）为企业版专属，社区版仅展示不可选
function isTimeTypeDisabled(value: string) {
  return !props.enterpriseFeatures && advancedTimeTypes.some((t) => t.value === value)
}

// cron快捷模板
const cronPresets = [
  { value: '0/15 * * * *', label: '每15分钟' },
  { value: '0/30 * * * *', label: '每30分钟' },
  { value: '0 * * * *', label: '每小时' },
  { value: '0 9 * * *', label: '每天9点' },
]

const periodTypes = [
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'MONTHLY', label: '每月' },
]

const weekdayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]

// 切换时间模式时清理其他模式的残留字段，避免后端按残留字段误判
function switchTimeType() {
  const cfg = formData.timeControlConfig
  if (cfg.timeType === 'DELAY') {
    delete cfg.countdownMinutes
    delete cfg.countdownSeconds
    delete cfg.specificTime
    delete cfg.cronExpression
    delete cfg.periodType
    delete cfg.periodTime
    delete cfg.periodWeekdays
    delete cfg.periodDayOfMonth
    delete cfg.workdayOnly
  } else if (cfg.timeType === 'COUNTDOWN') {
    delete cfg.delaySeconds
    delete cfg.specificTime
    delete cfg.cronExpression
    delete cfg.periodType
    delete cfg.periodTime
    delete cfg.periodWeekdays
    delete cfg.periodDayOfMonth
    delete cfg.workdayOnly
  } else if (cfg.timeType === 'SPECIFIC') {
    delete cfg.delaySeconds
    delete cfg.countdownMinutes
    delete cfg.countdownSeconds
    delete cfg.cronExpression
    delete cfg.periodType
    delete cfg.periodTime
    delete cfg.periodWeekdays
    delete cfg.periodDayOfMonth
    delete cfg.workdayOnly
  } else if (cfg.timeType === 'PERIODIC') {
    delete cfg.delaySeconds
    delete cfg.countdownMinutes
    delete cfg.countdownSeconds
    delete cfg.specificTime
    delete cfg.cronExpression
  } else if (cfg.timeType === 'CRON') {
    delete cfg.delaySeconds
    delete cfg.countdownMinutes
    delete cfg.countdownSeconds
    delete cfg.specificTime
    delete cfg.periodType
    delete cfg.periodTime
    delete cfg.periodWeekdays
    delete cfg.periodDayOfMonth
    delete cfg.workdayOnly
  }
  handleUpdate()
}

// 切换周期类型时清理对侧残留字段
function switchPeriodType() {
  const cfg = formData.timeControlConfig
  if (cfg.periodType === 'WEEKLY') {
    delete cfg.periodDayOfMonth
  } else if (cfg.periodType === 'MONTHLY') {
    delete cfg.periodWeekdays
  }
  handleUpdate()
}

// 切换星期执行日勾选状态
function toggleWeekday(day: number) {
  const cfg = formData.timeControlConfig
  const current = cfg.periodWeekdays || []
  cfg.periodWeekdays = current.includes(day)
    ? current.filter((d) => d !== day)
    : [...current, day].sort()
  handleUpdate()
}

// Agent 下拉选项：当前值不在列表中时保留显示，避免打开面板时回显丢失
const resolvedAgentOptions = computed(() => {
  const options = props.agentOptions ?? []
  const current = formData.config.agentCode as string | undefined
  if (current && !options.some((opt) => opt.code === current)) {
    return [...options, { code: current, name: current }]
  }
  return options
})

// 切换Agent编码，清空选项时移除配置项
function updateAgentCode(code: string) {
  if (code === '') {
    delete formData.config.agentCode
  } else {
    formData.config.agentCode = code
  }
  handleUpdate()
}

// 生成不重复条目 key（时间戳+随机后缀，避免连点冲突）
function genKey(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

// key 与其他条目重名时自动追加序号，防止 Object 合并静默丢数据
function uniqueKey(key: string, allKeys: string[], selfIndex: number): string {
  const taken = (k: string) => allKeys.some((existing, i) => existing === k && i !== selfIndex)
  if (!taken(key)) return key
  let seq = 2
  while (taken(`${key}_${seq}`)) seq++
  return `${key}_${seq}`
}

// 兼容对象与JSON字符串两种形态的Map配置（存量数据可能为字符串）
function parseStringMapValue(raw: unknown): Record<string, string> {
  if (raw && typeof raw === 'object') {
    return Object.fromEntries(Object.entries(raw as Record<string, unknown>).map(([k, v]) => [k, String(v ?? '')]))
  }
  if (typeof raw === 'string' && raw.trim() !== '') {
    try {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        return Object.fromEntries(Object.entries(parsed as Record<string, unknown>).map(([k, v]) => [k, String(v ?? '')]))
      }
    } catch {
      return {}
    }
  }
  return {}
}

// CONDITION 节点分支映射管理（条件值 -> 目标节点ID）
const branchEntries = computed(() => {
  return Object.entries(parseStringMapValue(formData.config.branches)).map(([key, value]) => ({ key, value }))
})

function addBranchEntry() {
  const branches = parseStringMapValue(formData.config.branches)
  branches[genKey('branch')] = ''
  formData.config.branches = branches
  handleUpdate()
}

function removeBranchEntry(index: number) {
  const entries = Object.entries(parseStringMapValue(formData.config.branches))
  entries.splice(index, 1)
  formData.config.branches = Object.fromEntries(entries)
  handleUpdate()
}

function updateBranchEntry(index: number, field: 'key' | 'value', val: string) {
  const entries = Object.entries(parseStringMapValue(formData.config.branches))
  if (field === 'key') {
    entries[index] = [uniqueKey(val, entries.map(([k]) => k), index), entries[index][1]]
  } else {
    entries[index][1] = val
  }
  formData.config.branches = Object.fromEntries(entries)
  handleUpdate()
}

// HTTP 请求头管理（header名 -> 值）
const headerEntries = computed(() => {
  return Object.entries(parseStringMapValue(formData.config.headers)).map(([key, value]) => ({ key, value }))
})

function addHeaderEntry() {
  const headers = parseStringMapValue(formData.config.headers)
  headers[genKey('header')] = ''
  formData.config.headers = headers
  handleUpdate()
}

function removeHeaderEntry(index: number) {
  const entries = Object.entries(parseStringMapValue(formData.config.headers))
  entries.splice(index, 1)
  formData.config.headers = Object.fromEntries(entries)
  handleUpdate()
}

function updateHeaderEntry(index: number, field: 'key' | 'value', val: string) {
  const entries = Object.entries(parseStringMapValue(formData.config.headers))
  if (field === 'key') {
    entries[index] = [uniqueKey(val, entries.map(([k]) => k), index), entries[index][1]]
  } else {
    entries[index][1] = val
  }
  formData.config.headers = Object.fromEntries(entries)
  handleUpdate()
}

// options 列表管理
const optionEntries = computed(() => formData.approvalConfig.options || [])

function addOption() {
  if (!formData.approvalConfig.options) formData.approvalConfig.options = []
  formData.approvalConfig.options.push('')
  handleUpdate()
}

function removeOption(index: number) {
  formData.approvalConfig.options?.splice(index, 1)
  handleUpdate()
}

function updateOption(index: number, val: string) {
  if (formData.approvalConfig.options) {
    formData.approvalConfig.options[index] = val
    handleUpdate()
  }
}

// inputFields 列表管理
const inputFieldEntries = computed(() => formData.approvalConfig.inputFields || [])

function addInputField() {
  if (!formData.approvalConfig.inputFields) formData.approvalConfig.inputFields = []
  formData.approvalConfig.inputFields.push('')
  handleUpdate()
}

function removeInputField(index: number) {
  formData.approvalConfig.inputFields?.splice(index, 1)
  handleUpdate()
}

function updateInputField(index: number, val: string) {
  if (formData.approvalConfig.inputFields) {
    formData.approvalConfig.inputFields[index] = val
    handleUpdate()
  }
}

function toggleApproval(enabled: boolean) {
  formData.approvalEnabled = enabled
  handleUpdate()
}

// ASSIGN 节点的赋值项管理
const assignEntries = computed(() => {
  const assignments = formData.config.assignments || {}
  return Object.entries(assignments).map(([key, value]) => ({ key, value: String(value) }))
})

function addAssignEntry() {
  if (!formData.config.assignments) formData.config.assignments = {}
  formData.config.assignments[genKey('var')] = ''
  handleUpdate()
}

function removeAssignEntry(index: number) {
  const entries = Object.entries(formData.config.assignments || {})
  entries.splice(index, 1)
  formData.config.assignments = Object.fromEntries(entries)
  handleUpdate()
}

function updateAssignEntry(index: number, field: 'key' | 'value', val: string) {
  const entries = Object.entries(formData.config.assignments || {})
  if (field === 'key') {
    entries[index] = [uniqueKey(val, entries.map(([k]) => k), index), entries[index][1]]
  } else {
    entries[index][1] = val
  }
  formData.config.assignments = Object.fromEntries(entries)
  handleUpdate()
}

// inputMappings 管理
const inputMappingEntries = computed(() => {
  return Object.entries(formData.inputMappings).map(([key, value]) => ({ key, value }))
})

function addInputMapping() {
  formData.inputMappings[genKey('param')] = ''
  handleUpdate()
}

function removeInputMapping(index: number) {
  const entries = Object.entries(formData.inputMappings)
  entries.splice(index, 1)
  formData.inputMappings = Object.fromEntries(entries)
  handleUpdate()
}

function updateInputMapping(index: number, field: 'key' | 'value', val: string) {
  const entries = Object.entries(formData.inputMappings)
  if (field === 'key') {
    entries[index] = [uniqueKey(val, entries.map(([k]) => k), index), entries[index][1]]
  } else {
    entries[index][1] = val
  }
  formData.inputMappings = Object.fromEntries(entries)
  handleUpdate()
}

// outputMappings 管理
const outputMappingEntries = computed(() => {
  return Object.entries(formData.outputMappings).map(([key, value]) => ({ key, value }))
})

function addOutputMapping() {
  formData.outputMappings[genKey('field')] = ''
  handleUpdate()
}

function removeOutputMapping(index: number) {
  const entries = Object.entries(formData.outputMappings)
  entries.splice(index, 1)
  formData.outputMappings = Object.fromEntries(entries)
  handleUpdate()
}

function updateOutputMapping(index: number, field: 'key' | 'value', val: string) {
  const entries = Object.entries(formData.outputMappings)
  if (field === 'key') {
    entries[index] = [uniqueKey(val, entries.map(([k]) => k), index), entries[index][1]]
  } else {
    entries[index][1] = val
  }
  formData.outputMappings = Object.fromEntries(entries)
  handleUpdate()
}

function buildApprovalConfig(): NodeApprovalConfig | undefined {
  // APPROVAL 节点始终输出 approvalConfig；其他节点仅在启用时输出
  if (props.nodeType !== 'APPROVAL' && !formData.approvalEnabled) {
    return undefined
  }
  const cfg = formData.approvalConfig
  const trimmedOptions = (cfg.options || []).map((o) => o.trim()).filter((o) => o !== '')
  const trimmedFields = (cfg.inputFields || []).map((f) => f.trim()).filter((f) => f !== '')
  return {
    reason: cfg.reason?.trim() || undefined,
    options: trimmedOptions.length > 0 ? trimmedOptions : undefined,
    inputFields: trimmedFields.length > 0 ? trimmedFields : undefined,
    timeoutSeconds: cfg.timeoutSeconds ?? 300,
    rejectBehavior: cfg.rejectBehavior || 'FAIL',
  }
}

// 输入框留空时 v-model.number 会产生空字符串，统一归一为 undefined 以继承定义级配置
function normalizeOptionalNumber(val: number | undefined): number | undefined {
  return typeof val === 'number' && Number.isFinite(val) ? val : undefined
}

function buildTimeControlConfig(): TimeControlConfig | undefined {
  if (props.nodeType !== 'TIME_CONTROL') {
    return undefined
  }
  const cfg = formData.timeControlConfig
  const timeType = cfg.timeType || 'DELAY'
  const built: TimeControlConfig = { timeType }
  if (timeType === 'DELAY') {
    built.delaySeconds = cfg.delaySeconds && cfg.delaySeconds > 0 ? cfg.delaySeconds : 10
  } else if (timeType === 'COUNTDOWN') {
    built.countdownMinutes = cfg.countdownMinutes && cfg.countdownMinutes > 0 ? cfg.countdownMinutes : 0
    built.countdownSeconds = cfg.countdownSeconds && cfg.countdownSeconds > 0 ? cfg.countdownSeconds : 0
    if (built.countdownMinutes === 0 && built.countdownSeconds === 0) {
      built.countdownSeconds = 30
    }
  } else if (timeType === 'SPECIFIC') {
    // datetime-local的"T"分隔格式转后端"yyyy-MM-dd HH:mm:ss"格式
    built.specificTime = cfg.specificTime?.trim() ? cfg.specificTime.trim().replace('T', ' ') : ''
  } else if (timeType === 'CRON') {
    built.cronExpression = cfg.cronExpression?.trim() || '0/15 * * * *'
  } else if (timeType === 'PERIODIC') {
    built.periodType = cfg.periodType || 'DAILY'
    built.periodTime = cfg.periodTime?.trim() || '09:00'
    if (built.periodType === 'WEEKLY') {
      built.periodWeekdays = (cfg.periodWeekdays || []).length > 0 ? [...cfg.periodWeekdays!] : [1, 2, 3, 4, 5]
    }
    if (built.periodType === 'MONTHLY') {
      built.periodDayOfMonth = cfg.periodDayOfMonth && cfg.periodDayOfMonth >= 1 && cfg.periodDayOfMonth <= 31 ? cfg.periodDayOfMonth : 1
    }
    built.workdayOnly = !!cfg.workdayOnly
  }
  return built
}

function handleUpdate() {
  const config = { ...formData.config }
  compressTransformConfig(config)
  // LOOP面板辅助字段不写入工作流定义
  delete config._loopMode
  emit('update', {
    name: formData.name,
    config,
    inputMappings: Object.keys(formData.inputMappings).length > 0 ? { ...formData.inputMappings } : undefined,
    outputMappings: Object.keys(formData.outputMappings).length > 0 ? { ...formData.outputMappings } : undefined,
    approvalConfig: buildApprovalConfig(),
    timeControlConfig: buildTimeControlConfig(),
    timeoutSeconds: normalizeOptionalNumber(formData.timeoutSeconds),
    maxRetries: normalizeOptionalNumber(formData.maxRetries),
  })
}

const transformTypes = [
  { value: 'SUBSTRING', label: '截取子串' },
  { value: 'REVERSE', label: '逆序' },
  { value: 'UPPER', label: '转大写' },
  { value: 'LOWER', label: '转小写' },
  { value: 'TRIM', label: '去空白' },
  { value: 'REPLACE', label: '替换' },
  { value: 'CONCAT', label: '拼接' },
  { value: 'TEMPLATE', label: '模板渲染' },
  { value: 'LENGTH', label: '取长度' },
  { value: 'MATH', label: '数学运算' },
]

const scriptTypes = [
  { value: 'SPEL', label: 'SpEL (Spring EL)' },
  { value: 'JS', label: 'JavaScript' },
  { value: 'GROOVY', label: 'Groovy' },
]

const httpMethods = [
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
  { value: 'PUT', label: 'PUT' },
  { value: 'DELETE', label: 'DELETE' },
]

// ========== 单节点试运行（Mock调试） ==========
const showDebugDrawer = ref(false)
const debugLoading = ref(false)
const debugError = ref('')
const debugResult = ref<any>(null)
const mockEntries = ref<Array<{ key: string; value: string }>>([])

// Mock变量默认列出输入映射的目标变量名，值留空待填
function openDebug() {
  const defaults: string[] = []
  for (const mappingValue of Object.values(formData.inputMappings)) {
    const matches = String(mappingValue || '').match(/\$\{([^}]+)\}/g) || []
    for (const m of matches) {
      const varName = m.slice(2, -1)
      if (!defaults.includes(varName)) defaults.push(varName)
    }
  }
  mockEntries.value = defaults.map((name) => ({ key: name, value: '' }))
  debugError.value = ''
  debugResult.value = null
  showDebugDrawer.value = true
}

function addMockEntry() {
  mockEntries.value.push({ key: '', value: '' })
}

function removeMockEntry(index: number) {
  mockEntries.value.splice(index, 1)
}

// 值按JSON尝试解析（对象/数组/数字/布尔），普通文本按字符串
function parseMockValue(raw: string): unknown {
  const trimmed = raw.trim()
  if (trimmed === '') return undefined
  try {
    return JSON.parse(trimmed)
  } catch {
    return raw
  }
}

const debugSummary = computed(() => {
  const summaries = debugResult.value?.nodeSummaries
  return Array.isArray(summaries) && summaries.length > 0 ? summaries[0] : null
})

const debugInputJson = computed(() => JSON.stringify(debugSummary.value?.input ?? {}, null, 2))
const debugOutputJson = computed(() => JSON.stringify(debugSummary.value?.output ?? {}, null, 2))

async function runDebug() {
  // 面板编辑后的最新配置需实时取定义，props.definition 是挂载时的过时快照
  const definition = props.getDefinition ? props.getDefinition() : props.definition
  if (!definition || debugLoading.value) return
  const mockVariables: Record<string, unknown> = {}
  for (const entry of mockEntries.value) {
    const key = entry.key.trim()
    if (key === '') continue
    const parsed = parseMockValue(entry.value)
    if (parsed !== undefined) mockVariables[key] = parsed
  }
  debugLoading.value = true
  debugError.value = ''
  try {
    const response = await fetch('/api/workflow/debug/node', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        definition,
        nodeId: props.nodeId,
        mockVariables,
      }),
    })
    const payload = await response.json()
    if (payload && typeof payload.success === 'boolean') {
      if (!payload.success) throw new Error(payload.message || '调试请求失败')
      debugResult.value = payload.data ?? null
    } else {
      debugResult.value = payload ?? null
    }
  } catch (e) {
    debugError.value = e instanceof Error ? e.message : '调试请求失败'
  } finally {
    debugLoading.value = false
  }
}

// ========== 执行详情（本次执行输入输出 + 重新执行） ==========
const activeTab = ref<'config' | 'exec'>('config')

const execInputJson = computed(() => JSON.stringify(props.execData?.input ?? {}, null, 2))
const execOutputJson = computed(() => JSON.stringify(props.execData?.output ?? {}, null, 2))

const execStatusLabel = computed(() => {
  const labels: Record<string, string> = {
    COMPLETED: '执行成功',
    FAILED: '执行失败',
    RUNNING: '执行中',
    SKIPPED: '已跳过',
    PAUSED: '已暂停',
    PENDING: '待执行',
  }
  return labels[props.execData?.status ?? ''] || props.execData?.status || '已执行'
})

const execStatusClass = computed(() => {
  const s = props.execData?.status
  if (s === 'COMPLETED') return 'is-success'
  if (s === 'FAILED') return 'is-failed'
  return ''
})

const reExecLoading = ref(false)
const reExecError = ref('')
const reExecResult = ref<any>(null)

const reExecInputJson = computed(() => JSON.stringify(reExecResult.value?.input ?? {}, null, 2))
const reExecOutputJson = computed(() => JSON.stringify(reExecResult.value?.output ?? {}, null, 2))

const reExecSuccess = computed(() => {
  const status = reExecResult.value?.status
  return status === 'COMPLETED' || status === 'SUCCESS' || reExecResult.value?.success === true
})

// 递归排序对象键，消除JSON序列化键顺序差异后再比对
function normalizeJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(normalizeJson)
  if (value && typeof value === 'object') {
    const out: Record<string, unknown> = {}
    for (const key of Object.keys(value as Record<string, unknown>).sort()) {
      out[key] = normalizeJson((value as Record<string, unknown>)[key])
    }
    return out
  }
  return value
}

// 重新执行输出与本次执行输出是否一致
const reExecSame = computed(() => {
  const oldOutput = props.execData?.output
  const newOutput = reExecResult.value?.output
  if (!oldOutput || !newOutput) return false
  return JSON.stringify(normalizeJson(oldOutput)) === JSON.stringify(normalizeJson(newOutput))
})

// 使用本次执行输入重新执行当前节点（复用单节点调试API，不产生实例）
async function runReExecute() {
  const definition = props.getDefinition ? props.getDefinition() : props.definition
  const input = props.execData?.input || {}
  if (!definition || reExecLoading.value) return
  const mockVariables: Record<string, unknown> = {}
  // 输入映射还原为引用变量（参数名 -> ${var}中的变量名），保证重新执行输入与本次一致
  for (const [param, expr] of Object.entries(props.inputMappings || {})) {
    const matched = String(expr ?? '').match(/^\$\{([^}]+)\}$/)
    if (matched) {
      if (input[param] !== undefined) mockVariables[matched[1]] = input[param]
    } else if (input[param] !== undefined) {
      mockVariables[param] = input[param]
    }
  }
  // 输入快照原样注入（无映射节点的输入即变量快照，直接复用作状态变量）
  for (const [key, value] of Object.entries(input)) {
    if (mockVariables[key] === undefined) mockVariables[key] = value
  }
  reExecLoading.value = true
  reExecError.value = ''
  reExecResult.value = null
  try {
    const response = await fetch('/api/workflow/debug/node', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        definition,
        nodeId: props.nodeId,
        mockVariables,
      }),
    })
    const payload = await response.json()
    if (payload && typeof payload.success === 'boolean' && !payload.success) {
      throw new Error(payload.message || '重新执行请求失败')
    }
    const data = payload?.data ?? payload ?? null
    const summary = Array.isArray(data?.nodeSummaries) && data.nodeSummaries.length > 0
      ? data.nodeSummaries[0]
      : data
    reExecResult.value = summary
  } catch (e) {
    reExecError.value = e instanceof Error ? e.message : '重新执行请求失败'
  } finally {
    reExecLoading.value = false
  }
}
</script>

<template>
  <div class="wf-panel wf-panel--node">
    <div class="wf-panel__header">
      <h3>{{ nodeTypeLabel }}</h3>
      <button v-if="!readonly" class="wf-panel__delete-btn" title="删除节点" @click="emit('delete')">🗑️</button>
    </div>
    <div class="wf-panel__tabs">
      <button
        class="wf-panel__tab"
        :class="{ 'is-active': activeTab === 'config' }"
        @click="activeTab = 'config'"
      >
        配置
      </button>
      <button
        class="wf-panel__tab"
        :class="{ 'is-active': activeTab === 'exec' }"
        @click="activeTab = 'exec'"
      >
        执行详情
      </button>
    </div>
    <div class="wf-panel__body">
      <!-- 审批/通知节点为企业版专属能力，社区版加载到含此类节点的流程时提示不可用 -->
      <div
        v-if="(nodeType === 'APPROVAL' || nodeType === 'NOTIFY') && !enterpriseFeatures"
        class="wf-panel__feature-warn"
      >
        {{ nodeType === 'NOTIFY' ? '通知节点' : '审批节点' }}为企业版专属功能，当前版本不可用，请在企业版中处理该节点。
      </div>
      <template v-if="activeTab === 'config'">
      <!-- 节点名称 -->
      <div class="wf-form-group">
        <label class="wf-form-label">节点名称</label>
        <input
          v-model="formData.name"
          class="wf-form-input"
          :disabled="readonly"
          placeholder="请输入节点名称"
          @change="handleUpdate"
        />
      </div>

      <!-- ========== 执行策略 (所有节点类型通用) ========== -->
      <template v-if="!['START', 'END'].includes(nodeType)">
        <div class="wf-section-title">执行策略</div>
        <div class="wf-form-group">
          <label class="wf-form-label">超时秒数</label>
          <input
            v-model.number="formData.timeoutSeconds"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            min="1"
            max="3600"
            placeholder="留空则继承定义级超时配置"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">最大重试次数</label>
          <input
            v-model.number="formData.maxRetries"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            min="0"
            max="10"
            placeholder="留空则继承定义级重试配置"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== AGENT ========== -->
      <template v-if="nodeType === 'AGENT'">
        <div class="wf-form-group">
          <label class="wf-form-label">Agent</label>
          <select
            :value="formData.config.agentCode ?? ''"
            class="wf-form-select"
            :disabled="readonly"
            @change="updateAgentCode(($event.target as HTMLSelectElement).value)"
          >
            <option value="">请选择 Agent</option>
            <option v-for="opt in resolvedAgentOptions" :key="opt.code" :value="opt.code">
              {{ opt.name }}（{{ opt.code }}）
            </option>
          </select>
          <div class="wf-form-hint">从已注册的 Agent 列表中选择本节点调用的执行体</div>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">提示词</label>
          <textarea
            v-model="formData.config.sysPrompt"
            class="wf-form-textarea"
            :disabled="readonly"
            placeholder="例如：请用一句话总结以下内容：${content}"
            rows="4"
            @change="handleUpdate"
          />
          <div class="wf-form-hint">支持 ${var} 引用工作流变量；留空时依次回退：工作流变量 prompt → 上游输入</div>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">最大迭代次数</label>
          <input
            v-model.number="formData.config.maxIterations"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            placeholder="请输入最大迭代次数"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== CONDITION ========== -->
      <template v-if="nodeType === 'CONDITION'">
        <div class="wf-form-group">
          <label class="wf-form-label">条件表达式</label>
          <textarea
            v-model="formData.config.conditionExpression"
            class="wf-form-textarea"
            :disabled="readonly"
            placeholder="${score} >= 60"
            rows="3"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">分支映射（条件值 → 目标节点ID）</label>
          <div class="wf-form-hint">条件值匹配后路由到对应目标节点</div>
        </div>
        <div v-for="(entry, index) in branchEntries" :key="'branch-' + index" class="wf-assign-row">
          <input
            :value="entry.key"
            class="wf-form-input wf-assign-key"
            :disabled="readonly"
            placeholder="条件值"
            @change="updateBranchEntry(index, 'key', ($event.target as HTMLInputElement).value)"
          />
          <span class="wf-assign-eq">→</span>
          <select
            :value="entry.value"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            @change="updateBranchEntry(index, 'value', ($event.target as HTMLSelectElement).value)"
          >
            <option value="">请选择目标节点</option>
            <option v-for="opt in nodeOptions" :key="opt.id" :value="opt.id">
              {{ opt.name }}（{{ opt.id }}）
            </option>
          </select>
          <button v-if="!readonly" class="wf-assign-remove" @click="removeBranchEntry(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addBranchEntry">+ 添加分支</button>
      </template>

      <!-- ========== PARALLEL ========== -->
      <template v-if="nodeType === 'PARALLEL'">
        <div class="wf-form-group">
          <label class="wf-form-label">分支数</label>
          <input
            v-model.number="formData.config.branches"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            min="2"
            placeholder="请输入分支数"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">汇聚模式</label>
          <select
            v-model="formData.config.joinType"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option value="ALL">全部完成</option>
            <option value="ANY">任一完成</option>
          </select>
        </div>
      </template>

      <!-- ========== LOOP ========== -->
      <template v-if="nodeType === 'LOOP'">
        <div class="wf-form-group">
          <label class="wf-form-label">循环模式</label>
          <select
            v-model="formData.config._loopMode"
            class="wf-form-select"
            :disabled="readonly"
            @change="switchLoopMode"
          >
            <option value="condition">条件循环</option>
            <option value="iteration">数组遍历</option>
          </select>
        </div>
        <template v-if="formData.config._loopMode !== 'iteration'">
          <div class="wf-form-group">
            <label class="wf-form-label">退出条件</label>
            <textarea
              v-model="formData.config.exitCondition"
              class="wf-form-textarea"
              :disabled="readonly"
              placeholder="${count} >= 10"
              rows="3"
              @change="handleUpdate"
            />
          </div>
        </template>
        <template v-if="formData.config._loopMode === 'iteration'">
          <div class="wf-form-group">
            <label class="wf-form-label">遍历变量名</label>
            <input
              v-model="formData.config.iterateOver"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="items"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">当前元素变量名</label>
            <input
              v-model="formData.config.currentItemVar"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="currentItem"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">当前索引变量名</label>
            <input
              v-model="formData.config.currentIndexVar"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="currentIndex"
              @change="handleUpdate"
            />
          </div>
        </template>
        <div class="wf-form-group">
          <label class="wf-form-label">最大迭代次数</label>
          <input
            v-model.number="formData.config.maxIterations"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            placeholder="100"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== SUBGRAPH ========== -->
      <template v-if="nodeType === 'SUBGRAPH'">
        <div class="wf-form-group">
          <label class="wf-form-label">引用工作流名称</label>
          <input
            v-model="formData.config.workflowName"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="请输入引用的工作流名称"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== TRANSFORM ========== -->
      <template v-if="nodeType === 'TRANSFORM'">
        <div class="wf-form-group">
          <label class="wf-form-label">变换类型</label>
          <select
            v-model="formData.config.transformType"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option v-for="t in transformTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>

        <!-- SUBSTRING 配置 -->
        <template v-if="formData.config.transformType === 'SUBSTRING'">
          <div class="wf-form-group">
            <label class="wf-form-label">起始位置 (start)</label>
            <input
              v-model.number="formData.config.transformConfigStart"
              class="wf-form-input"
              type="number"
              :disabled="readonly"
              placeholder="0"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">结束位置 (end, 可选)</label>
            <input
              v-model.number="formData.config.transformConfigEnd"
              class="wf-form-input"
              type="number"
              :disabled="readonly"
              placeholder="不填则到末尾"
              @change="handleUpdate"
            />
          </div>
        </template>

        <!-- REPLACE 配置 -->
        <template v-if="formData.config.transformType === 'REPLACE'">
          <div class="wf-form-group">
            <label class="wf-form-label">查找内容 (pattern)</label>
            <input
              v-model="formData.config.transformConfigPattern"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="old"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">替换为 (replacement)</label>
            <input
              v-model="formData.config.transformConfigReplacement"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="new"
              @change="handleUpdate"
            />
          </div>
        </template>

        <!-- CONCAT 配置 -->
        <template v-if="formData.config.transformType === 'CONCAT'">
          <div class="wf-form-group">
            <label class="wf-form-label">前缀 (prefix)</label>
            <input
              v-model="formData.config.transformConfigPrefix"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="前缀内容"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">后缀 (suffix)</label>
            <input
              v-model="formData.config.transformConfigSuffix"
              class="wf-form-input"
              :disabled="readonly"
              placeholder="后缀内容"
              @change="handleUpdate"
            />
          </div>
        </template>

        <!-- TEMPLATE 配置 -->
        <template v-if="formData.config.transformType === 'TEMPLATE'">
          <div class="wf-form-group">
            <label class="wf-form-label">模板内容</label>
            <textarea
              v-model="formData.config.transformConfigTemplate"
              class="wf-form-textarea"
              :disabled="readonly"
              placeholder="Hello ${name}, score=${score}"
              rows="3"
              @change="handleUpdate"
            />
          </div>
        </template>

        <!-- MATH 配置 -->
        <template v-if="formData.config.transformType === 'MATH'">
          <div class="wf-form-group">
            <label class="wf-form-label">数学表达式</label>
            <textarea
              v-model="formData.config.transformConfigExpression"
              class="wf-form-textarea"
              :disabled="readonly"
              placeholder="${x} + ${y} * 2"
              rows="2"
              @change="handleUpdate"
            />
          </div>
        </template>

        <div class="wf-form-group">
          <label class="wf-form-label">输出变量名</label>
          <input
            v-model="formData.config.outputVar"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="自定义输出变量名"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== SCRIPT ========== -->
      <template v-if="nodeType === 'SCRIPT'">
        <div class="wf-form-group">
          <label class="wf-form-label">脚本类型</label>
          <select
            v-model="formData.config.scriptType"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option v-for="t in scriptTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
          </select>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">{{ formData.config.scriptType === 'SPEL' ? 'SpEL 表达式' : '脚本内容' }}</label>
          <textarea
            v-model="formData.config.expression"
            class="wf-form-textarea"
            :disabled="readonly"
            :placeholder="formData.config.scriptType === 'SPEL' ? '#input.toUpperCase()' : 'return input.substring(1);'"
            rows="5"
            @change="handleUpdate"
          />
        </div>
        <div v-if="formData.config.scriptType === 'SPEL'" class="wf-form-hint">
          变量用 #varName 访问，Bean用 @beanName 调用
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">输出变量名</label>
          <input
            v-model="formData.config.outputVar"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="自定义输出变量名"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== HTTP ========== -->
      <template v-if="nodeType === 'HTTP'">
        <div class="wf-form-group">
          <label class="wf-form-label">请求方法</label>
          <select
            v-model="formData.config.method"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option v-for="m in httpMethods" :key="m.value" :value="m.value">{{ m.label }}</option>
          </select>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">URL</label>
          <input
            v-model="formData.config.url"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="https://api.example.com/data (支持 ${var})"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">请求头</label>
          <div class="wf-form-hint">值支持 ${var} 变量替换</div>
        </div>
        <div v-for="(entry, index) in headerEntries" :key="'header-' + index" class="wf-assign-row">
          <input
            :value="entry.key"
            class="wf-form-input wf-assign-key"
            :disabled="readonly"
            placeholder="Header名称"
            @change="updateHeaderEntry(index, 'key', ($event.target as HTMLInputElement).value)"
          />
          <span class="wf-assign-eq">:</span>
          <input
            :value="entry.value"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="Bearer ${token}"
            @change="updateHeaderEntry(index, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeHeaderEntry(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addHeaderEntry">+ 添加请求头</button>
        <div class="wf-form-group">
          <label class="wf-form-label">请求体</label>
          <textarea
            v-model="formData.config.body"
            class="wf-form-textarea"
            :disabled="readonly"
            placeholder='{"key": "value"} (支持 ${var})'
            rows="3"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">超时时间 (ms)</label>
          <input
            v-model.number="formData.config.timeout"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            placeholder="10000"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">响应变量名</label>
          <input
            v-model="formData.config.responseVar"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="自定义响应变量名"
            @change="handleUpdate"
          />
        </div>
      </template>

      <!-- ========== ASSIGN ========== -->
      <template v-if="nodeType === 'ASSIGN'">
        <div class="wf-form-group">
          <label class="wf-form-label">变量赋值</label>
          <div class="wf-form-hint">值支持：常量、变量引用 ${var}、SpEL #{expr}</div>
        </div>
        <div v-for="(entry, index) in assignEntries" :key="index" class="wf-assign-row">
          <input
            :value="entry.key"
            class="wf-form-input wf-assign-key"
            :disabled="readonly"
            placeholder="变量名"
            @change="updateAssignEntry(index, 'key', ($event.target as HTMLInputElement).value)"
          />
          <span class="wf-assign-eq">=</span>
          <input
            :value="entry.value"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="${var} 或 常量"
            @change="updateAssignEntry(index, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeAssignEntry(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addAssignEntry">+ 添加赋值</button>
      </template>

      <!-- ========== APPROVAL (审批节点主配置) ========== -->
      <template v-if="nodeType === 'APPROVAL'">
        <div class="wf-form-group">
          <label class="wf-form-label">审批原因</label>
          <textarea
            v-model="formData.approvalConfig.reason"
            class="wf-form-textarea"
            :disabled="readonly"
            placeholder="请输入需要人工审批的原因说明"
            rows="2"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">审批选项</label>
          <div class="wf-form-hint">审批人从选项中选择一个，留空则仅为确认/拒绝</div>
        </div>
        <div v-for="(opt, index) in optionEntries" :key="'opt-' + index" class="wf-assign-row">
          <input
            :value="opt"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="选项名称"
            @change="updateOption(index, ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeOption(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addOption">+ 添加选项</button>
        <div class="wf-form-group">
          <label class="wf-form-label">收集字段</label>
          <div class="wf-form-hint">审批人需填写的字段名，留空则不收集额外输入</div>
        </div>
        <div v-for="(field, index) in inputFieldEntries" :key="'field-' + index" class="wf-assign-row">
          <input
            :value="field"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="字段名"
            @change="updateInputField(index, ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeInputField(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addInputField">+ 添加字段</button>
        <div class="wf-form-group">
          <label class="wf-form-label">超时时间（秒）</label>
          <input
            v-model.number="formData.approvalConfig.timeoutSeconds"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            min="1"
            placeholder="300"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">拒绝行为</label>
          <select
            v-model="formData.approvalConfig.rejectBehavior"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option v-for="r in rejectBehaviors" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>
        </div>
      </template>

      <!-- ========== NOTIFY (通知节点主配置) ========== -->
      <template v-if="nodeType === 'NOTIFY'">
        <div class="wf-form-group">
          <label class="wf-form-label">集成渠道</label>
          <select
            :value="formData.config.channelId ?? ''"
            class="wf-form-select"
            :disabled="readonly"
            @change="updateNotifyChannel(($event.target as HTMLSelectElement).value)"
          >
            <option value="">请选择集成渠道</option>
            <option v-for="opt in resolvedChannelOptions" :key="opt.id" :value="opt.id">
              {{ opt.name }}（#{{ opt.id }}<template v-if="opt.type"> · {{ opt.type }}</template>）
            </option>
          </select>
          <div class="wf-form-hint">从集成渠道管理中已配置的渠道中选择，发送参数默认使用渠道配置</div>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">通知标题</label>
          <input
            v-model="formData.config.title"
            class="wf-form-input"
            :disabled="readonly"
            placeholder="例如：任务执行提醒 (支持 ${var})"
            @change="handleUpdate"
          />
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">通知内容</label>
          <textarea
            v-model="formData.config.content"
            class="wf-form-textarea"
            :disabled="readonly"
            placeholder="例如：流程 ${workflowName} 已完成，结果：${agent_summary.output}"
            rows="4"
            @change="handleUpdate"
          />
          <div class="wf-form-hint">支持 ${var} 引用工作流变量，内容为空时通知跳过且不影响主流程</div>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">通知级别</label>
          <select
            v-model="formData.config.level"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option v-for="l in notifyLevels" :key="l.value" :value="l.value">{{ l.label }}</option>
          </select>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">发送模式</label>
          <select
            v-model="formData.config.async"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option :value="false">同步（等待发送结果，写入变量 notifyOutput）</option>
            <option :value="true">异步（发起即走，不产生结果变量）</option>
          </select>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">失败处理</label>
          <select
            v-model="formData.config.ignoreFailure"
            class="wf-form-select"
            :disabled="readonly"
            @change="handleUpdate"
          >
            <option :value="true">忽略失败（继续执行后续节点）</option>
            <option :value="false">失败中断（终止工作流）</option>
          </select>
        </div>
        <div class="wf-form-group">
          <label class="wf-form-label">参数覆盖</label>
          <div class="wf-form-hint">覆盖渠道已配置参数（如接收人 to），值支持 ${var} 变量替换，留空项忽略</div>
        </div>
        <div v-for="(entry, index) in notifyOverrideEntries" :key="'override-' + index" class="wf-assign-row">
          <input
            :value="entry.key"
            class="wf-form-input wf-assign-key"
            :disabled="readonly"
            placeholder="参数名"
            @change="updateNotifyOverride(index, 'key', ($event.target as HTMLInputElement).value)"
          />
          <span class="wf-assign-eq">=</span>
          <input
            :value="entry.value"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="${email} 或常量"
            @change="updateNotifyOverride(index, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeNotifyOverride(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addNotifyOverride">+ 添加覆盖参数</button>
      </template>

      <!-- ========== TIME_CONTROL (时间控制节点主配置) ========== -->
      <template v-if="nodeType === 'TIME_CONTROL'">
        <div class="wf-form-group">
          <label class="wf-form-label">时间模式</label>
          <select
            v-model="formData.timeControlConfig.timeType"
            class="wf-form-select"
            :disabled="readonly"
            @change="switchTimeType"
          >
            <optgroup label="时间模式">
              <option v-for="t in basicTimeTypes" :key="t.value" :value="t.value">
                {{ t.label }}
              </option>
            </optgroup>
            <optgroup label="高级时间模式">
              <option
                v-for="t in advancedTimeTypes"
                :key="t.value"
                :value="t.value"
                :disabled="isTimeTypeDisabled(t.value)"
              >
                {{ t.label }}
              </option>
            </optgroup>
          </select>
          <div class="wf-form-hint">执行到该节点时流程暂停，到达设定时间后自动恢复并继续执行后续节点</div>
          <div v-if="!enterpriseFeatures" class="wf-form-hint">
            高级时间模式（具体时间、周期性时间、Cron 表达式）为企业版专属功能，当前版本不可选
          </div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'DELAY'" class="wf-form-group">
          <label class="wf-form-label">延迟秒数</label>
          <input
            v-model.number="formData.timeControlConfig.delaySeconds"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            min="1"
            placeholder="10"
            @change="handleUpdate"
          />
          <div class="wf-form-hint">流程暂停指定的秒数后继续执行</div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'COUNTDOWN'" class="wf-form-group">
          <label class="wf-form-label">倒计时</label>
          <div class="wf-time-row">
            <input
              v-model.number="formData.timeControlConfig.countdownMinutes"
              class="wf-form-input"
              type="number"
              :disabled="readonly"
              min="0"
              placeholder="0"
              @change="handleUpdate"
            />
            <span class="wf-time-unit">分</span>
            <input
              v-model.number="formData.timeControlConfig.countdownSeconds"
              class="wf-form-input"
              type="number"
              :disabled="readonly"
              min="0"
              max="59"
              placeholder="30"
              @change="handleUpdate"
            />
            <span class="wf-time-unit">秒</span>
          </div>
          <div class="wf-form-hint">流程暂停指定的分秒后继续执行</div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'SPECIFIC'" class="wf-form-group">
          <label class="wf-form-label">具体时间</label>
          <input
            v-model="formData.timeControlConfig.specificTime"
            class="wf-form-input"
            type="datetime-local"
            step="1"
            :disabled="readonly"
            @change="handleUpdate"
          />
          <div class="wf-form-hint">流程暂停至指定的日期时间后继续执行，设定时间已过时直接继续</div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'CRON'" class="wf-form-group">
          <label class="wf-form-label">Cron 表达式</label>
          <input
            v-model="formData.timeControlConfig.cronExpression"
            class="wf-form-input"
            type="text"
            :disabled="readonly"
            placeholder="0/15 * * * *"
            @change="handleUpdate"
          />
          <div class="wf-form-cron-presets">
            <button
              v-for="p in cronPresets"
              :key="p.value"
              type="button"
              class="wf-cron-preset-btn"
              :disabled="readonly"
              @click="formData.timeControlConfig.cronExpression = p.value; handleUpdate()"
            >{{ p.label }}</button>
          </div>
          <div class="wf-form-hint">5段"分 时 日 月 周"或6段"秒 分 时 日 月 周"，流程暂停至下一次触发时间后继续</div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'PERIODIC'" class="wf-form-group">
          <label class="wf-form-label">周期类型</label>
          <select
            v-model="formData.timeControlConfig.periodType"
            class="wf-form-select"
            :disabled="readonly"
            @change="switchPeriodType"
          >
            <option v-for="p in periodTypes" :key="p.value" :value="p.value">{{ p.label }}</option>
          </select>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'PERIODIC' && formData.timeControlConfig.periodType === 'WEEKLY'" class="wf-form-group">
          <label class="wf-form-label">每周执行日</label>
          <div class="wf-weekday-group">
            <label
              v-for="w in weekdayOptions"
              :key="w.value"
              class="wf-weekday-item"
              :class="{ 'is-active': (formData.timeControlConfig.periodWeekdays || []).includes(w.value) }"
            >
              <input
                type="checkbox"
                :checked="(formData.timeControlConfig.periodWeekdays || []).includes(w.value)"
                :disabled="readonly"
                @change="toggleWeekday(w.value)"
              />
              {{ w.label }}
            </label>
          </div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'PERIODIC' && formData.timeControlConfig.periodType === 'MONTHLY'" class="wf-form-group">
          <label class="wf-form-label">每月执行日</label>
          <input
            v-model.number="formData.timeControlConfig.periodDayOfMonth"
            class="wf-form-input"
            type="number"
            :disabled="readonly"
            min="1"
            max="31"
            placeholder="1"
            @change="handleUpdate"
          />
          <div class="wf-form-hint">当月不含该日期时顺延至下一个月的同一天</div>
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'PERIODIC'" class="wf-form-group">
          <label class="wf-form-label">执行时间点</label>
          <input
            v-model="formData.timeControlConfig.periodTime"
            class="wf-form-input"
            type="time"
            :disabled="readonly"
            @change="handleUpdate"
          />
        </div>
        <div v-if="formData.timeControlConfig.timeType === 'PERIODIC'" class="wf-form-group">
          <label class="wf-weekday-item is-standalone">
            <input
              type="checkbox"
              v-model="formData.timeControlConfig.workdayOnly"
              :disabled="readonly"
              @change="handleUpdate"
            />
            仅在工作日（周一至周五）执行
          </label>
          <div class="wf-form-hint">开启后周末的时间点将被跳过，顺延至下一个工作日</div>
        </div>
      </template>

      <!-- ========== 输入输出映射 (所有非START/END节点) ========== -->
      <template v-if="!['START', 'END'].includes(nodeType)">
        <div class="wf-section-title">输入映射</div>
        <div class="wf-form-hint">把工作流变量注入为节点入参：左侧为入参名，右侧用 ${var} 引用流程变量</div>
        <div v-for="(entry, index) in inputMappingEntries" :key="'in-' + index" class="wf-assign-row">
          <input
            :value="entry.key"
            class="wf-form-input wf-assign-key"
            :disabled="readonly"
            placeholder="参数名"
            @change="updateInputMapping(index, 'key', ($event.target as HTMLInputElement).value)"
          />
          <span class="wf-assign-eq">=</span>
          <input
            :value="entry.value"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="${变量名}"
            @change="updateInputMapping(index, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeInputMapping(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addInputMapping">+ 添加输入映射</button>

        <div class="wf-section-title">输出映射</div>
        <div class="wf-form-hint">把节点输出写入工作流变量供后续节点使用：左侧为输出字段名，右侧为要写入的变量名</div>
        <div v-for="(entry, index) in outputMappingEntries" :key="'out-' + index" class="wf-assign-row">
          <input
            :value="entry.key"
            class="wf-form-input wf-assign-key"
            :disabled="readonly"
            placeholder="输出字段"
            @change="updateOutputMapping(index, 'key', ($event.target as HTMLInputElement).value)"
          />
          <span class="wf-assign-eq">=</span>
          <input
            :value="entry.value"
            class="wf-form-input wf-assign-value"
            :disabled="readonly"
            placeholder="目标变量名"
            @change="updateOutputMapping(index, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button v-if="!readonly" class="wf-assign-remove" @click="removeOutputMapping(index)">x</button>
        </div>
        <button v-if="!readonly" class="wf-btn-add" @click="addOutputMapping">+ 添加输出映射</button>
      </template>

      <!-- ========== 通用审批配置 (方案2: 任意非START/END/APPROVAL节点可附加审批) ========== -->
      <template v-if="!['START', 'END', 'APPROVAL'].includes(nodeType)">
        <div class="wf-section-title">审批配置</div>
        <div class="wf-assign-row wf-approval-toggle">
          <label class="wf-approval-checkbox">
            <input
              type="checkbox"
              :checked="formData.approvalEnabled"
              :disabled="readonly"
              @change="toggleApproval(($event.target as HTMLInputElement).checked)"
            />
            <span>节点执行前需人工审批</span>
          </label>
        </div>
        <template v-if="formData.approvalEnabled">
          <div class="wf-form-group">
            <label class="wf-form-label">审批原因</label>
            <textarea
              v-model="formData.approvalConfig.reason"
              class="wf-form-textarea"
              :disabled="readonly"
              placeholder="请输入需要人工审批的原因说明"
              rows="2"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">审批选项</label>
            <div class="wf-form-hint">审批人从选项中选择一个，留空则仅为确认/拒绝</div>
          </div>
          <div v-for="(opt, index) in optionEntries" :key="'opt2-' + index" class="wf-assign-row">
            <input
              :value="opt"
              class="wf-form-input wf-assign-value"
              :disabled="readonly"
              placeholder="选项名称"
              @change="updateOption(index, ($event.target as HTMLInputElement).value)"
            />
            <button v-if="!readonly" class="wf-assign-remove" @click="removeOption(index)">x</button>
          </div>
          <button v-if="!readonly" class="wf-btn-add" @click="addOption">+ 添加选项</button>
          <div class="wf-form-group">
            <label class="wf-form-label">收集字段</label>
            <div class="wf-form-hint">审批人需填写的字段名，留空则不收集额外输入</div>
          </div>
          <div v-for="(field, index) in inputFieldEntries" :key="'field2-' + index" class="wf-assign-row">
            <input
              :value="field"
              class="wf-form-input wf-assign-value"
              :disabled="readonly"
              placeholder="字段名"
              @change="updateInputField(index, ($event.target as HTMLInputElement).value)"
            />
            <button v-if="!readonly" class="wf-assign-remove" @click="removeInputField(index)">x</button>
          </div>
          <button v-if="!readonly" class="wf-btn-add" @click="addInputField">+ 添加字段</button>
          <div class="wf-form-group">
            <label class="wf-form-label">超时时间（秒）</label>
            <input
              v-model.number="formData.approvalConfig.timeoutSeconds"
              class="wf-form-input"
              type="number"
              :disabled="readonly"
              min="1"
              placeholder="300"
              @change="handleUpdate"
            />
          </div>
          <div class="wf-form-group">
            <label class="wf-form-label">拒绝行为</label>
            <select
              v-model="formData.approvalConfig.rejectBehavior"
              class="wf-form-select"
              :disabled="readonly"
              @change="handleUpdate"
            >
              <option v-for="r in rejectBehaviors" :key="r.value" :value="r.value">{{ r.label }}</option>
            </select>
          </div>
        </template>
      </template>

      <!-- ========== 单节点试运行 ========== -->
      <template v-if="!readonly && !['START', 'END'].includes(nodeType) && definition">
        <div class="wf-section-title">调试</div>
        <button class="wf-debug-run" :disabled="debugLoading" @click="openDebug">▶ 试运行</button>
      </template>
      </template>

      <!-- ========== 执行详情 ========== -->
      <template v-else>
        <div v-if="!execData" class="wf-form-hint wf-exec-empty">
          暂无执行数据：请先执行工作流，再点击节点查看本次执行的输入输出
        </div>
        <template v-else>
          <div class="wf-exec-status" :class="execStatusClass">
            {{ execStatusLabel }}
            <span v-if="execData.durationMs != null"> · 耗时 {{ execData.durationMs }}ms</span>
          </div>

          <div class="wf-debug-block">
            <div class="wf-debug-title">节点输入</div>
            <pre>{{ execInputJson }}</pre>
          </div>

          <div class="wf-debug-block">
            <div class="wf-debug-title">本次执行输出</div>
            <pre>{{ execOutputJson }}</pre>
          </div>

          <div v-if="execData.errorMessage" class="wf-debug-block wf-debug-block--error">
            <div class="wf-debug-title">执行信息</div>
            <pre>{{ execData.errorMessage }}</pre>
          </div>

          <button
            v-if="!['START', 'END'].includes(nodeType)"
            class="wf-debug-run"
            :disabled="reExecLoading"
            @click="runReExecute"
          >
            {{ reExecLoading ? '重新执行中...' : '使用本次输入重新执行' }}
          </button>

          <div v-if="reExecError" class="wf-debug-block wf-debug-block--error">
            <div class="wf-debug-title">请求错误</div>
            <pre>{{ reExecError }}</pre>
          </div>

          <template v-if="reExecResult">
            <div class="wf-exec-compare">
              <span class="wf-exec-compare__label">结果对比</span>
              <span class="wf-exec-compare__badge" :class="reExecSame ? 'is-same' : 'is-diff'">
                {{ reExecSame ? '输出一致' : '输出不同' }}
              </span>
            </div>
            <div class="wf-exec-status" :class="reExecSuccess ? 'is-success' : 'is-failed'">
              {{ reExecSuccess ? '重新执行成功' : '重新执行失败' }}
              <span v-if="reExecResult.durationMs != null"> · 耗时 {{ reExecResult.durationMs }}ms</span>
            </div>
            <div class="wf-debug-block">
              <div class="wf-debug-title">重新执行输入</div>
              <pre>{{ reExecInputJson }}</pre>
            </div>
            <div class="wf-debug-block">
              <div class="wf-debug-title">重新执行输出</div>
              <pre>{{ reExecOutputJson }}</pre>
            </div>
            <div
              v-if="reExecResult.errorMessage && !reExecSuccess"
              class="wf-debug-block wf-debug-block--error"
            >
              <div class="wf-debug-title">错误信息</div>
              <pre>{{ reExecResult.errorMessage }}</pre>
            </div>
          </template>
        </template>
      </template>
    </div>

    <!-- Mock变量调试抽屉 -->
    <teleport to="body">
      <div v-if="showDebugDrawer" class="wf-debug-mask" @click.self="showDebugDrawer = false">
        <div class="wf-debug-drawer">
          <div class="wf-debug-header">
            <h4>节点试运行：{{ nodeName || nodeId }}</h4>
            <button class="wf-debug-close" title="关闭" @click="showDebugDrawer = false">x</button>
          </div>
          <div class="wf-debug-body">
            <div class="wf-form-label">Mock 变量（留空的变量调试时降级为空值）</div>
            <div v-for="(entry, index) in mockEntries" :key="'mock-' + index" class="wf-assign-row">
              <input
                v-model="entry.key"
                class="wf-form-input wf-assign-key"
                placeholder="变量名"
              />
              <span class="wf-assign-eq">=</span>
              <input
                v-model="entry.value"
                class="wf-form-input wf-assign-value"
                placeholder="值（支持JSON）"
              />
              <button class="wf-assign-remove" @click="removeMockEntry(index)">x</button>
            </div>
            <button class="wf-btn-add" @click="addMockEntry">+ 添加变量</button>
            <button class="wf-debug-run" :disabled="debugLoading" @click="runDebug">
              {{ debugLoading ? '执行中...' : '执行调试' }}
            </button>

            <div v-if="debugError" class="wf-debug-block wf-debug-block--error">
              <div class="wf-debug-title">请求错误</div>
              <pre>{{ debugError }}</pre>
            </div>

            <template v-if="debugResult">
              <div class="wf-debug-status" :class="debugResult.success ? 'is-success' : 'is-failed'">
                {{ debugResult.success ? '调试成功' : '调试失败' }}
                <span v-if="debugSummary?.durationMs != null"> · 耗时 {{ debugSummary.durationMs }}ms</span>
              </div>
              <div class="wf-debug-block">
                <div class="wf-debug-title">解析后输入</div>
                <pre>{{ debugInputJson }}</pre>
              </div>
              <div class="wf-debug-block">
                <div class="wf-debug-title">节点输出</div>
                <pre>{{ debugOutputJson }}</pre>
              </div>
              <div
                v-if="debugResult.errorMessage"
                class="wf-debug-block"
                :class="debugResult.success ? '' : 'wf-debug-block--error'"
              >
                <div class="wf-debug-title">{{ debugResult.success ? '告警信息' : '错误信息' }}</div>
                <pre>{{ debugResult.errorMessage }}</pre>
              </div>
            </template>
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.wf-panel {
  background: #fff;
  border-left: 1px solid #e2e8f0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.wf-panel__header {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.wf-panel__header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.wf-panel__delete-btn {
  border: 1px solid #fecaca;
  border-radius: 6px;
  background: #fef2f2;
  color: #ef4444;
  cursor: pointer;
  font-size: 14px;
  padding: 4px 8px;
  line-height: 1;
}
.wf-panel__delete-btn:hover {
  background: #fee2e2;
}
.wf-panel__body {
  padding: 16px;
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.wf-panel__feature-warn {
  margin-bottom: 12px;
  padding: 8px 12px;
  border: 1px solid #fde68a;
  border-radius: 6px;
  background: #fffbeb;
  color: #b45309;
  font-size: 13px;
  line-height: 1.5;
}
.wf-panel__tabs {
  display: flex;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}
.wf-panel__tab {
  flex: 1;
  padding: 10px 0;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
}
.wf-panel__tab:hover {
  color: #1e293b;
}
.wf-panel__tab.is-active {
  color: #2563eb;
  font-weight: 600;
  border-bottom-color: #2563eb;
  background: #fff;
}
.wf-exec-empty {
  margin-top: 8px;
}
.wf-exec-status {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}
.wf-exec-status.is-success {
  color: #16a34a;
}
.wf-exec-status.is-failed {
  color: #ef4444;
}
.wf-exec-compare {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  margin-bottom: 4px;
}
.wf-exec-compare__label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}
.wf-exec-compare__badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}
.wf-exec-compare__badge.is-same {
  background: #dcfce7;
  color: #16a34a;
}
.wf-exec-compare__badge.is-diff {
  background: #fee2e2;
  color: #ef4444;
}
.wf-form-group {
  margin-bottom: 16px;
}
.wf-form-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  margin-bottom: 4px;
}
.wf-form-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 8px;
}
.wf-form-input,
.wf-form-select,
.wf-form-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  color: #1e293b;
  background: #fff;
  box-sizing: border-box;
}
.wf-form-input:disabled,
.wf-form-select:disabled,
.wf-form-textarea:disabled {
  background: #f8fafc;
  color: #94a3b8;
}
.wf-form-textarea {
  resize: vertical;
  font-family: monospace;
}
.wf-section-title {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  margin-top: 20px;
  margin-bottom: 4px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}
.wf-assign-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.wf-assign-key {
  width: 35% !important;
  flex-shrink: 0;
}
.wf-assign-eq {
  color: #94a3b8;
  font-weight: 600;
  flex-shrink: 0;
}
.wf-assign-value {
  flex: 1;
}
.wf-assign-remove {
  width: 24px;
  height: 24px;
  border: 1px solid #fecaca;
  border-radius: 4px;
  background: #fef2f2;
  color: #ef4444;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.wf-assign-remove:hover {
  background: #fee2e2;
}
.wf-btn-add {
  width: 100%;
  padding: 6px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}
.wf-btn-add:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
}
.wf-approval-toggle {
  margin-bottom: 12px;
}
.wf-approval-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
}
.wf-approval-checkbox input {
  cursor: pointer;
}
.wf-debug-run {
  width: 100%;
  margin-top: 12px;
  padding: 8px;
  border: none;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.wf-debug-run:hover {
  background: #1d4ed8;
}
.wf-debug-run:disabled {
  background: #93c5fd;
  cursor: not-allowed;
}
.wf-debug-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.wf-debug-drawer {
  width: 440px;
  max-width: 90vw;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: -4px 0 16px rgba(15, 23, 42, 0.15);
}
.wf-debug-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
}
.wf-debug-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.wf-debug-close {
  width: 24px;
  height: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}
.wf-debug-close:hover {
  background: #f1f5f9;
}
.wf-debug-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
.wf-debug-status {
  margin: 16px 0 4px;
  font-size: 13px;
  font-weight: 600;
}
.wf-debug-status.is-success {
  color: #16a34a;
}
.wf-debug-status.is-failed {
  color: #ef4444;
}
.wf-debug-block {
  margin-top: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
}
.wf-debug-block--error {
  border-color: #fecaca;
}
.wf-debug-title {
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.wf-debug-block pre {
  margin: 0;
  padding: 10px;
  max-height: 220px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-all;
}
.wf-time-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.wf-time-row .wf-form-input {
  flex: 1;
  min-width: 0;
}
.wf-time-unit {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}
.wf-weekday-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.wf-weekday-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  user-select: none;
}
.wf-weekday-item.is-active {
  border-color: #8b5cf6;
  background: #f5f3ff;
  color: #6d28d9;
}
.wf-weekday-item.is-standalone {
  border: none;
  padding: 0;
  font-size: 13px;
  color: #334155;
}
.wf-weekday-item input {
  accent-color: #8b5cf6;
}
.wf-form-cron-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.wf-cron-preset-btn {
  padding: 3px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
}
.wf-cron-preset-btn:hover:not(:disabled) {
  border-color: #8b5cf6;
  color: #6d28d9;
  background: #f5f3ff;
}
.wf-cron-preset-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
