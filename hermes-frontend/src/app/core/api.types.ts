export type SessionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
export type SessionType = 'SEARCH' | 'RESCRAPE';
export type ListingStatus = 'FOR_SALE' | 'UNDER_OFFER' | 'SOLD' | 'WITHDRAWN';

export interface ListingSearchFilter {
  street?: string;
  houseNumber?: string;
  houseNumberAddition?: string;
  zipCode?: string;
  city?: string;
  province?: string;
  minBedrooms?: number | null;
  minRooms?: number | null;
  minLivingAreaM2?: number | null;
  energyLabel?: string | null;
  radiusKm?: number | null;
}

export interface ScrapingSessionResponse {
  id: string;
  status: SessionStatus;
  type: SessionType;
  createdAt: string;
  completedAt?: string;
}

export interface CreateScrapingSessionRequest {
  city: string;
  minPrice?: number;
  maxPrice?: number;
  minArea?: number;
  maxArea?: number;
  pageLimit: number;
}

export interface GeocodingBackfillResponse {
  queuedCount: number;
}

export interface AddressResponse {
  street?: string | null;
  houseNumber?: string | null;
  houseNumberAddition?: string | null;
  zipCode?: string | null;
  city?: string | null;
  province?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

export interface UpdateAddressRequest {
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode?: string;
  city: string;
  province?: string;
}

export interface GeoLocation {
  latitude: number;
  longitude: number;
  bboxLatMin?: number | null;
  bboxLatMax?: number | null;
  bboxLonMin?: number | null;
  bboxLonMax?: number | null;
}

export interface ListingPage {
  content: ListingSummaryResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ListingSummaryResponse {
  id: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  askingPrice?: number;
  status?: ListingStatus;
  firstSeenAt: string;
  location?: GeoLocation | null;
}

export interface ListingDetailResponse {
  id: string;
  fundaId: string;
  url: string;
  street: string;
  houseNumber: string;
  houseNumberAddition?: string;
  zipCode: string;
  city: string;
  province: string;
  firstSeenAt: string;
  lastSeenAt: string;
  currentPrice?: number;
  status?: ListingStatus;
  description?: string | null;
  livingAreaM2?: number | null;
  plotAreaM2?: number | null;
  rooms?: number | null;
  bedrooms?: number | null;
  energyLabel?: string | null;
  location?: GeoLocation | null;
}

export interface PricePointResponse {
  timestamp: string;
  price?: number;
}

export interface ListingReportResponse {
  listingId: string;
  daysInHermes: number;
  currentPrice?: number;
  initialPrice?: number;
  priceChangePct?: number;
  priceHistory: PricePointResponse[];
  currentStatus?: string;
}

export interface AiSummaryResponse {
  listingId: string;
  summary: string;
  generatedAt: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];

export function isSessionPolling(session: { status: SessionStatus } | null): boolean {
  return session !== null && !TERMINAL_STATUSES.includes(session.status);
}

export function isSessionTerminal(session: { status: SessionStatus } | null): boolean {
  return session !== null && TERMINAL_STATUSES.includes(session.status);
}

export interface ChatListingCard {
  id: string;
  street?: string;
  houseNumber?: string;
  houseNumberAddition?: string;
  city?: string;
  province?: string;
  currentPrice?: number;
  bedrooms?: number;
  livingAreaM2?: number;
  energyLabel?: string;
  status?: string;
}

export interface FavoriteDto {
  listingId: string;
  savedAt: string;
}

export interface ChatMessageRequest {
  sessionId: string;
  message: string;
}

export interface TokenFrame {
  type: 'TOKEN' | 'ERROR';
  content: string;
}

export interface ResultFrame {
  type: 'RESULT';
  listings: ChatListingCard[];
}

export interface AgentTaskResponse {
  id: string;
  type: string;
  status: string;
  userId: string;
  name: string;
  schedule?: string;
  lastRunAt?: string;
  nextRunAt?: string;
  createdAt?: string;
}

export interface NotificationResponse {
  id: string;
  taskId?: string;
  userId: string;
  title: string;
  body: string;
  listingIds: string[];
  read: boolean;
  createdAt: string;
  emailSentAt?: string;
}

export interface UnreadCountResponse {
  count: number;
}

export interface ChatSessionSummaryResponse {
  sessionId: string;
  title: string;
  lastMessageAt: string;
}

export interface ChatMessageResponse {
  role: string;
  content: string;
  createdAt: string;
}
