# Travel Assistant Agent

A minimal standalone FastAPI agent for travel planning.

## Features
- Accept user query and optional uploaded file (base64)
- Extract key travel slots (destination, days)
- Search web and images via Google Serper API
- Return Markdown travel plan with sources and images

## Run
1. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
2. Set environment variable:
   - `SERPER_API_KEY`
3. Start server:
   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```

## API
- `POST /api/trip/plan`

Request example:
```json
{
  "query": "帮我做一个东京5天旅行计划，偏美食和城市观光",
  "user_id": 1,
  "file_name": "my_notes.txt",
  "file_base64": "..."
}
```
