/**
 * Parse and validate a geographic coordinate.
 * @param {*} value
 * @param {string} name
 * @param {number} min
 * @param {number} max
 */
export function parseCoordinate(value, name, min, max) {
  if (value === undefined || value === null || value === '') {
    return { error: `${name} is required` };
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return { error: `${name} must be a valid number` };
  }
  if (num < min || num > max) {
    return { error: `${name} must be between ${min} and ${max}` };
  }
  return { value: num };
}

export function parseOptionalAccuracy(value) {
  if (value === undefined || value === null || value === '') {
    return { value: undefined };
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return { error: 'accuracy must be a valid number' };
  }
  if (num < 0) {
    return { error: 'accuracy must be a non-negative number' };
  }
  return { value: num };
}
