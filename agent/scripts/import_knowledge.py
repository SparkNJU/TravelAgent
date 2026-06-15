"""
批量导入本地文件到 RAG 知识库。

用法:
    # 导入单个文件
    python scripts/import_knowledge.py /path/to/file.pdf

    # 导入整个目录（递归扫描）
    python scripts/import_knowledge.py /path/to/knowledge_dir/

    # 指定 Agent 地址和 namespace
    python scripts/import_knowledge.py /path/to/docs/ --url http://localhost:8000 --namespace travel

    # dry-run 模式（只解析不写入）
    python scripts/import_knowledge.py /path/to/docs/ --dry-run

支持格式: .pdf, .docx, .txt, .md, .markdown
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

# 将 agent/ 加入 sys.path，以便引用项目内模块
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import requests


# ── 文件解析 ──────────────────────────────────────────────────

def _decode_with_fallback(raw: bytes) -> str:
    """尝试多种编码解码字节流。"""
    for enc in ("utf-8", "gbk", "latin-1"):
        try:
            return raw.decode(enc)
        except Exception:
            continue
    return raw.decode("utf-8", errors="ignore")


def parse_pdf(path: str) -> str:
    """从 PDF 提取全文。"""
    try:
        from PyPDF2 import PdfReader
        reader = PdfReader(path)
        pages = [p.extract_text() or "" for p in reader.pages]
        return "\n".join(pages).strip()
    except ImportError:
        print("  ⚠ 缺少 PyPDF2，跳过 PDF: pip install PyPDF2")
        return ""
    except Exception as e:
        print(f"  ⚠ PDF 解析失败: {e}")
        return ""


def parse_docx(path: str) -> str:
    """从 DOCX 提取全文。"""
    try:
        from docx import Document
        doc = Document(path)
        lines = [p.text for p in doc.paragraphs if p.text]
        return "\n".join(lines).strip()
    except ImportError:
        print("  ⚠ 缺少 python-docx，跳过 DOCX: pip install python-docx")
        return ""
    except Exception as e:
        print(f"  ⚠ DOCX 解析失败: {e}")
        return ""


def parse_text(path: str) -> str:
    """读取纯文本/Markdown 文件。"""
    try:
        raw = Path(path).read_bytes()
        return _decode_with_fallback(raw).strip()
    except Exception as e:
        print(f"  ⚠ 文本读取失败: {e}")
        return ""


PARSERS = {
    ".pdf": parse_pdf,
    ".docx": parse_docx,
    ".txt": parse_text,
    ".md": parse_text,
    ".markdown": parse_text,
}


# ── 文件扫描 ──────────────────────────────────────────────────

def scan_files(target: str) -> list[Path]:
    """扫描目标路径，返回所有可导入的文件列表。"""
    p = Path(target)
    if p.is_file():
        if p.suffix.lower() in PARSERS:
            return [p]
        print(f"⚠ 不支持的文件格式: {p.suffix}")
        return []

    if p.is_dir():
        files = []
        for f in sorted(p.rglob("*")):
            if f.is_file() and f.suffix.lower() in PARSERS:
                files.append(f)
        return files

    print(f"⚠ 路径不存在: {target}")
    return []


# ── API 调用 ──────────────────────────────────────────────────

def ingest_document(
    base_url: str,
    title: str,
    content: str,
    source_type: str = "bulk_import",
    source_ref: str | None = None,
    namespace: str | None = None,
) -> dict:
    """调用 Agent /api/knowledge/documents 写入一条知识。"""
    payload = {
        "title": title,
        "content": content,
        "source_type": source_type,
    }
    if source_ref:
        payload["source_ref"] = source_ref
    if namespace:
        payload["metadata"] = {"namespace": namespace}

    url = f"{base_url.rstrip('/')}/api/knowledge/documents"
    resp = requests.post(url, json=payload, timeout=60)
    resp.raise_for_status()
    return resp.json()


# ── 主流程 ──────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="批量导入本地文件（PDF/MD/TXT/DOCX）到 RAG 知识库",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("path", help="单个文件或目录路径")
    parser.add_argument("--url", default="http://localhost:8000", help="Agent 服务地址 (默认 http://localhost:8000)")
    parser.add_argument("--namespace", default=None, help="知识命名空间 (默认使用配置文件中的值)")
    parser.add_argument("--source-type", default="bulk_import", help="来源类型标记 (默认 bulk_import)")
    parser.add_argument("--dry-run", action="store_true", help="只解析文件不实际写入")
    parser.add_argument("--skip-confirm", action="store_true", help="跳过确认提示")
    args = parser.parse_args()

    # 1. 扫描文件
    files = scan_files(args.path)
    if not files:
        print("未找到可导入的文件。")
        sys.exit(1)

    print(f"\n📚 找到 {len(files)} 个可导入文件:\n")
    for i, f in enumerate(files, 1):
        size_kb = f.stat().st_size / 1024
        print(f"  {i:3d}. [{f.suffix}] {f.name} ({size_kb:.1f} KB)")

    # 2. 确认
    if not args.dry_run and not args.skip_confirm:
        print(f"\n目标 Agent: {args.url}")
        print(f"命名空间:   {args.namespace or '(默认)'}")
        confirm = input("\n确认开始导入? [y/N] ").strip().lower()
        if confirm not in ("y", "yes"):
            print("已取消。")
            sys.exit(0)

    # 3. 逐文件解析并导入
    success, failed = 0, 0
    for i, fpath in enumerate(files, 1):
        suffix = fpath.suffix.lower()
        parse_fn = PARSERS.get(suffix)
        if not parse_fn:
            print(f"  [{i}/{len(files)}] 跳过不支持的格式: {fpath.name}")
            failed += 1
            continue

        print(f"  [{i}/{len(files)}] 解析: {fpath.name} ... ", end="", flush=True)
        content = parse_fn(str(fpath))

        if not content or len(content.strip()) < 10:
            print("内容为空或过短，跳过")
            failed += 1
            continue

        title = fpath.stem  # 文件名（不含扩展名）作为标题
        source_ref = str(fpath)
        print(f"{len(content)} 字符", end="")

        if args.dry_run:
            print(" (dry-run，未写入)")
            success += 1
            continue

        try:
            result = ingest_document(
                base_url=args.url,
                title=title,
                content=content,
                source_type=args.source_type,
                source_ref=source_ref,
                namespace=args.namespace,
            )
            doc_id = result.get("doc_id", "?")
            chunk_count = result.get("chunk_count", "?")
            print(f" → ✅ doc_id={doc_id}, chunks={chunk_count}")
            success += 1
        except requests.exceptions.ConnectionError:
            print(f" → ❌ 连接失败 (Agent 未启动?)")
            failed += 1
        except Exception as e:
            print(f" → ❌ {e}")
            failed += 1

        # 小延迟避免打爆 API
        time.sleep(0.3)

    # 4. 汇总
    print(f"\n{'─' * 40}")
    print(f"📊 完成: {success} 成功, {failed} 失败, 共 {len(files)} 个文件")

    if args.dry_run:
        print("(dry-run 模式，未实际写入任何数据)")


if __name__ == "__main__":
    main()
