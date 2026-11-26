import { createRouter, createWebHistory } from 'vue-router'
import {installAccountGuards} from './guards'
export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to,from,savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path===from.path && !to.hash) return false
    if (/^#[A-Za-z][A-Za-z0-9_-]*$/.test(to.hash)) {
      const anchor=document.getElementById(to.hash.slice(1))
      if(anchor)return {el:anchor}
    }
    return {left:0,top:0}
  },
  routes: [
    { path: '/', component: () => import('../pages/market/MarketHomePage.vue'),meta:{requiresAuth:true} },
    { path:'/market/:id',component:()=>import('../pages/market/MarketAnalysisPage.vue'),meta:{requiresAuth:true} },
    { path:'/market/:id/predict',component:()=>import('../pages/prediction/PredictionPage.vue'),meta:{requiresAuth:true} },
    { path:'/comparisons',component:()=>import('../pages/comparison/ComparisonPage.vue'),meta:{requiresAuth:true} },
    { path:'/training',component:()=>import('../pages/training/TrainingPage.vue'),meta:{requiresAuth:true} },
    { path:'/predictions',component:()=>import('../pages/prediction-history/HistoryPage.vue'),meta:{requiresAuth:true} },
    { path:'/messages',component:()=>import('../pages/messages/MessagesPage.vue'),meta:{requiresAuth:true} },
    { path:'/alerts',component:()=>import('../pages/alerts/AlertsPage.vue'),meta:{requiresAuth:true} },
    { path:'/portfolio',component:()=>import('../pages/portfolio/PortfolioPage.vue'),meta:{requiresAuth:true} },
    { path:'/watchlist',component:()=>import('../pages/watchlist/WatchlistPage.vue'),meta:{requiresAuth:true} },
    { path:'/diagnostics',component:()=>import('../pages/EngineeringPage.vue'),meta:{requiresAuth:true} },
    { path:'/login',component:()=>import('../pages/auth/LoginPage.vue') },
    { path:'/register',component:()=>import('../pages/auth/RegisterPage.vue') },
    { path:'/profile',component:()=>import('../pages/profile/ProfilePage.vue'),meta:{requiresAuth:true} },
    { path:'/admin/data',component:()=>import('../pages/admin/data/DataPage.vue'),meta:{requiresAuth:true,admin:true} },
    { path:'/admin/statistics',component:()=>import('../pages/admin/statistics/StatisticsPage.vue'),meta:{requiresAuth:true,admin:true} },
    { path:'/admin/tasks',component:()=>import('../pages/admin/tasks/TasksPage.vue'),meta:{requiresAuth:true,admin:true} },
    { path:'/admin/models',component:()=>import('../pages/admin/ModelsPage.vue'),meta:{requiresAuth:true,admin:true} },
    { path:'/admin/users',component:()=>import('../pages/admin/UsersPage.vue'),meta:{requiresAuth:true,admin:true} },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
installAccountGuards(router)
