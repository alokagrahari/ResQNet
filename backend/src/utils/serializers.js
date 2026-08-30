function toIsoString(value) {
  if (!value) {
    return new Date().toISOString();
  }
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}

/**
 * Public emergency payload. Matches the existing Android-compatible contract.
 */
export function toEmergencyResponse(doc) {
  const obj = doc && typeof doc.toObject === 'function' ? doc.toObject() : doc;
  const response = {
    id: obj.id,
    type: obj.type,
    latitude: obj.latitude,
    longitude: obj.longitude,
    timestamp: toIsoString(obj.timestamp)
  };
  if (obj.messageId) {
    response.messageId = obj.messageId;
  }
  if (obj.sourceNodeId) {
    response.sourceNodeId = obj.sourceNodeId;
  }
  return response;
}

/**
 * Public location payload. Matches the existing API contract.
 */
export function toLocationResponse(doc) {
  const obj = doc && typeof doc.toObject === 'function' ? doc.toObject() : doc;
  const response = {
    id: obj.id,
    latitude: obj.latitude,
    longitude: obj.longitude,
    timestamp: toIsoString(obj.timestamp)
  };
  if (obj.accuracy !== undefined && obj.accuracy !== null) {
    response.accuracy = obj.accuracy;
  }
  if (obj.userId) {
    response.userId = obj.userId;
  }
  return response;
}
