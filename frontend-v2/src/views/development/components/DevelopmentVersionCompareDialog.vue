<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef } from 'vue'
import type * as Monaco from 'monaco-editor/esm/vs/editor/editor.api'

export interface CompareScheduleRow { name:string; current:string; online:string; changed:boolean }
const props=defineProps<{ modelValue:boolean; leftLabel:string; leftVersion:number; onlineVersion:number; sqlCurrent:string; sqlOnline:string; scheduleRows:CompareScheduleRow[] }>()
const emit=defineEmits<{ 'update:modelValue':[boolean] }>()
const activeTab=ref<'code'|'schedule'>('code')
const editorHost=ref<HTMLElement>()
const diffEditor=shallowRef<Monaco.editor.IStandaloneDiffEditor>()
let originalModel:Monaco.editor.ITextModel|undefined,modifiedModel:Monaco.editor.ITextModel|undefined
const sqlChanged=computed(()=>props.sqlCurrent.trimEnd()!==props.sqlOnline.trimEnd())
const scheduleChanged=computed(()=>props.scheduleRows.some(x=>x.changed))

async function mountEditor(){
  activeTab.value='code'
  await nextTick()
  if(!editorHost.value)return
  disposeEditor()
  const monaco=await import('monaco-editor/esm/vs/editor/editor.api')
  originalModel=monaco.editor.createModel(props.sqlOnline,'sql')
  modifiedModel=monaco.editor.createModel(props.sqlCurrent,'sql')
  diffEditor.value=monaco.editor.createDiffEditor(editorHost.value,{automaticLayout:true,readOnly:true,originalEditable:false,renderSideBySide:true,minimap:{enabled:false},fontSize:12,lineHeight:20,scrollBeyondLastLine:false})
  diffEditor.value.setModel({original:originalModel,modified:modifiedModel})
}
function disposeEditor(){diffEditor.value?.dispose();diffEditor.value=undefined;originalModel?.dispose();modifiedModel?.dispose();originalModel=undefined;modifiedModel=undefined}
function close(){emit('update:modelValue',false)}
onBeforeUnmount(disposeEditor)
</script>

<template>
  <el-dialog :model-value="modelValue" :title="`${leftLabel} vs 生产 V${onlineVersion}`" width="1080px" top="6vh" @update:model-value="emit('update:modelValue',$event)" @opened="mountEditor" @closed="disposeEditor">
    <div class="compare-summary"><span>{{leftLabel}} · V{{leftVersion}}</span><span>生产任务版本 V{{onlineVersion}}</span><el-tag size="small" :type="sqlChanged?'warning':'success'">代码{{sqlChanged?'有差异':'一致'}}</el-tag><el-tag size="small" :type="scheduleChanged?'warning':'success'">调度{{scheduleChanged?'有差异':'一致'}}</el-tag></div>
    <el-tabs v-model="activeTab" class="compare-tabs">
      <el-tab-pane name="code" label="代码对比"><div class="compare-code-head"><span>线上 V{{onlineVersion}}</span><span>{{leftLabel}} · V{{leftVersion}}</span></div><div ref="editorHost" class="compare-editor"></div></el-tab-pane>
      <el-tab-pane name="schedule" label="调度对比"><el-table :data="scheduleRows" size="small" max-height="480"><el-table-column prop="name" label="配置项" width="150"/><el-table-column prop="current" :label="leftLabel" min-width="250"/><el-table-column prop="online" label="当前线上" min-width="250"/><el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" :type="row.changed?'warning':'success'">{{row.changed?'有差异':'一致'}}</el-tag></template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
    <template #footer><el-button @click="close">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.compare-summary{display:flex;align-items:center;gap:10px;padding:9px 12px;margin-bottom:10px;border:1px solid #dfe8f3;border-radius:10px;background:#f6f9fd;font-size:12px;color:#475467}.compare-summary span:nth-child(2){margin-right:auto}.compare-tabs{min-height:520px}.compare-code-head{height:32px;display:grid;grid-template-columns:1fr 1fr;align-items:center;border:1px solid #e5e7eb;border-bottom:0;background:#f8fafc;color:#667085;font-size:10px}.compare-code-head span{padding:0 12px}.compare-code-head span+span{border-left:1px solid #e5e7eb}.compare-editor{height:430px;border:1px solid #e5e7eb}.compare-tabs :deep(.el-tabs__content){overflow:visible}
</style>
