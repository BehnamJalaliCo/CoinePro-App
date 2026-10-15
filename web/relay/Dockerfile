# The web app's relay (relay.py). One process: sessions live in its memory (and, with RELAY_STATE_FILE
# and RELAY_STATE_KEY, in one encrypted file on the /state volume), so do not scale it out.
FROM python:3.12-slim
WORKDIR /relay
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY relay.py .
# The tests ride along, so the image can check itself where it runs:
#   docker compose exec -T web-relay python -m unittest test_relay
COPY test_relay.py .
# The sessions file's directory, writable by the relay's user; mount a named volume here.
RUN mkdir -p /state && chown nobody /state && chmod 700 /state
VOLUME /state
USER nobody
ENV RELAY_HOST=0.0.0.0 RELAY_PORT=8790
EXPOSE 8790
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8790/relay/health')"
CMD ["python", "relay.py"]
