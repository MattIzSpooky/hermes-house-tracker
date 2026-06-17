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

export interface ErrorResponse {
  error: string;
  detail: string;
}

export const TERMINAL_STATUSES: SessionStatus[] = ['COMPLETED', 'FAILED', 'TIMED_OUT'];

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

export interface FavouriteDto {
  listingId: string;
  savedAt: string;
}

export interface ChatMessageRequest {
  sessionId: string;
  message: string;
  clientId?: string;
}

export interface TokenFrame {
  type: 'TOKEN' | 'ERROR';
  content: string;
}

export interface ResultFrame {
  type: 'RESULT';
  listings: ChatListingCard[];
}
