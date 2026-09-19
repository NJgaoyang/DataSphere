<script setup lang="ts">
import { Delete, Document, EditPen, SwitchButton, VideoPlay, View } from '@element-plus/icons-vue'
import StatusBadge from '../../../components/StatusBadge.vue'
import type { RealtimeManagementRow } from '../../../api/domain'
import { formatDateTime, statusLabel } from '../../../utils/display'

const props=defineProps<{ tasks:RealtimeManagementRow[]; loading:boolean; actionLoading:Record<string,'start'|'stop'> }>()
const emit=defineEmits<{ detail:[row:RealtimeManagementRow]; toggle:[row:RealtimeManagementRow]; more:[command:string,row:RealtimeManagementRow] }>()
function scopeLabel(v?:string){return v==='SINGLE_TABLE'?'单表':v==='FULL_DATABASE'?'整库':'多表'}
function releaseLabel(row:RealtimeManagementRow){const state=String(row.observedState||'').toUpperCase();if(['RUNNING','STARTING'].includes(state))return'已上线';if(['STOPPED','STOPPING','FAILED','UNKNOWN'].includes(state))return'已下线';return statusLabel(row.releaseState)}
function checkpointLabel(v?:string){const value=(v||'').toUpperCase();if(value==='COMPLETED')return'正常';if(value==='FAILED')return'失败';if(value==='IN_PROGRESS')return'进行中';if(['STOPPED','CANCELED','FINISHED'].includes(value))return'已停止';if(value==='UNKNOWN')return'状态未知';return'未采集'}
function lagLabel(v?:number){return v==null?'—':v<1000?`${v} ms`:`${(v/1000).toFixed(1)} s`}
function jobIdShort(v?:string){return v?v.length>14?`${v.slice(0,12)}…`:v:'—'}
function actionKey(row:RealtimeManagementRow){return String(row.id)}
function isActionLoading(row:RealtimeManagementRow){return !!props.actionLoading[actionKey(row)]}
function actionLoadingText(row:RealtimeManagementRow){const value=props.actionLoading[actionKey(row)];return value==='start'?'启动中':value==='stop'?'停止中':'更多'}
function detail(row:unknown){emit('detail',row as RealtimeManagementRow)}
</script>

<template>
  <el-table :data="tasks" v-loading="loading" class="realtime-table" row-class-name="realtime-task-row" @row-click="detail">
    <el-table-column label="任务名称" min-width="260" show-overflow-tooltip><template #default="s"><button class="name-link" @click.stop="emit('detail',s.row as RealtimeManagementRow)">{{s.row.name}}</button><div class="minor">{{s.row.owner||'—'}} · {{scopeLabel(s.row.syncScope)}} {{s.row.tableCount}} 张表<span v-if="s.row.engineJobId" class="task-job-id mono" :title="s.row.engineJobId"> · JobId {{jobIdShort(s.row.engineJobId)}}</span></div></template></el-table-column>
    <el-table-column label="发布状态" min-width="130" align="center" header-align="center"><template #default="s"><div class="release-cell"><StatusBadge :status="s.row.observedState==='RUNNING'?'RUNNING':'STOPPED'" :label="releaseLabel(s.row as RealtimeManagementRow)"/><span>当前版本 V{{s.row.definitionVersion||1}}</span></div></template></el-table-column>
    <el-table-column label="运行状态" min-width="110" align="center" header-align="center"><template #default="s"><StatusBadge :status="s.row.observedState"/></template></el-table-column>
    <el-table-column label="运行指标" min-width="200"><template #default="s"><div class="runtime-cell"><span>延迟：{{s.row.observedState==='RUNNING'?lagLabel(s.row.lagMs):'—'}}</span><span>Checkpoint：{{checkpointLabel(s.row.checkpointStatus)}}</span></div></template></el-table-column>
    <el-table-column label="最后更新时间" width="165" align="center" header-align="center"><template #default="s"><span class="updated-time">{{formatDateTime(s.row.updatedAt)}}</span></template></el-table-column>
    <el-table-column label="操作" width="220" fixed="right" align="center" header-align="center"><template #default="s"><div class="realtime-row-actions" @click.stop>
      <el-button class="inline-run-action" text :loading="isActionLoading(s.row as RealtimeManagementRow)" @click="emit('toggle',s.row as RealtimeManagementRow)">
        <el-icon v-if="!isActionLoading(s.row as RealtimeManagementRow)"><SwitchButton v-if="['RUNNING','STARTING'].includes(String(s.row.observedState).toUpperCase())"/><VideoPlay v-else/></el-icon>
        <span>{{isActionLoading(s.row as RealtimeManagementRow)?actionLoadingText(s.row as RealtimeManagementRow):(['RUNNING','STARTING'].includes(String(s.row.observedState).toUpperCase())?'停止':'启动')}}</span>
      </el-button>
      <el-dropdown trigger="click" placement="bottom" :offset="6" popper-class="realtime-action-popper" @command="(cmd:string)=>emit('more',cmd,s.row as RealtimeManagementRow)">
        <el-button class="inline-more-action" text>更多</el-button>
        <template #dropdown><el-dropdown-menu>
          <el-dropdown-item command="detail"><el-icon><View/></el-icon><span>查看详情</span></el-dropdown-item>
          <el-dropdown-item command="edit" :disabled="['STARTING','STOPPING'].includes(String(s.row.observedState).toUpperCase())"><el-icon><EditPen/></el-icon><span>编辑配置</span></el-dropdown-item>
          <el-dropdown-item command="logs"><el-icon><Document/></el-icon><span>运行日志</span></el-dropdown-item>
          <el-dropdown-item command="delete" divided :disabled="['STARTING','STOPPING'].includes(String(s.row.observedState).toUpperCase())" class="realtime-delete-item"><el-icon><Delete/></el-icon><span>删除任务</span></el-dropdown-item>
        </el-dropdown-menu></template>
      </el-dropdown>
    </div></template></el-table-column>
  </el-table>
</template>

<style scoped>
.realtime-table :deep(.el-table__cell){padding:12px 0}.realtime-table :deep(.realtime-task-row){cursor:pointer}.realtime-table :deep(.realtime-task-row:hover>td.el-table__cell){background:#f5f8ff!important}.realtime-table :deep(.cell){padding-left:11px;padding-right:11px}.name-link{padding:0;border:0;background:transparent;color:#276fe5;font-weight:680;cursor:pointer}.minor{margin-top:3px;color:#94a0b2;font-size:10px}.task-job-id{margin-left:4px;color:var(--ds-text-tertiary)}.release-cell{display:flex;flex-direction:column;align-items:center;gap:4px}.release-cell>span{font-size:10px;color:var(--ds-text-tertiary);white-space:nowrap}.runtime-cell{display:flex;flex-direction:column;gap:5px;color:var(--ds-text-secondary);font-size:11px;line-height:1.45}.updated-time{display:block;white-space:nowrap;text-align:center;font-size:12px;color:var(--ds-text-primary)}.realtime-row-actions{display:flex;align-items:center;justify-content:center;gap:8px;white-space:nowrap}.inline-run-action,.inline-more-action{height:36px!important;padding:0 12px!important;border-radius:8px!important;color:#667085!important;font-weight:500!important}.inline-run-action{gap:5px;background:#f5f8fc!important;border:1px solid #edf1f6!important}.inline-more-action{gap:5px}.inline-run-action:not(.is-disabled):hover,.inline-more-action:hover{background:#f4f5f7!important;color:#344054!important}:global(.realtime-action-popper){min-width:172px!important}:global(.realtime-action-popper.el-popper){border:0!important;border-radius:16px!important;box-shadow:0 12px 30px rgba(16,24,40,.16)!important;overflow:hidden}:global(.realtime-action-popper .el-popper__arrow){display:none}:global(.realtime-action-popper .el-dropdown-menu){min-width:168px;padding:8px!important;border-radius:16px!important}:global(.realtime-action-popper .el-dropdown-menu__item){height:44px;padding:0 16px!important;gap:10px;border-radius:7px;font-size:14px;color:#202124}:global(.realtime-action-popper .el-dropdown-menu__item .el-icon){font-size:17px;color:#667085}:global(.realtime-action-popper .el-dropdown-menu__item:not(.is-disabled):hover){background:#f5f6f8;color:#202124}:global(.realtime-action-popper .el-dropdown-menu__item.is-disabled),:global(.realtime-action-popper .el-dropdown-menu__item.is-disabled .el-icon){color:#c0c4cc}:global(.realtime-action-popper .el-dropdown-menu__item--divided){margin-top:7px!important;border-top:1px solid #ebeef2!important}:global(.realtime-action-popper .realtime-delete-item:not(.is-disabled)),:global(.realtime-action-popper .realtime-delete-item:not(.is-disabled) .el-icon){color:#f04438}
</style>
