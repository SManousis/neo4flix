export interface RecommendationShareOwner {
  id: string;
  movieId: string;
  createdAt: string;
  expiresAt: string | null;
  revoked: boolean;
}

export interface CreatedRecommendationShare extends RecommendationShareOwner {
  publicToken: string;
  publicPath: string;
}

export interface RecommendationShareUpdate {
  expiresInDays?: number;
  revoke?: boolean;
}

export interface RecommendationShareGenre {
  id: string;
  name: string;
}

export interface PublicRecommendationMovie {
  id: string;
  title: string;
  overview: string | null;
  releaseYear: number | null;
  releaseDate: string | null;
  posterUrl: string | null;
  genres: RecommendationShareGenre[];
  averageRating: number;
  ratingCount: number;
}

export interface PublicRecommendationShare {
  id: string;
  movieId: string;
  expiresAt: string | null;
  movie: PublicRecommendationMovie;
}
