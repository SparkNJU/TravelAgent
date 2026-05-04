import base64
import importlib
import os
import tempfile


def _decode_with_fallback(raw: bytes) -> str:
    for enc in ("utf-8", "gbk", "latin-1"):
        try:
            return raw.decode(enc)
        except Exception:
            continue
    return raw.decode("utf-8", errors="ignore")


def _parse_pdf(path: str) -> str:
    try:
        pdf_module = importlib.import_module("PyPDF2")
        PdfReader = getattr(pdf_module, "PdfReader")
        reader = PdfReader(path)
        pages = [p.extract_text() or "" for p in reader.pages]
        return "\n".join(pages).strip()
    except Exception:
        return ""


def _parse_docx(path: str) -> str:
    try:
        docx_module = importlib.import_module("docx")
        DocxDocument = getattr(docx_module, "Document")
        doc = DocxDocument(path)
        lines = [p.text for p in doc.paragraphs if p.text]
        return "\n".join(lines).strip()
    except Exception:
        return ""


def parse_uploaded_file(file_name: str | None, file_base64: str | None) -> str:
    if not file_base64:
        return ""

    raw = base64.b64decode(file_base64)
    suffix = os.path.splitext(file_name or "upload.txt")[1].lower() or ".txt"

    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(raw)
            temp_path = tmp.name

        if suffix == ".pdf":
            return _parse_pdf(temp_path)
        if suffix == ".docx":
            return _parse_docx(temp_path)
        return _decode_with_fallback(raw)
    except Exception:
        return ""
    finally:
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except Exception:
                pass
