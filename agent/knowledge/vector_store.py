from __future__ import annotations

from typing import Any

try:
    from pymilvus import AnnSearchRequest, DataType, Function, FunctionType, MilvusClient, WeightedRanker
except Exception:  # pragma: no cover - exercised only when dependency missing at runtime
    AnnSearchRequest = DataType = Function = FunctionType = MilvusClient = WeightedRanker = None


class MilvusKnowledgeStore:
    """Milvus-only storage for knowledge chunks with dense + BM25 hybrid search."""

    def __init__(
        self,
        uri: str,
        collection: str,
        embedding_dim: int,
        dense_weight: float = 0.75,
        sparse_weight: float = 0.25,
    ) -> None:
        if MilvusClient is None:
            raise RuntimeError("pymilvus is required for knowledge center; install pymilvus")
        self.client = MilvusClient(uri=uri)
        self.collection = collection
        self.embedding_dim = embedding_dim
        self.dense_weight = dense_weight
        self.sparse_weight = sparse_weight
        self._ensure_collection()

    def _ensure_collection(self) -> None:
        if self.client.has_collection(self.collection):
            return
        schema = self.client.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("chunk_id", DataType.VARCHAR, is_primary=True, max_length=128)
        schema.add_field("doc_id", DataType.VARCHAR, max_length=128)
        schema.add_field("doc_title", DataType.VARCHAR, max_length=512)
        schema.add_field("namespace", DataType.VARCHAR, max_length=64)
        schema.add_field("source_type", DataType.VARCHAR, max_length=64)
        schema.add_field("source_ref", DataType.VARCHAR, max_length=512)
        schema.add_field(
            "content",
            DataType.VARCHAR,
            max_length=65535,
            enable_analyzer=True,
            analyzer_params={"type": "chinese"},
            enable_match=True,
        )
        schema.add_field("metadata", DataType.JSON)
        schema.add_field("created_at", DataType.VARCHAR, max_length=64)
        schema.add_field("embedding", DataType.FLOAT_VECTOR, dim=self.embedding_dim)
        schema.add_field("sparse_bm25", DataType.SPARSE_FLOAT_VECTOR)
        schema.add_function(
            Function(
                name="bm25",
                function_type=FunctionType.BM25,
                input_field_names=["content"],
                output_field_names=["sparse_bm25"],
            )
        )

        index = self.client.prepare_index_params()
        index.add_index(
            "embedding",
            index_type="HNSW",
            metric_type="COSINE",
            params={"M": 16, "efConstruction": 64},
        )
        index.add_index("sparse_bm25", index_type="SPARSE_WAND", metric_type="BM25")
        self.client.create_collection(self.collection, schema=schema, index_params=index)

    def insert(self, rows: list[dict[str, Any]]) -> int:
        if not rows:
            return 0
        result = self.client.insert(self.collection, rows)
        return int(result.get("insert_count", 0))

    def hybrid_search(
        self,
        query_vector: list[float],
        query_text: str,
        namespace: str,
        limit: int,
    ) -> list[dict[str, Any]]:
        filter_expr = f'namespace == "{namespace}"'
        dense_req = AnnSearchRequest(
            data=[query_vector],
            anns_field="embedding",
            param={"metric_type": "COSINE", "params": {"ef": 64}},
            limit=limit,
            expr=filter_expr,
        )
        sparse_req = AnnSearchRequest(
            data=[query_text],
            anns_field="sparse_bm25",
            param={"metric_type": "BM25"},
            limit=limit,
            expr=filter_expr,
        )
        result = self.client.hybrid_search(
            collection_name=self.collection,
            reqs=[dense_req, sparse_req],
            ranker=WeightedRanker(self.dense_weight, self.sparse_weight),
            limit=limit,
            output_fields=[
                "chunk_id",
                "doc_id",
                "doc_title",
                "content",
                "source_type",
                "source_ref",
                "metadata",
            ],
        )
        hits = result[0] if result else []
        parsed: list[dict[str, Any]] = []
        for hit in hits:
            entity = hit.get("entity", {})
            parsed.append(
                {
                    "chunk_id": entity.get("chunk_id"),
                    "doc_id": entity.get("doc_id"),
                    "doc_title": entity.get("doc_title"),
                    "content": entity.get("content"),
                    "source_type": entity.get("source_type"),
                    "source_ref": entity.get("source_ref"),
                    "metadata": entity.get("metadata") or {},
                    "score": hit.get("score"),
                }
            )
        return parsed
