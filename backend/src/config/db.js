import mongoose from 'mongoose';

/**
 * Connect to MongoDB using MONGO_URI from the environment.
 * The URI must be provided via environment configuration; it is never hardcoded.
 */
const connectDB = async () => {
  const mongoUri = process.env.MONGO_URI;

  if (!mongoUri || typeof mongoUri !== 'string' || mongoUri.trim() === '') {
    const error = new Error(
      'MONGO_URI is not set. Copy .env.example to .env and set MONGO_URI.'
    );
    console.error(error.message);
    throw error;
  }

  try {
    const conn = await mongoose.connect(mongoUri.trim());
    console.log(`MongoDB Connected: ${conn.connection.host}/${conn.connection.name}`);
    return conn;
  } catch (error) {
    console.error(`MongoDB Connection Error: ${error.message}`);
    throw error;
  }
};

export const disconnectDB = async () => {
  await mongoose.disconnect();
};

export const isDatabaseConnected = () => mongoose.connection.readyState === 1;

export const getDatabaseStatus = () =>
  isDatabaseConnected() ? 'connected' : 'disconnected';

export default connectDB;
