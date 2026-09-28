export interface RatingResponse {
  movieId: string;
  score: number;
  createdAt: string;
  updatedAt: string;
}

export interface RatingWriteRequest {
  movieId: string;
  score: number;
}

export interface RatingSummary {
  movieId: string;
  averageRating: number | null;
  ratingCount: number;
}

export interface RatingHistoryEntry extends RatingResponse {
  movieTitle: string;
}

export interface RatingPage {
  content: RatingHistoryEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
