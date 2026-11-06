#!/bin/zsh
cd "${0:A:h}"
if /usr/bin/curl -fsS --max-time 2 http://localhost:8765/api/bootstrap | /usr/bin/grep -q 'Kadri TV'; then
  open -a 'Google Chrome' http://localhost:8765
  exit 0
fi
PYTHON_BIN="$(command -v python3)"
for KADRI_PYTHON in /opt/homebrew/bin/python3 /usr/local/bin/python3; do
  [[ -x "$KADRI_PYTHON" ]] && PYTHON_BIN="$KADRI_PYTHON" && break
done
if [[ -z "$PYTHON_BIN" ]]; then
  print 'Python 3 is required: https://www.python.org/downloads/'
  read '?Press Enter to close…'
  exit 1
fi
if [[ ! -x .venv/bin/python ]]; then
  "$PYTHON_BIN" -m venv .venv || exit 1
fi
if ! .venv/bin/python -c 'import flask, bs4, requests, pychromecast' 2>/dev/null; then
  .venv/bin/python -m pip install --no-cache-dir -r requirements.txt || { read '?Setup failed. Press Enter…'; exit 1; }
fi
.venv/bin/python mac/server.py
