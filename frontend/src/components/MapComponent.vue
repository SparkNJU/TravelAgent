<template>
  <div class="map-container">
    <!-- 地图 -->
    <div class="map-wrapper">
      <!-- 搜索框 -->
      <div class="search-box">
        <input
          v-model="searchQuery"
          @keyup.enter="searchLocation"
          placeholder="搜索地点（如：天安门、故宫）"
          class="search-input"
        />
        <button @click="searchLocation" class="search-btn">搜索</button>
      </div>

      <div ref="mapElement" class="map"></div>

      <!-- 标点模式提示条 -->
      <div v-if="pickingActive" class="picking-bar">
        <span>📍 请在地图上点击选择位置</span>
      </div>

      <!-- 地点面板收缩按钮 -->
      <button 
        class="collapse-btn" 
        @click="togglePanel"
        :class="{ collapsed: panelCollapsed }"
      >
        <span class="collapse-icon">{{ panelCollapsed ? '☰' : '›' }}</span>
      </button>
      
      <!-- 地点列表面板 -->
      <div class="location-panel" :class="{ collapsed: panelCollapsed }">
        <div class="panel-header">
          <h3>行程地点</h3>
          <p class="location-count">共 {{ totalLocations }} 个地点</p>
        </div>

        <div class="location-list">
          <div
            v-for="(item, index) in locationList"
            :key="index"
            :class="['location-item', { active: selectedIndex === index }]"
            @click="selectLocation(index)"
          >
            <div class="location-marker">
              <span class="marker-number">{{ (item.dayIndex != null ? item.dayIndex : index) + 1 }}</span>
            </div>
            <div class="location-info">
              <div class="location-name">{{ item.location }}</div>
              <div class="location-day">第{{ item.day }}天</div>
              <div v-if="item.description" class="location-desc">{{ item.description }}</div>
            </div>
            <div class="location-arrow">›</div>
          </div>
        </div>
      </div>

      <!-- 地点详情弹窗 -->
      <div v-if="selectedLocation" class="location-detail">
        <div class="detail-header">
          <h4>{{ selectedLocation.location }}</h4>
          <button @click="selectedLocation = null" class="close-btn">✕</button>
        </div>
        <div class="detail-body">
          <p v-if="selectedLocation.day" class="detail-day">📅 第{{ selectedLocation.day }}天</p>
          <p v-if="selectedLocation.time" class="detail-time">⏰ {{ selectedLocation.time }}</p>
          <p v-if="selectedLocation.description" class="detail-desc">📝 {{ selectedLocation.description }}</p>
          <button @click="centerToLocation" class="locate-btn">📍 定位到此</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'

const props = defineProps({
  destinations: Array,
  itinerary: Array,
  selectedDay: { type: Number, default: null },
  pickingMode: { type: Boolean, default: false }
})

const emit = defineEmits(['mapClick'])

const mapElement = ref(null)
const selectedIndex = ref(0)
const selectedLocation = ref(null)
const searchQuery = ref('')
const panelCollapsed = ref(false)
const pickingActive = ref(false)
watch(() => props.pickingMode, (v) => { pickingActive.value = v })

// 切换面板显示状态
const togglePanel = () => {
  panelCollapsed.value = !panelCollapsed.value
  console.log('面板状态:', panelCollapsed.value)
}

let map = null
let markers = []

// 地点坐标库（包含北京主要景点）
const locationCoordLibrary = {
  '天安门': [39.9042, 116.4074],
  '天安门广场': [39.9042, 116.4074],
  '故宫': [39.9163, 116.3972],
  '故宫博物院': [39.9163, 116.3972],
  '长城': [40.4319, 116.0073],
  '慕田峪长城': [40.4319, 116.0073],
  '颐和园': [40.0075, 116.2735],
  '景山': [39.9288, 116.4084],
  '景山公园': [39.9288, 116.4084],
  '鸟巢': [39.9942, 116.3974],
  '水立方': [39.9944, 116.3939],
  '清华大学': [40.0056, 116.3278],
  '北京大学': [39.9968, 116.3057],
  '南锣鼓巷': [39.9607, 116.4043],
  '三里屯': [39.9482, 116.4439],
  '王府井': [39.9054, 116.4139],
  '798艺术区': [39.9748, 116.4721],
  '什刹海': [39.9464, 116.3822],
  '全聚德': [39.8927, 116.4088],
  '烤鸭': [39.8927, 116.4088],
  '北京烤鸭': [39.8927, 116.4088]
}

// 地点列表（使用 ref 而非 computed，以便可以修改 lat/lng）
const locationList = ref([])

// 更新地点列表
const updateLocationList = () => {
  const locations = []
  if (props.itinerary && Array.isArray(props.itinerary)) {
    props.itinerary.forEach(day => {
      if (day.activities && Array.isArray(day.activities)) {
        let dayIndex = 0
        day.activities.forEach(activity => {
          if (activity.location) {
            locations.push({
              location: activity.location,
              day: day.day || 1,
              dayIndex: dayIndex++,
              time: activity.time || '',
              description: activity.description || '',
              lat: activity.coordinates?.[0] || null,
              lng: activity.coordinates?.[1] || null
            })
          }
        })
      }
    })
  }
  locationList.value = locations
}

// 监听 itinerary 变化
watch(() => props.itinerary, () => {
  updateLocationList()
  if (map) {
    searchAllLocations()
  }
}, { deep: true })

const totalLocations = computed(() => locationList.value.length)

// 初始化地图
const initMap = async () => {
  if (!mapElement.value || !window.AMap) {
    setTimeout(initMap, 100)
    return
  }

  // 创建地图实例
  map = new window.AMap.Map(mapElement.value, {
    zoom: 11,
    center: new window.AMap.LngLat(116.4074, 39.9042), // 北京
    resizeEnable: true
  })

  // 先更新地点列表，再搜索
  updateLocationList()
  await searchAllLocations()

  // 将地图中心定位到第一个有坐标的地点
  if (locationList.value.length > 0) {
    const first = locationList.value[0]
    if (first.lat && first.lng) {
      map.setCenter(new window.AMap.LngLat(first.lng, first.lat))
      map.setZoom(13)
    }
  }

  // 监听地图点击事件
  map.on('click', (e) => {
    if (pickingActive.value) {
      emit('mapClick', {
        lng: e.lnglat.getLng(),
        lat: e.lnglat.getLat()
      })
    }
  })
}

// 搜索所有地点
const searchAllLocations = async () => {
  clearMarkers()

  console.log('搜索地点数量:', locationList.value.length)

  for (let i = 0; i < locationList.value.length; i++) {
    const location = locationList.value[i]
    if (!location) {
      console.warn(`地点列表索引 ${i} 为空`)
      continue
    }
    // Skip geocoding if coordinates already set (e.g. by picking mode)
    if (location.lat != null && location.lng != null) {
      addMarker(i, location.lng, location.lat, location.location)
      continue
    }
    await geocodeLocation(i, location.location)
  }
}

// 地理编码：根据地名获取坐标
const geocodeLocation = async (index, locationName) => {
  // 检查地点列表是否存在
  if (!locationList.value[index]) {
    console.error(`地点列表索引 ${index} 不存在`)
    return
  }
  
  // 第一步：优先查找本地坐标库
  const localCoords = locationCoordLibrary[locationName]
  if (localCoords) {
    locationList.value[index].lng = localCoords[1]
    locationList.value[index].lat = localCoords[0]
    console.log(`✓ 本地库找到 "${locationName}": ${localCoords[1]}, ${localCoords[0]}`)
    addMarker(index, localCoords[1], localCoords[0], locationName)
    return
  }

  // 第二步：尝试模糊匹配（如果地名包含库中的关键词）
  for (const [key, coords] of Object.entries(locationCoordLibrary)) {
    if (locationName.includes(key) || key.includes(locationName)) {
      locationList.value[index].lng = coords[1]
      locationList.value[index].lat = coords[0]
      console.log(`✓ 模糊匹配 "${locationName}" 到 "${key}": ${coords[1]}, ${coords[0]}`)
      addMarker(index, coords[1], coords[0], locationName)
      return
    }
  }

  // 第三步：调用后端接口搜索
  console.log(`⏳ 本地库未找到 "${locationName}"，调用后端搜索接口...`)
  try {
    const response = await fetch(`/api/map/search?keyword=${encodeURIComponent(locationName)}`)
    const data = await response.json()
    
    if (data.code === 200 && data.data && data.data.pois && data.data.pois.length > 0) {
      const poi = data.data.pois[0]
      const lng = parseFloat(poi.location.split(',')[0])
      const lat = parseFloat(poi.location.split(',')[1])

      locationList.value[index].lng = lng
      locationList.value[index].lat = lat

      console.log(`✓ 后端接口找到 "${locationName}": ${lng}, ${lat}`)
      addMarker(index, lng, lat, poi.name || locationName)
      return
    }

    // 尝试地理编码
    const geoResponse = await fetch(`/api/map/geocode?address=${encodeURIComponent(locationName)}`)
    const geoData = await geoResponse.json()
    
    if (geoData.code === 200 && geoData.data && geoData.data.geocodes && geoData.data.geocodes.length > 0) {
      const location = geoData.data.geocodes[0]
      const lng = parseFloat(location.location.split(',')[0])
      const lat = parseFloat(location.location.split(',')[1])

      locationList.value[index].lng = lng
      locationList.value[index].lat = lat

      console.log(`✓ 地理编码找到 "${locationName}": ${lng}, ${lat}`)
      addMarker(index, lng, lat, locationName)
      return
    }

    console.warn(`✗ "${locationName}" 在所有搜索都找不到，使用默认坐标`)
    // 使用默认坐标（北京市中心 + 随机偏移）
    const defaultLng = 116.4074 + (Math.random() - 0.5) * 0.1
    const defaultLat = 39.9042 + (Math.random() - 0.5) * 0.1
    locationList.value[index].lng = defaultLng
    locationList.value[index].lat = defaultLat
    addMarker(index, defaultLng, defaultLat, locationName)
  } catch (error) {
    console.error('后端接口调用失败:', error)
    // 使用默认坐标
    const defaultLng = 116.4074 + (Math.random() - 0.5) * 0.1
    const defaultLat = 39.9042 + (Math.random() - 0.5) * 0.1
    locationList.value[index].lng = defaultLng
    locationList.value[index].lat = defaultLat
    addMarker(index, defaultLng, defaultLat, locationName)
  }
}

// 搜索地点
const searchLocation = async () => {
  if (!searchQuery.value.trim()) return

  const query = searchQuery.value.trim()

  // 优先查找本地坐标库
  const localCoords = locationCoordLibrary[query]
  if (localCoords) {
    addSearchMarker(localCoords[1], localCoords[0], query)
    return
  }

  // 调用后端接口搜索
  try {
    const response = await fetch(`/api/map/search?keyword=${encodeURIComponent(query)}`)
    const data = await response.json()
    
    if (data.code === 200 && data.data && data.data.pois && data.data.pois.length > 0) {
      const poi = data.data.pois[0]
      const lng = parseFloat(poi.location.split(',')[0])
      const lat = parseFloat(poi.location.split(',')[1])
      addSearchMarker(lng, lat, poi.name || query)
    } else {
      // 尝试地理编码
      const geoResponse = await fetch(`/api/map/geocode?address=${encodeURIComponent(query)}`)
      const geoData = await geoResponse.json()
      
      if (geoData.code === 200 && geoData.data && geoData.data.geocodes && geoData.data.geocodes.length > 0) {
        const location = geoData.data.geocodes[0]
        const lng = parseFloat(location.location.split(',')[0])
        const lat = parseFloat(location.location.split(',')[1])
        addSearchMarker(lng, lat, query)
      } else {
        alert(`未找到 "${query}" 的位置`)
      }
    }
  } catch (error) {
    console.error('搜索失败:', error)
    alert(`搜索失败: ${error.message}`)
  }
}

// 添加搜索标记
const addSearchMarker = (lng, lat, title) => {
  const marker = new window.AMap.Marker({
    position: new window.AMap.LngLat(lng, lat),
    title: title,
    map: map
  })

  const infoWindow = new window.AMap.InfoWindow({
    isCustom: true,
    content: `<div style="padding: 8px;"><strong>${title}</strong></div>`,
    offset: new window.AMap.Pixel(0, -30)
  })

  infoWindow.open(map, new window.AMap.LngLat(lng, lat))
  map.setCenter(new window.AMap.LngLat(lng, lat))
  map.setZoom(15)
  markers.push(marker)
}

// 添加标记
const addMarker = (index, lng, lat, title) => {
  const location = locationList.value[index]
  const day = location?.day || 1
  const dayIdx = location?.dayIndex || 0
  const isActive = !props.selectedDay || props.selectedDay === day

  const marker = new window.AMap.Marker({
    position: new window.AMap.LngLat(lng, lat),
    title: title,
    map: map,
    label: {
      content: markerLabelHtml(dayIdx + 1, day, isActive),
      direction: 'center',
      offset: new window.AMap.Pixel(0, 0)
    }
  })

  marker._day = day
  marker._dayIndex = dayIdx
  marker._index = index
  marker.on('click', () => {
    selectLocation(index)
  })

  markers.push(marker)
}

// Day color palette
const DAY_COLORS = ['#6366f1', '#10b981', '#f59e0b', '#f43f5e', '#06b6d4', '#8b5cf6', '#ec4899', '#84cc16']
function dayColor(day) {
  return DAY_COLORS[(day - 1) % DAY_COLORS.length]
}

// Generate marker label HTML
function markerLabelHtml(num, day, active) {
  let bg, color, scale, shadow
  if (active) {
    bg = dayColor(day)
    color = '#fff'
    scale = 'scale(1.1)'
    shadow = `0 2px 8px ${dayColor(day)}80`
  } else if (props.selectedDay == null) {
    // No day selected: each day gets its own color
    bg = dayColor(day)
    color = '#fff'
    scale = 'scale(1)'
    shadow = 'none'
  } else {
    bg = 'rgba(156,163,175,0.4)'
    color = '#9ca3af'
    scale = 'scale(0.85)'
    shadow = 'none'
  }
  return `<div style="
    width:26px;height:26px;border-radius:50%;
    background:${bg};color:${color};
    display:flex;align-items:center;justify-content:center;
    font-size:12px;font-weight:700;
    transform:${scale};
    box-shadow:${shadow};
    border:2px solid ${active ? '#fff' : 'transparent'};
    transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  ">${num}</div>`
}

// Refresh all marker styles when selectedDay changes
function refreshMarkerStyles() {
  markers.forEach(m => {
    const day = m._day || 1
    const isActive = !props.selectedDay || props.selectedDay === day
    const num = (m._dayIndex != null ? m._dayIndex : m._index) + 1
    m.setLabel({
      content: markerLabelHtml(num, day, isActive),
      direction: 'center',
      offset: new window.AMap.Pixel(0, 0)
    })
  })
}

// Watch selectedDay to update marker highlights
watch(() => props.selectedDay, () => {
  refreshMarkerStyles()
})

// 选择地点
const selectLocation = (index) => {
  selectedIndex.value = index
  const location = locationList.value[index]
  if (location) {
    selectedLocation.value = location

    // 地图定位到该位置
    if (location.lat && location.lng) {
      map.setCenter(new window.AMap.LngLat(location.lng, location.lat))
      map.setZoom(15)
    }
  }
}

// 处理搜索输入
const handleSearchInput = async () => {
  const query = searchQuery.value.trim()
  if (!query) {
    searchSuggestions.value = []
    return
  }

  // 调用后端接口获取搜索建议
  try {
    const response = await fetch(`/api/map/search?keyword=${encodeURIComponent(query)}`)
    const data = await response.json()
    
    if (data.code === 200 && data.data && data.data.pois && data.data.pois.length > 0) {
      searchSuggestions.value = data.data.pois.slice(0, 8).map(poi => ({
        name: poi.name,
        address: poi.address || poi.adname || '',
        location: poi.location
      }))
    } else {
      searchSuggestions.value = []
    }
  } catch (error) {
    console.error('获取搜索建议失败:', error)
    searchSuggestions.value = []
  }
}

// 选择搜索建议
const selectSuggestion = (item) => {
  searchQuery.value = item.name
  showSearchSuggestions.value = false
  
  // 获取坐标并添加标记
  if (item.location) {
    const lng = parseFloat(item.location.split(',')[0])
    const lat = parseFloat(item.location.split(',')[1])
    addSearchMarker(lng, lat, item.name)
  }
}

// 隐藏搜索建议
const hideSearchSuggestions = () => {
  setTimeout(() => {
    showSearchSuggestions.value = false
  }, 200)
}

// 定位到选中地点
const centerToLocation = () => {
  if (selectedLocation.value && selectedLocation.value.lat && selectedLocation.value.lng) {
    map.setCenter(new window.AMap.LngLat(selectedLocation.value.lng, selectedLocation.value.lat))
    map.setZoom(15)
  }
}

// 清除所有标记
const clearMarkers = () => {
  markers.forEach(marker => {
    marker.setMap(null)
  })
  markers = []
}

onMounted(() => {
  // 等待高德地图API加载
  const checkAMap = () => {
    if (window.AMap) {
      initMap()
    } else {
      setTimeout(checkAMap, 100)
    }
  }
  checkAMap()
})


</script>

<style scoped>
.map-container {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--color-surface);
}

.search-box {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 400px;
  padding: 6px 10px;
  display: flex;
  gap: 8px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  z-index: 20;
}

.search-input {
  flex: 1;
  padding: 10px 14px;
  border: none;
  font-size: 13px;
  font-family: var(--font-family);
  background: var(--color-card);
}

.search-input:focus {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--color-red-light);
}

.search-input::placeholder {
  color: var(--color-hint);
}

.search-btn {
  padding: 10px 16px;
  border: none;
  background: var(--color-red-light);
  color: white;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
}

.search-btn:hover {
  background: var(--color-red);
}

/* 标点模式提示条 */
.picking-bar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  padding: 8px 24px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(99,102,241,0.35);
  z-index: 30;
  animation: pickingPulse 1.5s ease-in-out infinite;
  pointer-events: none;
}
@keyframes pickingPulse {
  0%, 100% { box-shadow: 0 4px 16px rgba(99,102,241,0.35); }
  50% { box-shadow: 0 4px 24px rgba(99,102,241,0.55); }
}

.map-wrapper {
  flex: 1;
  display: flex;
  position: relative;
  overflow: hidden;
}

.map {
  flex: 1;
  position: relative;
  z-index: 1;
}

:deep(.custom-marker) {
  background: none !important;
  border: none !important;
  padding: 0 !important;
}

:deep(.marker-icon) {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8787 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 13px;
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.4);
  cursor: pointer;
}

:deep(.search-marker-icon) {
  background: none !important;
  border: none !important;
  padding: 0 !important;
}

:deep(.search-marker) {
  font-size: 20px;
  cursor: pointer;
}

:deep(.leaflet-popup-content) {
  font-size: 12px !important;
  margin: 0 !important;
  padding: 4px 8px !important;
}

/* 地点面板收缩按钮 */
.collapse-btn {
  position: absolute;
  right: 240px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 56px;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(6px);
  border: 1px solid #e5e7eb;
  border-right: none;
  border-radius: 6px 0 0 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 16;
  transition: all 0.2s;
  color: #6366f1;
  font-size: 20px;
  font-weight: 700;
  box-shadow: -2px 0 8px rgba(0,0,0,0.06);
}

.collapse-btn:hover {
  background: #fff;
  color: #4f46e5;
  box-shadow: -2px 0 12px rgba(0,0,0,0.1);
}

.collapse-btn.collapsed {
  right: 0;
  border-radius: 6px 0 0 6px;
}

.collapse-icon { line-height: 1; }

/* 地点列表面板 */
.location-panel {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 240px;
  background: var(--color-card);
  border-left: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 10;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.1);
  transition: width 0.3s ease;
}

.location-panel.collapsed {
  width: 0;
  overflow: hidden;
  border-left: none;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-card);
  flex-shrink: 0;
}

.panel-header h3 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
}

.location-count {
  margin: 0;
  font-size: 12px;
  color: var(--color-secondary);
}

.location-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.location-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.location-item:hover {
  background: rgba(255, 107, 107, 0.05);
}

.location-item.active {
  background: rgba(255, 107, 107, 0.1);
  border-left-color: var(--color-red-light);
}

.location-marker {
  width: 32px;
  height: 32px;
  min-width: 32px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8787 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: bold;
}

.location-item.active .location-marker {
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.4);
  transform: scale(1.1);
}

.location-info {
  flex: 1;
  min-width: 0;
}

.location-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-title);
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.location-day {
  font-size: 11px;
  color: var(--color-red-light);
  margin-bottom: 2px;
}

.location-desc {
  font-size: 11px;
  color: var(--color-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.location-arrow {
  color: var(--color-hint);
  font-size: 16px;
  flex-shrink: 0;
}

/* 地点详情弹窗 */
.location-detail {
  position: absolute;
  bottom: 20px;
  left: 20px;
  background: var(--color-card);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  min-width: 280px;
  max-width: 380px;
  z-index: 100;
  animation: slideIn 0.2s ease-out;
}

.clicked-location-info {
  position: absolute;
  bottom: 20px;
  right: 20px;
  background: var(--color-card);
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  min-width: 240px;
  z-index: 100;
  animation: slideIn 0.2s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.detail-header,
.info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--color-border);
}

.detail-header h4,
.info-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
}

.close-btn {
  background: none;
  border: none;
  font-size: 18px;
  color: var(--color-hint);
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.close-btn:hover {
  color: var(--color-title);
}

.detail-body,
.info-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-day,
.detail-time,
.detail-desc {
  margin: 0;
  font-size: 13px;
  color: #0a0a0a !important;
  line-height: 1.6;
}

.info-body p {
  margin: 0;
  font-size: 12px;
  color: #0a0a0a !important;
  line-height: 1.6;
}

.detail-day {
  color: #e53935 !important;
  font-weight: 500;
}

.locate-btn {
  align-self: flex-start;
  padding: 8px 16px;
  border: none;
  background: var(--color-red-light);
  color: white;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
}

.locate-btn:hover {
  background: var(--color-red);
}

.search-input {
  flex: 1;
  padding: 10px 14px;
  border: none;
  font-size: 13px;
  font-family: var(--font-family);
  background: var(--color-card);
}

.search-input:focus {
  outline: none;
  box-shadow: inset 0 0 0 2px var(--color-red-light);
}

.search-input::placeholder {
  color: var(--color-hint);
}

.search-btn {
  padding: 10px 16px;
  border: none;
  background: var(--color-red-light);
  color: white;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
}

.search-btn:hover {
  background: var(--color-red);
}

/* 地点列表面板 */
.location-panel {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.location-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.location-item:hover {
  background: rgba(255, 107, 107, 0.05);
}

.location-item.active {
  background: rgba(255, 107, 107, 0.1);
  border-left-color: var(--color-red-light);
}

.location-marker {
  width: 32px;
  height: 32px;
  min-width: 32px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8787 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: bold;
}

.location-item.active .location-marker {
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.4);
  transform: scale(1.1);
}

.location-info {
  flex: 1;
  min-width: 0;
}

.location-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-title);
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.location-day {
  font-size: 11px;
  color: var(--color-red-light);
  margin-bottom: 2px;
}

.location-desc {
  font-size: 11px;
  color: var(--color-body);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.location-arrow {
  color: var(--color-hint);
  font-size: 16px;
  flex-shrink: 0;
}


@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--color-border);
}

.detail-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--color-title);
}

.close-btn {
  background: none;
  border: none;
  font-size: 18px;
  color: var(--color-hint);
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.close-btn:hover {
  color: var(--color-title);
}

.detail-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.locate-btn {
  align-self: flex-start;
  padding: 8px 16px;
  border: none;
  background: var(--color-red-light);
  color: white;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  font-family: var(--font-family);
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
}

.locate-btn:hover {
  background: var(--color-red);
}

:deep(.marker-label) {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8787 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: bold;
}

:deep(.qq-map-container) {
  border: none;
}

:deep(.marker-label) {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8787 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.4);
}
</style>