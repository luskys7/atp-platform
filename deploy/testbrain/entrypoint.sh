#!/bin/bash
set -euo pipefail

echo "[testbrain] waiting for MySQL ${MYSQL_HOST:-testbrain-mysql}:${MYSQL_PORT:-3306} ..."
for i in $(seq 1 60); do
  if python - <<'PY'
import os, socket, sys
host=os.getenv("MYSQL_HOST","testbrain-mysql")
port=int(os.getenv("MYSQL_PORT","3306"))
s=socket.socket(); s.settimeout(2)
try:
    s.connect((host, port)); s.close(); sys.exit(0)
except Exception:
    sys.exit(1)
PY
  then
    break
  fi
  sleep 2
done

echo "[testbrain] waiting for Milvus ${MILVUS_HOST:-milvus}:${MILVUS_PORT:-19530} ..."
for i in $(seq 1 60); do
  if python - <<'PY'
import os, socket, sys
host=os.getenv("MILVUS_HOST","milvus")
port=int(os.getenv("MILVUS_PORT","19530"))
s=socket.socket(); s.settimeout(2)
try:
    s.connect((host, port)); s.close(); sys.exit(0)
except Exception:
    sys.exit(1)
PY
  then
    break
  fi
  sleep 2
done

echo "[testbrain] migrate ..."
python manage.py makemigrations core --noinput || true
python manage.py migrate --noinput || true

echo "[testbrain] start on :8000"
exec python manage.py runserver 0.0.0.0:8000 --noreload
