<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from '../../../ui/feedback'
import StatusBadge from '../../../components/StatusBadge.vue'
import { integrationApi, type IntegrationAttempt, type IntegrationBatch, type IntegrationInstance, type IntegrationTask } from '../../../api/domain'

const props=defineProps<{modelValue:boolean;task:IntegrationTask|null;batchId?:number;instanceId?:number;requestKey?:number}>()
const emit=defineEmits<{(e:'update:modelValue',value:boolean):void}>()
const visible=computed({get:()=>props.modelValue,set:value=>emit('update:modelValue',value)})
const loading=ref(false),batches=ref<IntegrationBatch[]>([]),instances=ref<IntegrationInstance[]>([])
const attempts=ref<Record<number,IntegrationAttempt[]>>({}),logs=ref<Record<string,string>>({})
const selectedKey=ref(''),selectedTitle=ref(''),maximized=ref(false)
let pollTimer:ReturnType<typeof setInterval>|null=null

const terminalStatuses=new Set(['SUCCESS','SUCCEEDED','FINISHED','COMPLETED','FAILED','FAIL','ERROR','STOPPED','CANCELED','CANCELLED'])
function terminal(status?:string){return terminalStatuses.has(String(status||'').toUpperCase())}
function fmt(value?:string){return value?value.replace('T',' ').replace(/\.\d+$/,'').slice(0,19):'—'}
function messageOf(error:unknown){return error instanceof Error?error.message:'操作失败'}
function isOnline(){return (props.task?.lifecycleStatus||'ONLINE').toUpperCase()==='ONLINE'}

function selectedMeta(){
  if(selectedKey.value.startsWith('attempt-')){
    const id=Number(selectedKey.value.slice(8))
    for(const batch of batches.value){const row=(attempts.value[batch.id]||[]).find(item=>item.id===id);if(row)return{executionId:row.executionId,status:row.status,fallback:row.errorMessage||batch.errorMessage||'暂无日志'}}
  }
  if(selectedKey.value.startsWith('instance-')){
    const id=Number(selectedKey.value.slice(9)),row=instances.value.find(item=>item.id===id)
    if(row)return{executionId:row.executionId,status:row.status,fallback:row.message||'暂无日志'}
  }
  return undefined
}
async function loadSelectedLog(force=false){
  if(!selectedKey.value)return
  const meta=selectedMeta();if(!meta)return
  if(!meta.executionId){logs.value[selectedKey.value]=meta.fallback;return}
  if(!force&&terminal(meta.status)&&logs.value[selectedKey.value])return
  try{logs.value[selectedKey.value]=await integrationApi.log(meta.executionId)}catch(error){logs.value[selectedKey.value]=messageOf(error)}
}
function selectAttempt(batch:IntegrationBatch,row:IntegrationAttempt){selectedKey.value=`attempt-${row.id}`;selectedTitle.value=`${batch.batchCode} / Attempt #${row.attemptNo}`;void loadSelectedLog()}
function selectInstance(row:IntegrationInstance){selectedKey.value=`instance-${row.id}`;selectedTitle.value=row.executionId||`历史执行 #${row.id}`;void loadSelectedLog()}
function selectionExists(){return Boolean(selectedMeta())}

async function refresh(showLoading=false,forceLog=false){
  if(!props.task)return
  if(showLoading)loading.value=true
  try{
    const[batchRows,instanceRows,allAttempts]=await Promise.all([integrationApi.batches(props.task.id),integrationApi.instances(props.task.id),integrationApi.attemptsForTask(props.task.id)])
    batches.value=batchRows;instances.value=instanceRows
    const grouped:Record<number,IntegrationAttempt[]>={};for(const row of allAttempts)(grouped[row.batchId]??=[]).push(row);attempts.value=grouped
    if(!selectionExists()){
      const focusBatch=props.batchId?batchRows.find(row=>row.id===props.batchId):undefined
      const focusAttempt=focusBatch?(grouped[focusBatch.id]||[]).find(row=>row.executionId)||(grouped[focusBatch.id]||[])[0]:undefined
      if(focusBatch&&focusAttempt){selectedKey.value=`attempt-${focusAttempt.id}`;selectedTitle.value=`${focusBatch.batchCode} / Attempt #${focusAttempt.attemptNo}`}
      else if(props.instanceId){const row=instanceRows.find(item=>item.id===props.instanceId);if(row){selectedKey.value=`instance-${row.id}`;selectedTitle.value=row.executionId||`历史执行 #${row.id}`}}
      if(!selectedKey.value){const batch=batchRows.find(item=>(grouped[item.id]||[]).length);const row=batch?(grouped[batch.id]||[])[0]:undefined;if(batch&&row){selectedKey.value=`attempt-${row.id}`;selectedTitle.value=`${batch.batchCode} / Attempt #${row.attemptNo}`}else if(instanceRows[0]){selectedKey.value=`instance-${instanceRows[0].id}`;selectedTitle.value=instanceRows[0].executionId||`历史执行 #${instanceRows[0].id}`}}
    }
    await loadSelectedLog(forceLog)
  }catch(error){if(showLoading)ElMessage.error(messageOf(error))}finally{if(showLoading)loading.value=false}
}
function startPolling(){stopPolling();pollTimer=setInterval(()=>{if(visible.value)void refresh(false,false)},2000)}
function stopPolling(){if(pollTimer)clearInterval(pollTimer);pollTimer=null}
async function open(){selectedKey.value='';selectedTitle.value='';logs.value={};await refresh(true,true);startPolling()}
function closed(){stopPolling();maximized.value=false;selectedKey.value='';selectedTitle.value=''}
async function retryBatch(batch:IntegrationBatch){if(!isOnline())return ElMessage.warning('任务已下线，请先上线后再重试');try{await ElMessageBox.confirm('重试会复用该批次创建时固化的运行快照，不使用任务当前的新配置。确认重试？','重试离线批次',{type:'warning'});await integrationApi.retryBatch(batch.id);ElMessage.success('重试已提交');await refresh(true,true)}catch(error){if(error!=='cancel'&&error!=='close')ElMessage.error(messageOf(error))}}
async function reconcileBatch(batch:IntegrationBatch){try{await integrationApi.reconcileBatch(batch.id);ElMessage.success('已向 SeaTunnel 重新核对运行状态');await refresh(false,true)}catch(error){ElMessage.error(messageOf(error))}}
function keydown(event:KeyboardEvent){if(event.key==='Escape'&&maximized.value){event.preventDefault();maximized.value=false}}
watch(()=>[props.modelValue,props.task?.id,props.batchId,props.instanceId,props.requestKey] as const,([openState])=>{if(openState&&props.task)void open();else if(!openState)closed()})
onMounted(()=>window.addEventListener('keydown',keydown));onBeforeUnmount(()=>{stopPolling();window.removeEventListener('keydown',keydown)})
</script>

<template>
  <el-drawer v-model="visible" :title="`执行日志 · ${task?.name || ''}`" size="60%" :close-on-press-escape="!maximized" @closed="closed">
    <div class="history-shell" v-loading="loading">
      <div class="runtime-note">当前日志对应所选批次；任务执行中每 2 秒自动刷新，已完成历史日志会保留缓存。</div>
      <div class="history-records" v-if="batches.length">
        <section v-for="batch in batches" :key="batch.id" class="history-batch-card">
          <div class="history-batch-head">
            <div><strong>{{batch.batchCode}}</strong><StatusBadge :status="batch.status" /></div>
            <span>{{batch.triggerType}}</span><span>{{fmt(batch.startedAt||batch.createdAt)}} → {{fmt(batch.finishedAt)}}</span>
            <div class="history-batch-actions"><el-button link type="primary" @click="reconcileBatch(batch)">核对状态</el-button><el-button link type="primary" :disabled="!isOnline()" @click="retryBatch(batch)">重试</el-button></div>
          </div>
          <div class="history-attempts">
            <button v-for="row in attempts[batch.id]||[]" :key="row.id" type="button" :class="['history-attempt-row',{selected:selectedKey===`attempt-${row.id}`} ]" @click="selectAttempt(batch,row)">
              <strong>Attempt #{{row.attemptNo}}</strong><StatusBadge :status="row.status"/><span>{{row.executionId||'未生成执行 ID'}}</span><span>{{fmt(row.startedAt)}} → {{fmt(row.finishedAt)}}</span><span>查看日志</span>
            </button>
            <div v-if="!(attempts[batch.id]||[]).length" class="history-empty-row">该批次尚未生成 Attempt</div>
          </div>
        </section>
      </div>
      <div class="history-records" v-else-if="instances.length">
        <button v-for="row in instances" :key="row.id" type="button" :class="['history-attempt-row','legacy',{selected:selectedKey===`instance-${row.id}`} ]" @click="selectInstance(row)">
          <strong>#{{row.id}}</strong><StatusBadge :status="row.status"/><span>{{row.executionId||'未生成执行 ID'}}</span><span>{{fmt(row.startedAt)}} → {{fmt(row.finishedAt)}}</span><span>查看日志</span>
        </button>
      </div>
      <div :class="['history-log-panel',{ 'is-maximized':maximized }]">
        <div class="history-log-toolbar"><div><strong>执行日志</strong><span>{{selectedTitle||'当前执行'}}</span><em>运行中自动刷新</em></div><div class="history-log-actions"><el-button size="small" @click="refresh(false,true)">刷新</el-button><el-button v-if="!maximized" size="small" type="primary" plain @click="maximized=true">放大</el-button><el-button v-else size="small" type="primary" @click="maximized=false">还原（Esc）</el-button></div></div>
        <div class="history-log-viewer"><pre>{{selectedKey?(logs[selectedKey]||'暂无日志'):'暂无日志'}}</pre></div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.history-shell{height:calc(100vh - 104px);display:flex;flex-direction:column;gap:10px;min-height:520px}.runtime-note{padding:8px 10px;border:1px solid #e4e9f0;border-radius:8px;background:#f8fafc;color:#667085;font-size:12px}.history-records{flex:0 0 auto;max-height:255px;overflow:auto;display:flex;flex-direction:column;gap:10px;padding-right:2px}.history-batch-card{border:1px solid #e4ebf5;border-radius:10px;background:#fff;overflow:hidden}.history-batch-head{min-height:48px;padding:8px 12px;display:grid;grid-template-columns:minmax(220px,1fr) 100px 285px auto;align-items:center;gap:12px;background:#f8fafc;border-bottom:1px solid #e4e9f0;font-size:12px;color:#667085}.history-batch-head>div:first-child{display:flex;align-items:center;gap:10px;min-width:0}.history-batch-head strong{color:#344054}.history-batch-head span{white-space:nowrap}.history-batch-actions{display:flex;justify-content:flex-end}.history-attempts{display:flex;flex-direction:column}.history-attempt-row{width:100%;border:0;border-bottom:1px solid #eef0f2;background:#fff;padding:9px 12px;display:grid;grid-template-columns:100px 100px minmax(220px,1fr) 260px 72px;align-items:center;gap:10px;text-align:left;font:inherit;font-size:12px;color:#667085;cursor:pointer}.history-attempt-row:last-child{border-bottom:0}.history-attempt-row:hover,.history-attempt-row.selected{background:#f5f8ff}.history-attempt-row.selected{box-shadow:inset 3px 0 0 var(--el-color-primary)}.history-attempt-row strong{color:#344054}.history-attempt-row.legacy{border:1px solid #eef0f2;border-radius:8px}.history-empty-row{padding:10px 12px;color:#98a2b3;font-size:12px}.history-log-panel{flex:1;min-height:0;display:flex;flex-direction:column;border:1px solid #e4ebf5;border-radius:12px;background:#fff;box-shadow:0 6px 18px rgba(32,76,145,.025);overflow:hidden}.history-log-toolbar{min-height:52px;padding:8px 12px;border-bottom:1px solid #e4e9f0;display:flex;align-items:center;justify-content:space-between;gap:16px}.history-log-toolbar>div:first-child{display:flex;align-items:center;gap:10px;min-width:0}.history-log-toolbar strong{color:#344054}.history-log-toolbar span{font-size:12px;color:#667085;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-log-toolbar em{font-style:normal;font-size:12px;color:#17a673;white-space:nowrap}.history-log-actions{display:flex;gap:6px;flex:0 0 auto}.history-log-viewer{flex:1;min-height:260px;overflow:auto;background:#111827;padding:14px 16px}.history-log-viewer pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}.history-log-panel.is-maximized{position:fixed;z-index:4000;inset:18px;background:#fff;border:1px solid #cfd4dc;box-shadow:0 16px 48px rgba(0,0,0,.24)}.history-log-panel.is-maximized .history-log-viewer{min-height:0}@media(max-width:1200px){.history-batch-head{grid-template-columns:minmax(180px,1fr) 80px 220px auto}.history-attempt-row{grid-template-columns:90px 90px minmax(180px,1fr) 200px 60px}}
</style>
