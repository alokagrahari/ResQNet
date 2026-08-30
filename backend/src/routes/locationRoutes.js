import express from 'express';
import {
  storeLocation,
  getAllLocations
} from '../controllers/locationController.js';

const router = express.Router();

router.post('/location', storeLocation);
router.get('/locations', getAllLocations);

export default router;
