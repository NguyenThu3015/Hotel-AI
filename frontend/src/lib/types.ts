export interface Tokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface AuthChallenge {
  challengeId: string;
  maskedDestination: string;
  expiresAt: string;
}

export interface UserProfile {
  id: number | string;
  identityId?: string;
  email: string;
  fullName: string;
  phone?: string;
  status: string;
  emailVerified: boolean;
  roles: string[];
  permissions?: string[];
}

export interface DestinationCatalogItem {
  code: string;
  name: string;
  province: string;
  region: "BAC" | "TRUNG" | "NAM";
  regionLabel: string;
  styleTag: string;
  propertyName: string;
  landmark: string;
  description: string;
  displayOrder: number;
  active: boolean;
  roomTypeCount: number;
  availableRooms: number;
  minNightlyPrice?: number | null;
}

export interface RoomOption {
  roomTypeId: number | string;
  code: string;
  name: string;
  description: string;
  maxAdults: number;
  maxChildren: number;
  availableRooms: number;
  roomSubtotal: number;
  taxAmount: number;
  serviceFee: number;
  totalAmount: number;
  imageUrl?: string;
  amenities: string[];
  cancellationPolicy: string;
  recommendationScore?: number;
  reasons?: string[];
  destinationCode?: string;
  destinationName?: string;
  province?: string;
  region?: string;
  regionLabel?: string;
  styleTag?: string;
  propertyName?: string;
  landmark?: string;
}

export interface Booking {
  id: number | string;
  bookingCode: string;
  status: string;
  hotelId: number;
  checkIn: string;
  checkOut: string;
  totalAmount: number;
  paidAmount: number;
  adults?: number;
  children?: number;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  holdExpiresAt?: string;
  version?: number;
  destinationCode?: string;
  destinationName?: string;
  propertyName?: string;
  rooms: Array<{
    bookingRoomId?: number;
    roomTypeId?: number | string;
    roomType: string;
    roomNumber?: string;
    destinationCode?: string;
    destinationName?: string;
    propertyName?: string;
  }>;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface Reservation {
  id: string;
  reference: string;
  status: string;
  roomTypeId: string;
  checkIn: string;
  checkOut: string;
  roomCount: number;
  adults: number;
  children: number;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  specialRequests?: string;
  currency: string;
  totalAmount: number;
  depositRequired: number;
  paidAmount: number;
  cancellationPolicy?: unknown;
  version: number;
}

export interface Payment {
  id: string;
  reservationId: string;
  purpose: string;
  provider: string;
  status: string;
  amount: number;
  currency: string;
  providerOrderId: string;
  checkoutUrl?: string;
  providerTransactionId?: string;
  expiresAt?: string;
  completedAt?: string;
}

export interface BankTransferInstructions {
  bankId: string;
  accountNumber: string;
  accountName: string;
  amount: number;
  currency: string;
  transferContent: string;
  qrUrl: string;
  expiresAt?: string;
}

export interface Room {
  id: number | string;
  roomTypeId: number | string;
  roomNumber: string;
  floor?: string;
  floorId?: string;
  status: string;
  version?: number;
  destinationCode?: string;
  destinationName?: string;
  propertyName?: string;
}

export interface RoomType {
  id: number | string;
  hotelId: number;
  code: string;
  name: string;
  description?: string;
  maxAdults: number;
  maxChildren: number;
  basePrice: number;
  sizeM2?: number;
  bedType?: string;
  amenities: string[];
  published?: boolean;
  active?: boolean;
  version?: number;
  destinationCode?: string;
  destinationName?: string;
  province?: string;
  regionLabel?: string;
  styleTag?: string;
  propertyName?: string;
  landmark?: string;
}

export interface Service {
  id: number | string;
  hotelId: number;
  code: string;
  name: string;
  unit: string;
  price: number;
}

export interface Review {
  id: number | string;
  bookingCode: string;
  rating: number;
  comment?: string;
  status: string;
  sentiment?: string;
  sentimentScore?: number;
  sentimentTopics: string[];
  urgency?: string;
  aiConfidence?: number;
  aiDraftResponse?: string;
  aiModelVersion?: string;
  aiProvider?: string;
  hotelResponse?: string;
  createdAt: string;
}
