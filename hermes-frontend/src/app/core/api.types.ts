export type SessionStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT';
export type SessionType = 'SEARCH' | 'RESCRAPE';
export type ListingStatus = 'FOR_SALE' | 'UNDER_OFFER' | 'SOLD' | 'WITHDRAWN';

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

export interface SnapshotResponse {
  id: string;
  scrapedAt: string;
  askingPrice?: number;
  livingAreaM2?: number;
  rooms?: number;
  energyLabel?: string;
  listedOnFundaSince?: string;
  status?: ListingStatus;
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
  latestSnapshot?: SnapshotResponse;
}

export interface PricePointResponse {
  scrapedAt: string;
  askingPrice?: number;
}

export interface StatusPointResponse {
  scrapedAt: string;
  status: ListingStatus;
}

export interface ListingReportResponse {
  listingId: string;
  daysListedOnFunda?: number;
  daysInHermes: number;
  currentPrice?: number;
  initialPrice?: number;
  priceChangePct?: number;
  priceHistory: PricePointResponse[];
  statusHistory: StatusPointResponse[];
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
