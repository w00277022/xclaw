<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #304156">
      <div style="padding: 20px; text-align: center; color: #fff; font-size: 20px; font-weight: bold">
        🤖 XClaw
      </div>
      <el-menu :default-active="route.path" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/create">
          <el-icon><Plus /></el-icon>
          <span>创建 XClaw</span>
        </el-menu-item>
        <el-menu-item index="/manage">
          <el-icon><Setting /></el-icon>
          <span>XClaw 管理</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>XClaw 对话</span>
        </el-menu-item>
        <el-menu-item index="/native">
          <el-icon><Monitor /></el-icon>
          <span>XClaw Native</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main style="padding: 0; overflow: hidden">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<style>
body { margin: 0; }
</style>
