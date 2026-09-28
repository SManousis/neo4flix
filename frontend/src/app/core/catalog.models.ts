export interface GenreSummary { id: string; name: string; }
export interface MovieSummary {
  id: string; title: string; overview: string; releaseYear: number;
  releaseDate: string | null; posterUrl: string | null; genres: GenreSummary[];
  averageRating: number; ratingCount: number;
}
export interface MovieDetail extends MovieSummary {
  runtimeMinutes: number | null; externalSource: string | null; externalId: string | null;
  createdAt: string; updatedAt: string;
}
export interface MovieWrite {
  title: string; overview: string; releaseYear: number; releaseDate: string | null;
  runtimeMinutes: number | null; posterUrl: string | null; externalSource: string | null;
  externalId: string | null; genreIds: string[];
}
export interface PageResult<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

export interface CatalogFilters {
  title?: string;
  genre?: string;
  minYear?: number;
  maxYear?: number;
  fromDate?: string;
  sort?: string;
  direction?: 'asc' | 'desc';
  page?: number;
  size?: number;
}
