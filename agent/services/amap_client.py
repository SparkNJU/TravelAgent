import os
import requests
import logging
import random

logger = logging.getLogger(__name__)

LOCAL_LANDMARKS = {
    "南京": {
        "夫子庙": (32.0221, 118.7909),
        "秦淮河": (32.0200, 118.7850),
        "中山陵": (32.0620, 118.8480),
        "玄武湖": (32.0725, 118.7997),
        "总统府": (32.0450, 118.8055),
        "鸡鸣寺": (32.0601, 118.7981),
        "遇难同胞纪念馆": (32.0360, 118.7360),
        "南京大屠杀遇难同胞纪念馆": (32.0360, 118.7360),
        "雨花台": (32.0010, 118.7770),
        "栖霞山": (32.1550, 118.9630),
    },
    "杭州": {
        "西湖": (30.2520, 120.1500),
        "灵隐寺": (30.2435, 120.1008),
        "雷峰塔": (30.2312, 120.1418),
        "千岛湖": (29.6050, 119.0400),
        "西溪湿地": (30.2700, 120.0600),
        "拱宸桥": (30.3200, 120.1380),
        "宋城": (30.1770, 120.0960),
    },
    "北京": {
        "天安门": (39.9054, 116.3976),
        "故宫": (39.9169, 116.3970),
        "颐和园": (39.9995, 116.2736),
        "八达岭长城": (40.3601, 116.0240),
        "天坛": (39.8836, 116.4108),
        "圆明园": (40.0100, 116.2990),
        "鸟巢": (39.9930, 116.3962),
        "水立方": (39.9918, 116.3860),
    },
    "上海": {
        "外滩": (31.2400, 121.4900),
        "东方明珠": (31.2397, 121.4997),
        "南京路": (31.2350, 121.4780),
        "城隍庙": (31.2260, 121.4910),
        "迪士尼": (31.1440, 121.6570),
        "田子坊": (31.2090, 121.4680),
    }
}

CITY_BASES = {
    "北京": (39.9042, 116.4074),
    "上海": (31.2304, 121.4737),
    "东京": (35.6762, 139.6503),
    "大阪": (34.6937, 135.5023),
    "京都": (35.0116, 135.7681),
    "首尔": (37.5665, 126.9780),
    "曼谷": (13.7563, 100.5018),
    "新加坡": (1.3521, 103.8198),
    "南京": (32.0603, 118.7969),
    "杭州": (30.2741, 120.1551),
}

class AMapClient:
    def __init__(self) -> None:
        self._api_key = os.getenv("AMAP_MAPS_API_KEY", "").strip()
        self._base_url = "https://restapi.amap.com/v3/geocode/geo"

    @property
    def enabled(self) -> bool:
        return bool(self._api_key)

    def geocode(self, address: str, city: str | None = None) -> tuple[float, float] | None:
        """
        Geocodes the address into (latitude, longitude).
        Returns None if not found or on error.
        """
        if not self.enabled:
            return None
        
        try:
            params = {
                "key": self._api_key,
                "address": address,
            }
            if city:
                params["city"] = city
            
            response = requests.get(self._base_url, params=params, timeout=10)
            response.raise_for_status()
            data = response.json()
            
            if data.get("status") == "1" and data.get("geocodes"):
                geocode_info = data["geocodes"][0]
                location_str = geocode_info.get("location", "")
                if location_str:
                    # AMap returns "longitude,latitude"
                    lng_str, lat_str = location_str.split(",")
                    return float(lat_str), float(lng_str)
        except Exception as e:
            logger.error(f"AMap geocoding failed for '{address}': {e}")
        
        return None

def get_coordinates(city: str, location_name: str, amap_client: AMapClient) -> tuple[float, float]:
    """
    Retrieves coordinates (latitude, longitude) for a landmark in a city.
    """
    clean_city = city.replace("市", "") if city else ""
    
    # 1. Local Landmarks matching
    if clean_city in LOCAL_LANDMARKS:
        city_db = LOCAL_LANDMARKS[clean_city]
        for landmark, coords in city_db.items():
            if landmark in location_name or location_name in landmark:
                return coords

    # 2. AMap geocoding
    if amap_client.enabled:
        query_address = f"{city} {location_name}" if city else location_name
        coords = amap_client.geocode(query_address, city)
        if coords:
            return coords

    # 3. Fallback: random offset around city base
    base = CITY_BASES.get(clean_city) or (30.0, 110.0)
    offset_lat = random.uniform(-0.02, 0.02)
    offset_lng = random.uniform(-0.02, 0.02)
    return (base[0] + offset_lat, base[1] + offset_lng)
