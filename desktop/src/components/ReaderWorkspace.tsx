import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Bookmark,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  Columns2,
  Eraser,
  ExternalLink,
  FileText,
  Highlighter,
  Languages,
  List,
  MessageSquarePlus,
  PanelRight,
  PenTool,
  Trash2,
  Type,
  X,
} from 'lucide-react';
import {
  BookmarkItem,
  HighlightNote,
  InkStroke,
  LumaDocument,
  ReaderFontId,
  WorkspacePreferences,
} from '../types';
import { LUMA_THEMES } from '../themes';
import { ViewportProfile } from '../hooks/useViewportProfile';

interface ReaderWorkspaceProps {
  document: LumaDocument;
  prefs: WorkspacePreferences;
  viewport: ViewportProfile;
  highlights: HighlightNote[];
  bookmarks: BookmarkItem[];
  compactSplit?: boolean;
  onUpdateDocument: (updater: (doc: LumaDocument) => LumaDocument) => void;
  onUpdatePrefs: (patch: Partial<WorkspacePreferences>) => void;
  onAddHighlight: (item: Omit<HighlightNote, 'id' | 'createdAt'>) => void;
  onDeleteHighlight: (id: string) => void;
  onToggleBookmark: (docId: string, unitIndex: number, label: string) => void;
  onOpenAppearance: () => void;
}

const HIGHLIGHT_STYLES: Record<HighlightNote['color'], { label: string; bg: string; dot: string }> = {
  amber: { label: 'Ámbar', bg: 'rgba(246, 201, 87, 0.38)', dot: '#F6C957' },
  cobalt: { label: 'Cobalto', bg: 'rgba(107, 166, 255, 0.32)', dot: '#2B64F5' },
  mint: { label: 'Menta', bg: 'rgba(57, 198, 137, 0.30)', dot: '#257A5E' },
  coral: { label: 'Coral', bg: 'rgba(255, 122, 133, 0.30)', dot: '#E11D48' },
};

const FONT_MAP: Record<ReaderFontId, { label: string; css: string }> = {
  literata: { label: 'Literata', css: 'var(--font-serif)' },
  plus_jakarta: { label: 'Plus Jakarta', css: 'var(--font-sans)' },
  lexend: { label: 'Lexend', css: 'var(--font-dyslexic)' },
  jetbrains_mono: { label: 'JetBrains Mono', css: 'var(--font-mono)' },
};

export const ReaderWorkspace: React.FC<ReaderWorkspaceProps> = ({
  document: doc,
  prefs,
  viewport,
  highlights,
  bookmarks,
  compactSplit = false,
  onUpdateDocument,
  onUpdatePrefs,
  onAddHighlight,
  onDeleteHighlight,
  onToggleBookmark,
  onOpenAppearance,
}) => {
  const [unitIndex, setUnitIndex] = useState(doc.currentUnit || 0);
  const [inspectorTab, setInspectorTab] = useState<'toc' | 'notes' | 'bookmarks' | 'typo'>('toc');
  const [translateManhwa, setTranslateManhwa] = useState(false);
  const [sheetView, setSheetView] = useState(doc.format === 'DOCX' || doc.format === 'PPTX');
  const [drawEnabled, setDrawEnabled] = useState(false);
  const [drawTool, setDrawTool] = useState<'pen' | 'highlighter'>('pen');
  const [drawColor, setDrawColor] = useState('#2B64F5');
  const [activeStroke, setActiveStroke] = useState<InkStroke | null>(null);
  const [selectionText, setSelectionText] = useState('');
  const [noteDraft, setNoteDraft] = useState('');
  const [showNoteInput, setShowNoteInput] = useState(false);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setUnitIndex(doc.currentUnit || 0);
    setSheetView(doc.format === 'DOCX' || doc.format === 'PPTX');
    setSelectionText('');
    setShowNoteInput(false);
  }, [doc.id]);

  const totalUnits = doc.slides?.length
    ? doc.slides.length
    : Math.max(1, doc.chapters.length);

  const safeUnit = Math.min(Math.max(0, unitIndex), totalUnits - 1);
  const currentChapter = doc.chapters[safeUnit] ?? doc.chapters[0];
  const currentSlide = doc.slides?.[safeUnit];

  const docHighlights = useMemo(
    () => highlights.filter((h) => h.documentId === doc.id),
    [highlights, doc.id]
  );
  const docBookmarks = useMemo(
    () => bookmarks.filter((b) => b.documentId === doc.id),
    [bookmarks, doc.id]
  );
  const isCurrentBookmarked = docBookmarks.some((b) => b.unitIndex === safeUnit);

  const strokesForUnit = doc.annotationsByUnit?.[safeUnit] ?? [];

  const goToUnit = (nextIdx: number) => {
    const clamped = Math.min(Math.max(0, nextIdx), totalUnits - 1);
    setUnitIndex(clamped);
    scrollContainerRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
    const progress = totalUnits <= 1 ? 1 : (clamped + 1) / totalUnits;
    onUpdateDocument((prev) => ({
      ...prev,
      currentUnit: clamped,
      progressPercent: Math.max(prev.progressPercent, progress),
      lastOpenedAt: Date.now(),
    }));
  };

  // Render drawing overlay canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    canvas.width = Math.max(1, Math.round(rect.width * dpr));
    canvas.height = Math.max(1, Math.round(rect.height * dpr));
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, rect.width, rect.height);

    const drawStroke = (s: InkStroke) => {
      if (s.points.length < 2) return;
      ctx.save();
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.strokeStyle = s.color;
      ctx.globalAlpha = s.opacity;
      ctx.lineWidth = s.width;
      ctx.beginPath();
      ctx.moveTo(s.points[0].x * rect.width, s.points[0].y * rect.height);
      for (let i = 1; i < s.points.length; i++) {
        ctx.lineTo(s.points[i].x * rect.width, s.points[i].y * rect.height);
      }
      ctx.stroke();
      ctx.restore();
    };

    strokesForUnit.forEach(drawStroke);
    if (activeStroke) drawStroke(activeStroke);
  }, [strokesForUnit, activeStroke, drawEnabled, safeUnit, viewport.width, viewport.height]);

  const handleCanvasPointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawEnabled) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const pt = {
      x: (e.clientX - rect.left) / rect.width,
      y: (e.clientY - rect.top) / rect.height,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
    setActiveStroke({
      id: `st_${Date.now().toString(36)}`,
      tool: drawTool,
      color: drawColor,
      width: drawTool === 'highlighter' ? 16 : 3,
      opacity: drawTool === 'highlighter' ? 0.34 : 0.95,
      points: [pt],
    });
  };

  const handleCanvasPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawEnabled || !activeStroke) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const pt = {
      x: Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width)),
      y: Math.min(1, Math.max(0, (e.clientY - rect.top) / rect.height)),
    };
    setActiveStroke((prev) => (prev ? { ...prev, points: [...prev.points, pt] } : null));
  };

  const handleCanvasPointerUp = () => {
    if (!activeStroke) return;
    if (activeStroke.points.length >= 2) {
      const finished = activeStroke;
      onUpdateDocument((prev) => ({
        ...prev,
        annotationsByUnit: {
          ...(prev.annotationsByUnit ?? {}),
          [safeUnit]: [...(prev.annotationsByUnit?.[safeUnit] ?? []), finished],
        },
      }));
    }
    setActiveStroke(null);
  };

  const clearUnitStrokes = () => {
    onUpdateDocument((prev) => ({
      ...prev,
      annotationsByUnit: {
        ...(prev.annotationsByUnit ?? {}),
        [safeUnit]: [],
      },
    }));
  };

  const handleMouseUpSelection = () => {
    if (drawEnabled) return;
    const sel = window.getSelection()?.toString().trim() ?? '';
    if (sel.length >= 2 && sel.length <= 420) {
      setSelectionText(sel);
    } else {
      setSelectionText('');
      setShowNoteInput(false);
    }
  };

  const openExternalLookup = (engine: 'wiki' | 'rae' | 'wr') => {
    const term = encodeURIComponent(selectionText.split(/\s+/).slice(0, 3).join(' '));
    const url =
      engine === 'wiki'
        ? `https://es.wikipedia.org/wiki/Special:Search?search=${term}`
        : engine === 'rae'
          ? `https://dle.rae.es/${term}`
          : `https://www.wordreference.com/es/en/translation.asp?spen=${term}`;

    if (window.lumaDesktop?.openExternal) {
      window.lumaDesktop.openExternal(url);
    } else {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  };

  const saveHighlight = (color: HighlightNote['color']) => {
    if (!selectionText) return;
    onAddHighlight({
      documentId: doc.id,
      chapterIndex: safeUnit,
      quotedText: selectionText,
      noteText: noteDraft.trim(),
      color,
    });
    setSelectionText('');
    setNoteDraft('');
    setShowNoteInput(false);
    window.getSelection()?.removeAllRanges();
  };

  const showInspector = prefs.showInspector && !compactSplit && viewport.tier !== 'compact';
  const useTwoPage =
    prefs.twoPageSpread &&
    viewport.canTwoPageSpread &&
    !compactSplit &&
    !sheetView &&
    !doc.manhwaPanels &&
    !doc.pdfDataUrl;

  const readerFontCss = FONT_MAP[prefs.readerFont].css;

  const currentUnitTitle =
    currentSlide?.title ?? currentChapter?.title ?? doc.title;

  return (
    <div className="h-full flex flex-col bg-[var(--luma-bg)] text-[var(--luma-ink)] overflow-hidden">
      {/* Contextual Notion-style Reader Top Bar */}
      <div className="h-11 px-3 md:px-4 border-b border-[var(--luma-border)] bg-[var(--luma-surface)] flex items-center justify-between gap-2 shrink-0">
        {/* Left: Breadcrumb */}
        <div className="flex items-center gap-2 min-w-0 text-xs">
          <span
            className="w-2.5 h-2.5 rounded-sm shrink-0"
            style={{ backgroundColor: doc.coverColor }}
          />
          <span className="font-semibold truncate max-w-[180px] sm:max-w-[260px]">
            {doc.title}
          </span>
          <span className="text-[var(--luma-ink-muted)] hidden sm:inline">/</span>
          <span className="text-[var(--luma-ink-muted)] truncate hidden sm:inline max-w-[220px]">
            {currentUnitTitle}
          </span>
        </div>

        {/* Right: Contextual Format & Tool Actions */}
        <div className="flex items-center gap-1 shrink-0">
          {doc.manhwaPanels && (
            <button
              type="button"
              onClick={() => setTranslateManhwa((v) => !v)}
              className={`h-7 px-2.5 rounded-md text-xs font-medium inline-flex items-center gap-1.5 transition-colors whitespace-nowrap ${
                translateManhwa
                  ? 'bg-[var(--luma-accent)] text-[var(--luma-on-accent)]'
                  : 'border border-[var(--luma-border)] text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
              }`}
            >
              <Languages className="w-3.5 h-3.5" />
              <span>{translateManhwa ? 'Traducido ES' : 'Traducir EN→ES'}</span>
            </button>
          )}

          {(doc.format === 'DOCX' || doc.format === 'PPTX') && !doc.pdfDataUrl && (
            <button
              type="button"
              onClick={() => setSheetView((v) => !v)}
              className={`h-7 px-2.5 rounded-md text-xs font-medium inline-flex items-center gap-1.5 transition-colors whitespace-nowrap ${
                sheetView
                  ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)]'
                  : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
              }`}
            >
              <FileText className="w-3.5 h-3.5" />
              <span className="hidden md:inline">
                {sheetView ? (doc.format === 'PPTX' ? 'Lámina 16:9' : 'Hoja A4') : 'Modo fluido'}
              </span>
            </button>
          )}

          {viewport.canTwoPageSpread && !compactSplit && !doc.slides && !doc.manhwaPanels && !doc.pdfDataUrl && (
            <button
              type="button"
              onClick={() => onUpdatePrefs({ twoPageSpread: !prefs.twoPageSpread })}
              title="Lectura en doble página"
              className={`h-7 px-2 rounded-md text-xs inline-flex items-center gap-1 transition-colors ${
                prefs.twoPageSpread
                  ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-medium'
                  : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
              }`}
            >
              <Columns2 className="w-3.5 h-3.5" />
              <span className="hidden lg:inline">2 págs</span>
            </button>
          )}

          <button
            type="button"
            onClick={() => setDrawEnabled((v) => !v)}
            title="Dibujar o firmar sobre la página"
            className={`h-7 px-2.5 rounded-md text-xs font-medium inline-flex items-center gap-1.5 transition-colors whitespace-nowrap ${
              drawEnabled
                ? 'bg-[var(--luma-accent)] text-[var(--luma-on-accent)]'
                : 'text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
            }`}
          >
            <PenTool className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">{drawEnabled ? 'Dibujando' : 'Anotar'}</span>
          </button>

          <button
            type="button"
            onClick={() => onToggleBookmark(doc.id, safeUnit, currentUnitTitle)}
            title={isCurrentBookmarked ? 'Quitar marcador' : 'Guardar marcador'}
            className={`h-7 w-7 rounded-md flex items-center justify-center transition-colors ${
              isCurrentBookmarked
                ? 'text-[var(--luma-accent)] bg-[var(--luma-accent-soft)]'
                : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
            }`}
          >
            <Bookmark className="w-3.5 h-3.5" fill={isCurrentBookmarked ? 'currentColor' : 'none'} />
          </button>

          {!compactSplit && (
            <button
              type="button"
              onClick={() => onUpdatePrefs({ showInspector: !prefs.showInspector })}
              title="Panel de índice, notas y tipografía"
              className={`h-7 w-7 rounded-md flex items-center justify-center transition-colors ${
                prefs.showInspector
                  ? 'text-[var(--luma-accent)] bg-[var(--luma-accent-soft)]'
                  : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
              }`}
            >
              <PanelRight className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Inline Drawing Strip when Drawing Mode is active */}
      {drawEnabled && (
        <div className="px-4 py-1.5 border-b border-[var(--luma-border)] bg-[var(--luma-surface-alt)] flex flex-wrap items-center justify-between gap-2 text-xs shrink-0">
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setDrawTool('pen')}
              className={`h-7 px-2.5 rounded-md font-medium inline-flex items-center gap-1.5 ${
                drawTool === 'pen'
                  ? 'bg-[var(--luma-surface)] text-[var(--luma-accent)] border border-[var(--luma-border)]'
                  : 'text-[var(--luma-ink-muted)]'
              }`}
            >
              <PenTool className="w-3.5 h-3.5" />
              Lápiz / Firma
            </button>
            <button
              type="button"
              onClick={() => setDrawTool('highlighter')}
              className={`h-7 px-2.5 rounded-md font-medium inline-flex items-center gap-1.5 ${
                drawTool === 'highlighter'
                  ? 'bg-[var(--luma-surface)] text-[var(--luma-accent)] border border-[var(--luma-border)]'
                  : 'text-[var(--luma-ink-muted)]'
              }`}
            >
              <Highlighter className="w-3.5 h-3.5" />
              Resaltador
            </button>
            <div className="w-px h-4 bg-[var(--luma-border)] mx-1" />
            {['#2B64F5', '#C2642B', '#E11D48', '#257A5E', '#F59E0B', '#1A2233'].map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setDrawColor(c)}
                className={`w-5 h-5 rounded-full border border-black/15 ${
                  drawColor === c ? 'ring-2 ring-offset-1 ring-[var(--luma-ink)]' : ''
                }`}
                style={{ backgroundColor: c }}
              />
            ))}
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[11px] text-[var(--luma-ink-muted)] tabular-nums">
              {strokesForUnit.length} trazos en esta página
            </span>
            <button
              type="button"
              onClick={clearUnitStrokes}
              className="h-7 px-2 rounded-md text-xs text-[var(--luma-ink-muted)] hover:text-red-500 hover:bg-[var(--luma-surface)] inline-flex items-center gap-1"
            >
              <Eraser className="w-3.5 h-3.5" />
              Limpiar
            </button>
          </div>
        </div>
      )}

      {/* Main Reader Viewport + Right Inspector */}
      <div className="flex-1 min-h-0 flex overflow-hidden">
        <div className="flex-1 min-w-0 relative flex flex-col overflow-hidden">
          {/* Selection Floating Highlight & Dictionary Bar */}
          {selectionText && (
            <div className="absolute top-3 left-1/2 -translate-x-1/2 z-30 w-[min(92%,520px)] rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] shadow-xl p-2.5 space-y-2">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium truncate text-[var(--luma-ink-muted)]">
                  “{selectionText}”
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setSelectionText('');
                    setShowNoteInput(false);
                  }}
                  className="p-1 rounded hover:bg-[var(--luma-surface-alt)]"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-1.5">
                  {(['amber', 'cobalt', 'mint', 'coral'] as const).map((color) => (
                    <button
                      key={color}
                      type="button"
                      onClick={() => saveHighlight(color)}
                      title={`Subrayar en ${HIGHLIGHT_STYLES[color].label}`}
                      className="w-6 h-6 rounded-full border border-black/15 hover:scale-110 transition-transform"
                      style={{ backgroundColor: HIGHLIGHT_STYLES[color].dot }}
                    />
                  ))}
                  <button
                    type="button"
                    onClick={() => setShowNoteInput((v) => !v)}
                    className="h-7 px-2 rounded-md text-xs font-medium hover:bg-[var(--luma-surface-alt)] inline-flex items-center gap-1"
                  >
                    <MessageSquarePlus className="w-3.5 h-3.5 text-[var(--luma-accent)]" />
                    Nota
                  </button>
                </div>

                <div className="flex items-center gap-1 text-[11px]">
                  <button
                    type="button"
                    onClick={() => openExternalLookup('wiki')}
                    className="h-7 px-2 rounded-md bg-[var(--luma-surface-alt)] hover:bg-[var(--luma-accent-soft)] inline-flex items-center gap-1"
                  >
                    Wikipedia <ExternalLink className="w-3 h-3" />
                  </button>
                  <button
                    type="button"
                    onClick={() => openExternalLookup('rae')}
                    className="h-7 px-2 rounded-md bg-[var(--luma-surface-alt)] hover:bg-[var(--luma-accent-soft)] inline-flex items-center gap-1"
                  >
                    RAE <ExternalLink className="w-3 h-3" />
                  </button>
                  <button
                    type="button"
                    onClick={() => openExternalLookup('wr')}
                    className="h-7 px-2 rounded-md bg-[var(--luma-surface-alt)] hover:bg-[var(--luma-accent-soft)] inline-flex items-center gap-1"
                  >
                    Traducir <ExternalLink className="w-3 h-3" />
                  </button>
                </div>
              </div>

              {showNoteInput && (
                <div className="flex items-center gap-2 pt-1">
                  <input
                    value={noteDraft}
                    onChange={(e) => setNoteDraft(e.target.value)}
                    placeholder="Escribe una nota marginal para esta cita..."
                    className="flex-1 h-8 px-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] text-xs outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => saveHighlight('amber')}
                    className="h-8 px-3 rounded-lg bg-[var(--luma-accent)] text-[var(--luma-on-accent)] text-xs font-semibold"
                  >
                    Guardar
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Scrollable Reading Area */}
          <div
            ref={scrollContainerRef}
            onMouseUp={handleMouseUpSelection}
            className="flex-1 overflow-y-auto px-4 sm:px-8 py-6 md:py-10 relative"
          >
            {/* Relative Wrapper for Content + Drawing Layer */}
            <div className="relative mx-auto w-full max-w-[1060px]">
              {/* 1. Native Imported PDF Viewer */}
              {doc.pdfDataUrl ? (
                <div className="w-full h-[78vh] rounded-xl overflow-hidden border border-[var(--luma-border)] bg-[var(--luma-surface)] luma-sheet-shadow">
                  <iframe
                    src={doc.pdfDataUrl}
                    title={doc.title}
                    className="w-full h-full border-0"
                  />
                </div>
              ) : doc.manhwaPanels ? (
                /* 2. Manhwa Vertical Comic Panels with Interactive EN->ES OCR Bubbles */
                <div className="max-w-[660px] mx-auto space-y-5">
                  {doc.manhwaPanels.map((panel) => (
                    <div
                      key={panel.id}
                      className="relative aspect-[3/4] rounded-xl overflow-hidden border border-[var(--luma-border)] luma-sheet-shadow"
                      style={{
                        background: `linear-gradient(155deg, ${panel.gradientFrom}, ${panel.gradientTo})`,
                      }}
                    >
                      <div className="absolute top-3 left-4 text-[11px] font-mono tracking-wider text-white/80">
                        {panel.episodeLabel}
                      </div>

                      {/* Speed lines & dramatic graphic backdrop */}
                      <svg
                        viewBox="0 0 600 800"
                        className="absolute inset-0 w-full h-full opacity-25 pointer-events-none"
                      >
                        {Array.from({ length: 18 }).map((_, idx) => {
                          const angle = (idx * 20 * Math.PI) / 180;
                          return (
                            <line
                              key={idx}
                              x1={300 + Math.cos(angle) * 90}
                              y1={400 + Math.sin(angle) * 90}
                              x2={300 + Math.cos(angle) * 520}
                              y2={400 + Math.sin(angle) * 520}
                              stroke={panel.accentColor}
                              strokeWidth="2"
                            />
                          );
                        })}
                      </svg>

                      {/* Speech Bubbles */}
                      {panel.bubbles.map((b) => (
                        <div
                          key={b.id}
                          style={{
                            left: `${b.x}%`,
                            top: `${b.y}%`,
                            width: `${b.w}%`,
                            minHeight: `${b.h}%`,
                          }}
                          className="absolute rounded-3xl bg-white text-slate-900 border-[3px] border-slate-900 px-4 py-3 flex flex-col items-center justify-center text-center shadow-lg transition-all"
                        >
                          <p className="text-xs sm:text-sm font-bold leading-snug tracking-tight">
                            {translateManhwa ? b.translated : b.original}
                          </p>
                          {translateManhwa && (
                            <span className="mt-1 text-[10px] text-slate-500 font-medium">
                              OCR traducido
                            </span>
                          )}
                        </div>
                      ))}

                      <div className="absolute bottom-3 right-4 text-[11px] text-white/70">
                        {panel.sceneCaption}
                      </div>
                    </div>
                  ))}
                </div>
              ) : doc.slides && sheetView && currentSlide ? (
                /* 3. PPTX 16:9 Slide Deck */
                <div className="max-w-[960px] mx-auto space-y-5">
                  <div
                    className="aspect-[16/9] rounded-2xl p-8 md:p-12 flex flex-col justify-between border border-[var(--luma-border)] luma-sheet-shadow"
                    style={{
                      backgroundColor: currentSlide.bgColor,
                      color: currentSlide.textColor,
                    }}
                  >
                    <div>
                      <div
                        className="w-12 h-1.5 rounded-full mb-5"
                        style={{ backgroundColor: currentSlide.accentColor }}
                      />
                      <h1 className="text-2xl md:text-4xl font-bold tracking-tight leading-tight">
                        {currentSlide.title}
                      </h1>
                      {currentSlide.subtitle && (
                        <p className="text-sm md:text-lg opacity-80 mt-2 max-w-2xl">
                          {currentSlide.subtitle}
                        </p>
                      )}
                    </div>

                    <ul className="space-y-2.5 text-sm md:text-base my-4">
                      {currentSlide.bullets.map((line, i) => (
                        <li key={i} className="flex items-start gap-2.5">
                          <span
                            className="w-2 h-2 rounded-full mt-2 shrink-0"
                            style={{ backgroundColor: currentSlide.accentColor }}
                          />
                          <span>{line}</span>
                        </li>
                      ))}
                    </ul>

                    <div className="flex items-center justify-between text-xs opacity-65 pt-2 border-t border-current/15 tabular-nums">
                      <span>{doc.title}</span>
                      <span>
                        Lámina {safeUnit + 1} de {doc.slides.length}
                      </span>
                    </div>
                  </div>

                  {currentSlide.notes && (
                    <div className="p-4 rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] text-xs text-[var(--luma-ink-muted)]">
                      <span className="font-semibold text-[var(--luma-ink)]">Notas del presentador: </span>
                      {currentSlide.notes}
                    </div>
                  )}
                </div>
              ) : (
                /* 4. Fluid Editorial Book Reader / A4 Sheet View / Two-Page Spread */
                <article
                  style={{
                    fontFamily: readerFontCss,
                    fontSize: `${prefs.fontSizePx}px`,
                    lineHeight: prefs.lineHeight,
                    textAlign: prefs.textAlign,
                    maxWidth: useTwoPage
                      ? '1020px'
                      : sheetView
                        ? '794px'
                        : `${prefs.pageWidthCh}ch`,
                  }}
                  className={`mx-auto transition-all ${
                    sheetView
                      ? 'bg-[var(--luma-surface)] border border-[var(--luma-border)] rounded-xl px-8 sm:px-14 py-12 min-h-[960px] luma-sheet-shadow'
                      : ''
                  } ${useTwoPage ? 'columns-2 gap-14' : ''}`}
                >
                  {currentChapter?.blocks.map((block) => {
                    if (block.type === 'h1') {
                      return (
                        <h1
                          key={block.id}
                          className="text-[1.65em] font-bold tracking-tight leading-snug mb-6 text-[var(--luma-ink)]"
                        >
                          {block.text}
                        </h1>
                      );
                    }
                    if (block.type === 'h2') {
                      return (
                        <h2
                          key={block.id}
                          className="text-[1.25em] font-semibold tracking-tight mt-8 mb-3 text-[var(--luma-ink)]"
                        >
                          {block.text}
                        </h2>
                      );
                    }
                    if (block.type === 'quote') {
                      return (
                        <blockquote
                          key={block.id}
                          className="my-6 px-5 py-4 rounded-xl bg-[var(--luma-surface)] border border-[var(--luma-border)] italic text-[var(--luma-ink-muted)]"
                        >
                          {block.text}
                        </blockquote>
                      );
                    }
                    if (block.type === 'list') {
                      return (
                        <p key={block.id} className="mb-3 pl-5 relative">
                          <span className="absolute left-0 text-[var(--luma-accent)]">•</span>
                          {block.text}
                        </p>
                      );
                    }
                    return (
                      <p key={block.id} className="mb-5">
                        {block.text}
                      </p>
                    );
                  })}
                </article>
              )}

              {/* Freehand Drawing / Signature Overlay Canvas */}
              <canvas
                ref={canvasRef}
                onPointerDown={handleCanvasPointerDown}
                onPointerMove={handleCanvasPointerMove}
                onPointerUp={handleCanvasPointerUp}
                onPointerCancel={handleCanvasPointerUp}
                className={`absolute inset-0 w-full h-full z-20 ${
                  drawEnabled ? 'pointer-events-auto cursor-crosshair touch-none' : 'pointer-events-none'
                }`}
              />
            </div>
          </div>

          {/* Quiet Bottom Pagination Bar */}
          {totalUnits > 1 && (
            <div className="h-10 px-4 border-t border-[var(--luma-border)] bg-[var(--luma-surface)] flex items-center justify-between text-xs shrink-0">
              <button
                type="button"
                disabled={safeUnit === 0}
                onClick={() => goToUnit(safeUnit - 1)}
                className="h-7 px-2.5 rounded-md inline-flex items-center gap-1 hover:bg-[var(--luma-surface-alt)] disabled:opacity-40"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
                <span>Anterior</span>
              </button>

              <span className="text-[var(--luma-ink-muted)] tabular-nums">
                {safeUnit + 1} / {totalUnits} · {Math.round(((safeUnit + 1) / totalUnits) * 100)}%
              </span>

              <button
                type="button"
                disabled={safeUnit >= totalUnits - 1}
                onClick={() => goToUnit(safeUnit + 1)}
                className="h-7 px-2.5 rounded-md inline-flex items-center gap-1 hover:bg-[var(--luma-surface-alt)] disabled:opacity-40"
              >
                <span>Siguiente</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </div>
          )}
        </div>

        {/* Right Notion-Style Collapsible Reading Inspector */}
        {showInspector && (
          <aside
            style={{ width: viewport.inspectorWidth }}
            className="h-full shrink-0 border-l border-[var(--luma-border)] bg-[var(--luma-surface)] flex flex-col"
          >
            {/* Segmented Tabs */}
            <div className="p-2 border-b border-[var(--luma-border)] grid grid-cols-4 gap-1 text-[11px] font-medium">
              {(
                [
                  { id: 'toc', label: 'Índice', icon: <List className="w-3.5 h-3.5" /> },
                  { id: 'notes', label: 'Notas', icon: <Highlighter className="w-3.5 h-3.5" /> },
                  { id: 'bookmarks', label: 'Marc.', icon: <Bookmark className="w-3.5 h-3.5" /> },
                  { id: 'typo', label: 'Texto', icon: <Type className="w-3.5 h-3.5" /> },
                ] as const
              ).map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setInspectorTab(t.id)}
                  className={`py-1.5 rounded-md flex flex-col items-center gap-0.5 transition-colors ${
                    inspectorTab === t.id
                      ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                      : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
                  }`}
                >
                  {t.icon}
                  <span>{t.label}</span>
                </button>
              ))}
            </div>

            <div className="flex-1 overflow-y-auto p-3.5 space-y-3 text-xs">
              {inspectorTab === 'toc' && (
                <div className="space-y-1">
                  <div className="text-[11px] font-semibold text-[var(--luma-ink-muted)] mb-2">
                    Contenido ({totalUnits})
                  </div>
                  {(doc.slides
                    ? doc.slides.map((s, idx) => ({ id: s.id, title: `${idx + 1}. ${s.title}`, idx }))
                    : doc.chapters.map((c, idx) => ({ id: c.id, title: c.title, idx }))
                  ).map((entry) => (
                    <button
                      key={entry.id}
                      type="button"
                      onClick={() => goToUnit(entry.idx)}
                      className={`w-full px-2.5 py-2 rounded-lg text-left transition-colors ${
                        entry.idx === safeUnit
                          ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                          : 'hover:bg-[var(--luma-surface-alt)] text-[var(--luma-ink)]'
                      }`}
                    >
                      <div className="line-clamp-2">{entry.title}</div>
                    </button>
                  ))}
                </div>
              )}

              {inspectorTab === 'notes' && (
                <div className="space-y-2.5">
                  <div className="text-[11px] font-semibold text-[var(--luma-ink-muted)]">
                    Subrayados y marginalia ({docHighlights.length})
                  </div>
                  {docHighlights.length === 0 ? (
                    <p className="text-[var(--luma-ink-muted)] leading-relaxed">
                      Selecciona cualquier frase del texto para resaltarla en color editorial o añadir una nota.
                    </p>
                  ) : (
                    docHighlights.map((hl) => (
                      <div
                        key={hl.id}
                        className="p-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] space-y-1.5"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <span
                            className="px-1.5 py-0.5 rounded text-[11px] leading-snug"
                            style={{ backgroundColor: HIGHLIGHT_STYLES[hl.color].bg }}
                          >
                            “{hl.quotedText}”
                          </span>
                          <button
                            type="button"
                            onClick={() => onDeleteHighlight(hl.id)}
                            className="p-1 text-[var(--luma-ink-muted)] hover:text-red-500 shrink-0"
                          >
                            <Trash2 className="w-3 h-3" />
                          </button>
                        </div>
                        {hl.noteText && (
                          <p className="text-[11px] text-[var(--luma-ink-muted)]">{hl.noteText}</p>
                        )}
                      </div>
                    ))
                  )}
                </div>
              )}

              {inspectorTab === 'bookmarks' && (
                <div className="space-y-2">
                  <div className="text-[11px] font-semibold text-[var(--luma-ink-muted)]">
                    Marcadores ({docBookmarks.length})
                  </div>
                  {docBookmarks.length === 0 ? (
                    <p className="text-[var(--luma-ink-muted)]">
                      Pulsa el icono de marcador en la barra superior para guardar tu posición.
                    </p>
                  ) : (
                    docBookmarks.map((bm) => (
                      <button
                        key={bm.id}
                        type="button"
                        onClick={() => goToUnit(bm.unitIndex)}
                        className="w-full p-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] hover:border-[var(--luma-accent)] text-left flex items-center gap-2"
                      >
                        <BookOpen className="w-3.5 h-3.5 text-[var(--luma-accent)] shrink-0" />
                        <span className="truncate">{bm.label}</span>
                      </button>
                    ))
                  )}
                </div>
              )}

              {inspectorTab === 'typo' && (
                <div className="space-y-4">
                  <div>
                    <div className="text-[11px] font-semibold text-[var(--luma-ink-muted)] mb-2">
                      Tipografía
                    </div>
                    <div className="grid grid-cols-2 gap-1.5">
                      {(Object.keys(FONT_MAP) as ReaderFontId[]).map((fid) => (
                        <button
                          key={fid}
                          type="button"
                          onClick={() => onUpdatePrefs({ readerFont: fid })}
                          style={{ fontFamily: FONT_MAP[fid].css }}
                          className={`p-2 rounded-lg border text-left text-xs ${
                            prefs.readerFont === fid
                              ? 'border-[var(--luma-accent)] bg-[var(--luma-accent-soft)] font-semibold'
                              : 'border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)]'
                          }`}
                        >
                          {FONT_MAP[fid].label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between">
                      <span className="font-medium">Tamaño</span>
                      <span className="font-mono tabular-nums text-[var(--luma-ink-muted)]">
                        {prefs.fontSizePx}px
                      </span>
                    </div>
                    <input
                      type="range"
                      min={14}
                      max={26}
                      value={prefs.fontSizePx}
                      onChange={(e) => onUpdatePrefs({ fontSizePx: parseInt(e.target.value, 10) })}
                      className="w-full accent-[var(--luma-accent)]"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between">
                      <span className="font-medium">Interlineado</span>
                      <span className="font-mono tabular-nums text-[var(--luma-ink-muted)]">
                        {prefs.lineHeight.toFixed(2)}
                      </span>
                    </div>
                    <input
                      type="range"
                      min={1.35}
                      max={2.1}
                      step={0.05}
                      value={prefs.lineHeight}
                      onChange={(e) => onUpdatePrefs({ lineHeight: parseFloat(e.target.value) })}
                      className="w-full accent-[var(--luma-accent)]"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between">
                      <span className="font-medium">Ancho de columna</span>
                      <span className="font-mono tabular-nums text-[var(--luma-ink-muted)]">
                        {prefs.pageWidthCh}ch
                      </span>
                    </div>
                    <input
                      type="range"
                      min={52}
                      max={88}
                      value={prefs.pageWidthCh}
                      onChange={(e) => onUpdatePrefs({ pageWidthCh: parseInt(e.target.value, 10) })}
                      className="w-full accent-[var(--luma-accent)]"
                    />
                  </div>

                  <div className="pt-2 border-t border-[var(--luma-border)]">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-[11px] font-semibold text-[var(--luma-ink-muted)]">
                        Temas rápidos
                      </span>
                      <button
                        type="button"
                        onClick={onOpenAppearance}
                        className="text-[11px] text-[var(--luma-accent)] font-semibold hover:underline"
                      >
                        Ver los 17
                      </button>
                    </div>
                    <div className="grid grid-cols-6 gap-1.5">
                      {LUMA_THEMES.slice(0, 12).map((t) => (
                        <button
                          key={t.id}
                          type="button"
                          onClick={() => onUpdatePrefs({ theme: t.id })}
                          title={t.name}
                          className={`h-7 rounded-md border ${
                            prefs.theme === t.id
                              ? 'ring-2 ring-[var(--luma-accent)] border-[var(--luma-accent)]'
                              : 'border-black/15'
                          }`}
                          style={{ backgroundColor: t.bg }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </aside>
        )}
      </div>
    </div>
  );
};
