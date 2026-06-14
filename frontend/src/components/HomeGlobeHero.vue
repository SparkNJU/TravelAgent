<template>
  <section class="home-hero">
    <div class="starfield" aria-hidden="true">
      <span class="stars stars-a"></span>
      <span class="stars stars-b"></span>
      <span class="stars stars-c"></span>
    </div>

    <RouterLink class="hero-brand" to="/" aria-label="TravelMind 首页">
      <img src="/logo.svg" alt="" />
      <span>TravelMind</span>
    </RouterLink>

    <div class="hero-copy">
      
      <h1>Your Travel NeverMind</h1>
      <h1>TravelMind Here</h1>
      
      <form class="plan-search" @submit.prevent="submitCustomPlan" aria-label="AI 旅行计划输入">
        <SvgIcon class="search-icon" name="send" :size="18" />
        <input
          v-model="customQuery"
          type="text"
          placeholder="东京 5 天，美食、购物和城市漫游"
        />
        <div class="search-submit-wrap">
          <Transition name="swap-up" mode="out-in">
            <button
              v-if="showAiButton"
              key="ai"
              class="search-submit ai-btn"
              type="submit"
            >
              <SvgIcon name="sparkles" :size="15" />
              AI生成
            </button>
            <button
              v-else
              key="note"
              class="search-submit note-btn"
              type="button"
              @click="goDiscover"
            >
              <SvgIcon name="search" :size="15" />
              搜索笔记
            </button>
          </Transition>
        </div>
      </form>

      <div class="hero-actions">
        <button class="hero-primary" @click="startAgentPlan">
          <SvgIcon name="sparkles" :size="17" />
          Agent规划
        </button>
        <button class="hero-secondary" @click="$emit('open-explore')">
          <SvgIcon name="globe" :size="17" />
          探索发现
        </button>
      </div>
    </div>

    <div class="globe-stage">
      <div class="globe-aura" aria-hidden="true"></div>
      <div ref="mountRef" class="hero-globe" aria-label="可点击的 3D 旅行地球"></div>
      <button
        v-for="label in labelStates"
        :key="label.id"
        class="globe-label"
        :class="{ visible: label.visible, flip: label.flip, active: label.id === activeCity.id }"
        :style="{ left: `${label.x}%`, top: `${label.y}%` }"
        @click="selectCityById(label.id)"
        @mouseenter="hoverCity(label.id)"
        @mouseleave="hoverCity('')"
      >
        {{ label.name }}
      </button>
    </div>

    <aside v-if="planPopoverOpen" class="city-popover" aria-label="城市推荐方案">
      <button class="popover-close" aria-label="关闭推荐方案" @click="closePlanPopover">
        <SvgIcon name="close" :size="17" />
      </button>

      <div class="popover-cover" :style="{ backgroundImage: `url(${selectedCity.image})` }">
        <span>{{ selectedCity.country }}</span>
      </div>

      <div class="popover-body">
        <p class="popover-kicker">Preset routes</p>
        <h2>{{ selectedCity.name }}</h2>
        <p class="popover-desc">{{ selectedCity.description }}</p>

        <div class="popover-tags">
          <span v-for="tag in selectedCity.tags.slice(0, 4)" :key="tag">{{ tag }}</span>
        </div>

        <div class="popover-meta">
          <span>
            <small>建议</small>
            <strong>{{ selectedCity.days }}</strong>
          </span>
          <span>
            <small>预算</small>
            <strong>{{ selectedCity.budget }}</strong>
          </span>
        </div>

        <div class="preset-list">
          <button
            v-for="plan in presetPlans"
            :key="plan.id"
            class="preset-card"
            type="button"
            @click="emit('start-plan', plan.prompt)"
          >
            <strong>{{ plan.title }}</strong>
            <span>{{ plan.summary }}</span>
            <small>{{ plan.spots.join(' / ') }}</small>
          </button>
        </div>
        <button class="popover-primary" type="button" @click="startAgentPlan">
          <SvgIcon name="sparkles" :size="15" />
          进入 Agent 规划
        </button>
      </div>
    </aside>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import * as THREE from 'three'
import SvgIcon from './SvgIcon.vue'
import { cities } from '../data/travelData'

const emit = defineEmits(['start-plan', 'open-explore'])

const mountRef = ref(null)
const labelStates = ref([])
const selectedCity = ref(cities[0])
const hoveredCityId = ref('')
const router = useRouter()
const customQuery = ref('')
const planPopoverOpen = ref(false)
const showAiButton = ref(true)
let swapTimer = null
const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()

let scene
let camera
let renderer
let world
let outlineMaterial
let animationId = 0
let hotspots = []
let targetRotationY = -2.25
let isDragging = false
let dragStartX = 0
let dragStartRotation = 0

const activeCity = computed(() => {
  if (hoveredCityId.value) {
    return cities.find(city => city.id === hoveredCityId.value) || selectedCity.value
  }
  return selectedCity.value
})

const presetPlans = computed(() => {
  const city = selectedCity.value
  const spots = city.spots?.length ? city.spots : [city.name]
  return [
    {
      id: 'classic',
      title: '经典初访',
      summary: `覆盖${city.name}最有代表性的景点。`,
      spots: spots.slice(0, 3),
      prompt: `帮我规划${city.name}${city.days || '3天'}经典初访路线，重点包含${spots.slice(0, 3).join('、')}`,
    },
    {
      id: 'local',
      title: '在地慢游',
      summary: '把时间留给街区、餐馆、夜景和城市细节。',
      spots: spots.slice(1, 4).length ? spots.slice(1, 4) : spots.slice(0, 3),
      prompt: `帮我规划${city.name}在地慢游路线，偏休闲体验、美食和城市漫游`,
    },
  ]
})

function getCameraDistance(width) {
  if (width < 560) return 8.25
  if (width < 860) return 7.9
  return 7.35
}

function latLngToVector3(lat, lng, radius) {
  const phi = (90 - lat) * Math.PI / 180
  const theta = (lng + 180) * Math.PI / 180
  return new THREE.Vector3(
    -radius * Math.sin(phi) * Math.cos(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.sin(theta),
  )
}

function targetRotationForCity(city) {
  return -((city.lng + 180) * Math.PI / 180) + Math.PI * 0.28
}

function createOutlineTexture(geoJson = null) {
  const canvas = document.createElement('canvas')
  canvas.width = 2048
  canvas.height = 1024
  const ctx = canvas.getContext('2d')

  const toCanvasLngLat = ([lng, lat]) => [
    ((lng + 180) / 360) * canvas.width,
    ((90 - lat) / 180) * canvas.height,
  ]

  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'

  const fallback = [
    [[72, -168], [70, -140], [60, -124], [49, -123], [32, -117], [16, -96], [8, -80], [19, -73], [45, -67], [72, -75], [72, -168]],
    [[12, -81], [8, -70], [-5, -52], [-16, -39], [-34, -54], [-55, -68], [-18, -76], [12, -81]],
    [[72, -10], [60, 10], [56, 40], [65, 80], [70, 130], [55, 160], [22, 118], [8, 104], [22, 78], [8, 45], [30, 30], [42, -5], [72, -10]],
    [[36, -17], [31, 10], [32, 32], [12, 43], [-5, 38], [-34, 20], [-20, 12], [-5, -10], [24, -16], [36, -17]],
    [[-10, 112], [-17, 128], [-15, 145], [-28, 153], [-39, 145], [-35, 115], [-10, 112]],
  ]

  function strokeGeoLine(points) {
    ctx.beginPath()
    let previousX = null
    points.forEach((coord, index) => {
      const [x, y] = toCanvasLngLat(coord)
      if (index === 0 || (previousX !== null && Math.abs(x - previousX) > canvas.width * 0.48)) {
        ctx.moveTo(x, y)
      } else {
        ctx.lineTo(x, y)
      }
      previousX = x
    })
    ctx.stroke()
  }

  function strokeFallbackLine(points) {
    ctx.beginPath()
    points.forEach(([lat, lng], index) => {
      const x = ((lng + 180) / 360) * canvas.width
      const y = ((90 - lat) / 180) * canvas.height
      if (index === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    })
    ctx.stroke()
  }

  function drawFallback(style, width) {
    ctx.strokeStyle = style
    ctx.lineWidth = width
    fallback.forEach(strokeFallbackLine)
  }

  function drawGeo(style, width) {
    if (!geoJson?.features?.length) return
    ctx.strokeStyle = style
    ctx.lineWidth = width
    geoJson.features.forEach((feature) => {
      const geometry = feature.geometry
      if (!geometry) return
      const polygons = geometry.type === 'Polygon'
        ? [geometry.coordinates]
        : geometry.type === 'MultiPolygon'
          ? geometry.coordinates
          : []
      polygons.forEach((polygon) => {
        polygon.forEach((ring) => {
          if (ring?.length) strokeGeoLine(ring)
        })
      })
    })
  }

  if (geoJson?.features?.length) {
    drawGeo('rgba(255, 95, 115, 0.28)', 4.2)
    drawGeo('rgba(255, 95, 115, 0.85)', 1.25)
  } else {
    drawFallback('rgba(255, 95, 115, 0.24)', 7)
    drawFallback('rgba(255, 95, 115, 0.8)', 2.5)
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = renderer?.capabilities?.getMaxAnisotropy?.() || 1
  return texture
}

function createRimShell(radius) {
  return new THREE.Mesh(
    new THREE.SphereGeometry(radius, 96, 96),
    new THREE.ShaderMaterial({
      transparent: true,
      depthWrite: false,
      side: THREE.FrontSide,
      uniforms: {
        rimColor: { value: new THREE.Color('#ff8fa0') },
      },
      vertexShader: `
        varying vec3 vNormal;
        varying vec3 vViewPosition;
        void main() {
          vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
          vViewPosition = -mvPosition.xyz;
          vNormal = normalize(normalMatrix * normal);
          gl_Position = projectionMatrix * mvPosition;
        }
      `,
      fragmentShader: `
        uniform vec3 rimColor;
        varying vec3 vNormal;
        varying vec3 vViewPosition;
        void main() {
          float fresnel = 1.0 - max(dot(normalize(vNormal), normalize(vViewPosition)), 0.0);
          float rim = smoothstep(0.42, 0.98, fresnel);
          gl_FragColor = vec4(rimColor, rim * 0.58);
        }
      `,
    }),
  )
}

function createLatLine(lat, radius) {
  const points = []
  for (let lng = -180; lng <= 180; lng += 3) {
    points.push(latLngToVector3(lat, lng, radius))
  }
  return new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(points),
    new THREE.LineBasicMaterial({
      color: '#ff2442',
      transparent: true,
      opacity: Math.abs(lat) === 0 ? 0.34 : 0.18,
      depthWrite: false,
    }),
  )
}

function createLngLine(lng, radius) {
  const points = []
  for (let lat = -84; lat <= 84; lat += 3) {
    points.push(latLngToVector3(lat, lng, radius))
  }
  return new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(points),
    new THREE.LineBasicMaterial({
      color: '#ff2442',
      transparent: true,
      opacity: lng % 90 === 0 ? 0.24 : 0.14,
      depthWrite: false,
    }),
  )
}

function createGraticule(radius) {
  const group = new THREE.Group()
  for (let lat = -60; lat <= 60; lat += 20) group.add(createLatLine(lat, radius))
  for (let lng = -180; lng < 180; lng += 20) group.add(createLngLine(lng, radius))
  return group
}

function createHotspot(city) {
  const group = new THREE.Group()
  group.position.copy(latLngToVector3(city.lat, city.lng, 2.095))
  group.userData.cityId = city.id

  const halo = new THREE.Mesh(
    new THREE.SphereGeometry(0.09, 18, 18),
    new THREE.MeshBasicMaterial({ color: '#ff7a8d', transparent: true, opacity: 0.16, depthWrite: false }),
  )
  const dot = new THREE.Mesh(
    new THREE.SphereGeometry(0.043, 18, 18),
    new THREE.MeshBasicMaterial({ color: '#ff5f73', transparent: true, opacity: 1, depthWrite: false }),
  )
  halo.userData.cityId = city.id
  dot.userData.cityId = city.id
  group.add(halo)
  group.add(dot)
  return group
}

function updateHotspotDepth() {
  if (!world || !camera) return
  const cameraPosition = camera.position.clone()
  const nextLabels = []
  hotspots.forEach((hotspot) => {
    const city = cities.find(item => item.id === hotspot.userData.cityId)
    if (!city) return

    const worldPosition = hotspot.getWorldPosition(new THREE.Vector3())
    const normal = worldPosition.clone().normalize()
    const cameraDirection = cameraPosition.clone().sub(worldPosition).normalize()
    const facing = normal.dot(cameraDirection)
    const isVisible = facing > 0.08
    const isActive = city.id === activeCity.value.id
    const opacity = isVisible ? 1 : 0.06

    hotspot.scale.setScalar(isActive ? 1.48 : 1)
    hotspot.children.forEach((child, index) => {
      child.material.opacity = index === 0 ? opacity * (isActive ? 0.32 : 0.18) : opacity
      child.material.color.set(isActive ? '#ff2442' : '#ff7a8d')
    })

    const projected = worldPosition.clone().project(camera)
    const x = (projected.x * 0.5 + 0.5) * 100
    const y = (-projected.y * 0.5 + 0.5) * 100
    nextLabels.push({
      id: city.id,
      name: city.name,
      x,
      y,
      flip: x > 68,
      visible: isVisible && projected.z < 1 && x > 4 && x < 96 && y > 4 && y < 96,
    })
  })
  labelStates.value = nextLabels
}

function buildScene() {
  const mount = mountRef.value
  if (!mount) return

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(36, mount.clientWidth / mount.clientHeight, 0.1, 100)
  camera.position.set(0, 0.02, getCameraDistance(mount.clientWidth))
  camera.lookAt(0, 0, 0)

  renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, preserveDrawingBuffer: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(mount.clientWidth, mount.clientHeight)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  mount.appendChild(renderer.domElement)

  world = new THREE.Group()
  world.rotation.y = targetRotationY
  scene.add(world)

  world.add(createRimShell(2.012))
  world.add(createGraticule(2.055))

  outlineMaterial = new THREE.MeshBasicMaterial({
    map: createOutlineTexture(),
    transparent: true,
    opacity: 1,
    side: THREE.FrontSide,
    depthWrite: false,
  })
  world.add(new THREE.Mesh(new THREE.SphereGeometry(2.028, 128, 128), outlineMaterial))

  fetch('/data/ne_110m_land.geojson')
    .then(response => (response.ok ? response.json() : null))
    .then((geoJson) => {
      if (!geoJson) return
      const texture = createOutlineTexture(geoJson)
      outlineMaterial.map?.dispose()
      outlineMaterial.map = texture
      outlineMaterial.needsUpdate = true
    })
    .catch(() => {})

  hotspots = cities.map((city) => {
    const hotspot = createHotspot(city)
    world.add(hotspot)
    return hotspot
  })

  scene.add(new THREE.AmbientLight('#ffffff', 1.36))
  const redLight = new THREE.PointLight('#ffb3bf', 4.2, 8)
  redLight.position.set(-2.8, -1.1, 2.8)
  scene.add(redLight)

  renderer.domElement.addEventListener('pointerdown', handlePointerDown)
  renderer.domElement.addEventListener('pointermove', handlePointerMove)
  renderer.domElement.addEventListener('pointerup', handlePointerUp)
  renderer.domElement.addEventListener('pointerleave', handlePointerLeave)
  window.addEventListener('resize', handleResize)
  focusCity(selectedCity.value, true)
  animate()
}

function animate() {
  animationId = requestAnimationFrame(animate)
  if (world) {
    if (!isDragging) {
      world.rotation.y += (targetRotationY - world.rotation.y) * 0.035
      world.rotation.y += 0.0009
      targetRotationY += 0.0009
    }
    updateHotspotDepth()
  }
  renderer?.render(scene, camera)
}

function handleResize() {
  const mount = mountRef.value
  if (!mount || !camera || !renderer) return
  camera.aspect = mount.clientWidth / mount.clientHeight
  camera.position.z = getCameraDistance(mount.clientWidth)
  camera.lookAt(0, 0, 0)
  camera.updateProjectionMatrix()
  renderer.setSize(mount.clientWidth, mount.clientHeight)
}

function intersectHotspot(event) {
  if (!renderer || !camera) return null
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const dotMeshes = hotspots.flatMap(hotspot => hotspot.children)
  const intersects = raycaster.intersectObjects(dotMeshes)
  if (!intersects.length) return null
  return cities.find(item => item.id === intersects[0].object.userData.cityId) || null
}

function handlePointerDown(event) {
  isDragging = true
  dragStartX = event.clientX
  dragStartRotation = world?.rotation.y || targetRotationY
  renderer?.domElement?.setPointerCapture?.(event.pointerId)
}

function handlePointerMove(event) {
  if (isDragging && world) {
    const delta = event.clientX - dragStartX
    world.rotation.y = dragStartRotation + delta * 0.006
    targetRotationY = world.rotation.y
    return
  }
  const city = intersectHotspot(event)
  hoveredCityId.value = city?.id || ''
}

function handlePointerUp(event) {
  const moved = Math.abs(event.clientX - dragStartX)
  const city = moved < 6 ? intersectHotspot(event) : null
  isDragging = false
  renderer?.domElement?.releasePointerCapture?.(event.pointerId)
  if (city) selectCity(city)
}

function handlePointerLeave() {
  hoveredCityId.value = ''
  isDragging = false
}

function hoverCity(cityId) {
  hoveredCityId.value = cityId
}

function focusCity(city, immediate = false) {
  targetRotationY = targetRotationForCity(city)
  if (immediate && world) world.rotation.y = targetRotationY
}

function selectCity(city) {
  selectedCity.value = city
  hoveredCityId.value = ''
  planPopoverOpen.value = true
  focusCity(city)
}

function selectCityById(cityId) {
  const city = cities.find(item => item.id === cityId)
  if (city) selectCity(city)
}

function closePlanPopover() {
  planPopoverOpen.value = false
}

function buildCityPrompt(city) {
  const tags = city.tags?.length ? `，偏好${city.tags.join('、')}` : ''
  const spots = city.spots?.length ? `，重点包含${city.spots.slice(0, 3).join('、')}` : ''
  return `帮我规划${city.name}${city.days || '3天'}旅行${tags}${spots}`
}

function startAgentPlan() {
  const prompt = buildCityPrompt(selectedCity.value)
  emit('start-plan', prompt)
}

function submitCustomPlan() {
  const query = customQuery.value.trim()
  emit('start-plan', query)
}

function goDiscover() {
  const query = customQuery.value.trim()
  router.push({ path: '/discover', query: query ? { q: query } : {} })
}

onMounted(() => {
  swapTimer = setInterval(() => { showAiButton.value = !showAiButton.value }, 10000)
  nextTick(buildScene)
})

onBeforeUnmount(() => {
  if (swapTimer) clearInterval(swapTimer)
  cancelAnimationFrame(animationId)
  window.removeEventListener('resize', handleResize)
  renderer?.domElement?.removeEventListener('pointerdown', handlePointerDown)
  renderer?.domElement?.removeEventListener('pointermove', handlePointerMove)
  renderer?.domElement?.removeEventListener('pointerup', handlePointerUp)
  renderer?.domElement?.removeEventListener('pointerleave', handlePointerLeave)
  renderer?.dispose()
  if (renderer?.domElement?.parentNode) {
    renderer.domElement.parentNode.removeChild(renderer.domElement)
  }
})
</script>

<style scoped>
.home-hero {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-rows: auto minmax(420px, 1fr);
  justify-items: center;
  min-height: 100vh;
  padding: clamp(72px, 8vh, 100px) clamp(18px, 3.5vw, 48px) clamp(20px, 3vh, 32px);
  overflow: hidden;
  background:
    radial-gradient(circle at 51% 44%, rgba(255, 36, 66, 0.18), transparent 28%),
    radial-gradient(circle at 15% 24%, rgba(255, 36, 66, 0.13), transparent 23%),
    radial-gradient(circle at 84% 22%, rgba(255, 95, 115, 0.12), transparent 24%),
    radial-gradient(circle at 50% 88%, rgba(255, 36, 66, 0.12), transparent 42%),
    linear-gradient(180deg, #fffafa 0%, #ffffff 42%, #fff4f6 100%);
  color: var(--color-title);
}

.home-hero::before {
  content: '';
  position: absolute;
  inset: 13% 7% auto;
  z-index: 0;
  height: 56%;
  border: 1px solid rgba(255, 36, 66, 0.08);
  border-radius: 50%;
  transform: rotate(-8deg);
  box-shadow: 0 0 42px rgba(255, 36, 66, 0.08), inset 0 0 52px rgba(255, 36, 66, 0.05);
  pointer-events: none;
}

.starfield {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  background:
    radial-gradient(circle at 16% 16%, rgba(255, 36, 66, 0.34) 0 1px, transparent 1.8px),
    radial-gradient(circle at 40% 11%, rgba(255, 36, 66, 0.22) 0 1px, transparent 2px),
    radial-gradient(circle at 63% 18%, rgba(255, 95, 115, 0.28) 0 1px, transparent 2px),
    radial-gradient(circle at 82% 12%, rgba(255, 36, 66, 0.2) 0 1px, transparent 2px),
    radial-gradient(circle at 8% 56%, rgba(255, 36, 66, 0.18) 0 1px, transparent 2px),
    radial-gradient(circle at 92% 62%, rgba(255, 36, 66, 0.18) 0 1px, transparent 2px),
    radial-gradient(circle at 31% 86%, rgba(255, 36, 66, 0.22) 0 1px, transparent 2px),
    radial-gradient(circle at 73% 88%, rgba(255, 95, 115, 0.2) 0 1px, transparent 2px);
  animation: starfield-breathe 7s ease-in-out infinite;
}

.starfield::before,
.starfield::after {
  content: '';
  position: absolute;
  inset: -18%;
  background:
    radial-gradient(circle at 5% 12%, rgba(255, 36, 66, 0.2) 0 1px, transparent 1.8px),
    radial-gradient(circle at 13% 47%, rgba(255, 36, 66, 0.18) 0 1px, transparent 2px),
    radial-gradient(circle at 21% 72%, rgba(255, 95, 115, 0.22) 0 1px, transparent 2px),
    radial-gradient(circle at 36% 29%, rgba(255, 36, 66, 0.24) 0 1px, transparent 2px),
    radial-gradient(circle at 49% 63%, rgba(255, 36, 66, 0.16) 0 1px, transparent 2px),
    radial-gradient(circle at 59% 18%, rgba(255, 95, 115, 0.2) 0 1px, transparent 2px),
    radial-gradient(circle at 69% 78%, rgba(255, 36, 66, 0.18) 0 1px, transparent 2px),
    radial-gradient(circle at 81% 38%, rgba(255, 36, 66, 0.22) 0 1px, transparent 2px),
    radial-gradient(circle at 94% 68%, rgba(255, 95, 115, 0.2) 0 1px, transparent 2px);
  opacity: 0.88;
  animation: star-drift 24s linear infinite;
}

.starfield::after {
  background:
    linear-gradient(115deg, transparent 0 42%, rgba(255, 36, 66, 0.26) 48%, transparent 56%),
    linear-gradient(115deg, transparent 0 62%, rgba(255, 95, 115, 0.2) 66%, transparent 71%),
    radial-gradient(circle at 22% 34%, rgba(255, 36, 66, 0.12) 0 1px, transparent 2px),
    radial-gradient(circle at 77% 24%, rgba(255, 36, 66, 0.16) 0 1px, transparent 2px),
    radial-gradient(circle at 62% 72%, rgba(255, 36, 66, 0.14) 0 1px, transparent 2px);
  filter: blur(0.2px);
  opacity: 0.46;
  transform: translateX(-18%);
  animation: meteor-sweep 9.5s ease-in-out infinite;
}

.stars {
  position: absolute;
  width: 2px;
  height: 2px;
  border-radius: 50%;
  background: rgba(255, 36, 66, 0.62);
  box-shadow:
    7vw 10vh rgba(255, 36, 66, 0.26),
    10vw 81vh rgba(255, 36, 66, 0.18),
    15vw 37vh rgba(255, 36, 66, 0.22),
    23vw 16vh rgba(255, 36, 66, 0.3),
    34vw 72vh rgba(255, 36, 66, 0.22),
    44vw 28vh rgba(255, 36, 66, 0.34),
    52vw 64vh rgba(255, 36, 66, 0.24),
    61vw 12vh rgba(255, 36, 66, 0.28),
    70vw 82vh rgba(255, 36, 66, 0.2),
    80vw 34vh rgba(255, 36, 66, 0.3),
    91vw 69vh rgba(255, 36, 66, 0.24);
  animation: star-pulse 3.8s ease-in-out infinite, star-float 18s linear infinite;
}

.stars-b {
  left: 4vw;
  top: 11vh;
  opacity: 0.72;
  transform: scale(1.4);
  animation-delay: -1.2s;
  animation-duration: 4.6s, 24s;
}

.stars-c {
  right: 6vw;
  top: 3vh;
  opacity: 0.5;
  transform: scale(0.8);
  animation-delay: -2.4s;
  animation-duration: 3.2s, 30s;
}

.hero-brand {
  position: absolute;
  left: clamp(18px, 3.4vw, 46px);
  top: clamp(18px, 3vh, 32px);
  z-index: 9;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 8px 13px 8px 9px;
  border: 1px solid rgba(255, 36, 66, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: #ff2442;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 17px;
  font-weight: 800;
  text-decoration: none;
  box-shadow: 0 14px 34px rgba(255, 36, 66, 0.12);
}

.hero-brand img {
  width: 26px;
  height: 26px;
}

.hero-copy {
  position: relative;
  z-index: 3;
  display: grid;
  justify-items: center;
  width: min(1180px, 100%);
  gap: 16px;
  text-align: center;
}

.hero-kicker {
  margin: 0;
  color: rgba(255, 36, 66, 0.76);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-copy h1 {
  max-width: min(1140px, calc(100vw - 60px));
  margin: 0;
  color: #ff2442;
  font-family: Georgia, 'Times New Roman', 'Noto Serif SC', serif;
  font-size: clamp(38px, 5.5vw, 76px);
  font-weight: 950;
  line-height: 0.98;
  text-shadow: 0 16px 42px rgba(255, 36, 66, 0.16);
}

.hero-subtitle {
  max-width: 680px;
  margin: 0;
  color: rgba(133, 31, 48, 0.78);
  font-size: 16px;
  line-height: 1.75;
}

.plan-search {
  display: flex;
  align-items: center;
  width: min(660px, calc(100vw - 56px));
  height: 52px;
  margin-top: 16px;
  padding: 0 8px 0 20px;
  border: 1px solid rgba(255, 36, 66, 0.28);
  border-radius: 999px;
  background: #ffffff;
  color: #ff2442;
  box-shadow: 0 18px 46px rgba(255, 36, 66, 0.14);
}

.search-icon {
  color: #ff2442;
}

.plan-search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 14px;
  border: 0;
  outline: 0;
  background: transparent;
  color: #3b1118;
  font-size: 15px;
}

.plan-search input::placeholder {
  color: rgba(133, 31, 48, 0.48);
}

.search-submit-wrap {
  position: relative;
  overflow: hidden;
  height: 38px;
  min-width: 80px;
}

.search-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 38px;
  min-width: 80px;
  padding: 0 16px;
  border-radius: 999px;
  border: none;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  white-space: nowrap;
}

/* AI生成：红底白字 */
.search-submit.ai-btn {
  background: #ff2442;
  color: #ffffff;
  box-shadow: 0 12px 28px rgba(255, 36, 66, 0.28);
}

/* 搜索笔记：白底红字 */
.search-submit.note-btn {
  background: #ffffff;
  color: #ff2442;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.08);
  border: 1.5px solid rgba(255, 36, 66, 0.18);
}

/* ── Swap-up transition ── */
.swap-up-enter-active,
.swap-up-leave-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}
.swap-up-enter-from {
  opacity: 0;
  transform: translateY(16px);
}
.swap-up-leave-to {
  opacity: 0;
  transform: translateY(-16px);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.hero-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 900;
  transition: transform 0.16s ease;
}

.hero-actions button:hover,
.preset-card:hover {
  transform: translateY(-2px);
}

.hero-primary {
  background: #ff2442;
  color: #ffffff;
  box-shadow: 0 16px 36px rgba(255, 36, 66, 0.28);
}

.hero-secondary {
  border: 1px solid rgba(255, 36, 66, 0.36);
  background: #ffffff;
  color: #ff2442;
  box-shadow: 0 14px 32px rgba(255, 36, 66, 0.12);
}

.globe-stage {
  position: relative;
  z-index: 2;
  width: min(92vw, 850px);
  height: clamp(440px, 54vh, 600px);
  margin-top: clamp(14px, 2.5vh, 28px);
}

.globe-aura {
  position: absolute;
  inset: 7% 4%;
  border-radius: 50%;
  background:
    radial-gradient(circle, rgba(255, 36, 66, 0.22), transparent 58%),
    radial-gradient(circle, rgba(255, 36, 66, 0.1), transparent 72%);
  filter: blur(18px);
  animation: aura-pulse 5.5s ease-in-out infinite;
}

.hero-globe {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  cursor: grab;
}

.hero-globe:active {
  cursor: grabbing;
}

.globe-label {
  position: absolute;
  z-index: 4;
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(255, 36, 66, 0.38);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  color: #ff5f73;
  font-family: var(--font-family);
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
  box-shadow: 0 10px 26px rgba(255, 95, 115, 0.14);
  opacity: 0;
  pointer-events: none;
  transform: translate(12px, -50%) scale(0.96);
  transition: opacity 0.14s ease, transform 0.14s ease, background 0.14s ease;
}

.globe-label.visible {
  opacity: 1;
  pointer-events: auto;
  transform: translate(12px, -50%) scale(1);
}

.globe-label.active {
  background: #ff5f73;
  color: #ffffff;
}

.globe-label.flip {
  transform: translate(calc(-100% - 12px), -50%) scale(0.96);
}

.globe-label.flip.visible {
  transform: translate(calc(-100% - 12px), -50%) scale(1);
}

.city-popover {
  position: fixed;
  right: 28px;
  top: 50%;
  z-index: 20;
  width: min(360px, calc(100vw - 44px));
  max-height: min(680px, calc(100vh - 56px));
  overflow: hidden;
  border: 1px solid rgba(255, 36, 66, 0.18);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 24px 72px rgba(255, 36, 66, 0.2);
  transform: translateY(-50%);
  backdrop-filter: blur(18px);
}

.popover-close {
  position: absolute;
  right: 12px;
  top: 12px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  color: #ff2442;
  box-shadow: 0 10px 22px rgba(59, 17, 24, 0.12);
}

.popover-cover {
  position: relative;
  min-height: 136px;
  background-position: center;
  background-size: cover;
}

.popover-cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.02), rgba(70, 0, 12, 0.42));
}

.popover-cover span {
  position: absolute;
  left: 15px;
  bottom: 13px;
  z-index: 1;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #3b1118;
  font-size: 12px;
  font-weight: 900;
}

.popover-body {
  display: grid;
  gap: 12px;
  max-height: calc(min(680px, 100vh - 56px) - 136px);
  padding: 18px;
  overflow-y: auto;
}

.popover-kicker {
  margin: 0;
  color: rgba(255, 36, 66, 0.72);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.popover-body h2 {
  margin: 0;
  color: #3b1118;
  font-family: Georgia, 'Times New Roman', 'Noto Serif SC', serif;
  font-size: 32px;
  line-height: 1.05;
}

.popover-desc {
  margin: 0;
  color: rgba(61, 20, 28, 0.76);
  font-size: 13px;
  line-height: 1.65;
}

.popover-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.popover-tags span {
  padding: 6px 9px;
  border-radius: 999px;
  background: #fff1f3;
  color: #ff2442;
  font-size: 12px;
  font-weight: 900;
}

.popover-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.popover-meta span {
  display: grid;
  gap: 2px;
  padding: 10px;
  border: 1px solid rgba(255, 36, 66, 0.12);
  border-radius: 13px;
  background: rgba(255, 250, 250, 0.8);
}

.popover-meta small {
  color: rgba(133, 31, 48, 0.54);
  font-size: 11px;
  font-weight: 800;
}

.popover-meta strong {
  overflow: hidden;
  color: #3b1118;
  font-size: 13px;
  font-weight: 950;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preset-list {
  display: grid;
  gap: 9px;
}

.preset-card {
  display: grid;
  gap: 7px;
  width: 100%;
  padding: 13px;
  border: 1px solid rgba(255, 36, 66, 0.14);
  border-radius: 15px;
  background: #fffafa;
  color: #3b1118;
  text-align: left;
  box-shadow: 0 10px 24px rgba(255, 36, 66, 0.08);
  transition: transform 0.16s ease, border-color 0.16s ease, box-shadow 0.16s ease;
}

.preset-card:hover {
  border-color: rgba(255, 36, 66, 0.36);
  box-shadow: 0 14px 30px rgba(255, 36, 66, 0.13);
}

.preset-card strong {
  font-size: 14px;
  font-weight: 950;
}

.preset-card span {
  color: rgba(61, 20, 28, 0.72);
  font-size: 12px;
  line-height: 1.55;
}

.preset-card small {
  color: rgba(255, 36, 66, 0.86);
  font-size: 12px;
  font-weight: 800;
  line-height: 1.45;
}

.popover-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  min-height: 42px;
  border-radius: 999px;
  background: #ff2442;
  color: #ffffff;
  font-size: 13px;
  font-weight: 950;
  box-shadow: 0 14px 30px rgba(255, 36, 66, 0.24);
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.popover-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 18px 34px rgba(255, 36, 66, 0.28);
}

@media (max-width: 860px) {
  .home-hero {
    grid-template-rows: auto minmax(360px, 1fr);
    padding: 58px 16px 16px;
    overflow-y: auto;
  }

  .hero-brand {
    left: 16px;
    top: 14px;
    min-height: 38px;
    font-size: 15px;
  }

  .hero-brand img {
    width: 24px;
    height: 24px;
  }

  .hero-copy {
    gap: 12px;
  }

  .hero-copy h1 {
    max-width: calc(100vw - 32px);
    font-size: clamp(30px, 10vw, 48px);
  }

  .hero-subtitle {
    max-width: 360px;
    font-size: 14px;
  }

  .plan-search {
    width: 100%;
    height: 48px;
  }

  .search-submit-wrap {
    min-width: 68px;
  }

  .search-submit {
    min-width: 68px;
    padding: 0 12px;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions button {
    flex: 1 1 150px;
  }

  .globe-stage {
    width: 100%;
    height: clamp(380px, 50vh, 460px);
  }

  .globe-label {
    display: none;
  }

  .city-popover {
    right: 12px;
    left: 12px;
    top: auto;
    bottom: 12px;
    width: auto;
    max-height: min(74vh, 620px);
    transform: none;
    border-radius: 20px;
  }
}

@media (max-width: 520px) {
  .hero-copy h1 {
    font-size: clamp(26px, 9vw, 38px);
  }

  .plan-search {
    height: 44px;
    padding-left: 14px;
  }

  .plan-search input {
    font-size: 13px;
  }

  .search-submit-wrap {
    min-width: 48px;
  }

  .search-submit {
    min-width: 48px;
    width: 48px;
    padding: 0;
    font-size: 0;
  }

  .hero-actions button {
    flex-basis: 100%;
    min-height: 38px;
  }
}

@keyframes star-drift {
  from { transform: translate3d(0, 0, 0); }
  to { transform: translate3d(-34px, 24px, 0); }
}

@keyframes star-pulse {
  0%, 100% { opacity: 0.42; }
  50% { opacity: 0.9; }
}

@keyframes starfield-breathe {
  0%, 100% {
    opacity: 0.72;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.015);
  }
}

@keyframes star-float {
  from { translate: 0 0; }
  to { translate: -18px 14px; }
}

@keyframes meteor-sweep {
  0%, 62% {
    opacity: 0;
    transform: translateX(-26%) translateY(10%);
  }
  70% { opacity: 0.5; }
  100% {
    opacity: 0;
    transform: translateX(22%) translateY(-8%);
  }
}

@keyframes aura-pulse {
  0%, 100% {
    opacity: 0.72;
    transform: scale(0.98);
  }
  50% {
    opacity: 1;
    transform: scale(1.04);
  }
}
</style>
