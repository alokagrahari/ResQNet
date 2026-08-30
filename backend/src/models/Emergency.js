import mongoose from 'mongoose';
import { randomUUID } from 'crypto';

const emergencySchema = new mongoose.Schema(
  {
    id: {
      type: String,
      default: () => randomUUID(),
      unique: true,
      index: true
    },
    messageId: {
      type: String,
      trim: true,
      sparse: true,
      unique: true
    },
    sourceNodeId: {
      type: String,
      trim: true,
      index: true
    },
    type: {
      type: String,
      required: [true, 'Emergency type is required'],
      trim: true
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
    timestamp: {
      type: Date,
      default: Date.now,
      index: true
    },
    description: {
      type: String,
      default: '',
      trim: true
    },
    location: {
      type: mongoose.Schema.Types.Mixed,
      default: undefined
    },
    severity: {
      type: String,
      enum: ['low', 'medium', 'high', 'critical'],
      default: 'high'
    },
    reporterName: {
      type: String,
      default: 'Anonymous',
      trim: true
    },
    reporterContact: {
      type: String,
      default: null,
      trim: true
    },
    status: {
      type: String,
      enum: ['active', 'pending', 'resolved'],
      default: 'active'
    },
    isOfflineSynced: {
      type: Boolean,
      default: false
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

const Emergency = mongoose.model('Emergency', emergencySchema);

export default Emergency;
