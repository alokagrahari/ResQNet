/**
 * Parse a client-supplied emergency timestamp.
 * Accepts ISO-8601 strings or epoch milliseconds.
 * Missing/empty values are left unset so the caller can apply a default.
 */
export function parseEmergencyTimestamp(value) {
  if (value === undefined || value === null || value === '') {
    return { value: undefined };
  }

  if (typeof value === 'number') {
    return parseEpochMillis(value);
  }

  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === '') {
      return { value: undefined };
    }
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) {
      return parseEpochMillis(Number(trimmed));
    }
    const parsed = new Date(trimmed);
    if (Number.isNaN(parsed.getTime())) {
      return { error: 'timestamp must be a valid date' };
    }
    return { value: parsed };
  }

  return { error: 'timestamp must be a valid date' };
}

function parseEpochMillis(num) {
  if (!Number.isFinite(num)) {
    return { error: 'timestamp must be a valid date' };
  }
  const date = new Date(num);
  if (Number.isNaN(date.getTime())) {
    return { error: 'timestamp must be a valid date' };
  }
  return { value: date };
}
