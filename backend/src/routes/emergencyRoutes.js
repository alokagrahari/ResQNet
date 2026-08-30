import express from 'express';
import {
  createEmergency,
  getAllEmergencies
} from '../controllers/emergencyController.js';

const router = express.Router();

router.post('/emergency', createEmergency);
router.get('/emergencies', getAllEmergencies);

export default router;
