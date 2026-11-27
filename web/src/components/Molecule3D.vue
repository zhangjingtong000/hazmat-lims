<template>
  <div ref="hostRef" class="mol3d" />
</template>

<script setup>
/**
 * 三维分子模型（真实物质球棍模型）
 * 原子球 + 化学键圆柱 + 玻璃质感材质 + UnrealBloom 辉光 + 可拖拽旋转。
 * 原子配色遵循 CPK 惯例（C 灰 / H 白 / O 红 / S 黄 / Na 紫）。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'

const props = defineProps({
  molecule: { type: Object, required: true },
  autoRotate: { type: Boolean, default: true }
})

const hostRef = ref(null)
let renderer, scene, camera, controls, composer, raf
const disposables = []

const CPK = { C: 0x8c8c8c, H: 0xf0f0f0, O: 0xff4d4d, S: 0xe8d24a, Na: 0xa55cf2, N: 0x5a7bff, Cl: 0x3ad46a }
const RADIUS = { C: 0.38, H: 0.22, O: 0.34, S: 0.46, Na: 0.5, N: 0.36, Cl: 0.44 }

function buildMolecule(mol) {
  const group = new THREE.Group()
  const scale = mol.scale || 1
  const atomGeo = new THREE.SphereGeometry(1, 32, 24)
  disposables.push(atomGeo)

  const positions = mol.atoms.map(a => new THREE.Vector3(a.x * scale, a.y * scale, a.z * scale))
  // 原子
  mol.atoms.forEach((a, i) => {
    const color = CPK[a.el] ?? 0x8c8c8c
    const mat = new THREE.MeshPhysicalMaterial({
      color, roughness: 0.22, metalness: 0.15,
      clearcoat: 1, clearcoatRoughness: 0.18,
      emissive: color, emissiveIntensity: a.el === 'H' ? 0.05 : 0.16
    })
    disposables.push(mat)
    const mesh = new THREE.Mesh(atomGeo, mat)
    mesh.position.copy(positions[i])
    mesh.scale.setScalar(RADIUS[a.el] ?? 0.34)
    group.add(mesh)
  })

  // 化学键
  const bondMat = new THREE.MeshStandardMaterial({ color: 0x9fb8cc, roughness: 0.45, metalness: 0.35 })
  disposables.push(bondMat)
  for (const [i, j] of mol.bonds) {
    const a = positions[i], b = positions[j]
    const dir = new THREE.Vector3().subVectors(b, a)
    const len = dir.length()
    const geo = new THREE.CylinderGeometry(0.075, 0.075, len, 14)
    disposables.push(geo)
    const cyl = new THREE.Mesh(geo, bondMat)
    cyl.position.copy(a).addScaledVector(dir, 0.5)
    cyl.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.clone().normalize())
    group.add(cyl)
  }
  // 质心归零，保证任何分子都居中显示
  const box = new THREE.Box3().setFromObject(group)
  const center = box.getCenter(new THREE.Vector3())
  group.children.forEach(c => c.position.sub(center))
  return group
}

let currentModel = null

/** 自适应取景：竖长/扁宽容器都按较短边的视场算距离，保证分子完整入画 */
function frameModel(model) {
  currentModel = model || currentModel
  if (!currentModel || !camera) return
  const sphere = new THREE.Box3().setFromObject(currentModel).getBoundingSphere(new THREE.Sphere())
  const r = sphere.radius || 3
  const vFov = camera.fov * Math.PI / 180
  const hFov = 2 * Math.atan(Math.tan(vFov / 2) * camera.aspect)
  const dist = (r / Math.sin(Math.min(vFov, hFov) / 2)) * 1.12
  camera.position.set(0, r * 0.26, dist)
  camera.lookAt(0, 0, 0)
  controls.target.set(0, 0, 0)
  controls.update()
}

function init() {
  const host = hostRef.value
  if (!host) return
  const w = host.clientWidth || 300
  const h = host.clientHeight || 380

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(w, h)
  renderer.setClearColor(0x000000, 0)
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.15
  host.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(42, w / h, 0.1, 100)
  camera.position.set(0, 1.6, 7.2)

  // 光照：冷青主光 + 暖金补光，营造实验室冷调
  scene.add(new THREE.AmbientLight(0x3a5570, 1.6))
  const key = new THREE.DirectionalLight(0xdff1ff, 2.0)
  key.position.set(4, 6, 6)
  scene.add(key)
  const cyan = new THREE.PointLight(0x5ac8fa, 26, 24)
  cyan.position.set(-5, 2.5, 3)
  scene.add(cyan)
  const gold = new THREE.PointLight(0xe8b33a, 16, 22)
  gold.position.set(4.5, -2.5, -2.5)
  scene.add(gold)

  const model = buildMolecule(props.molecule)
  scene.add(model)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.enablePan = false
  controls.enableZoom = false
  controls.autoRotate = props.autoRotate
  controls.autoRotateSpeed = 0.9
  frameModel(model)

  composer = new EffectComposer(renderer)
  composer.setSize(w, h)
  composer.addPass(new RenderPass(scene, camera))
  const bloom = new UnrealBloomPass(new THREE.Vector2(w, h), 0.72, 0.62, 0.82)
  composer.addPass(bloom)
  disposables.push(bloom)

  const loop = () => {
    controls.update()
    composer.render()
    raf = requestAnimationFrame(loop)
  }
  loop()

  window.addEventListener('resize', onResize)
}

function onResize() {
  const host = hostRef.value
  if (!host || !renderer) return
  const w = host.clientWidth || 300
  const h = host.clientHeight || 380
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
  composer.setSize(w, h)
  frameModel()
}

onMounted(init)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (raf) cancelAnimationFrame(raf)
  controls?.dispose()
  disposables.forEach(d => d.dispose?.())
  scene?.traverse(o => {
    if (o.isMesh) {
      o.geometry?.dispose?.()
      if (Array.isArray(o.material)) o.material.forEach(m => m.dispose?.())
      else o.material?.dispose?.()
    }
  })
  renderer?.dispose()
  renderer?.domElement?.remove()
})

watch(() => props.molecule, (cur, prev) => {
  // 仅在分子真正切换时重建（避免轮询导致的重复重建与镜头跳动）
  if (!scene || !renderer || cur === prev) return
  const old = scene.children.filter(c => c.isGroup)
  old.forEach(g => scene.remove(g))
  const next = buildMolecule(cur)
  scene.add(next)
  frameModel(next)
})
</script>

<style scoped>
.mol3d { width: 100%; height: 100%; cursor: grab; }
.mol3d:active { cursor: grabbing; }
</style>
