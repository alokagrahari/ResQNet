import mongoose from 'mongoose';
import { randomUUID } from 'crypto';

const locationSchema = new mongoose.Schema(
  {
    id: {
      type: String,
      default: () => randomUUID(),
      unique: true,
      index: true
    },
    userId: {
      type: String,
      trim: true,
      index: true
    },
    latitude: {
      type: Number,
      required: [true, 'latitude is required'],
      min: [-90, 'latitude must be between -90 and 90'],
      max: [90, 'latitude must be between -90 and 90']
    },
    longitude: {
      type: Number,
      required: [true, 'longitude is required'],
      min: [-180, 'longitude must be between -180 and 180'],
      max: [180, 'longitude must be between -180 and 180']
    },
    accuracy: {
      type: Number,
      min: [0, 'accuracy must be a non-negative number']
    },
    isOfflineSynced: {
      type: Boolean,
      default: false
    },
    timestamp: {
      type: Date,
      default: Date.now,
      index: true
    }
  },
  {
    timestamps: false,
    versionKey: false,
    toJSON: {
      transform: (doc, ret) => {
        delete ret._id;
        delete ret.__v;
        return ret;
      }
    },
    toObject: {
      transform: (doc, ret) => {
        delete ret._id;
        delete ret.__v;
        return ret;
      }
    }
  }
);

const Location = mongoose.model('Location', locationSchema);

export default Location;
