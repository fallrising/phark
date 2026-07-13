# Stream Deck

A monolithic Stream Deck-style web application with a Spring Boot backend and React frontend served from the same origin.

## Docker build

```bash
docker build -t stream-deck .
```

## Docker run

```bash
docker run --rm -p 8080:8080 -v stream-deck-data:/data stream-deck
```

Open [http://localhost:8080](http://localhost:8080) in your browser.

## Health check

```bash
curl -fsS http://localhost:8080/actuator/health
```

## API

- `GET /api/posts`
- `GET /api/posts?channel=home`
- `POST /api/posts`

Example create request:

```bash
curl -fsS -X POST http://localhost:8080/api/posts \
  -H 'Content-Type: application/json' \
  -d '{"author":"Alice","content":"Hello","channel":"home"}'
```