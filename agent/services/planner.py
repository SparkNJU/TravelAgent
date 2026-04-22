import re

from .serper_client import SerperClient


class TripPlanner:
    def __init__(self, serper: SerperClient) -> None:
        self._serper = serper

    def _guess_destination(self, query: str) -> str:
        text = query.strip()
        patterns = [
            r"(?:去|到|前往|安排|规划)?([\u4e00-\u9fffA-Za-z0-9]{2,12})(?:旅游|旅行|行程|之旅)",
            r"([\u4e00-\u9fffA-Za-z0-9]{2,12})(?:自由行|攻略|路线)",
        ]
        for p in patterns:
            m = re.search(p, text)
            if m:
                return m.group(1)
        return text[:12] or "未知目的地"

    def _extract_days(self, query: str) -> int:
        m = re.search(r"(\d+)\s*天", query)
        if not m:
            return 3
        try:
            return max(1, min(int(m.group(1)), 14))
        except ValueError:
            return 3

    def _build_markdown(
        self,
        destination: str,
        days: int,
        query: str,
        file_summary: str,
        search_results: list[dict],
        image_results: list[dict],
    ) -> str:
        lines: list[str] = [f"# {destination}{days}日旅行计划", ""]

        lines.extend(["## 用户需求", query, ""])

        if file_summary:
            lines.extend(["## 上传资料摘要", file_summary, ""])

        lines.extend([
            "## 行前建议",
            "- 优先确认机酒和关键门票",
            "- 每天安排 2-3 个主活动，预留机动时间",
            "- 交通和餐饮尽量按区域聚类，减少往返",
            "",
            "## 每日行程",
        ])

        for day in range(1, days + 1):
            lines.append(f"### Day {day}")
            if day == 1:
                lines.append(f"- 抵达 {destination}，入住酒店，周边轻量探索")
            elif day == days:
                lines.append("- 返程前补打卡、购物和收尾")
            else:
                lines.append("- 上午经典景点，下午特色街区，晚上本地美食")
            lines.append("")

        if search_results:
            lines.append("## 联网参考来源")
            for item in search_results[:5]:
                title = item.get("title") or "搜索结果"
                link = item.get("link") or item.get("url") or ""
                snippet = item.get("snippet") or ""
                lines.append(f"- [{title}]({link})")
                if snippet:
                    lines.append(f"  - {snippet}")
            lines.append("")

        if image_results:
            lines.append("## 图片参考")
            for img in image_results[:4]:
                image_url = img.get("imageUrl") or img.get("image") or img.get("url")
                source_url = img.get("sourceUrl") or img.get("link") or ""
                title = img.get("title") or destination
                if image_url:
                    lines.append(f"![{title}]({image_url})")
                    if source_url:
                        lines.append(f"来源：{source_url}")
                    lines.append("")

        lines.extend([
            "## 出发前检查",
            "- 证件与支付方式",
            "- 住宿地址与交通备选",
            "- 天气与应急物品",
        ])

        return "\n".join(lines).strip()

    def generate(self, query: str, file_summary: str = "") -> dict:
        destination = self._guess_destination(query)
        days = self._extract_days(query)

        search_query = f"{destination} travel guide attractions food transportation"
        search_results = self._serper.search(search_query, num=5)
        image_results = self._serper.images(f"{destination} attractions", num=4)

        markdown = self._build_markdown(
            destination=destination,
            days=days,
            query=query,
            file_summary=file_summary,
            search_results=search_results,
            image_results=image_results,
        )

        images = [
            {
                "title": item.get("title") or destination,
                "imageUrl": item.get("imageUrl") or item.get("image") or item.get("url"),
                "sourceUrl": item.get("sourceUrl") or item.get("link"),
            }
            for item in image_results[:4]
            if item.get("imageUrl") or item.get("image") or item.get("url")
        ]

        sources = [
            {
                "title": item.get("title") or "搜索结果",
                "link": item.get("link") or item.get("url"),
                "snippet": item.get("snippet") or "",
            }
            for item in search_results[:5]
        ]

        return {
            "title": f"{destination}{days}日旅行计划",
            "destination": destination,
            "days": days,
            "summary": f"基于需求生成的 {destination} 旅行计划",
            "markdown": markdown,
            "images": images,
            "sources": sources,
            "file_summary": file_summary or None,
        }
