import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeEmbeddingClient:
    def embed(self, texts):
        return [[float(len(text)), 0.0, 1.0] for text in texts]

    def rerank(self, query, docs, top_n):
        return [
            {"index": idx, "relevance_score": 1.0 - idx * 0.1}
            for idx, _ in enumerate(docs[:top_n])
        ]


class FakeVectorStore:
    def __init__(self):
        self.inserted_rows = []
        self.hits = []

    def insert(self, rows):
        self.inserted_rows.extend(rows)
        return len(rows)

    def hybrid_search(self, query_vector, query_text, namespace, limit):
        return self.hits[:limit]


class KnowledgeServiceTest(unittest.TestCase):
    def test_ingest_text_splits_embeds_and_inserts_milvus_rows_without_tags(self):
        from knowledge.schemas import KnowledgeDocumentCreate
        from knowledge.service import KnowledgeService
        from knowledge.splitter import TextSplitter

        store = FakeVectorStore()
        service = KnowledgeService(
            vector_store=store,
            embedding_client=FakeEmbeddingClient(),
            splitter=TextSplitter(chunk_size=12, chunk_overlap=3),
            namespace="default",
        )

        result = service.ingest_text(
            KnowledgeDocumentCreate(
                title="东京旅行沉淀",
                content="东京美食很多，浅草寺适合上午游览。银座适合购物。",
                source_type="conversation_turn",
                source_ref="conversation:local-1:turn-2",
                metadata={"contains_web_search": True},
            )
        )

        self.assertEqual(result.status, "indexed")
        self.assertEqual(result.chunk_count, len(store.inserted_rows))
        self.assertGreater(result.chunk_count, 1)
        first = store.inserted_rows[0]
        self.assertEqual(first["namespace"], "default")
        self.assertEqual(first["doc_id"], result.doc_id)
        self.assertEqual(first["doc_title"], "东京旅行沉淀")
        self.assertEqual(first["source_type"], "conversation_turn")
        self.assertEqual(first["source_ref"], "conversation:local-1:turn-2")
        self.assertEqual(first["metadata"], {"contains_web_search": True})
        self.assertEqual(first["embedding"], [float(len(first["content"])), 0.0, 1.0])
        self.assertNotIn("tags", first)
        self.assertNotIn("tags_json", first)

    def test_search_hybrid_recalls_and_reranks_chunks(self):
        from knowledge.schemas import KnowledgeSearchRequest
        from knowledge.service import KnowledgeService
        from knowledge.splitter import TextSplitter

        store = FakeVectorStore()
        store.hits = [
            {
                "chunk_id": "c1",
                "doc_id": "d1",
                "doc_title": "东京攻略",
                "content": "浅草寺上午人少，适合城市观光。",
                "score": 0.6,
                "source_type": "conversation_turn",
                "source_ref": "conversation:1",
                "metadata": {"kind": "saved_turn"},
            },
            {
                "chunk_id": "c2",
                "doc_id": "d2",
                "doc_title": "大阪攻略",
                "content": "大阪适合美食路线。",
                "score": 0.5,
                "source_type": "conversation_turn",
                "source_ref": "conversation:2",
                "metadata": {},
            },
        ]
        service = KnowledgeService(
            vector_store=store,
            embedding_client=FakeEmbeddingClient(),
            splitter=TextSplitter(),
            namespace="default",
            recall_limit=10,
        )

        result = service.search(KnowledgeSearchRequest(query="东京城市观光", top_k=1))

        self.assertEqual(len(result.items), 1)
        self.assertEqual(result.items[0].chunk_id, "c1")
        self.assertEqual(result.items[0].title, "东京攻略")
        self.assertEqual(result.items[0].score, 1.0)


if __name__ == "__main__":
    unittest.main()
