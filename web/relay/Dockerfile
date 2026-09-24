# The web app's relay (relay.py). One process: sessions live in its memory, so do not scale it out.
FROM python:3.12-slim
WORKDIR /relay
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY relay.py .
USER nobody
ENV RELAY_HOST=0.0.0.0 RELAY_PORT=8790
EXPOSE 8790
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8790/relay/health')"
CMD ["python", "relay.py"]
