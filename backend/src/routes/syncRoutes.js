import express from 'express';
import { syncOfflineData } from '../controllers/syncController.js';

const router = express.Router();

router.post('/sync', syncOfflineData);

export default router;
