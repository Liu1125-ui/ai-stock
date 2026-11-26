export interface RegisterForm{email:string;nickname:string;code:string;password:string;confirmPassword:string}
export function validPassword(value:string){const length=Array.from(value).length;return length>=12&&length<=64&&new TextEncoder().encode(value).length<=72}
export function validateRegisterForm(form:RegisterForm){
 const errors:Partial<Record<keyof RegisterForm,string>>={}
 if(!/^[\x21-\x7e]+@[\x21-\x7e]+\.[\x21-\x7e]+$/.test(form.email.trim())||form.email.trim().length>254)errors.email='请输入有效邮箱'
 const length=Array.from(form.nickname.trim()).length
 if(length<2||length>30||/[\x00-\x1f\x7f]/.test(form.nickname))errors.nickname='昵称须为2至30个字符且不含控制字符'
 if(!/^\d{6}$/.test(form.code))errors.code='请输入邮件中的6位验证码'
 if(!validPassword(form.password))errors.password='密码须为12至64个字符且不超过72字节'
 if(form.confirmPassword!==form.password)errors.confirmPassword='两次输入的密码不一致'
 return errors
}
