<script setup lang="ts">
import { onMounted } from 'vue'
import { useEngineeringStore } from '../stores/engineering'

const engineering = useEngineeringStore()
onMounted(() => { void engineering.load() })
</script>

<template>
  <main class="page">
    <el-card class="panel" shadow="never">
      <h1>股票时序分析与辅助决策系统</h1>
      <el-tag type="warning">工程连接检查</el-tag>
      <p>当前页面只检查工程连接状态。行情、自选股、五日预测和AI解释请从对应业务页面使用；连接正常不代表所有数据、模型和业务均已通过验收。</p>
      <el-button :loading="engineering.loading" @click="engineering.load">重新检查</el-button>
      <el-alert v-if="engineering.error" type="error" :title="engineering.error" :closable="false" show-icon />
      <el-descriptions v-if="engineering.status" :column="1" border>
        <el-descriptions-item label="组件">{{ engineering.status.component }}</el-descriptions-item>
        <el-descriptions-item label="阶段">{{ engineering.status.phase }}</el-descriptions-item>
        <el-descriptions-item label="关联 ID">{{ engineering.traceId }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </main>
</template>

<style scoped>
.page { min-height: 100vh; padding: 56px 24px; background: #f4f7fb; color: #17253d; }
.panel { max-width: 760px; margin: 0 auto; border-radius: 16px; }
h1 { margin: 0 0 18px; font-size: 28px; }
p { line-height: 1.8; color: #526179; }
.el-button { margin: 12px 0 18px; }
.el-alert { margin-bottom: 16px; }
</style>

