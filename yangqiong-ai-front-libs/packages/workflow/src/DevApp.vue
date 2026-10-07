<script setup lang="ts">
import { ref, reactive } from 'vue'
import { WorkflowEditor } from './workflow'
import type { WorkflowDefinition, WorkflowExecuteResult } from './workflow'

const definition = reactive<WorkflowDefinition>({
  name: 'demo-workflow',
  description: '示例工作流',
  nodes: [
    {
      id: 'start_1',
      name: '开始',
      type: 'START',
      config: {},
      position: { x: 400, y: 50 },
    },
    {
      id: 'transform_1',
      name: '去掉首字符',
      type: 'TRANSFORM',
      config: {
        transformType: 'SUBSTRING',
        transformConfig: { start: 1 },
      },
      inputMappings: { input: '${input}' },
      outputMappings: { outputText: 'trimmed' },
      position: { x: 400, y: 180 },
    },
    {
      id: 'condition_1',
      name: '长度校验',
      type: 'CONDITION',
      config: {
        conditionExpression: '${input.length} > 5',
        branches: { pass: 'transform_2', fail: 'http_1' },
      },
      position: { x: 400, y: 310 },
    },
    {
      id: 'transform_2',
      name: '逆序输出',
      type: 'TRANSFORM',
      config: {
        transformType: 'REVERSE',
      },
      inputMappings: { input: '${trimmed}' },
      outputMappings: { outputText: 'reversed' },
      position: { x: 650, y: 440 },
    },
    {
      id: 'http_1',
      name: '异常上报',
      type: 'HTTP',
      config: {
        method: 'GET',
        url: 'https://example.com/report',
        headers: { 'X-Trace-Id': '${input}' },
        timeout: 10000,
      },
      outputMappings: { reportStatus: 'status' },
      position: { x: 150, y: 440 },
    },
    {
      id: 'end_1',
      name: '结束',
      type: 'END',
      config: {},
      inputMappings: { original: '${input}', trimmed: '${trimmed}', reversed: '${reversed}' },
      position: { x: 400, y: 570 },
    },
  ],
  edges: [
    { id: 'e1', sourceId: 'start_1', targetId: 'transform_1', type: 'NORMAL' },
    { id: 'e2', sourceId: 'transform_1', targetId: 'condition_1', type: 'NORMAL' },
    { id: 'e3', sourceId: 'condition_1', targetId: 'transform_2', type: 'CONDITIONAL', conditionExpression: 'pass', conditionLabel: '通过' },
    { id: 'e4', sourceId: 'condition_1', targetId: 'http_1', type: 'CONDITIONAL', conditionExpression: 'fail', conditionLabel: '拒绝' },
    { id: 'e5', sourceId: 'transform_2', targetId: 'end_1', type: 'NORMAL' },
    { id: 'e6', sourceId: 'http_1', targetId: 'end_1', type: 'NORMAL' },
  ],
  errorStrategy: 'STOP',
})

const executing = ref(false)
const executeResult = ref<WorkflowExecuteResult | null>(null)
const showResult = ref(false)

function handleSave(def: WorkflowDefinition) {
  console.log('保存工作流:', JSON.stringify(def, null, 2))
  alert('工作流定义已保存到控制台（Console）')
}

function handleExecute(def: WorkflowDefinition) {
  executing.value = true
  showResult.value = false

  fetch('/api/workflow/execute', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      definition: def,
      initialVariables: { input: 'HelloWorld' },
    }),
  })
    .then((res) => res.json())
    .then((data) => {
      executeResult.value = data.data || data
      showResult.value = true
    })
    .catch((err) => {
      alert('执行失败: ' + err.message)
    })
    .finally(() => {
      executing.value = false
    })
}
</script>

<template>
  <div class="dev-app">
    <WorkflowEditor
      :model-value="definition"
      @save="handleSave"
      @execute="handleExecute"
    />
    <div v-if="executing" class="dev-executing">
      执行中...
    </div>
    <div v-if="showResult && executeResult" class="dev-result">
      <h4>执行结果</h4>
      <div class="dev-result__content">
        <p><strong>状态:</strong> {{ executeResult.status }}</p>
        <p><strong>输出:</strong> {{ executeResult.outputText }}</p>
        <p><strong>耗时:</strong> {{ executeResult.totalDurationMs }}ms</p>
        <details>
          <summary>查看变量</summary>
          <pre>{{ JSON.stringify(executeResult.variables, null, 2) }}</pre>
        </details>
        <details>
          <summary>查看节点摘要</summary>
          <pre>{{ JSON.stringify(executeResult.nodeSummaries, null, 2) }}</pre>
        </details>
      </div>
    </div>
  </div>
</template>

<style>
html, body {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
#app {
  width: 100%;
  height: 100%;
}
.dev-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.dev-executing {
  padding: 12px;
  background: #fef3c7;
  text-align: center;
  font-size: 14px;
}
.dev-result {
  padding: 16px;
  background: #f0fdf4;
  border-top: 1px solid #bbf7d0;
  max-height: 300px;
  overflow-y: auto;
}
.dev-result h4 {
  margin: 0 0 8px 0;
  color: #166534;
}
.dev-result__content {
  font-size: 13px;
  color: #475569;
}
.dev-result pre {
  font-size: 12px;
  background: #f8fafc;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}
</style>
