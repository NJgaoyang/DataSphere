<script setup lang="ts">
import type { ProjectImpactView, ProjectRerunBatch } from '../../../api/domain'
import { formatDateTime, statusLabel } from '../../../utils/display'

const props = defineProps<{
  impactOpen:boolean
  historyOpen:boolean
  selectedName:string
  impactBusinessDate:string
  impactIncludeSource:boolean
  impactLoading:boolean
  impactView?:ProjectImpactView
  rerunBatch?:ProjectRerunBatch
  historyLoading:boolean
  history:ProjectRerunBatch[]
}>()
const emit = defineEmits<{
  'update:impactOpen':[value:boolean]
  'update:historyOpen':[value:boolean]
  'update:impactBusinessDate':[value:string]
  'update:impactIncludeSource':[value:boolean]
  preview:[]
  start:[]
  cancel:[batch:ProjectRerunBatch]
  retry:[batch:ProjectRerunBatch]
  'refresh-history':[]
  'impact-closed':[]
}>()

function runtimeClass(value?:string){const s=(value||'').toUpperCase();if(s==='WAITING_DEPENDENCY')return'waiting';if(/RUNNING|STARTING|QUEUED|SUBMITTED|PENDING|CANCELLING/.test(s))return'running';if(/SUCCESS|FINISHED|COMPLETED/.test(s))return'success';if(/FAIL|ERROR/.test(s))return'failed';if(/CANCEL|STOP|KILL/.test(s))return'stopped';return'idle'}
function runtimeLabel(value?:string){const s=(value||'').toUpperCase();if(s==='WAITING_DEPENDENCY')return'等待依赖';return statusLabel(s||'READY')}
function lifecycleLabel(value?:string){return value==='ONLINE'?'已上线':'未上线'}
function fmt(value?:string){return formatDateTime(value)}
function updateDate(value:unknown){emit('update:impactBusinessDate',String(value||''))}
function updateInclude(value:unknown){emit('update:impactIncludeSource',Boolean(value))}
</script>

<template>
  <el-dialog :model-value="impactOpen" title="影响范围与下游重跑" width="620px" @update:model-value="emit('update:impactOpen',$event)" @closed="emit('impact-closed')">
    <div v-if="selectedName" class="impact-head"><div><span>起点任务</span><b>{{selectedName}}</b></div><label>业务日期 <el-date-picker :model-value="impactBusinessDate" type="date" value-format="YYYY-MM-DD" @update:model-value="updateDate"/></label></div>
    <div class="impact-options"><el-switch :model-value="impactIncludeSource" @update:model-value="updateInclude" @change="emit('preview')"/><span>包含当前节点一起重跑</span><button class="btn" :disabled="impactLoading" @click="emit('preview')">刷新范围</button></div>
    <div v-if="impactLoading" class="impact-empty">正在计算影响范围…</div>
    <template v-else-if="rerunBatch">
      <div class="batch-summary"><b>重跑批次 #{{rerunBatch.id}}</b><span :class="['runtime-pill',runtimeClass(rerunBatch.status)]"><i></i>{{runtimeLabel(rerunBatch.status)}}</span><span>{{rerunBatch.successTasks}} 成功</span><span>{{rerunBatch.failedTasks}} 失败</span><span>{{rerunBatch.waitingTasks}} 等待依赖</span></div>
      <div v-if="rerunBatch.sourceIntegrationTaskId" class="source-runtime"><b>SeaTunnel 源任务</b><span :class="['runtime-pill',runtimeClass(rerunBatch.sourceStatus)]"><i></i>{{runtimeLabel(rerunBatch.sourceStatus)}}</span><small>{{rerunBatch.sourceErrorMessage||''}}</small></div>
      <div class="impact-list"><div v-for="t in rerunBatch.tasks" :key="t.id" class="impact-row"><span class="seq">{{t.sequenceNo}}</span><b>{{t.name}}</b><span :class="['runtime-pill',runtimeClass(t.status)]"><i></i>{{runtimeLabel(t.status)}}</span><small>{{t.errorMessage||''}}</small></div></div>
    </template>
    <template v-else-if="impactView">
      <div class="impact-summary">预计影响 <b>{{impactView.tasks.length}}</b> 个开发任务；同层级任务并行执行，上游失败时对应下游保持“等待依赖”。</div>
      <div class="impact-list"><div v-for="(t,i) in impactView.tasks" :key="t.fileId" class="impact-row"><span class="seq">{{i+1}}</span><b>{{t.name}}</b><span>{{t.fileType}} · 层级 {{t.level}}</span><span>{{lifecycleLabel(t.lifecycleStatus)}}</span></div></div>
    </template>
    <div v-else class="impact-empty">请选择项目工作流中的任务节点。</div>
    <template #footer><button class="btn" @click="emit('update:impactOpen',false)">关闭</button><button v-if="impactView&&!rerunBatch" class="btn primary" :disabled="impactLoading||(!impactView.tasks.length&&!(impactView.sourceIntegrationTaskId&&impactIncludeSource))" @click="emit('start')">开始重跑</button></template>
  </el-dialog>

  <el-dialog :model-value="historyOpen" title="项目重跑历史" width="860px" @update:model-value="emit('update:historyOpen',$event)">
    <div v-loading="historyLoading" class="history-list">
      <div v-for="b in history" :key="b.id" class="history-row">
        <div><b>#{{b.id}}</b><small>{{b.businessDate}} · {{fmt(b.createdAt)}}<span v-if="b.parentBatchId"> · 重试自 #{{b.parentBatchId}}</span></small></div>
        <span :class="['runtime-pill',runtimeClass(b.status)]"><i></i>{{runtimeLabel(b.status)}}</span>
        <span>{{b.successTasks}}/{{b.totalTasks}} 成功</span><span>{{b.failedTasks}} 失败</span><span>{{b.waitingTasks}} 等待</span>
        <div class="history-actions"><button v-if="['PENDING','RUNNING','CANCELLING'].includes(b.status)" class="btn danger" @click="emit('cancel',b)">取消</button><button v-else-if="b.failedTasks||b.waitingTasks||b.status==='CANCELED'" class="btn" @click="emit('retry',b)">失败分支重试</button></div>
      </div>
      <div v-if="!history.length&&!historyLoading" class="impact-empty">暂无重跑历史</div>
    </div>
    <template #footer><button class="btn" @click="emit('update:historyOpen',false)">关闭</button><button class="btn" @click="emit('refresh-history')">刷新</button></template>
  </el-dialog>
</template>

<style scoped>
.btn{height:34px;padding:0 13px;border:1px solid #e1e7ef;border-radius:8px;background:#fff;color:#40536c;cursor:pointer}.btn.primary{background:#2f6fed;border-color:#2f6fed;color:#fff}.btn.danger{color:#d84b4b;border-color:#efcece}.runtime-pill{display:inline-flex;align-items:center;gap:5px;white-space:nowrap}.runtime-pill i{width:7px;height:7px;border-radius:50%;background:#98a2b3}.runtime-pill.success{color:#16815a}.runtime-pill.success i{background:#1fa971}.runtime-pill.failed{color:#c74646}.runtime-pill.failed i{background:#d84b4b}.runtime-pill.running{color:#2f6fed}.runtime-pill.running i{background:#2f6fed}.runtime-pill.waiting{color:#a66a0a}.runtime-pill.waiting i{width:9px;height:9px;background:transparent;border:1.5px solid #dfb267;border-top-color:#d18a16;animation:spin .8s linear infinite}.impact-head{display:flex;align-items:end;justify-content:space-between;padding-bottom:12px;border-bottom:1px solid #edf1f6}.impact-head span,.impact-head b{display:block}.impact-head span{color:#98a2b3;font-size:11px}.impact-head b{margin-top:4px;font-size:15px}.impact-head label{color:#667085;font-size:11px}.impact-options{height:50px;display:flex;align-items:center;gap:8px}.impact-options .btn{margin-left:auto}.impact-summary,.batch-summary{padding:10px 12px;border:1px solid #e4eaf2;border-radius:8px;background:#f8fafc;color:#667085;font-size:11px}.batch-summary{display:flex;align-items:center;gap:12px}.impact-list{max-height:300px;overflow:auto;margin-top:10px;border:1px solid #e8edf4;border-radius:8px}.impact-row{min-height:44px;display:grid;grid-template-columns:32px minmax(120px,1fr) 110px 110px;align-items:center;gap:8px;padding:6px 10px;border-bottom:1px solid #eef2f6;font-size:11px}.impact-row:last-child{border-bottom:0}.impact-row .seq{width:24px;height:24px;display:grid;place-items:center;border-radius:50%;background:#edf4ff;color:#2f6fed}.impact-row small{grid-column:2/5;color:#c74646}.impact-empty{height:120px;display:grid;place-items:center;color:#98a2b3;font-size:11px}.source-runtime{display:flex;align-items:center;gap:10px;margin-top:10px;padding:9px 12px;border:1px solid #e8edf4;border-radius:8px;background:#fff}.source-runtime small{margin-left:auto;color:#c74646;font-size:10px}.history-list{min-height:120px;max-height:520px;overflow:auto}.history-row{display:grid;grid-template-columns:minmax(180px,1.5fr) 110px 100px 80px 80px 150px;align-items:center;gap:10px;min-height:58px;padding:8px 10px;border-bottom:1px solid #edf1f6;font-size:11px}.history-row>div:first-child b,.history-row>div:first-child small{display:block}.history-row>div:first-child small{margin-top:4px;color:#98a2b3}.history-actions{display:flex;justify-content:flex-end}.history-actions .btn{height:30px;padding:0 9px;font-size:10px}@keyframes spin{to{transform:rotate(360deg)}}
</style>
