import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import app from './app.js';
import connectDB from './config/db.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(__dirname, '../.env') });

const PORT = process.env.PORT || 5000;

async function start() {
  try {
    await connectDB();
  } catch {
    console.error('Failed to start server: MongoDB connection failed.');
    process.exit(1);
  }

  app.listen(PORT, () => {
    console.log(`ResQNet backend server is running on port ${PORT}`);
    console.log(`Health check: http://localhost:${PORT}/api/health`);
  });
}

start();
