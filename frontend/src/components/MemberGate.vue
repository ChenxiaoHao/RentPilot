<script setup lang="ts">
import {useRoute} from 'vue-router'
import AppIcon from './AppIcon.vue'
defineProps<{title:string;description?:string}>()
const route=useRoute()
</script>
<template><section class="rental-state"><AppIcon name="user" :size="32"/><h2>{{ title }}</h2><p>{{ description||'登录后，收藏与看房计划会保存到你的账户。' }}</p><RouterLink :to="{path:'/account',query:{redirect:route.fullPath}}" class="primary">登录 / 注册<AppIcon name="arrow-right" :size="17"/></RouterLink></section></template>
