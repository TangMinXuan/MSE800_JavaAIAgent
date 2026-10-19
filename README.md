chroma db config:
1. create python venv: "python -m venv ./.venvs/chroma "
2. install chroma db: "./.venvs/chroma/bin/python  -m pip install chromadb"
3. check chroma db: "./.venvs/chroma/bin/chroma --version"
4. start chroma db: "./.venvs/chroma/bin/chroma run \
   --host localhost \
   --port 8000 \
   --path ./chromaData
"