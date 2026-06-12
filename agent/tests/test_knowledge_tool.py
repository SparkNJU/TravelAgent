import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


class FakeKnowledgeClient:
    def __init__(self):
        self.calls = []

    def search(self, query, top_k=6):
        self.calls.append({"query": query, "top_k": top_k})
        return {"items": [{"title": "东京沉淀", "content": "浅草寺上午游览", "score": 0.9}]}


class KnowledgeSearchToolTest(unittest.TestCase):
    def test_tool_calls_knowledge_client_without_user_or_tags(self):
        from services.tool_registry import KnowledgeSearchTool

        client = FakeKnowledgeClient()
        tool = KnowledgeSearchTool(client)

        result = tool.execute(query="东京五天怎么玩", top_k=3)

        self.assertEqual(client.calls, [{"query": "东京五天怎么玩", "top_k": 3}])
        self.assertEqual(result["items"][0]["title"], "东京沉淀")
        schema_text = str(tool.parameters_schema)
        self.assertNotIn("user_id", schema_text)
        self.assertNotIn("tags", schema_text)


if __name__ == "__main__":
    unittest.main()
