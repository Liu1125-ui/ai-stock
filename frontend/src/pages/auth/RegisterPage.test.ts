import {expect,it} from 'vitest'
import {validateRegisterForm} from './validation'
it('requires the user-entered verification code and matching password',()=>{
 const form={email:'member@example.com',nickname:'测试用户',code:'',password:'Long-test-password-1',confirmPassword:'Long-test-password-1'}
 expect(validateRegisterForm(form).code).toBeDefined()
 expect(validateRegisterForm({...form,code:'123456'})).toEqual({})
 expect(validateRegisterForm({...form,code:'123456',confirmPassword:'other'}).confirmPassword).toBeDefined()
 expect(validateRegisterForm({...form,code:'123456',password:'汉'.repeat(25),confirmPassword:'汉'.repeat(25)}).password).toBeDefined()
})
