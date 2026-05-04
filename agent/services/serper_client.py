import os
import requests


class SerperClient:
    def __init__(self) -> None:
        self._api_key = os.getenv("SERPER_API_KEY", "").strip()
        self._base_url = "https://google.serper.dev"

    @property
    def enabled(self) -> bool:
        return bool(self._api_key)

    def _post(self, endpoint: str, payload: dict) -> dict:
        if not self.enabled:
            return {}
        try:
            response = requests.post(
                f"{self._base_url}/{endpoint}",
                headers={
                    "X-API-KEY": self._api_key,
                    "Content-Type": "application/json",
                },
                json=payload,
                timeout=20,
            )
            response.raise_for_status()
            return response.json()
        except Exception:
            return {}

    def search(self, query: str, num: int = 5) -> list[dict]:
        data = self._post("search", {"q": query, "num": num})
        return data.get("organic") or []

    def images(self, query: str, num: int = 4) -> list[dict]:
        data = self._post("images", {"q": query, "num": num})
        return data.get("images") or []
