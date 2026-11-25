export type DocumentFormat = 'EPUB' | 'PDF' | 'DOCX' | 'PPTX' | 'TXT' | 'FB2';

export type ReadingThemeId =
  | 'warm_cream'
  | 'sepia'
  | 'sand'
  | 'amber_soft'
  | 'peach'
  | 'sage'
  | 'lemon'
  | 'light'
  | 'lavender'
  | 'sky'
  | 'rose'
  | 'warm_night'
  | 'forest_night'
  | 'navy_mid'
  | 'midnight'
  | 'dark'
  | 'amoled';

export type ReaderFontId = 'literata' | 'plus_jakarta' | 'lexend' | 'jetbrains_mono';

export type InterfaceDensity = 'auto' | 'compact' | 'comfortable' | 'spacious';

export type ViewportTier = 'compact' | 'tablet' | 'laptop' | 'desktop' | 'ultrawide';

export type BlockType = 'h1' | 'h2' | 'p' | 'quote' | 'list';

export interface ParsedBlock {
  id: string;
  type: BlockType;
  text: string;
}

export interface ParsedChapter {
  id: string;
  title: string;
  blocks: ParsedBlock[];
}

export interface ManhwaBubble {
  id: string;
  x: number; // 0..100%
  y: number;
  w: number;
  h: number;
  original: string;
  translated: string;
}

export interface ManhwaPanel {
  id: string;
  episodeLabel: string;
  gradientFrom: string;
  gradientTo: string;
  accentColor: string;
  sceneCaption: string;
  bubbles: ManhwaBubble[];
}

export interface SlidePage {
  id: string;
  title: string;
  subtitle?: string;
  bgColor: string;
  textColor: string;
  accentColor: string;
  bullets: string[];
  notes: string;
}

export interface HighlightNote {
  id: string;
  documentId: string;
  chapterIndex: number;
  quotedText: string;
  noteText: string;
  color: 'amber' | 'cobalt' | 'mint' | 'coral';
  createdAt: number;
}

export interface BookmarkItem {
  id: string;
  documentId: string;
  unitIndex: number;
  label: string;
  createdAt: number;
}

export interface InkPoint {
  x: number; // normalized 0..1
  y: number;
}

export interface InkStroke {
  id: string;
  tool: 'pen' | 'highlighter';
  color: string;
  width: number;
  opacity: number;
  points: InkPoint[];
}

export interface LumaDocument {
  id: string;
  title: string;
  author: string;
  format: DocumentFormat;
  badgeTag: string;
  coverColor: string;
  coverAccent: string;
  genre: string;
  year: string;
  synopsis: string;
  totalWords: number;
  currentUnit: number;
  progressPercent: number;
  pinned?: boolean;
  lastOpenedAt: number;
  addedAt: number;
  chapters: ParsedChapter[];
  slides?: SlidePage[];
  manhwaPanels?: ManhwaPanel[];
  pdfDataUrl?: string;
  annotationsByUnit?: Record<number, InkStroke[]>;
}

export interface SagaPlaylist {
  id: string;
  name: string;
  description: string;
  accentColor: string;
  documentIds: string[];
  createdAt: number;
}

export type NotebookPaperPattern = 'blank' | 'ruled' | 'grid' | 'dotted' | 'cornell';

export type NotebookPageSize = 'A4' | 'LETTER' | 'WIDE_16_9' | 'SQUARE';

export type CanvasTool =
  | 'select'
  | 'pen'
  | 'highlighter'
  | 'eraser'
  | 'text'
  | 'rect'
  | 'ellipse'
  | 'arrow'
  | 'line'
  | 'sticky'
  | 'pan';

export interface NotebookLayer {
  id: string;
  name: string;
  visible: boolean;
  locked: boolean;
}

export interface BaseCanvasObject {
  id: string;
  layerId: string;
  x: number; // 0..1000 virtual coordinate space width-normalized
  y: number;
  w: number;
  h: number;
  opacity: number;
}

export interface StrokeCanvasObject extends BaseCanvasObject {
  kind: 'stroke';
  tool: 'pen' | 'highlighter';
  color: string;
  strokeWidth: number;
  points: Array<{ x: number; y: number }>; // in page pixel space (0..pageW, 0..pageH)
}

export interface TextCanvasObject extends BaseCanvasObject {
  kind: 'text';
  text: string;
  fontFamily: 'sans' | 'serif' | 'mono' | 'hand';
  fontSize: number;
  bold: boolean;
  color: string;
  fillColor: string;
  borderColor: string;
  align: 'left' | 'center' | 'right';
  isBanner?: boolean;
}

export interface ShapeCanvasObject extends BaseCanvasObject {
  kind: 'shape';
  shape: 'rect' | 'ellipse' | 'arrow' | 'line';
  strokeColor: string;
  fillColor: string;
  strokeWidth: number;
  label?: string;
}

export interface StickyCanvasObject extends BaseCanvasObject {
  kind: 'sticky';
  text: string;
  bgColor: string;
  textColor: string;
}

export interface TableCanvasObject extends BaseCanvasObject {
  kind: 'table';
  rows: number;
  cols: number;
  cells: string[];
  headerBg: string;
  borderColor: string;
}

export interface StickerCanvasObject extends BaseCanvasObject {
  kind: 'sticker';
  symbol: string;
  label: string;
  color: string;
  bgColor: string;
}

export type CanvasObject =
  | StrokeCanvasObject
  | TextCanvasObject
  | ShapeCanvasObject
  | StickyCanvasObject
  | TableCanvasObject
  | StickerCanvasObject;

export interface NotebookPage {
  id: string;
  title: string;
  pattern: NotebookPaperPattern;
  backgroundColor: string;
  objects: CanvasObject[];
}

export interface LumaNotebook {
  id: string;
  title: string;
  subtitle: string;
  coverColor: string;
  pageSize: NotebookPageSize;
  pinned?: boolean;
  updatedAt: number;
  layers: NotebookLayer[];
  pages: NotebookPage[];
  activePageIndex: number;
}

export type WorkspaceTabKind = 'home' | 'library' | 'sagas' | 'notebooks' | 'reader' | 'studio';

export interface WorkspaceTab {
  id: string;
  kind: WorkspaceTabKind;
  title: string;
  targetId?: string;
}

export interface WorkspacePreferences {
  theme: ReadingThemeId;
  readerFont: ReaderFontId;
  fontSizePx: number;
  lineHeight: number;
  pageWidthCh: number;
  textAlign: 'left' | 'justify';
  twoPageSpread: boolean;
  warmFilterStrength: number; // 0..0.45
  nightDimming: number; // 0..0.35
  density: InterfaceDensity;
  sidebarWidth: number;
  sidebarCollapsed: boolean;
  showInspector: boolean;
}

export interface LumaWorkspaceState {
  documents: LumaDocument[];
  playlists: SagaPlaylist[];
  notebooks: LumaNotebook[];
  highlights: HighlightNote[];
  bookmarks: BookmarkItem[];
  prefs: WorkspacePreferences;
}

export interface PickedDesktopFile {
  name: string;
  path?: string;
  size: number;
  base64: string;
}

export interface LumaDesktopBridge {
  isElectron: boolean;
  getPlatform: () => Promise<string>;
  minimize: () => Promise<void>;
  toggleMaximize: () => Promise<boolean>;
  close: () => Promise<void>;
  isMaximized: () => Promise<boolean>;
  onWindowState: (cb: (state: { maximized: boolean }) => void) => () => void;
  loadWorkspace: () => Promise<string | null>;
  saveWorkspace: (jsonString: string) => Promise<boolean>;
  pickFiles: (mode?: 'document' | 'image') => Promise<PickedDesktopFile[]>;
  saveExport: (payload: {
    defaultName: string;
    data: string;
    encoding: 'base64' | 'utf8';
  }) => Promise<{ saved: boolean; filePath?: string }>;
  openExternal: (url: string) => Promise<boolean>;
}

declare global {
  interface Window {
    lumaDesktop?: LumaDesktopBridge;
  }
}
