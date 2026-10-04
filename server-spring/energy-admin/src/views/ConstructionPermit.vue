<template>
  <div class="permit-page" v-loading="loading">
    <div class="toolbar"><el-button @click="router.back()">返回</el-button><el-button type="primary" @click="printPermit">打印施工许可证</el-button></div>
    <article v-if="row" class="permit-sheet">
      <div class="permit-border">
        <header class="permit-header">
          <div class="brand"><img src="/images/wanyuecity-logo.jpg" alt="万悦城"></div>
          <h1>商户施工许可证</h1>
        </header>
        <table class="permit-table">
          <colgroup><col class="col-group"><col class="col-label"><col class="col-value"><col class="col-label"><col class="col-value-wide"></colgroup>
          <tbody>
          <tr class="wide-row"><th>施工位置</th><td colspan="3">{{ location }}</td><td rowspan="2" class="top-qr"><img :src="qrUrl" alt="施工流程二维码"><small>{{ typeLabel(row.workflowType) }}<br>{{ row.permitNo }}</small></td></tr>
          <tr class="wide-row"><th>施工周期</th><td colspan="3">{{ constructionPeriod }}</td></tr>
          <tr>
            <th rowspan="3" class="group">施工方</th><th>商户名称</th><td>{{ row.merchantName || '-' }}</td><th>施工公司名称</th><td>{{ row.constructionCompanyName || '-' }}</td>
          </tr>
          <tr><th>商户负责人</th><td>{{ row.merchantPrincipalName || '-' }}</td><th>施工负责人</th><td>{{ row.constructionPrincipalName || '-' }}</td></tr>
          <tr><th>联系电话</th><td>{{ row.merchantPrincipalPhone || '-' }}</td><th>联系电话</th><td>{{ row.constructionPrincipalPhone || '-' }}</td></tr>
          <tr>
            <th rowspan="2" class="group">甲方</th><th>物业工程员</th><td>{{ row.propertyEngineerName || '-' }}</td><th>商管协调人</th><td>{{ row.commercialManagerName || '-' }}</td>
          </tr>
          <tr><th>联系电话</th><td>{{ row.propertyEngineerPhone || '-' }}</td><th>联系电话</th><td>{{ row.commercialManagerPhone || '-' }}</td></tr>
          <tr class="night-row"><th colspan="2">夜班值班电话</th><td colspan="3">0377-63269152</td></tr>
          </tbody>
        </table>
      </div>
      <section class="notice-area">
        <svg class="no-smoking-svg" viewBox="0 0 160 160" role="img" aria-label="禁止吸烟">
          <circle cx="80" cy="80" r="66" fill="none" stroke="#ed1c24" stroke-width="17"/>
          <rect x="40" y="84" width="77" height="13" fill="#000"/><rect x="105" y="84" width="5" height="13" fill="#fff"/><rect x="115" y="84" width="5" height="13" fill="#fff"/>
          <path d="M83 73c-18-5-22-14-17-27M102 73c-18-6-21-15-15-28" fill="none" stroke="#111" stroke-width="3"/>
          <line x1="34" y1="34" x2="126" y2="126" stroke="#ed1c24" stroke-width="18"/>
        </svg>
        <div class="warning-copy"><strong>严禁吸烟<br>违者重罚</strong></div>
        <p>施工现场　非工作人员请勿入内</p>
      </section>
    </article>
  </div>
</template>
<script setup lang="ts">
import { computed,onBeforeUnmount,onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { constructionApi,type ConstructionWorkflow } from '@/api/construction'
const route=useRoute(),router=useRouter(),row=ref<ConstructionWorkflow>(),loading=ref(false)
const location=computed(()=>[row.value?.floorName||row.value?.floorCode,row.value?.stallCode].filter(Boolean).join(' ')||'-')
const qrUrl=computed(()=>`/api/v1/construction-public/${row.value?.publicToken}/qr`)
const constructionPeriod=computed(()=>`${formatPermitTime(row.value?.constructionStartTime)} 至 ${formatPermitTime(row.value?.constructionEndTime)}`)
function typeLabel(v:string){return v==='MERCHANT_CONSTRUCTION_ENTRY'?'入场装修':v==='MERCHANT_CONSTRUCTION_EXIT'?'撤场施工':v}
function formatPermitTime(value?:string){if(!value)return '-';const m=String(value).match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);return m?`${m[1]}年${Number(m[2])}月${Number(m[3])}日 ${m[4]}:${m[5]}`:String(value)}
function printPermit(){window.print()}
onMounted(async()=>{document.body.classList.add('construction-permit-print');loading.value=true;try{row.value=await constructionApi.get(Number(route.params.id)) as any}finally{loading.value=false}})
onBeforeUnmount(()=>document.body.classList.remove('construction-permit-print'))
</script>
<style scoped>
.permit-page{min-height:100vh;background:#eef1f5;padding:20px}.toolbar{max-width:794px;margin:0 auto 12px;display:flex;justify-content:flex-end;gap:8px}.permit-sheet{box-sizing:border-box;width:794px;min-height:1123px;margin:auto;padding:46px 52px 28px;background:#fff;color:#111;font-family:"Microsoft YaHei","SimSun",sans-serif}.permit-border{border:2px solid #222}.permit-header{height:105px;display:flex;align-items:center;border-bottom:2px solid #222;padding:0 26px}.brand{width:220px;display:flex;align-items:flex-end;gap:5px;white-space:nowrap}.brand strong{font-size:43px;line-height:1;font-weight:900}.brand small{font-size:8px;margin-bottom:5px}.permit-header h1{flex:1;text-align:center;font-size:35px;font-weight:500;letter-spacing:3px;margin:0}.permit-meta{height:31px;padding:0 12px;display:flex;align-items:center;justify-content:space-between;border-bottom:1.5px solid #222;font-size:12px}.permit-table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:15px}.permit-table th,.permit-table td{border:1.5px solid #222;padding:9px 8px;text-align:center;line-height:1.4;word-break:break-all}.permit-table tr>*:first-child{border-left:0}.permit-table tr>*:last-child{border-right:0}.permit-table tr:last-child>*{border-bottom:0}.permit-table th{font-weight:500}.permit-table .group{width:54px;font-size:17px}.permit-table tr:not(.wide-row) th:nth-child(2){width:104px}.permit-table tr:not(.wide-row) td:nth-child(3){width:145px}.permit-table tr:not(.wide-row) th:nth-child(4){width:104px}.wide-row{height:61px}.wide-row th{width:155px}.wide-row td{font-size:17px}.night-row th,.night-row td{font-size:17px;font-weight:600}.notice-area{min-height:350px;display:grid;grid-template-columns:155px 1fr 120px;align-items:center;column-gap:22px;padding:24px 18px 0}.smoking-symbol{position:relative;width:128px;height:128px;border:14px solid #222;border-radius:50%;box-sizing:border-box}.smoking-symbol:after{content:"";position:absolute;left:-8px;top:48px;width:118px;height:14px;background:#222;transform:rotate(-45deg);transform-origin:center}.smoking-symbol span:before{content:"";position:absolute;left:24px;top:58px;width:54px;height:11px;border:6px solid #222;border-left:0;box-sizing:border-box}.warning-copy strong{font-size:39px;line-height:1.45;letter-spacing:7px}.warning-copy p{font-size:21px;margin:35px 0 0;white-space:nowrap}.permit-qr{text-align:center;font-size:11px;line-height:1.55;align-self:end;margin-bottom:48px}.permit-qr img{display:block;width:112px;height:112px;margin:0 auto 7px}.permit-sheet footer{text-align:center;border-top:1px solid #999;padding-top:11px;font-size:12px;color:#555}.permit-sheet,.permit-sheet *{box-sizing:border-box}@media(max-width:850px){.permit-page{padding:10px;overflow:auto}.toolbar,.permit-sheet{width:794px}.toolbar{justify-content:flex-start}}
.permit-sheet{position:relative}
.brand{width:235px;height:92px;display:flex;align-items:center}.brand img{display:block;width:225px;height:88px;object-fit:contain}.permit-header{height:112px}.permit-header h1{font-family:"SimSun","STSong",serif;font-size:39px;letter-spacing:2px;white-space:nowrap}.permit-table .col-group{width:12%}.permit-table .col-label{width:19%}.permit-table .col-value{width:22%}.permit-table .col-value-wide{width:28%}.permit-table .wide-row{height:68px}.permit-table .wide-row>th{font-size:15px}.permit-table .wide-row>td:not(.top-qr){font-size:15px}.top-qr{padding:4px!important}.top-qr img{display:block;width:74px;height:74px;margin:0 auto 2px}.top-qr small{display:block;font-size:7.5px;line-height:1.2;word-break:break-all}.permit-table th,.permit-table td{height:55px;padding:7px 5px;white-space:nowrap;word-break:normal;font-size:13px}.permit-table th{font-size:14px}.permit-table .group{font-size:17px}.night-row{height:72px}.night-row th,.night-row td{font-family:"SimSun","STSong",serif;font-size:24px}.notice-area{min-height:335px;display:grid;grid-template-columns:190px 1fr;grid-template-rows:220px auto;align-items:center;column-gap:45px;padding:12px 86px 0}.no-smoking-svg{width:175px;height:175px}.warning-copy strong{font-family:"SimSun","STSong",serif;font-size:43px;line-height:1.45;letter-spacing:6px;font-weight:500}.notice-area>p{grid-column:1/3;text-align:center;font-family:"SimSun","STSong",serif;font-size:24px;letter-spacing:2px;margin:0;white-space:nowrap}
.permit-table tr:not(.wide-row) th:nth-child(2),.permit-table tr:not(.wide-row) td:nth-child(3),.permit-table tr:not(.wide-row) th:nth-child(4){width:auto}.permit-table .wide-row>th{width:auto}.permit-table .group{width:auto}.top-qr{width:auto!important}
.top-qr img{width:92px;height:92px}.notice-area>p{font-size:30px;font-weight:700}
@media print{.permit-page{padding:0!important;background:#fff!important}.toolbar{display:none!important}.permit-sheet{width:210mm;height:297mm;min-height:297mm;margin:0!important;padding:12mm 13mm 8mm!important}.notice-area{min-height:91mm}.permit-sheet footer{position:absolute;left:13mm;right:13mm;bottom:8mm}@page{size:A4 portrait;margin:0}}
</style>
