chroma db config:
1. create python venv: "python -m venv ./.venvs/chroma "
2. install chroma db: "./.venvs/chroma/bin/python  -m pip install chromadb"
3. check chroma db: "./.venvs/chroma/bin/chroma --version"
4. start chroma db: "./.venvs/chroma/bin/chroma run \
   --host 127.0.0.1 \
   --port 8000 \
   --path ./chromaData
"
5. curl -N -X POST http://localhost:7070/api/chat/stream \
   -H "Content-Type: application/json" \
   -d '{
   "conversationId": "conv_test",
   "rawQuery": "what about the db eviction policy"
   }'