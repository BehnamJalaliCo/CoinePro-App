# Pro Chart's owner console (admin.py). One process, state in one SQLite file on a volume.
#
# The typeface is IRANYekanX, the product's only one. Copy the four files in before building —
#   cp ../../core/designsystem/src/main/res/font/iranyekanx_*.ttf fonts/
# — and the console uses them; without them it falls back to the system face and still works.
FROM python:3.12-slim
WORKDIR /admin
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN mkdir -p /data fonts && chown nobody /data
# The tests ride along, so the image can check itself where it runs:
#   docker compose exec -T admin python -m unittest test_admin
USER nobody
ENV ADMIN_HOST=0.0.0.0 ADMIN_PORT=8791 ADMIN_DB=/data/admin.sqlite3 PYTHONUNBUFFERED=1
EXPOSE 8791
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8791/admin/health')"
CMD ["python", "admin.py"]
