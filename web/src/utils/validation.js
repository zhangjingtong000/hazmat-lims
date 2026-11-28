/**
 * 表单校验规则工厂（声明式）——所有 el-form 的 rules 统一由此生成，
 * 与后端 com.qingyan.hazmat.common.Validators 的口径一一对应
 * （必填 / 长度 / 数值范围 / 格式），错误文案与后端保持一致。
 *
 * 用法：<el-form-item label="品名" prop="name" :rules="R.required('品名')">
 *      <el-form ref="formRef" :model="form"> … </el-form>
 * 提交前：const ok = await formRef.value.validate().catch(() => false)
 *
 * 纪律：只校验真正必填/有边界约束的字段——把非必填字段标必填会让校验形同虚设。
 */

/** 必填文本（blur 触发，输入框）。 */
export const required = (label, trigger = 'blur') => [
  { required: true, whitespace: true, message: `${label}不能为空`, trigger }
]

/** 必填下拉/日期/数字等非文本控件（change 触发）。 */
export const requiredPick = (label, trigger = 'change') => [
  { required: true, message: `请选择${label}`, trigger }
]

/** 必填数字（change 触发）。 */
export const requiredNumber = (label, trigger = 'change') => [
  { required: true, message: `${label}不能为空`, trigger }
]

/** 长度上限（可空字段用，覆盖"长度"约束）。 */
export const maxLen = (limit, label, trigger = 'blur') => [
  { max: limit, message: `${label}不能超过 ${limit} 个字符`, trigger }
]

/** 必填 + 长度上限。 */
export const requiredLen = (limit, label, trigger = 'blur') => [
  { required: true, whitespace: true, message: `${label}不能为空`, trigger },
  { max: limit, message: `${label}不能超过 ${limit} 个字符`, trigger }
]

/** 正数（>0）。 */
export const positive = (label, trigger = 'change') => [
  { required: true, message: `${label}不能为空`, trigger },
  {
    validator: (rule, value, callback) => {
      if (value === null || value === undefined || value === '') return callback()
      return Number(value) > 0 ? callback() : callback(new Error(`${label}必须大于 0`))
    },
    trigger
  }
]

/** 非负数（≥0）。 */
export const nonNegative = (label, trigger = 'change') => [
  {
    validator: (rule, value, callback) => {
      if (value === null || value === undefined || value === '') return callback()
      return Number(value) >= 0 ? callback() : callback(new Error(`${label}不能为负数`))
    },
    trigger
  }
]

/** 整数区间 [min, max]（含边界）。 */
export const range = (min, max, label, trigger = 'change') => [
  {
    validator: (rule, value, callback) => {
      if (value === null || value === undefined || value === '') return callback()
      const n = Number(value)
      return n >= min && n <= max ? callback() : callback(new Error(`${label}须在 ${min}~${max} 之间`))
    },
    trigger
  }
]

/** 数值上限（如"处置数量不得超过批次余量"由服务层精确守卫，前端做软提示）。 */
export const maxNumber = (limit, label, trigger = 'change') => [
  {
    validator: (rule, value, callback) => {
      if (value === null || value === undefined || value === '') return callback()
      return Number(value) <= limit ? callback() : callback(new Error(`${label}不得超过 ${limit}`))
    },
    trigger
  }
]

/** CAS 登记号格式（2~7 位数字-2 位数字-1 位数字）。 */
export const casNo = (trigger = 'blur') => [
  {
    validator: (rule, value, callback) => {
      if (!value) return callback()
      return /^\d{2,7}-\d{2}-\d$/.test(String(value).trim())
        ? callback()
        : callback(new Error('CAS 号格式如 67-64-1'))
    },
    trigger
  }
]

/** 必填 + CAS 格式。 */
export const requiredCasNo = (trigger = 'blur') => [
  { required: true, whitespace: true, message: 'CAS 号不能为空', trigger },
  ...casNo(trigger)
]

/** 通用正则格式。 */
export const format = (pattern, message, trigger = 'blur') => [
  {
    validator: (rule, value, callback) => {
      if (!value) return callback()
      return pattern.test(String(value).trim()) ? callback() : callback(new Error(message))
    },
    trigger
  }
]

/** 口令策略（与后端 Validators.password 一致：8~64 位且至少两类字符）。 */
export const password = () => [
  { required: true, message: '密码不能为空', trigger: 'blur' },
  { min: 8, max: 64, message: '密码长度须为 8~64 位', trigger: 'blur' },
  {
    validator: (rule, value, callback) => {
      if (!value) return callback()
      const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter(p => p.test(value)).length
      return classes >= 2 ? callback() : callback(new Error('密码须含大小写字母/数字/符号中至少两类'))
    },
    trigger: 'blur'
  }
]

/** 两字段一致性（如目标柜位不得等于当前柜位）。 */
export const notEqual = (otherValue, label, trigger = 'change') => [
  {
    validator: (rule, value, callback) => {
      if (value === null || value === undefined || value === '') return callback()
      return value === otherValue() ? callback(new Error(`${label}不得与当前值相同`)) : callback()
    },
    trigger
  }
]

/** 至少一项必填（用于"既有台账或新建档案"二选一场景）。 */
export const anyOf = (values, message, trigger = 'change') => [
  {
    validator: (rule, value, callback) => {
      const hit = values().some(v => v !== null && v !== undefined && v !== '')
      return hit ? callback() : callback(new Error(message))
    },
    trigger
  }
]

const R = {
  required, requiredPick, requiredNumber, maxLen, requiredLen, positive, nonNegative,
  range, maxNumber, casNo, requiredCasNo, format, password, notEqual, anyOf
}

export default R
