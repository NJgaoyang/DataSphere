<script setup lang="ts">
import type { RecycledDevFile } from '../../../api/domain'
import { formatDateTime } from '../../../utils/display'

defineProps<{ modelValue:boolean; files:RecycledDevFile[] }>()
const emit=defineEmits<{ 'update:modelValue':[value:boolean]; restore:[row:RecycledDevFile]; remove:[row:RecycledDevFile] }>()
function fileTypeText(value?:string){return ({SQL:'SQL',SHELL:'SH',PYTHON:'PY'} as Record<string,string>)[(value||'SQL').toUpperCase()]||'TXT'}
</script>

<template>
  <el-dialog :model-value="modelValue" title="回收箱" width="760px" @update:model-value="emit('update:modelValue',$event)">
    <div class="recycle-tip">已下线的开发任务删除后会保留在这里；已上线任务必须先下线才能删除。</div>
    <el-table :data="files" size="small" max-height="430">
      <el-table-column prop="name" label="文件" min-width="180"><template #default="{row}"><span class="recycle-file-type">{{fileTypeText(row.fileType)}}</span>{{row.name}}</template></el-table-column>
      <el-table-column label="原目录" min-width="120"><template #default="{row}">{{row.folderName||'根目录'}}</template></el-table-column>
      <el-table-column label="删除人" prop="recycledBy" width="100"/>
      <el-table-column label="删除时间" width="170"><template #default="{row}">{{formatDateTime(row.recycledAt)}}</template></el-table-column>
      <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button link type="primary" @click="emit('restore',row as RecycledDevFile)">恢复</el-button><el-button link type="danger" @click="emit('remove',row as RecycledDevFile)">彻底删除</el-button></template></el-table-column>
    </el-table>
    <el-empty v-if="!files.length" description="回收箱为空"/>
    <template #footer><el-button @click="emit('update:modelValue',false)">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.recycle-tip{padding:9px 11px;margin-bottom:10px;border:1px solid #e2e9f3;background:#f7faff;border-radius:8px;color:#667085;font-size:11px}.recycle-file-type{display:inline-grid;place-items:center;min-width:24px;height:18px;margin-right:7px;border:1px solid #c8d4ec;border-radius:4px;background:#f5f8ff;color:#2f6fed;font-size:8px;font-weight:600}
</style>
