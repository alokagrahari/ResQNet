import Location from '../models/Location.js';
import { isDatabaseConnected } from '../config/db.js';
import { parseCoordinate, parseOptionalAccuracy } from '../utils/coordinates.js';
import { persistenceErrorResponse } from '../utils/mongoErrors.js';
import { toLocationResponse } from '../utils/serializers.js';

function databaseUnavailable(res) {
  return res.status(503).json({
    status: 'error',
    message: 'Database unavailable'
  });
}

/**
 * @desc    Receive and store a location
 * @route   POST /api/location
 */
export const storeLocation = async (req, res) => {
  try {
    const body = req.body || {};
    const { latitude, longitude } = body;

    const latResult = parseCoordinate(latitude, 'latitude', -90, 90);
    if (latResult.error) {
      return res.status(400).json({ status: 'error', message: latResult.error });
    }

    const lngResult = parseCoordinate(longitude, 'longitude', -180, 180);
    if (lngResult.error) {
      return res.status(400).json({ status: 'error', message: lngResult.error });
    }

    const accuracyResult = parseOptionalAccuracy(body.accuracy);
    if (accuracyResult.error) {
      return res.status(400).json({ status: 'error', message: accuracyResult.error });
    }

    if (!isDatabaseConnected()) {
      return databaseUnavailable(res);
    }

    const payload = {
      latitude: latResult.value,
      longitude: lngResult.value,
      timestamp: new Date(),
      isOfflineSynced: true
    };

    if (accuracyResult.value !== undefined) {
      payload.accuracy = accuracyResult.value;
    }

    if (typeof body.userId === 'string' && body.userId.trim() !== '') {
      payload.userId = body.userId.trim();
    }

    if (body.timestamp) {
      const parsed = new Date(body.timestamp);
      if (!Number.isNaN(parsed.getTime())) {
        payload.timestamp = parsed;
      }
    }

    const created = await Location.create(payload);
    return res.status(201).json({
      status: 'ok',
      message: 'Location recorded successfully',
      data: toLocationResponse(created)
    });
  } catch (error) {
    console.error('Error storing location:', error.message);
    const mapped = persistenceErrorResponse(error);
    mapped.body.message =
      mapped.statusCode === 500
        ? 'Internal server error while storing location'
        : mapped.body.message;
    return res.status(mapped.statusCode).json(mapped.body);
  }
};

/**
 * @desc    Get all stored locations
 * @route   GET /api/locations
 */
export const getAllLocations = async (req, res) => {
  try {
    if (!isDatabaseConnected()) {
      return databaseUnavailable(res);
    }

    const locations = await Location.find().sort({ timestamp: -1 });
    return res.status(200).json({
      locations: locations.map(toLocationResponse)
    });
  } catch (error) {
    console.error('Error fetching locations:', error.message);
    const mapped = persistenceErrorResponse(error);
    mapped.body.message =
      mapped.statusCode === 500
        ? 'Internal server error while fetching locations'
        : mapped.body.message;
    return res.status(mapped.statusCode).json(mapped.body);
  }
};
