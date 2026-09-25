import { create } from 'zustand';
import { MediaItem, MediaType, UIMode } from '../types/media';

interface AppState {
  // Navigation & Mode
  mode: UIMode;
  setMode: (mode: UIMode) => void;

  // Search & Filters
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  activeCategory: string;
  setActiveCategory: (cat: string) => void;
  activeType: MediaType | 'all';
  setActiveType: (type: MediaType | 'all') => void;
  activeDuration: 'short' | 'medium' | 'long' | 'all';
  setActiveDuration: (dur: 'short' | 'medium' | 'long' | 'all') => void;
  activeProvider: string;
  setActiveProvider: (prov: string) => void;

  // Player & Modal
  selectedMedia: MediaItem | null;
  setSelectedMedia: (media: MediaItem | null) => void;
  isMuted: boolean;
  toggleMute: () => void;

  // Local user data
  favorites: MediaItem[];
  addFavorite: (item: MediaItem) => void;
  removeFavorite: (id: string) => void;
  isFavorite: (id: string) => boolean;

  watchLater: MediaItem[];
  toggleWatchLater: (item: MediaItem) => void;

  history: MediaItem[];
  addToHistory: (item: MediaItem) => void;

  // X Timeline Following
  followedProviders: string[];
  toggleFollowProvider: (providerId: string) => void;
  isFollowingProvider: (providerId: string) => void;

  // 18+ Age Gate & Disclaimers
  ageGateAccepted: boolean;
  acceptAgeGate: () => void;

  // Report Modal
  reportItem: MediaItem | null;
  setReportItem: (item: MediaItem | null) => void;

  // Load from local storage
  hydrateFromLocalStorage: () => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  mode: 'all18',
  setMode: (mode) => set({ mode }),

  searchQuery: '',
  setSearchQuery: (searchQuery) => set({ searchQuery }),

  activeCategory: 'Trending',
  setActiveCategory: (activeCategory) => set({ activeCategory }),

  activeType: 'all',
  setActiveType: (activeType) => set({ activeType }),

  activeDuration: 'all',
  setActiveDuration: (activeDuration) => set({ activeDuration }),

  activeProvider: 'all',
  setActiveProvider: (activeProvider) => set({ activeProvider }),

  selectedMedia: null,
  setSelectedMedia: (selectedMedia) => {
    if (selectedMedia) {
      get().addToHistory(selectedMedia);
    }
    set({ selectedMedia });
  },

  isMuted: true,
  toggleMute: () => set((state) => ({ isMuted: !state.isMuted })),

  favorites: [],
  addFavorite: (item) => {
    set((state) => {
      if (state.favorites.some((f) => f.id === item.id)) return state;
      const updated = [item, ...state.favorites];
      if (typeof window !== 'undefined') {
        localStorage.setItem('nexus18_favorites', JSON.stringify(updated));
      }
      return { favorites: updated };
    });
  },
  removeFavorite: (id) => {
    set((state) => {
      const updated = state.favorites.filter((f) => f.id !== id);
      if (typeof window !== 'undefined') {
        localStorage.setItem('nexus18_favorites', JSON.stringify(updated));
      }
      return { favorites: updated };
    });
  },
  isFavorite: (id) => get().favorites.some((f) => f.id === id),

  watchLater: [],
  toggleWatchLater: (item) => {
    set((state) => {
      const exists = state.watchLater.some((w) => w.id === item.id);
      const updated = exists
        ? state.watchLater.filter((w) => w.id !== item.id)
        : [item, ...state.watchLater];
      if (typeof window !== 'undefined') {
        localStorage.setItem('nexus18_watch_later', JSON.stringify(updated));
      }
      return { watchLater: updated };
    });
  },

  history: [],
  addToHistory: (item) => {
    set((state) => {
      const filtered = state.history.filter((h) => h.id !== item.id);
      const updated = [item, ...filtered].slice(0, 50);
      if (typeof window !== 'undefined') {
        localStorage.setItem('nexus18_history', JSON.stringify(updated));
      }
      return { history: updated };
    });
  },

  followedProviders: ['redtube', 'eporner', 'redgifs', 'rule34', 'spankbang'],
  toggleFollowProvider: (providerId) => {
    set((state) => {
      const exists = state.followedProviders.includes(providerId);
      const updated = exists
        ? state.followedProviders.filter((p) => p !== providerId)
        : [...state.followedProviders, providerId];
      if (typeof window !== 'undefined') {
        localStorage.setItem('nexus18_followed_providers', JSON.stringify(updated));
      }
      return { followedProviders: updated };
    });
  },
  isFollowingProvider: (providerId) => get().followedProviders.includes(providerId),

  ageGateAccepted: false,
  acceptAgeGate: () => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('nexus18_age_gate', 'accepted');
    }
    set({ ageGateAccepted: true });
  },

  reportItem: null,
  setReportItem: (reportItem) => set({ reportItem }),

  hydrateFromLocalStorage: () => {
    if (typeof window === 'undefined') return;

    try {
      const ageGate = localStorage.getItem('nexus18_age_gate');
      const favs = localStorage.getItem('nexus18_favorites');
      const wl = localStorage.getItem('nexus18_watch_later');
      const hist = localStorage.getItem('nexus18_history');
      const followed = localStorage.getItem('nexus18_followed_providers');

      set({
        ageGateAccepted: ageGate === 'accepted',
        favorites: favs ? JSON.parse(favs) : [],
        watchLater: wl ? JSON.parse(wl) : [],
        history: hist ? JSON.parse(hist) : [],
        followedProviders: followed
          ? JSON.parse(followed)
          : ['redtube', 'eporner', 'redgifs', 'rule34', 'spankbang'],
      });
    } catch (e) {
      console.warn('Error reading from localStorage', e);
    }
  },
}));
