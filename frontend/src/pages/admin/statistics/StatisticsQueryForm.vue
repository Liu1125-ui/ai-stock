<script setup lang="ts">
import {reactive,ref,watch} from 'vue'
import type {Window} from '../../../api/adminStatistics'
import {defaultWindow,validateWindow} from './statisticsPresentation'
const props=defineProps<{window:Window;busy:boolean}>(),emit=defineEmits<{apply:[window:Window]}>()
const draft=reactive({...props.window}),error=ref('')
watch(()=>props.window,w=>Object.assign(draft,w))
function choose(days:number){Object.assign(draft,defaultWindow(new Date(),days));error.value=''}
function submit(){if(props.busy)return;error.value=validateWindow(draft);if(!error.value)emit('apply',{...draft})}
</script>
<template><form class="stats-query" @submit.prevent="submit"><div class="stats-quick"><span>选择日期</span><button type="button" :disabled="busy" @click="choose(1)">今天</button><button type="button" :disabled="busy" @click="choose(7)">最近7日</button><button type="button" :disabled="busy" @click="choose(30)">最近30日</button></div><div class="stats-dates"><label>开始日期<input v-model="draft.from" name="from" type="date" required :disabled="busy"/></label><label>结束日期<input v-model="draft.to" name="to" type="date" required :disabled="busy"/></label><button type="submit" :disabled="busy">应用日期</button></div><p v-if="error" role="alert" class="stats-error">{{error}}</p><p class="small hint">上海自然日，含首尾两天，每次最多90日。选择后点击“应用日期”才会查询。</p></form></template>
