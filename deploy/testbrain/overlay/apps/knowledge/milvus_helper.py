"""Stub: avoid importing spacy/unstructured in ATP bridge deployment.
File-upload RAG path is disabled; use POST /api/v1/ai/knowledge/ingest instead.
"""


def process_singel_file(file_path):
    raise RuntimeError(
        "Document file parsing is disabled in ATP bridge image "
        "(spacy not installed). Use /api/v1/ai/knowledge/ingest with plain text."
    )
