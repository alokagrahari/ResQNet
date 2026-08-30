import express from 'express';
import cors from 'cors';
import emergencyRoutes from './routes/emergencyRoutes.js';
import locationRoutes from './routes/locationRoutes.js';
import syncRoutes from './routes/syncRoutes.js';
import { getDatabaseStatus, isDatabaseConnected } from './config/db.js';

const app = express();

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Health check: HTTP process plus MongoDB connection state
app.get('/api/health', (req, res) => {
  const connected = isDatabaseConnected();
  return res.status(connected ? 200 : 503).json({
    status: connected ? 'ok' : 'error',
    service: 'ResQNet',
    database: getDatabaseStatus()
  });
});

// API Routes
app.use('/api', emergencyRoutes);
app.use('/api', locationRoutes);
app.use('/api', syncRoutes);

// 404 handler for undefined routes
app.use((req, res) => {
  res.status(404).json({
    status: 'error',
    message: 'Route not found'
  });
});

// Global error handler (incl. invalid JSON bodies)
app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && 'body' in err) {
    return res.status(400).json({
      status: 'error',
      message: 'Invalid JSON in request body'
    });
  }

  console.error('Unhandled error:', err.message);
  return res.status(500).json({
    status: 'error',
    message: 'Internal server error'
  });
});

export default app;
