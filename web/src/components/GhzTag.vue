<template>
  <span
    class="ghz-wrap"
    :title="tooltip"
    :style="{ width: size + 'px', height: size + 'px' }"
  >
    <span
      class="ghz-diamond"
      :style="{ borderColor: cat.color, background: withBg ? cat.light : '#fff' }"
    ></span>
    <span
      class="ghz-code"
      :style="{ color: cat.color, fontSize: size * 0.32 + 'px' }"
    >{{ cat.key }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { categoryOf } from '../utils/constants'

/**
 * GHS 警示菱形标签（系统差异化签名组件）
 * 菱形边框 + 类别缩写（JD剧毒/YZ易制毒/YB易制爆/WH危化品/PT普通）+ 分类色。
 * 用于台账列表、档案页、批次表与打印联单。
 */
const props = defineProps({
  /** 分类值 0-4（chem_info.category） */
  category: { type: [Number, String], default: 0 },
  /** 菱形边长（px） */
  size: { type: Number, default: 26 },
  /** 是否带分类浅色底 */
  withBg: { type: Boolean, default: false },
  /** 是否显示品类名称悬浮提示 */
  tooltip: { type: String, default: '' }
})

const cat = computed(() => categoryOf(Number(props.category)))
</script>

<style scoped>
.ghz-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  vertical-align: middle;
}
.ghz-diamond {
  position: absolute;
  inset: 12%;
  transform: rotate(45deg);
  border: 2px solid;
  border-radius: 2px;
}
.ghz-code {
  position: relative;
  font-weight: 800;
  font-family: 'Consolas', monospace;
  letter-spacing: 0.02em;
  line-height: 1;
  user-select: none;
}
</style>
