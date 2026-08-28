<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">同步日志详情</div>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      在这里展示某一批次的账户/设备/关系变更详情。当前由后端
      <code>platform_*_current</code> / <code>platform_*_snapshot</code> 表提供数据，
      可以根据 batchNo 进一步钻取（待后端补 detail 接口）。
    </el-alert>
  </div>
</template>
