<script setup lang="ts">
import { ArrowDown, Delete, Document, Download, EditPen, Upload, VideoPlay, View } from '@element-plus/icons-vue'
import type { IntegrationBatch, IntegrationInstance, IntegrationTask, IntegrationTaskSummary } from '../../../api/domain'
import { formatDateTime } from '../../../utils/display'

const props=defineProps<{
  tasks:IntegrationTask[]
  loading:boolean
  summaries:Record<number,IntegrationTaskSummary|undefined>
  latestInstances:Record<number,IntegrationInstance|undefined>
  latestBatches:Record<number,IntegrationBatch|undefined>
}>()
const emit=defineEmits<{ detail:[task:IntegrationTask]; run:[task:IntegrationTask]; more:[command:string,task:IntegrationTask] }>()
function taskSummary(task:IntegrationTask){return props.summaries[task.id]}
function latestStatus(task:IntegrationTask){return props.latestBatches[task.id]?.status||props.latestInstances[task.id]?.status||'PENDING'}
function taskStateLabel(task:IntegrationTask){const value=String(latestStatus(task)||'').toUpperCase();if(['RUNNING','STARTING','SUBMITTED','QUEUED','WAITING'].includes(value))return'运行中';if(['SUCCESS','SUCCEEDED','FINISHED','COMPLETED'].includes(value))return'成功';if(['FAILED','FAIL','ERROR','LOST'].includes(value))return'失败';return'待调度'}
function taskStateClass(task:IntegrationTask){const label=taskStateLabel(task);return label==='运行中'?'task-state-running':label==='成功'?'task-state-success':label==='失败'?'task-state-failed':'task-state-pending'}
function formatCount(value?:number){return value==null?'—':new Intl.NumberFormat('zh-CN').format(value)}
function formatDuration(ms?:number){if(ms==null)return'—';const seconds=Math.max(0,Math.round(ms/1000));if(seconds<60)return`${seconds}秒`;const minutes=Math.floor(seconds/60),rest=seconds%60;if(minutes<60)return`${minutes}分${rest?`${rest}秒`:''}`;const hours=Math.floor(minutes/60),min=minutes%60;return`${hours}小时${min?`${min}分`:''}`}
function isOnline(task:IntegrationTask){return(task.lifecycleStatus||'ONLINE').toUpperCase()==='ONLINE'}
function taskCreatedAt(task:IntegrationTask){return formatDateTime(taskSummary(task)?.createdAt)}
function taskCreatedBy(task:IntegrationTask){return taskSummary(task)?.createdBy||'platform'}
function detail(row:unknown){emit('detail',row as IntegrationTask)}
</script>

<template>
  <el-table :data="tasks" v-loading="loading" row-class-name="offline-task-row" @row-click="detail">
    <el-table-column label="任务名称" width="170" show-overflow-tooltip><template #default="scope"><button class="task-name-link" @click.stop="emit('detail',scope.row as IntegrationTask)">{{scope.row.name}}</button></template></el-table-column>
    <el-table-column label="执行概况" min-width="165"><template #default="scope"><div class="runtime-summary"><span>数据量：<strong>{{formatCount(taskSummary(scope.row as IntegrationTask)?.dataCount)}}</strong></span><span>耗时：{{formatDuration(taskSummary(scope.row as IntegrationTask)?.durationMs)}}</span></div></template></el-table-column>
    <el-table-column label="状态" width="100"><template #default="scope"><span :class="['publish-state-tag',isOnline(scope.row as IntegrationTask)?'is-online':'is-offline']">{{isOnline(scope.row as IntegrationTask)?'上线':'下线'}}</span></template></el-table-column>
    <el-table-column label="任务状态" width="110"><template #default="scope"><span :class="['task-state-tag',taskStateClass(scope.row as IntegrationTask)]">{{taskStateLabel(scope.row as IntegrationTask)}}</span></template></el-table-column>
    <el-table-column label="调度" min-width="205"><template #default="scope"><div class="schedule-summary"><span>上次：{{formatDateTime(taskSummary(scope.row as IntegrationTask)?.lastRunAt)}}</span><span>下次：{{formatDateTime(taskSummary(scope.row as IntegrationTask)?.nextRunAt)}}</span></div></template></el-table-column>
    <el-table-column label="创建时间" min-width="175"><template #default="scope"><span class="time-cell">{{taskCreatedAt(scope.row as IntegrationTask)}}</span></template></el-table-column>
    <el-table-column label="创建人" min-width="100"><template #default="scope">{{taskCreatedBy(scope.row as IntegrationTask)}}</template></el-table-column>
    <el-table-column label="操作" width="190" fixed="right">
      <template #default="scope"><div class="row-actions batch-row-actions" @click.stop>
        <el-button class="inline-run-action" text :disabled="!isOnline(scope.row as IntegrationTask)" @click="emit('run',scope.row as IntegrationTask)"><el-icon><VideoPlay/></el-icon><span>运行</span></el-button>
        <el-dropdown trigger="click" popper-class="batch-action-popper" @command="(cmd:string)=>emit('more',cmd,scope.row as IntegrationTask)">
          <el-button class="inline-more-action" text>更多<el-icon class="more-arrow"><ArrowDown/></el-icon></el-button>
          <template #dropdown><el-dropdown-menu class="batch-action-menu">
            <el-dropdown-item command="detail"><el-icon><View/></el-icon><span>查看详情</span></el-dropdown-item>
            <el-dropdown-item command="edit" :disabled="isOnline(scope.row as IntegrationTask)"><el-icon><EditPen/></el-icon><span>编辑配置</span></el-dropdown-item>
            <el-dropdown-item command="logs"><el-icon><Document/></el-icon><span>运行日志</span></el-dropdown-item>
            <el-dropdown-item v-if="isOnline(scope.row as IntegrationTask)" command="offline" divided><el-icon><Download/></el-icon><span>下线任务</span></el-dropdown-item>
            <el-dropdown-item v-else command="online" divided><el-icon><Upload/></el-icon><span>上线任务</span></el-dropdown-item>
            <el-dropdown-item command="delete" divided :disabled="isOnline(scope.row as IntegrationTask)" class="batch-delete-item"><el-icon><Delete/></el-icon><span>删除任务</span></el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
      </div></template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
:deep(.offline-task-row){cursor:pointer}:deep(.offline-task-row td.el-table__cell){padding:7px 0}:deep(.offline-task-row .cell){padding-left:7px;padding-right:7px}:deep(.offline-task-row:hover>td.el-table__cell){background:#f5f8ff!important}.task-name-link{border:0;background:transparent;padding:0;color:#276fe5;font:inherit;font-weight:680;cursor:pointer;text-align:left}.task-name-link:hover{text-decoration:underline}.runtime-summary,.schedule-summary{display:flex;flex-direction:column;gap:4px;font-size:12px;line-height:1.45;color:var(--ds-text-secondary)}.runtime-summary strong{color:var(--ds-text-primary);font-weight:600}.publish-state-tag,.task-state-tag{display:inline-flex;align-items:center;justify-content:center;min-width:50px;height:23px;padding:0 9px;border-radius:999px;font-size:11px;font-weight:650}.publish-state-tag.is-online,.task-state-success{color:#1f9d55;background:#ecf9f1}.publish-state-tag.is-offline,.task-state-pending{color:#667085;background:#f2f4f7}.task-state-running{color:#1677ff;background:#eaf3ff}.task-state-failed{color:#d92d20;background:#fff0ee}.time-cell{white-space:nowrap;font-size:13px;color:var(--ds-text-primary)}.row-actions{display:flex;align-items:center;gap:4px;white-space:nowrap}.batch-row-actions{gap:8px}.inline-run-action,.inline-more-action{height:36px!important;padding:0 12px!important;border-radius:8px!important;color:#667085!important;font-weight:500!important}.inline-run-action{gap:5px;background:#f5f8fc!important;border:1px solid #edf1f6!important}.inline-more-action{gap:5px}.inline-run-action:not(.is-disabled):hover,.inline-more-action:hover{background:#f4f5f7!important;color:#344054!important}.inline-run-action.is-disabled{opacity:.45}:global(.batch-action-popper){min-width:196px!important}:global(.batch-action-popper.el-popper){border:0!important;border-radius:16px!important;box-shadow:0 12px 30px rgba(16,24,40,.16)!important;overflow:hidden}:global(.batch-action-popper .el-popper__arrow){display:none}:global(.batch-action-popper .el-dropdown-menu){min-width:196px;padding:8px!important;border-radius:16px!important}:global(.batch-action-popper .el-dropdown-menu__item){height:44px;padding:0 16px!important;gap:10px;border-radius:7px;font-size:14px;color:#202124}:global(.batch-action-popper .el-dropdown-menu__item .el-icon){font-size:17px;color:#667085}:global(.batch-action-popper .el-dropdown-menu__item:not(.is-disabled):hover){background:#f5f6f8;color:#202124}:global(.batch-action-popper .el-dropdown-menu__item.is-disabled),:global(.batch-action-popper .el-dropdown-menu__item.is-disabled .el-icon){color:#c0c4cc}:global(.batch-action-popper .el-dropdown-menu__item--divided){margin-top:7px!important;border-top:1px solid #ebeef2!important}:global(.batch-action-popper .batch-delete-item:not(.is-disabled)),:global(.batch-action-popper .batch-delete-item:not(.is-disabled) .el-icon){color:#f04438}
</style>
