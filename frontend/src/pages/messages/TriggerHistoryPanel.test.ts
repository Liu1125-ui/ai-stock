import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import ElementPlus from 'element-plus'
import TriggerHistoryPanel from './TriggerHistoryPanel.vue'
import * as api from '../../api/messages'
import {useAuthStore} from '../../stores/auth'
vi.mock('../../api/messages',()=>({listAlertTriggers:vi.fn()}))
vi.mock('../../stores/auth',()=>({useAuthStore:vi.fn()}))
const auth=reactive({profile:{userId:'a'}})
beforeEach(()=>{vi.resetAllMocks();auth.profile={userId:'a'};vi.mocked(useAuthStore).mockReturnValue(auth as never);vi.mocked(api.listAlertTriggers).mockResolvedValue({items:[],page:1,size:20,total:0})})
it('does not equate empty trigger history with conditions never met',async()=>{const w=mount(TriggerHistoryPanel,{props:{ruleId:'r1'},global:{plugins:[ElementPlus]}});await flushPromises();expect(api.listAlertTriggers).toHaveBeenCalledWith('r1',1);expect(w.text()).toContain('暂无触发记录');expect(w.text()).toContain('不代表历史上从未满足条件');w.unmount()})
it('ignores old rule and old account responses',async()=>{let finish!:(v:api.TriggerPage)=>void;vi.mocked(api.listAlertTriggers).mockReturnValueOnce(new Promise(r=>finish=r));const w=mount(TriggerHistoryPanel,{props:{ruleId:'r1'},global:{plugins:[ElementPlus]}});await flushPromises();await w.setProps({ruleId:'r2'});await flushPromises();finish({items:[],page:1,size:20,total:99});await flushPromises();expect(w.text()).not.toContain('99');auth.profile={userId:'b'};await flushPromises();expect(api.listAlertTriggers).toHaveBeenLastCalledWith('r2',1);w.unmount()})
