import Emergency from '../models/Emergency.js';
import { isDatabaseConnected } from '../config/db.js';
import { parseCoordinate } from '../utils/coordinates.js';
import {
  isDuplicateKeyError,
  persistenceErrorResponse
} from '../utils/mongoErrors.js';
import { toEmergencyResponse } from '../utils/serializers.js';
import { parseEmergencyTimestamp } from '../utils/timestamp.js';

function optionalString(value) {
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed === '' ? undefined : trimmed;
}

function databaseUnavailable(res) {
  return res.status(503).json({
    status: 'error',
    message: 'Database unavailable'
  });
}

function buildEmergencyPayload({ type, latitude, longitude, messageId, sourceNodeId, timestamp }) {
  const payload = {
    type: String(type).trim(),
    latitude,
    longitude,
    timestamp,
    location: {
      latitude,
      longitude
    },
    isOfflineSynced: true
  };
  if (messageId) {
    payload.messageId = messageId;
  }
  if (sourceNodeId) {
    payload.sourceNodeId = sourceNodeId;
  }
  return payload;
}

/**
 * @desc    Create a new emergency alert
 * @route   POST /api/emergency
 */
export const createEmergency = async (req, res) => {
  try {
    const body = req.body || {};
    const { type, latitude, longitude } = body;
    const messageId = optionalString(body.messageId);
    const sourceNodeId = optionalString(body.sourceNodeId);

    if (!type || (typeof type === 'string' && type.trim() === '')) {
      return res.status(400).json({
        status: 'error',
        message: 'Missing required field: type'
      });
    }

    const latResult = parseCoordinate(latitude, 'latitude', -90, 90);
    if (latResult.error) {
      return res.status(400).json({ status: 'error', message: latResult.error });
    }

    const lngResult = parseCoordinate(longitude, 'longitude', -180, 180);
    if (lngResult.error) {
      return res.status(400).json({ status: 'error', message: lngResult.error });
    }

    const timeResult = parseEmergencyTimestamp(body.timestamp);
    if (timeResult.error) {
      return res.status(400).json({ status: 'error', message: timeResult.error });
    }

    if (!isDatabaseConnected()) {
      return databaseUnavailable(res);
    }

    const payload = buildEmergencyPayload({
      type,
      latitude: latResult.value,
      longitude: lngResult.value,
      messageId,
      sourceNodeId,
      timestamp: timeResult.value || new Date()
    });

    try {
      const created = await Emergency.create(payload);
      return res.status(201).json(toEmergencyResponse(created));
    } catch (error) {
      if (isDuplicateKeyError(error) && messageId) {
        const existing = await Emergency.findOne({ messageId });
        if (existing) {
          return res.status(200).json(toEmergencyResponse(existing));
        }
      }
      throw error;
    }
  } catch (error) {
    if (isDuplicateKeyError(error)) {
      const retryId = optionalString((req.body || {}).messageId);
      if (retryId) {
        const existing = await Emergency.findOne({ messageId: retryId });
        if (existing) {
          return res.status(200).json(toEmergencyResponse(existing));
        }
      }
    }

    console.error('Error creating emergency alert:', error.message);
    const mapped = persistenceErrorResponse(error);
    mapped.body.message =
      mapped.statusCode === 500
        ? 'Internal server error while creating emergency alert'
        : mapped.body.message;
    return res.status(mapped.statusCode).json(mapped.body);
  }
};

/**
 * @desc    Get all emergency alerts
 * @route   GET /api/emergencies
 */
export const getAllEmergencies = async (req, res) => {
  try {
    if (!isDatabaseConnected()) {
      return databaseUnavailable(res);
    }

    const emergencies = await Emergency.find().sort({ timestamp: -1 });
    return res.status(200).json({
      emergencies: emergencies.map(toEmergencyResponse)
    });
  } catch (error) {
    console.error('Error fetching emergency alerts:', error.message);
    const mapped = persistenceErrorResponse(error);
    mapped.body.message =
      mapped.statusCode === 500
        ? 'Internal server error while fetching emergency alerts'
        : mapped.body.message;
    return res.status(mapped.statusCode).json(mapped.body);
  }
};
