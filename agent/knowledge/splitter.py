from __future__ import annotations


class TextSplitter:
    """Small dependency-free recursive-ish splitter for plain/Markdown text."""

    def __init__(self, chunk_size: int = 800, chunk_overlap: int = 200) -> None:
        if chunk_size <= 0:
            raise ValueError("chunk_size must be positive")
        if chunk_overlap < 0 or chunk_overlap >= chunk_size:
            raise ValueError("chunk_overlap must be >= 0 and < chunk_size")
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap

    def split_text(self, text: str) -> list[str]:
        normalized = (text or "").strip()
        if not normalized:
            return []

        paragraphs = [p.strip() for p in normalized.split("\n\n") if p.strip()]
        chunks: list[str] = []
        current = ""
        for paragraph in paragraphs:
            if len(paragraph) > self.chunk_size:
                if current:
                    chunks.extend(self._split_long_text(current))
                    current = ""
                chunks.extend(self._split_long_text(paragraph))
                continue
            candidate = paragraph if not current else f"{current}\n\n{paragraph}"
            if len(candidate) <= self.chunk_size:
                current = candidate
            else:
                chunks.extend(self._split_long_text(current))
                current = paragraph
        if current:
            chunks.extend(self._split_long_text(current))
        return [c for c in chunks if c.strip()]

    def _split_long_text(self, text: str) -> list[str]:
        if len(text) <= self.chunk_size:
            return [text]
        result: list[str] = []
        start = 0
        step = self.chunk_size - self.chunk_overlap
        while start < len(text):
            chunk = text[start : start + self.chunk_size].strip()
            if chunk:
                result.append(chunk)
            if start + self.chunk_size >= len(text):
                break
            start += step
        return result

