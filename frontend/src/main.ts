import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import {createPageRecovery} from './router/pageRecovery'

const recoverPage=createPageRecovery({getItem:key=>window.sessionStorage.getItem(key),setItem:(key,value)=>window.sessionStorage.setItem(key,value)},path=>window.location.replace(path))
router.onError((error,to)=>{if(recoverPage(error,to.fullPath)==='manual')window.dispatchEvent(new Event('aistock:page-unavailable'))})

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
