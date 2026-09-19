<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>
<template>
  <header class="ds-page-header">
    <h1 class="ds-page-header__title">{{ title }}</h1>
    <div v-if="$slots.actions" class="ds-page-header__actions"><slot name="actions" /></div>
  </header>
</template>
