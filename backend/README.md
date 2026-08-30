# ResQNet Backend

Express API for the ResQNet emergency mesh prototype. It receives SOS alerts, location updates, and mesh sync payloads from the Android client and persists them in MongoDB through Mongoose.

## Technology stack

- **Node.js** 18+
- **Express** HTTP API
- **CORS** enabled for Android clients
- **dotenv** for environment configuration
- **Mongoose** ODM for MongoDB persistence

## Folder structure

```text
backend/
├── src/
│   ├── config/
│   │   └── db.js                 # MongoDB connection (MONGO_URI)
│   ├── controllers/
│   │   ├── emergencyController.js
│   │   ├── locationController.js
│   │   └── syncController.js
│   ├── models/
│   │   ├── Emergency.js
│   │   └── Location.js
│   ├── routes/
│   │   ├── emergencyRoutes.js
│   │   ├── locationRoutes.js
│   │   └── syncRoutes.js
│   ├── utils/
│   ├── app.js                    # Express app, middleware, routes
│   └── server.js                 # Env loading, MongoDB connect, HTTP server
├── tests/
│   └── api.test.js
├── docs/
│   └── API.md                    # Request/response examples
├── .env.example
├── .gitignore
├── package.json
├── package-lock.json
└── README.md
```

## Setup

```bash
npm install
```

## Environment

Copy the example file and set values:

```bash
copy .env.example .env
```

| Variable    | Description                                      | Default |
|-------------|--------------------------------------------------|---------|
| `PORT`      | HTTP port                                        | `5000`  |
| `MONGO_URI` | MongoDB connection string (required to start)  | none    |

`MONGO_URI` must point at a reachable MongoDB instance. The database name is taken from the URI (for example `.../resqnet`). Do not hardcode credentials. Do not commit `.env`.

## Run

Development (auto-reload with nodemon):

```bash
npm run dev
```

Production:

```bash
npm start
```

The server loads `.env`, connects to MongoDB with Mongoose, then starts listening on `PORT` (default `5000`). If MongoDB is unavailable the process logs the failure and exits. There is no in-memory fallback.

## MongoDB

MongoDB must be running before the backend will start. `src/config/db.js` reads `MONGO_URI` and calls `mongoose.connect`. Emergency and location documents are stored in the `emergencies` and `locations` collections.

Local example URI (no credentials):

```text
mongodb://127.0.0.1:27017/resqnet
```

## Tests

```bash
npm test
```

Tests start an ephemeral MongoDB via `mongodb-memory-server` and verify HTTP + Mongoose persistence. They do not use the in-memory arrays that previously backed the API.

## API route groups

Base URL: `http://localhost:5000`

| Group      | Methods | Paths                          | Description                    |
|------------|---------|--------------------------------|--------------------------------|
| Health     | GET     | `/api/health`                  | Service and database health    |
| Emergency  | POST    | `/api/emergency`              | Create an SOS / emergency      |
| Emergency  | GET     | `/api/emergencies`             | List stored emergencies        |
| Location   | POST    | `/api/location`               | Store a location               |
| Location   | GET     | `/api/locations`              | List stored locations          |
| Sync       | POST    | `/api/sync`                   | Accept a mesh sync payload      |

Request and response examples: [docs/API.md](docs/API.md).

Android Storage (`HttpEmergencyApi`) posts `{ "messageId", "sourceNodeId", "type", "latitude", "longitude", "timestamp" }` to `POST /api/emergency`. Older `{ type, latitude, longitude }` requests remain accepted. When `messageId` is present it is unique and retries are idempotent.

### Health check

```bash
curl http://localhost:5000/api/health
```

```json
{
  "status": "ok",
  "service": "ResQNet",
  "database": "connected"
}
```

If MongoDB is disconnected, the endpoint returns HTTP `503` with `"database": "disconnected"`.
