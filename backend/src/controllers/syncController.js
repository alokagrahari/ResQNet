import Emergency from '../models/Emergency.js';
import Location from '../models/Location.js';
import { isDatabaseConnected } from '../config/db.js';
import { parseCoordinate, parseOptionalAccuracy } from '../utils/coordinates.js';
import {
  isDuplicateKeyError,
  persistenceErrorResponse
} from '../utils/mongoErrors.js';
import { parseEmergencyTimestamp } from '../utils/timestamp.js';

function optionalMessageId(value) {
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

function looksLikeEmergency(item) {
  return Boolean(item && typeof item === 'object' && item.type);
}

function looksLikeLocation(item) {
  return Boolean(
    item &&
      typeof item === 'object' &&
      (item.latitude !== undefined || item.longitude !== undefined)
  );
}

async function persistEmergencyMessage(item) {
  const type = item.type;
  if (!type || (typeof type === 'string' && type.trim() === '')) {
    return;
  }

  const latResult = parseCoordinate(item.latitude, 'latitude', -90, 90);
  const lngResult = parseCoordinate(item.longitude, 'longitude', -180, 180);
  if (latResult.error || lngResult.error) {
    return;
  }

  const messageId = optionalMessageId(item.messageId);
  const sourceNodeId = optionalMessageId(item.sourceNodeId);
  const timeResult = parseEmergencyTimestamp(item.timestamp);

  const payload = {
    type: String(type).trim(),
    latitude: latResult.value,
    longitude: lngResult.value,
    timestamp: timeResult.value || new Date(),
    location: {
      latitude: latResult.value,
      longitude: lngResult.value
    },
    isOfflineSynced: true
  };

  if (messageId) {
    payload.messageId = messageId;
  }
  if (sourceNodeId) {
    payload.sourceNodeId = sourceNodeId;
  }

  try {
    await Emergency.create(payload);
  } catch (error) {
    if (isDuplicateKeyError(error)) {
      return;
    }
    throw error;
  }
}

async function persistLocationMessage(item) {
  const latResult = parseCoordinate(item.latitude, 'latitude', -90, 90);
  const lngResult = parseCoordinate(item.longitude, 'longitude', -180, 180);
  if (latResult.error || lngResult.error) {
    return;
  }

  const payload = {
    latitude: latResult.value,
    longitude: lngResult.value,
    timestamp: new Date(),
    isOfflineSynced: true
  };

  const accuracyResult = parseOptionalAccuracy(item.accuracy);
  if (!accuracyResult.error && accuracyResult.value !== undefined) {
    payload.accuracy = accuracyResult.value;
  }

  if (typeof item.userId === 'string' && item.userId.trim() !== '') {
    payload.userId = item.userId.trim();
  }

  await Location.create(payload);
}

/**
 * @desc    Sync payload from a ResQNet mesh node
 * @route   POST /api/sync
 */
export const syncOfflineData = async (req, res) => {
  try {
    const body = req.body || {};
    const { nodeId, messages } = body;

    if (!nodeId || (typeof nodeId === 'string' && nodeId.trim() === '')) {
      return res.status(400).json({
        status: 'error',
        message: 'Missing required field: nodeId'
      });
    }

    if (messages !== undefined && !Array.isArray(messages)) {
      return res.status(400).json({
        status: 'error',
        message: 'messages must be an array when provided'
      });
    }

    if (!isDatabaseConnected()) {
      return res.status(503).json({
        status: 'error',
        message: 'Database unavailable'
      });
    }

    const messageList = Array.isArray(messages) ? messages : [];

    for (const item of messageList) {
      if (looksLikeEmergency(item)) {
        await persistEmergencyMessage(item);
      } else if (looksLikeLocation(item)) {
        await persistLocationMessage(item);
      }
    }

    return res.status(200).json({
      status: 'ok',
      message: 'Sync accepted',
      nodeId: String(nodeId).trim(),
      received: messageList.length
    });
  } catch (error) {
    console.error('Error syncing offline data:', error.message);
    const mapped = persistenceErrorResponse(error);
    mapped.body.message =
      mapped.statusCode === 500
        ? 'Internal server error while synchronizing data'
        : mapped.body.message;
    return res.status(mapped.statusCode).json(mapped.body);
  }
};
