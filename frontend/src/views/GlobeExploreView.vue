<template>
  <div class="globe-page">
    <aside class="explore-panel left-panel">
      <div class="panel-brand">
        <p>3D Destination Explorer</p>
        <h1>在地球上挑一个下一站</h1>
      </div>

      <div class="city-search">
        <SvgIcon name="search" :size="15" />
        <input v-model="keyword" placeholder="搜索城市，例如：成都、东京" />
      </div>

      <div class="city-list">
        <button
          v-for="city in filteredCities"
          :key="city.id"
          :class="['city-row', { active: city.id === selectedCity.id }]"
          @click="selectCity(city)"
        >
          <span class="city-dot"></span>
          <span>
            <strong>{{ city.name }}</strong>
            <small>{{ city.country }} · {{ city.days }}</small>
          </span>
        </button>
      </div>

      <button class="back-discover" @click="router.push('/')">
        返回发现页
      </button>
    </aside>

    <main class="globe-stage">
      <div ref="globeMount" class="globe-canvas" aria-label="3D 全球目的地地球"></div>
      <div class="stage-caption">
        <span>点击红色热点选择城市</span>
        <strong>{{ selectedCity.name }}</strong>
      </div>
    </main>

    <aside class="explore-panel city-panel">
      <div class="city-hero" :style="{ backgroundImage: `url(${selectedCity.image})` }">
        <span>{{ selectedCity.country }}</span>
      </div>

      <div class="city-content">
        <p class="city-kicker">当前目的地</p>
        <h2>{{ selectedCity.name }}</h2>
        <p class="city-desc">{{ selectedCity.description }}</p>

        <div class="tag-list">
          <span v-for="tag in selectedCity.tags" :key="tag">{{ tag }}</span>
        </div>

        <div class="info-grid">
          <div>
            <small>推荐季节</small>
            <strong>{{ selectedCity.bestSeason }}</strong>
          </div>
          <div>
            <small>推荐天数</small>
            <strong>{{ selectedCity.days }}</strong>
          </div>
          <div>
            <small>预算等级</small>
            <strong>{{ selectedCity.budget }}</strong>
          </div>
        </div>

        <section class="spot-section">
          <h3>代表景点</h3>
          <div class="spot-list">
            <span v-for="spot in selectedCity.spots" :key="spot">{{ spot }}</span>
          </div>
        </section>

        <button class="plan-city" @click="startPlanning">
          <SvgIcon name="sparkles" :size="16" />
          开始规划 {{ selectedCity.name }}
        </button>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as THREE from 'three'
import SvgIcon from '../components/SvgIcon.vue'
import { cities } from '../data/travelData'

const router = useRouter()
const globeMount = ref(null)
const keyword = ref('')
const selectedCity = ref(cities[0])

let scene
let camera
let renderer
let world
let hotspots = []
let animationId = 0
let targetRotationY = 0
const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()

const filteredCities = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return cities
  return cities.filter(city => [
    city.name,
    city.country,
    city.description,
    ...city.tags,
    ...city.spots,
  ].some(item => String(item).toLowerCase().includes(q)))
})

function latLngToVector3(lat, lng, radius) {
  const phi = (90 - lat) * Math.PI / 180
  const theta = (lng + 180) * Math.PI / 180
  return new THREE.Vector3(
    -radius * Math.sin(phi) * Math.cos(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.sin(theta),
  )
}

const continentOutlines = [
  {
    name: 'North America',
    points: [
      [72, -168], [70, -140], [60, -124], [49, -123], [32, -117],
      [16, -96], [8, -80], [19, -73], [30, -81], [45, -67],
      [58, -60], [72, -75], [78, -110], [72, -168],
    ],
  },
  {
    name: 'South America',
    points: [
      [12, -81], [8, -70], [-5, -52], [-16, -39], [-34, -54],
      [-55, -68], [-52, -75], [-18, -76], [2, -79], [12, -81],
    ],
  },
  {
    name: 'Eurasia',
    points: [
      [72, -10], [60, 10], [56, 40], [65, 80], [70, 130],
      [55, 160], [35, 140], [22, 118], [8, 104], [22, 78],
      [8, 45], [30, 30], [36, 10], [42, -5], [55, -10], [72, -10],
    ],
  },
  {
    name: 'Africa',
    points: [
      [36, -17], [31, 10], [32, 32], [12, 43], [-5, 38],
      [-34, 20], [-35, 18], [-20, 12], [-5, -10], [10, -16],
      [24, -16], [36, -17],
    ],
  },
  {
    name: 'Australia',
    points: [
      [-10, 112], [-17, 128], [-15, 145], [-28, 153], [-39, 145],
      [-35, 115], [-22, 113], [-10, 112],
    ],
  },
  {
    name: 'Greenland',
    points: [
      [82, -52], [75, -20], [65, -38], [60, -52], [67, -70],
      [77, -72], [82, -52],
    ],
  },
]

function createOutlineTexture(geoJson = null) {
  const canvas = document.createElement('canvas')
  canvas.width = 2048
  canvas.height = 1024
  const ctx = canvas.getContext('2d')

  const toCanvasLatLng = ([lat, lng]) => [
    ((lng + 180) / 360) * canvas.width,
    ((90 - lat) / 180) * canvas.height,
  ]
  const toCanvasLngLat = ([lng, lat]) => [
    ((lng + 180) / 360) * canvas.width,
    ((90 - lat) / 180) * canvas.height,
  ]

  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'

  function drawFallbackOutlines(style, width) {
    ctx.strokeStyle = style
    ctx.lineWidth = width
    continentOutlines.forEach(({ points }) => {
      ctx.beginPath()
      points.forEach((point, index) => {
        const [x, y] = toCanvasLatLng(point)
        if (index === 0) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
      })
      ctx.stroke()
    })
  }

  function drawGeoJsonOutlines(style, width) {
    ctx.strokeStyle = style
    ctx.lineWidth = width
    geoJson.features?.forEach((feature) => {
      const geometry = feature.geometry
      if (!geometry) return
      const polygons = geometry.type === 'Polygon'
        ? [geometry.coordinates]
        : geometry.type === 'MultiPolygon'
          ? geometry.coordinates
          : []
      polygons.forEach((polygon) => {
        const outerRing = polygon[0]
        if (!outerRing?.length) return
        ctx.beginPath()
        let previousX = null
        outerRing.forEach((coord, index) => {
          const [x, y] = toCanvasLngLat(coord)
          if (index === 0 || (previousX !== null && Math.abs(x - previousX) > canvas.width * 0.5)) {
            ctx.moveTo(x, y)
          } else {
            ctx.lineTo(x, y)
          }
          previousX = x
        })
        ctx.stroke()
      })
    })
  }

  if (geoJson?.features?.length) {
    drawGeoJsonOutlines('rgba(255, 36, 66, 0.12)', 5.8)
    drawGeoJsonOutlines('rgba(255, 244, 236, 0.96)', 2)
  } else {
    drawFallbackOutlines('rgba(255, 36, 66, 0.14)', 7.5)
    drawFallbackOutlines('rgba(255, 244, 236, 0.95)', 2.6)
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = renderer?.capabilities?.getMaxAnisotropy?.() || 1
  return texture
}

function createOutlineSphere() {
  return new THREE.Mesh(
    new THREE.SphereGeometry(2.028, 128, 128),
    new THREE.MeshBasicMaterial({
      map: createOutlineTexture(),
      transparent: true,
      opacity: 1,
      side: THREE.FrontSide,
      depthWrite: false,
    }),
  )
}

async function loadNaturalEarthOutlines(material) {
  try {
    const response = await fetch('/data/ne_110m_land.geojson')
    if (!response.ok) return
    const geoJson = await response.json()
    const texture = createOutlineTexture(geoJson)
    material.map?.dispose()
    material.map = texture
    material.needsUpdate = true
  } catch {
    // The fallback outline texture is already applied.
  }
}

function createRimShell(radius) {
  return new THREE.Mesh(
    new THREE.SphereGeometry(radius, 96, 96),
    new THREE.ShaderMaterial({
      transparent: true,
      depthWrite: false,
      side: THREE.FrontSide,
      uniforms: {
        rimColor: { value: new THREE.Color('#fff4ec') },
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
          float rim = smoothstep(0.45, 0.98, fresnel);
          gl_FragColor = vec4(rimColor, rim * 0.42);
        }
      `,
    }),
  )
}

function createTransparentGlobe() {
  const group = new THREE.Group()
  group.add(createRimShell(2.012))
  return group
}

function createHotspot(city) {
  const group = new THREE.Group()
  const position = latLngToVector3(city.lat, city.lng, 2.085)

  const dot = new THREE.Mesh(
    new THREE.SphereGeometry(0.04, 18, 18),
    new THREE.MeshBasicMaterial({
      color: '#ff2442',
      transparent: true,
      opacity: 1,
    }),
  )
  dot.userData.cityId = city.id

  group.position.copy(position)
  group.add(dot)
  group.userData.cityId = city.id
  return group
}

function setupScene() {
  const mount = globeMount.value
  if (!mount) return

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(42, mount.clientWidth / mount.clientHeight, 0.1, 100)
  camera.position.set(0, 0.08, 6.05)
  camera.lookAt(0, 0, 0)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(mount.clientWidth, mount.clientHeight)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  mount.appendChild(renderer.domElement)

  world = new THREE.Group()
  scene.add(world)

  world.add(createTransparentGlobe())
  const outlineSphere = createOutlineSphere()
  world.add(outlineSphere)
  loadNaturalEarthOutlines(outlineSphere.material)

  hotspots = cities.map((city) => {
    const hotspot = createHotspot(city)
    world.add(hotspot)
    return hotspot
  })

  scene.add(new THREE.AmbientLight('#fff4ec', 1.25))
  const keyLight = new THREE.DirectionalLight('#ffffff', 1.4)
  keyLight.position.set(3.5, 2.4, 4.8)
  scene.add(keyLight)
  const redLight = new THREE.PointLight('#ff2442', 5.8, 8)
  redLight.position.set(-3, -1.2, 2.4)
  scene.add(redLight)

  renderer.domElement.addEventListener('pointerdown', handlePointerDown)
  window.addEventListener('resize', handleResize)
  focusCity(selectedCity.value, true)
  animate()
}

function animate() {
  animationId = requestAnimationFrame(animate)
  if (world) {
    world.rotation.y += (targetRotationY - world.rotation.y) * 0.04
    world.rotation.y += 0.0015
  }
  renderer?.render(scene, camera)
}

function handleResize() {
  const mount = globeMount.value
  if (!mount || !renderer || !camera) return
  camera.aspect = mount.clientWidth / mount.clientHeight
  camera.updateProjectionMatrix()
  renderer.setSize(mount.clientWidth, mount.clientHeight)
}

function handlePointerDown(event) {
  if (!renderer || !camera) return
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const dots = hotspots.map(h => h.children[0])
  const intersects = raycaster.intersectObjects(dots)
  if (!intersects.length) return
  const city = cities.find(item => item.id === intersects[0].object.userData.cityId)
  if (city) selectCity(city)
}

function focusCity(city, immediate = false) {
  targetRotationY = -((city.lng + 180) * Math.PI / 180) + Math.PI * 0.22
  if (immediate && world) {
    world.rotation.y = targetRotationY
  }
  updateHotspots()
}

function updateHotspots() {
  hotspots.forEach((hotspot) => {
    const material = hotspot.children[0].material
    material.color.set('#ff2442')
    material.opacity = 1
    hotspot.scale.setScalar(1)
  })
}

function selectCity(city) {
  selectedCity.value = city
  focusCity(city)
}

function startPlanning() {
  const city = selectedCity.value
  router.push({
    path: '/ai-plan',
    query: {
      q: `帮我做一个${city.name}${city.days}旅行计划，偏${city.tags.join('、')}`,
    },
  })
}

watch(selectedCity, focusCity)

onMounted(() => {
  nextTick(setupScene)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId)
  window.removeEventListener('resize', handleResize)
  renderer?.domElement?.removeEventListener('pointerdown', handlePointerDown)
  renderer?.dispose()
  if (renderer?.domElement?.parentNode) {
    renderer.domElement.parentNode.removeChild(renderer.domElement)
  }
})
</script>

<style scoped>
.globe-page {
  position: relative;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 360px;
  height: 100%;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 45%, rgba(255, 244, 236, 0.07), transparent 30%),
    radial-gradient(circle at 52% 54%, rgba(255, 36, 66, 0.055), transparent 36%),
    linear-gradient(135deg, #181614 0%, #211c19 52%, #120f0e 100%);
  color: #ffffff;
}

.explore-panel {
  position: relative;
  z-index: 2;
  margin: 18px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  color: #1f1f1f;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.28);
}

.left-panel {
  display: flex;
  flex-direction: column;
  padding: 22px;
  min-height: 0;
}

.panel-brand p,
.city-kicker {
  margin: 0 0 8px;
  color: #ff2442;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
}

.panel-brand h1 {
  margin: 0 0 20px;
  color: #1f1f1f;
  font-size: 26px;
  line-height: 1.22;
  letter-spacing: 0;
}

.city-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 13px;
  border: 1px solid #eeeeee;
  border-radius: 999px;
  background: #fafafa;
  color: #999999;
}

.city-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: #1f1f1f;
  font-size: 13px;
}

.city-list {
  flex: 1;
  min-height: 0;
  margin: 18px -8px 18px;
  padding: 0 8px;
  overflow-y: auto;
}

.city-row {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 11px 10px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: #1f1f1f;
  text-align: left;
  transition: background 0.16s ease;
}

.city-row:hover,
.city-row.active {
  background: #fff1f3;
}

.city-row strong {
  display: block;
  font-size: 14px;
}

.city-row small {
  display: block;
  margin-top: 2px;
  color: #777777;
  font-size: 12px;
}

.city-dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #ff2442;
  box-shadow: 0 0 0 5px rgba(255, 36, 66, 0.12);
}

.back-discover {
  height: 40px;
  border: 1px solid #eeeeee;
  border-radius: 999px;
  background: #ffffff;
  color: #4b4b4b;
  font-weight: 800;
}

.globe-stage {
  position: relative;
  min-width: 0;
}

.globe-canvas {
  width: 100%;
  height: 100%;
}

.stage-caption {
  position: absolute;
  left: 50%;
  bottom: 32px;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  transform: translateX(-50%);
  padding: 9px 14px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  background: rgba(18, 15, 14, 0.72);
  color: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(12px);
}

.stage-caption strong {
  color: #ffffff;
}

.city-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.city-hero {
  position: relative;
  height: 180px;
  background-size: cover;
  background-position: center;
}

.city-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.42));
}

.city-hero span {
  position: absolute;
  left: 18px;
  bottom: 16px;
  z-index: 1;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: #1f1f1f;
  font-size: 12px;
  font-weight: 900;
}

.city-content {
  flex: 1;
  min-height: 0;
  padding: 22px 22px 28px;
  overflow-y: auto;
}

.city-content h2 {
  margin: 0 0 8px;
  color: #1f1f1f;
  font-size: 32px;
  line-height: 1.1;
}

.city-desc {
  margin: 0 0 16px;
  color: #4b4b4b;
  font-size: 14px;
  line-height: 1.75;
}

.tag-list,
.spot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-list span {
  padding: 6px 10px;
  border-radius: 999px;
  background: #fff1f3;
  color: #ff2442;
  font-size: 12px;
  font-weight: 900;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin: 20px 0;
}

.info-grid div {
  padding: 13px;
  border: 1px solid #eeeeee;
  border-radius: 12px;
  background: #fafafa;
}

.info-grid small {
  display: block;
  margin-bottom: 4px;
  color: #999999;
  font-size: 12px;
}

.info-grid strong {
  color: #1f1f1f;
  font-size: 14px;
}

.spot-section {
  padding-top: 4px;
}

.spot-section h3 {
  margin: 0 0 10px;
  color: #1f1f1f;
  font-size: 15px;
}

.spot-list span {
  padding: 7px 11px;
  border-radius: 999px;
  background: #f3f3f3;
  color: #4b4b4b;
  font-size: 13px;
}

.plan-city {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 44px;
  margin-top: 22px;
  border: 0;
  border-radius: 999px;
  background: #ff2442;
  color: #ffffff;
  font-size: 14px;
  font-weight: 900;
  box-shadow: 0 12px 28px rgba(255, 36, 66, 0.24);
}

@media (max-width: 1180px) {
  .globe-page {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .city-panel {
    position: absolute;
    right: 18px;
    top: 18px;
    bottom: 18px;
    width: 330px;
  }
}

@media (max-width: 860px) {
  .globe-page {
    display: flex;
    flex-direction: column;
    height: auto;
    min-height: 100%;
    overflow: auto;
  }

  .left-panel,
  .city-panel {
    position: relative;
    inset: auto;
    width: calc(100% - 36px);
    max-width: none;
    box-sizing: border-box;
    min-width: 0;
  }

  .panel-brand h1 {
    font-size: 24px;
    overflow-wrap: anywhere;
  }

  .globe-stage {
    height: 420px;
    order: 1;
  }

  .left-panel {
    order: 2;
  }

  .city-panel {
    order: 3;
  }
}
</style>
