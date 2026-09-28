export interface RecommendationFilters {
  genre?: string | null;
  fromYear?: number | null;
  toYear?: number | null;
  minimumAverageRating?: number | null;
  sort?: 'recommendation' | 'rating' | 'newest' | string | null;
  page?: number | null;
  size?: number | null;
}

export interface RecommendationGenre {
  id: string;
  name: string;
}

export interface RecommendationMovie {
  id: string;
  title: string;
  overview: string | null;
  releaseYear: number | null;
  releaseDate: string | null;
  posterUrl: string | null;
  genres: RecommendationGenre[];
  averageRating: number;
  ratingCount: number;
}

export interface RecommendationSignals {
  collaborative: number;
  content: number;
  popularity: number;
}

export type RecommendationStrategy = 'HYBRID' | 'CONTENT_PLUS_POPULARITY' | 'POPULARITY';
export type RecommendationReasonType = 'SIMILAR_USERS' | 'GENRE_MATCH' | 'POPULAR';

export interface RecommendationReason {
  type: RecommendationReasonType;
  text: string;
}

export interface RecommendationItem {
  movie: RecommendationMovie;
  recommendationScore: number;
  signals: RecommendationSignals;
  strategy: RecommendationStrategy;
  reason: RecommendationReason;
}

export interface RecommendationResponse {
  items: RecommendationItem[];
  strategy: RecommendationStrategy;
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}
