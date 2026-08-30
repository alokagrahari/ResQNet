# ResQNet API

Base URL: `http://localhost:5000`

All bodies are JSON. CORS is enabled.

---

## GET /api/health

**Response `200`**

```json
{
  "status": "ok",
  "service": "ResQNet",
  "database": "connected"
}
```

If MongoDB is not connected, the response is HTTP `503`:

```json
{
  "status": "error",
  "service": "ResQNet",
  "database": "disconnected"
}
```

```bash
curl http://localhost:5000/api/health
```

---

## POST /api/emergency

**Request**

```json
{
  "messageId": "TEST-123",
  "sourceNodeId": "NODE-A",
  "type": "SOS",
  "latitude": 28.4595,
  "longitude": 77.0266,
  "timestamp": "2026-08-29T10:00:00.000Z"
}
```

Required: `type`, `latitude` (-90..90), `longitude` (-180..180).

Optional: `messageId`, `sourceNodeId`, `timestamp`. When `messageId` is present it is stored and used for idempotency (first request `201`, retry `200`, one MongoDB document). The original `timestamp` is preserved when provided.

Older clients that send only `{ type, latitude, longitude }` remain accepted.

**Response `201`**

```json
{
  "id": "generated-uuid",
  "type": "SOS",
  "latitude": 28.4595,
  "longitude": 77.0266,
  "timestamp": "2026-08-27T16:00:00.000Z"
}
```

```bash
curl -X POST http://localhost:5000/api/emergency ^
  -H "Content-Type: application/json" ^
  -d "{\"type\":\"SOS\",\"latitude\":28.4595,\"longitude\":77.0266}"
```

**Errors `400`** — missing fields or out-of-range coordinates.

---

## GET /api/emergencies

**Response `200`**

```json
{
  "emergencies": [
    {
      "id": "123",
      "type": "SOS",
      "latitude": 28.4595,
      "longitude": 77.0266,
      "timestamp": "2026-08-27T16:00:00.000Z"
    }
  ]
}
```

Empty store:

```json
{
  "emergencies": []
}
```

```bash
curl http://localhost:5000/api/emergencies
```

---

## POST /api/location

**Request**

```json
{
  "latitude": 28.4595,
  "longitude": 77.0266
}
```

**Response `201`**

```json
{
  "status": "ok",
  "message": "Location recorded successfully",
  "data": {
    "id": "generated-uuid",
    "latitude": 28.4595,
    "longitude": 77.0266,
    "timestamp": "2026-08-27T16:00:00.000Z"
  }
}
```

```bash
curl -X POST http://localhost:5000/api/location ^
  -H "Content-Type: application/json" ^
  -d "{\"latitude\":28.4595,\"longitude\":77.0266}"
```

---

## GET /api/locations

**Response `200`**

```json
{
  "locations": [
    {
      "id": "generated-uuid",
      "latitude": 28.4595,
      "longitude": 77.0266,
      "timestamp": "2026-08-27T16:00:00.000Z"
    }
  ]
}
```

Empty store:

```json
{
  "locations": []
}
```

```bash
curl http://localhost:5000/api/locations
```

---

## POST /api/sync

**Request**

```json
{
  "nodeId": "NODE_A",
  "messages": []
}
```

Required: `nodeId`. `messages` must be an array when provided (defaults to `[]`).

**Response `200`**

```json
{
  "status": "ok",
  "message": "Sync accepted",
  "nodeId": "NODE_A",
  "received": 0
}
```

```bash
curl -X POST http://localhost:5000/api/sync ^
  -H "Content-Type: application/json" ^
  -d "{\"nodeId\":\"NODE_A\",\"messages\":[]}"
```

---

## Test checklist

- [x] Server starts (after MongoDB connects)
- [x] GET /api/health
- [x] POST /api/emergency
- [x] GET /api/emergencies
- [x] POST /api/location
- [x] GET /api/locations
- [x] POST /api/sync
- [x] Invalid emergency rejected (400)
- [x] Invalid latitude rejected (400)
- [x] Invalid longitude rejected (400)
- [x] Invalid sync request rejected (400)
- [x] Invalid JSON rejected (400)
- [x] Server still healthy after invalid requests
