<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="title">万悦城后勤管理系统</div>
      <div class="subtitle">综合后勤业务管理平台</div>
      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="账号" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password size="large" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="onSubmit" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
        <el-checkbox v-model="rememberDevice" style="margin-bottom:14px">在此设备上自动登录（30天）</el-checkbox>
      </el-form>
      <div class="register-link"><el-button v-if="hasDeviceLogin" link type="warning" @click="cancelAutoLogin">取消自动登录，使用其他账号</el-button><el-button link type="primary" @click="registerVisible=true">没有账号？自主注册</el-button></div>
    </el-card>
    <el-dialog v-model="registerVisible" title="自主注册" width="460px" append-to-body>
      <el-alert title="注册后默认无任何权限，请联系管理员分配角色" type="warning" :closable="false" style="margin-bottom:16px" />
      <el-form ref="registerRef" :model="registerForm" :rules="registerRules" label-width="86px">
        <el-form-item label="用户名" prop="username"><el-input v-model="registerForm.username" placeholder="3-64位字母、数字或 _ . -" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="registerForm.displayName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="registerForm.phone" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="registerForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="registerForm.confirmPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer><el-button @click="registerVisible=false">取消</el-button><el-button type="primary" :loading="registering" @click="register">提交注册</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const hasDeviceLogin=ref(!!localStorage.getItem('device_login_token'))
const rememberDevice=ref(true),registerVisible=ref(false),registering=ref(false),registerRef=ref<FormInstance>()
const registerForm=reactive({username:'',displayName:'',phone:'',password:'',confirmPassword:''})
const registerRules={username:[{required:true,message:'请输入用户名'},{pattern:/^[A-Za-z0-9_.-]{3,64}$/,message:'用户名格式不正确'}],password:[{required:true,message:'请输入密码'},{min:6,max:64,message:'密码长度为6-64位'}],confirmPassword:[{validator:(_:any,v:string,cb:any)=>v===registerForm.password?cb():cb(new Error('两次密码不一致')),trigger:'blur'}]}
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form.username, form.password,rememberDevice.value)
      ElMessage.success('登录成功')
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    } catch (e) {
      // 错误已由 http 拦截器提示
    } finally {
      loading.value = false
    }
  })
}
async function register(){if(!await registerRef.value?.validate().catch(()=>false))return;registering.value=true;try{await authApi.register(registerForm);ElMessage.success('注册成功，请联系管理员分配权限');form.username=registerForm.username;registerVisible.value=false}finally{registering.value=false}}
function cancelAutoLogin(){localStorage.removeItem('device_login_token');localStorage.removeItem('token');localStorage.removeItem('user');hasDeviceLogin.value=false;ElMessage.success('已取消本设备自动登录')}
onMounted(async()=>{if(route.query.switch==='1'){cancelAutoLogin();return}if(localStorage.getItem('device_login_token')){loading.value=true;try{if(await userStore.autoLogin()){ElMessage.success('已通过本设备自动登录');router.replace((route.query.redirect as string)||'/')}}finally{loading.value=false}}})
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px;
}
.login-card {
  width: 400px;
  padding: 24px 8px;
  border-radius: 8px;
}
.title {
  font-size: 22px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 4px;
}
.subtitle {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-bottom: 24px;
}
.hint { text-align: center; color: #909399; font-size: 12px; }
.register-link{text-align:center}
@media (max-width: 480px) {
  .login-card { width: 100%; padding: 18px 2px; border-radius: 12px; }
  .title { font-size: 21px; }
}
</style>
