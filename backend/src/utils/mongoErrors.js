const DUPLICATE_KEY_CODE = 11000;

export function isDuplicateKeyError(error) {
  return Boolean(error) && error.code === DUPLICATE_KEY_CODE;
}

export function isMongoUnavailableError(error) {
  if (!error) {
    return false;
  }
  const name = error.name || '';
  return (
    name === 'MongoServerSelectionError' ||
    name === 'MongoNetworkError' ||
    name === 'MongoTimeoutError' ||
    name === 'MongoNetworkTimeoutError' ||
    name === 'MongooseServerSelectionError'
  );
}

export function isMongooseValidationError(error) {
  return Boolean(error) && error.name === 'ValidationError';
}

/**
 * Map persistence failures to a client-safe HTTP response.
 * Never includes connection strings, credentials, or driver internals.
 */
export function persistenceErrorResponse(error) {
  if (isMongooseValidationError(error)) {
    const first = error.errors && Object.values(error.errors)[0];
    return {
      statusCode: 400,
      body: {
        status: 'error',
        message: (first && first.message) || 'Validation failed'
      }
    };
  }

  if (isMongoUnavailableError(error)) {
    return {
      statusCode: 503,
      body: {
        status: 'error',
        message: 'Database unavailable'
      }
    };
  }

  return {
    statusCode: 500,
    body: {
      status: 'error',
      message: 'Internal server error'
    }
  };
}
