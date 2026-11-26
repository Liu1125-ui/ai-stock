<script setup lang="ts">
import {time} from './statisticsPresentation'
defineProps<{title:string;description:string;busy:boolean;error:string;stale:boolean;queriedAt?:string}>()
</script>
<template><section class="stats-section" :aria-label="title" :aria-busy="busy"><header><h2>{{title}}</h2><span v-if="busy" role="status" class="small">读取中…</span></header><p class="stats-description">{{description}}</p><p v-if="queriedAt" class="small stats-time">查询时间：{{time(queriedAt)}}（上海时间）</p><p v-if="stale" role="status" class="stats-stale">尚未刷新，以下保留上次读取结果和原查询时间。</p><p v-if="error" role="alert" class="stats-error">{{error}}</p><slot/></section></template>
