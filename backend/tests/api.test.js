import { after, before, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import mongoose from 'mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import app from '../src/app.js';
import Emergency from '../src/models/Emergency.js';
import Location from '../src/models/Location.js';

let mongod;
let server;
let baseUrl;

async function request(method, path, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: response.status, json };
}

describe('ResQNet MongoDB persistence', { timeout: 180000 }, () => {
  before(async () => {
    mongod = await MongoMemoryServer.create();
    await mongoose.connect(mongod.getUri());
    server = http.createServer(app);
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    const address = server.address();
    baseUrl = `http://127.0.0.1:${address.port}`;
  });

  after(async () => {
    if (server) {
      await new Promise((resolve, reject) => {
        server.close((err) => (err ? reject(err) : resolve()));
      });
    }
    await mongoose.disconnect();
    if (mongod) {
      await mongod.stop();
    }
  });

  it('GET /api/health reports connected database', async () => {
    const { status, json } = await request('GET', '/api/health');
    assert.equal(status, 200);
    assert.equal(json.status, 'ok');
    assert.equal(json.service, 'ResQNet');
    assert.equal(json.database, 'connected');
  });

  it('POST /api/emergency creates a MongoDB document and returns 201', async () => {
    const payload = {
      type: 'SOS',
      latitude: 28.4595,
      longitude: 77.0266
    };

    const { status, json } = await request('POST', '/api/emergency', payload);
    assert.equal(status, 201);
    assert.equal(json.type, 'SOS');
    assert.equal(json.latitude, 28.4595);
    assert.equal(json.longitude, 77.0266);
    assert.ok(json.id);
    assert.ok(json.timestamp);

    const stored = await Emergency.findOne({ id: json.id });
    assert.ok(stored, 'emergency should exist in MongoDB');
    assert.equal(stored.type, 'SOS');
    assert.equal(stored.latitude, 28.4595);
    assert.equal(stored.longitude, 77.0266);
  });

  it('GET /api/emergencies returns the previously persisted emergency', async () => {
    const created = await request('POST', '/api/emergency', {
      type: 'MEDICAL',
      latitude: 19.076,
      longitude: 72.8777
    });
    assert.equal(created.status, 201);

    const { status, json } = await request('GET', '/api/emergencies');
    assert.equal(status, 200);
    assert.ok(Array.isArray(json.emergencies));
    const found = json.emergencies.find((item) => item.id === created.json.id);
    assert.ok(found, 'created emergency should be returned from MongoDB');
    assert.equal(found.type, 'MEDICAL');
    assert.equal(found.latitude, 19.076);
    assert.equal(found.longitude, 72.8777);
  });

  it('POST /api/location persists a location document', async () => {
    const { status, json } = await request('POST', '/api/location', {
      latitude: 28.4595,
      longitude: 77.0266,
      accuracy: 12.5
    });
    assert.equal(status, 201);
    assert.equal(json.status, 'ok');
    assert.equal(json.data.latitude, 28.4595);
    assert.equal(json.data.longitude, 77.0266);
    assert.equal(json.data.accuracy, 12.5);

    const stored = await Location.findOne({ id: json.data.id });
    assert.ok(stored, 'location should exist in MongoDB');
    assert.equal(stored.latitude, 28.4595);
    assert.equal(stored.longitude, 77.0266);
  });

  it('rejects invalid coordinates with 400 and does not create a document', async () => {
    const beforeCount = await Emergency.countDocuments();
    const { status, json } = await request('POST', '/api/emergency', {
      type: 'SOS',
      latitude: 999,
      longitude: 77.0266
    });
    assert.equal(status, 400);
    assert.equal(json.status, 'error');
    const afterCount = await Emergency.countDocuments();
    assert.equal(afterCount, beforeCount);
  });

  it('rejects invalid longitude with 400', async () => {
    const beforeCount = await Emergency.countDocuments();
    const { status } = await request('POST', '/api/emergency', {
      type: 'SOS',
      latitude: 28.4595,
      longitude: 200
    });
    assert.equal(status, 400);
    const afterCount = await Emergency.countDocuments();
    assert.equal(afterCount, beforeCount);
  });

  it('rejects missing type with 400', async () => {
    const { status, json } = await request('POST', '/api/emergency', {
      latitude: 28.4595,
      longitude: 77.0266
    });
    assert.equal(status, 400);
    assert.match(json.message, /type/);
  });

  it('treats the same messageId as a duplicate instead of inserting twice', async () => {
    const payload = {
      type: 'SOS',
      latitude: 12.9716,
      longitude: 77.5946,
      messageId: 'MSG-DEDUP-1'
    };

    const first = await request('POST', '/api/emergency', payload);
    assert.equal(first.status, 201);

    const second = await request('POST', '/api/emergency', payload);
    assert.ok(second.status === 200 || second.status === 201);
    assert.equal(second.json.id, first.json.id);
    assert.equal(second.json.messageId, 'MSG-DEDUP-1');

    const count = await Emergency.countDocuments({ messageId: 'MSG-DEDUP-1' });
    assert.equal(count, 1);
  });

  it('preserves mesh identity fields and original timestamp', async () => {
    const payload = {
      messageId: 'TEST-123',
      sourceNodeId: 'NODE-A',
      type: 'SOS',
      latitude: 26.8467,
      longitude: 80.9462,
      timestamp: '2026-08-29T10:00:00Z'
    };

    const { status, json } = await request('POST', '/api/emergency', payload);
    assert.equal(status, 201);
    assert.equal(json.messageId, 'TEST-123');
    assert.equal(json.sourceNodeId, 'NODE-A');
    assert.equal(json.type, 'SOS');
    assert.equal(json.latitude, 26.8467);
    assert.equal(json.longitude, 80.9462);
    assert.equal(
      new Date(json.timestamp).toISOString(),
      new Date('2026-08-29T10:00:00Z').toISOString()
    );

    const stored = await Emergency.findOne({ messageId: 'TEST-123' });
    assert.ok(stored, 'MongoDB document should exist');
    assert.equal(stored.messageId, 'TEST-123');
    assert.equal(stored.sourceNodeId, 'NODE-A');
    assert.equal(stored.type, 'SOS');
    assert.equal(stored.latitude, 26.8467);
    assert.equal(stored.longitude, 80.9462);
    assert.equal(
      stored.timestamp.toISOString(),
      new Date('2026-08-29T10:00:00Z').toISOString()
    );
  });

  it('retries with the same messageId do not create a second document', async () => {
    const payload = {
      messageId: 'TEST-123-RETRY',
      sourceNodeId: 'NODE-A',
      type: 'SOS',
      latitude: 26.8467,
      longitude: 80.9462,
      timestamp: '2026-08-29T10:00:00Z'
    };

    const first = await request('POST', '/api/emergency', payload);
    const second = await request('POST', '/api/emergency', payload);
    assert.equal(first.status, 201);
    assert.equal(second.status, 200);
    assert.equal(second.json.messageId, 'TEST-123-RETRY');
    assert.equal(second.json.sourceNodeId, 'NODE-A');
    assert.equal(
      new Date(second.json.timestamp).toISOString(),
      new Date('2026-08-29T10:00:00Z').toISOString()
    );

    const count = await Emergency.countDocuments({ messageId: 'TEST-123-RETRY' });
    assert.equal(count, 1);
  });

  it('stores different messageIds as separate documents', async () => {
    const first = await request('POST', '/api/emergency', {
      messageId: 'TEST-123-A',
      sourceNodeId: 'NODE-A',
      type: 'SOS',
      latitude: 26.8467,
      longitude: 80.9462,
      timestamp: '2026-08-29T10:00:00Z'
    });
    const second = await request('POST', '/api/emergency', {
      messageId: 'TEST-456',
      sourceNodeId: 'NODE-B',
      type: 'SOS',
      latitude: 26.8467,
      longitude: 80.9462,
      timestamp: '2026-08-29T11:00:00Z'
    });
    assert.equal(first.status, 201);
    assert.equal(second.status, 201);
    assert.equal(await Emergency.countDocuments({ messageId: 'TEST-123-A' }), 1);
    assert.equal(await Emergency.countDocuments({ messageId: 'TEST-456' }), 1);
    assert.equal(
      await Emergency.countDocuments({
        messageId: { $in: ['TEST-123-A', 'TEST-456'] }
      }),
      2
    );
  });

  it('POST /api/sync accepts mesh payloads and persists emergency-shaped messages', async () => {
    const { status, json } = await request('POST', '/api/sync', {
      nodeId: 'NODE_A',
      messages: [
        {
          type: 'SOS',
          latitude: 13.0827,
          longitude: 80.2707,
          messageId: 'MSG-SYNC-1'
        }
      ]
    });
    assert.equal(status, 200);
    assert.equal(json.status, 'ok');
    assert.equal(json.nodeId, 'NODE_A');
    assert.equal(json.received, 1);

    const stored = await Emergency.findOne({ messageId: 'MSG-SYNC-1' });
    assert.ok(stored, 'sync emergency should exist in MongoDB');
  });

  it('rejects invalid sync payloads with 400', async () => {
    const { status } = await request('POST', '/api/sync', { messages: [] });
    assert.equal(status, 400);
  });

  it('GET /api/locations returns persisted locations', async () => {
    const created = await request('POST', '/api/location', {
      latitude: 8.5241,
      longitude: 76.9366
    });
    assert.equal(created.status, 201);

    const { status, json } = await request('GET', '/api/locations');
    assert.equal(status, 200);
    const found = json.locations.find((item) => item.id === created.json.data.id);
    assert.ok(found, 'created location should be returned from MongoDB');
  });

  it('rejects invalid JSON with 400', async () => {
    const response = await fetch(`${baseUrl}/api/emergency`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{not-json'
    });
    assert.equal(response.status, 400);
    const json = await response.json();
    assert.equal(json.status, 'error');
  });
});
