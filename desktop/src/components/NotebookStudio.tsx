import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowUpRight,
  Circle,
  Copy,
  Download,
  Eraser,
  Eye,
  EyeOff,
  Highlighter,
  Layers,
  LayoutTemplate,
  Lock,
  Minus,
  MousePointer2,
  PenTool,
  Plus,
  Printer,
  Redo2,
  Shapes,
  Square,
  StickyNote,
  Table2,
  Trash2,
  Type,
  Undo2,
  Unlock,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import {
  CanvasObject,
  CanvasTool,
  LumaNotebook,
  NotebookPageSize,
  NotebookPaperPattern,
  ShapeCanvasObject,
  StickerCanvasObject,
  StickyCanvasObject,
  StrokeCanvasObject,
  TableCanvasObject,
  TextCanvasObject,
} from '../types';
import { ViewportProfile } from '../hooks/useViewportProfile';

interface NotebookStudioProps {
  notebook: LumaNotebook;
  viewport: ViewportProfile;
  compactSplit?: boolean;
  onChangeNotebook: (updated: LumaNotebook) => void;
}

const PAGE_DIMENSIONS: Record<NotebookPageSize, { w: number; h: number; label: string }> = {
  A4: { w: 1000, h: 1414, label: 'A4' },
  LETTER: { w: 1000, h: 1294, label: 'Carta' },
  WIDE_16_9: { w: 1000, h: 562, label: '16:9' },
  SQUARE: { w: 1000, h: 1000, label: 'Cuadrado' },
};

const INK_COLORS = [
  '#1A2233',
  '#2B64F5',
  '#C2642B',
  '#257A5E',
  '#E11D48',
  '#5B4BDB',
  '#F59E0B',
  '#0EA5E9',
];

const STICKERS = [
  { symbol: '✓', label: 'Clave', color: '#15803D', bg: '#DCFCE7' },
  { symbol: '★', label: 'Examen', color: '#B45309', bg: '#FEF3C7' },
  { symbol: '!', label: 'Alerta', color: '#BE123C', bg: '#FFE4E6' },
  { symbol: '→', label: 'Causa', color: '#1D4ED8', bg: '#DBEAFE' },
  { symbol: '◈', label: 'Idea', color: '#6D28D9', bg: '#EDE9FE' },
  { symbol: '⊕', label: 'Extra', color: '#0F766E', bg: '#CCFBF1' },
];

type CanvaDrawerTab = 'templates' | 'draw' | 'text' | 'elements' | 'layers' | null;

function uid(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
}

export const NotebookStudio: React.FC<NotebookStudioProps> = ({
  notebook,
  viewport,
  compactSplit = false,
  onChangeNotebook,
}) => {
  const [drawerTab, setDrawerTab] = useState<CanvaDrawerTab>(
    compactSplit || viewport.tier === 'compact' ? null : 'elements'
  );
  const [tool, setTool] = useState<CanvasTool>('select');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [inkColor, setInkColor] = useState('#2B64F5');
  const [inkWidth, setInkWidth] = useState(3.5);
  const [zoom, setZoom] = useState(compactSplit ? 0.62 : 0.78);
  const [history, setHistory] = useState<CanvasObject[][]>([]);
  const [future, setFuture] = useState<CanvasObject[][]>([]);
  const [activeStroke, setActiveStroke] = useState<StrokeCanvasObject | null>(null);
  const [exportNotice, setExportNotice] = useState<string | null>(null);

  const dragStateRef = useRef<{
    id: string;
    mode: 'move' | 'resize';
    startMouseX: number;
    startMouseY: number;
    origX: number;
    origY: number;
    origW: number;
    origH: number;
  } | null>(null);

  const pageDims = PAGE_DIMENSIONS[notebook.pageSize];
  const activePage = notebook.pages[notebook.activePageIndex] ?? notebook.pages[0];

  const layerMap = useMemo(() => {
    const m = new Map(notebook.layers.map((l) => [l.id, l]));
    return m;
  }, [notebook.layers]);

  const selectedObj = activePage.objects.find((o) => o.id === selectedId) ?? null;

  useEffect(() => {
    setSelectedId(null);
    setEditingId(null);
    setHistory([]);
    setFuture([]);
  }, [notebook.id, notebook.activePageIndex]);

  const commitObjects = (nextObjects: CanvasObject[], recordUndo = true) => {
    if (recordUndo) {
      setHistory((h) => [...h.slice(-39), activePage.objects]);
      setFuture([]);
    }
    const nextPages = notebook.pages.map((p, idx) =>
      idx === notebook.activePageIndex ? { ...p, objects: nextObjects } : p
    );
    onChangeNotebook({ ...notebook, pages: nextPages, updatedAt: Date.now() });
  };

  const handleUndo = () => {
    if (history.length === 0) return;
    const prev = history[history.length - 1];
    setHistory((h) => h.slice(0, -1));
    setFuture((f) => [activePage.objects, ...f.slice(0, 39)]);
    const nextPages = notebook.pages.map((p, idx) =>
      idx === notebook.activePageIndex ? { ...p, objects: prev } : p
    );
    onChangeNotebook({ ...notebook, pages: nextPages, updatedAt: Date.now() });
  };

  const handleRedo = () => {
    if (future.length === 0) return;
    const next = future[0];
    setFuture((f) => f.slice(1));
    setHistory((h) => [...h, activePage.objects]);
    const nextPages = notebook.pages.map((p, idx) =>
      idx === notebook.activePageIndex ? { ...p, objects: next } : p
    );
    onChangeNotebook({ ...notebook, pages: nextPages, updatedAt: Date.now() });
  };

  // Keyboard shortcuts for Studio
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (editingId) return;
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) return;

      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
        e.preventDefault();
        if (e.shiftKey) handleRedo();
        else handleUndo();
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'y') {
        e.preventDefault();
        handleRedo();
      } else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'd' && selectedObj) {
        e.preventDefault();
        duplicateSelected();
      } else if ((e.key === 'Delete' || e.key === 'Backspace') && selectedId) {
        e.preventDefault();
        commitObjects(activePage.objects.filter((o) => o.id !== selectedId));
        setSelectedId(null);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  const addObject = (obj: CanvasObject) => {
    commitObjects([...activePage.objects, obj]);
    setSelectedId(obj.id);
    setTool('select');
  };

  const updateObject = (id: string, patch: Partial<CanvasObject>, recordUndo = false) => {
    commitObjects(
      activePage.objects.map((o) => (o.id === id ? ({ ...o, ...patch } as CanvasObject) : o)),
      recordUndo
    );
  };

  const duplicateSelected = () => {
    if (!selectedObj) return;
    const clone: CanvasObject = {
      ...selectedObj,
      id: uid(selectedObj.kind),
      x: selectedObj.x + 28,
      y: selectedObj.y + 28,
    };
    addObject(clone);
  };

  const deleteSelected = () => {
    if (!selectedId) return;
    commitObjects(activePage.objects.filter((o) => o.id !== selectedId));
    setSelectedId(null);
    setEditingId(null);
  };

  // Add Canva Preset Blocks
  const addTextPreset = (preset: 'title' | 'subtitle' | 'note_red' | 'note_hand' | 'body') => {
    const base: TextCanvasObject = {
      id: uid('txt'),
      kind: 'text',
      layerId: 'content',
      x: 80,
      y: 90 + (activePage.objects.length % 5) * 34,
      w: preset === 'title' ? 760 : 380,
      h: preset === 'title' ? 76 : 110,
      opacity: 1,
      text:
        preset === 'title'
          ? 'Título de sección'
          : preset === 'subtitle'
            ? 'Subtítulo clave'
            : preset === 'note_red'
              ? 'Punto crítico para recordar'
              : preset === 'note_hand'
                ? 'Anotación rápida a mano...'
                : 'Haz doble clic para editar este bloque de texto.',
      fontFamily: preset === 'note_hand' ? 'hand' : preset === 'body' ? 'serif' : 'sans',
      fontSize: preset === 'title' ? 24 : preset === 'subtitle' ? 18 : preset === 'note_hand' ? 20 : 14,
      bold: preset === 'title' || preset === 'subtitle' || preset === 'note_red',
      color:
        preset === 'title'
          ? '#1D5BFF'
          : preset === 'note_red'
            ? '#BE123C'
            : preset === 'note_hand'
              ? '#6D28D9'
              : '#1A2233',
      fillColor:
        preset === 'title'
          ? '#FFFFFF'
          : preset === 'note_red'
            ? '#FFF1F2'
            : preset === 'note_hand'
              ? '#FAF5FF'
              : '#FFFFFF',
      borderColor:
        preset === 'title'
          ? '#1D5BFF'
          : preset === 'note_red'
            ? '#FB7185'
            : preset === 'note_hand'
              ? '#C4B5FD'
              : '#CBD5E1',
      align: 'left',
      isBanner: preset === 'title',
    };
    addObject(base);
  };

  const addShape = (shape: ShapeCanvasObject['shape']) => {
    const obj: ShapeCanvasObject = {
      id: uid('shp'),
      kind: 'shape',
      layerId: 'content',
      shape,
      x: 160 + (activePage.objects.length % 6) * 24,
      y: 160 + (activePage.objects.length % 6) * 24,
      w: shape === 'line' || shape === 'arrow' ? 210 : 190,
      h: shape === 'line' ? 24 : shape === 'arrow' ? 84 : 130,
      opacity: 1,
      strokeColor: inkColor,
      fillColor: shape === 'rect' || shape === 'ellipse' ? `${inkColor}18` : 'transparent',
      strokeWidth: 2.5,
    };
    addObject(obj);
  };

  const addSticky = (bgColor = '#FEF3C7', textColor = '#451A03') => {
    const obj: StickyCanvasObject = {
      id: uid('stk'),
      kind: 'sticky',
      layerId: 'content',
      x: 140 + (activePage.objects.length % 5) * 26,
      y: 150 + (activePage.objects.length % 5) * 26,
      w: 240,
      h: 170,
      opacity: 1,
      text: 'Nota rápida\nDoble clic para escribir...',
      bgColor,
      textColor,
    };
    addObject(obj);
  };

  const addTable = () => {
    const obj: TableCanvasObject = {
      id: uid('tbl'),
      kind: 'table',
      layerId: 'content',
      x: 90,
      y: 180,
      w: 780,
      h: 220,
      opacity: 1,
      rows: 3,
      cols: 3,
      headerBg: '#EEF2FF',
      borderColor: '#4F46E5',
      cells: ['Concepto', 'Definición', 'Ejemplo', 'Tema 1', '', '', 'Tema 2', '', ''],
    };
    addObject(obj);
  };

  const addSticker = (st: (typeof STICKERS)[number]) => {
    const obj: StickerCanvasObject = {
      id: uid('stkr'),
      kind: 'sticker',
      layerId: 'content',
      x: 200 + (activePage.objects.length % 6) * 24,
      y: 140 + (activePage.objects.length % 6) * 24,
      w: 118,
      h: 44,
      opacity: 1,
      symbol: st.symbol,
      label: st.label,
      color: st.color,
      bgColor: st.bg,
    };
    addObject(obj);
  };

  // Pointer handlers on artboard for Pen / Highlighter / Eraser
  const handleArtboardPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget && tool === 'select') {
      setSelectedId(null);
      setEditingId(null);
      return;
    }

    if (tool !== 'pen' && tool !== 'highlighter' && tool !== 'eraser') return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = (e.clientX - rect.left) / zoom;
    const y = (e.clientY - rect.top) / zoom;

    if (tool === 'eraser') {
      // Remove any stroke near cursor
      const remaining = activePage.objects.filter((o) => {
        if (o.kind !== 'stroke') return true;
        return !o.points.some((pt) => Math.hypot(pt.x - x, pt.y - y) < 20);
      });
      if (remaining.length !== activePage.objects.length) {
        commitObjects(remaining);
      }
      return;
    }

    e.currentTarget.setPointerCapture(e.pointerId);
    const layerId = tool === 'highlighter' ? 'underlay' : 'ink';
    if (layerMap.get(layerId)?.locked) return;

    setActiveStroke({
      id: uid('strk'),
      kind: 'stroke',
      layerId,
      tool,
      x: 0,
      y: 0,
      w: pageDims.w,
      h: pageDims.h,
      opacity: tool === 'highlighter' ? 0.32 : 1,
      color: inkColor,
      strokeWidth: tool === 'highlighter' ? Math.max(18, inkWidth * 4.5) : inkWidth,
      points: [{ x, y }],
    });
  };

  const handleArtboardPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (dragStateRef.current) {
      const ds = dragStateRef.current;
      const dx = (e.clientX - ds.startMouseX) / zoom;
      const dy = (e.clientY - ds.startMouseY) / zoom;
      if (ds.mode === 'move') {
        updateObject(ds.id, {
          x: Math.round(ds.origX + dx),
          y: Math.round(ds.origY + dy),
        });
      } else {
        updateObject(ds.id, {
          w: Math.max(48, Math.round(ds.origW + dx)),
          h: Math.max(28, Math.round(ds.origH + dy)),
        });
      }
      return;
    }

    if (!activeStroke) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = Math.min(pageDims.w, Math.max(0, (e.clientX - rect.left) / zoom));
    const y = Math.min(pageDims.h, Math.max(0, (e.clientY - rect.top) / zoom));
    setActiveStroke((prev) => (prev ? { ...prev, points: [...prev.points, { x, y }] } : null));
  };

  const handleArtboardPointerUp = () => {
    if (dragStateRef.current) {
      dragStateRef.current = null;
      return;
    }
    if (activeStroke) {
      if (activeStroke.points.length >= 2) {
        commitObjects([...activePage.objects, activeStroke]);
      }
      setActiveStroke(null);
    }
  };

  const startObjectDrag = (
    e: React.PointerEvent,
    obj: CanvasObject,
    mode: 'move' | 'resize'
  ) => {
    if (tool !== 'select') return;
    if (layerMap.get(obj.layerId)?.locked) return;
    e.stopPropagation();
    setSelectedId(obj.id);
    setHistory((h) => [...h.slice(-39), activePage.objects]);
    setFuture([]);
    dragStateRef.current = {
      id: obj.id,
      mode,
      startMouseX: e.clientX,
      startMouseY: e.clientY,
      origX: obj.x,
      origY: obj.y,
      origW: obj.w,
      origH: obj.h,
    };
  };

  // Export HD PNG (real rendered offscreen canvas!)
  const exportPageAsPng = async () => {
    const scale = 2;
    const canvas = document.createElement('canvas');
    canvas.width = pageDims.w * scale;
    canvas.height = pageDims.h * scale;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.scale(scale, scale);
    ctx.fillStyle = activePage.backgroundColor || '#FFFFFF';
    ctx.fillRect(0, 0, pageDims.w, pageDims.h);

    // Draw paper pattern
    ctx.strokeStyle = '#E2E8F0';
    ctx.lineWidth = 1;
    if (activePage.pattern === 'ruled' || activePage.pattern === 'cornell') {
      for (let y = 120; y < pageDims.h - 40; y += 36) {
        ctx.beginPath();
        ctx.moveTo(40, y);
        ctx.lineTo(pageDims.w - 40, y);
        ctx.stroke();
      }
      if (activePage.pattern === 'cornell') {
        ctx.strokeStyle = '#FCA5A5';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(310, 120);
        ctx.lineTo(310, pageDims.h - 180);
        ctx.stroke();
      }
    } else if (activePage.pattern === 'grid') {
      for (let x = 32; x < pageDims.w; x += 32) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, pageDims.h);
        ctx.stroke();
      }
      for (let y = 32; y < pageDims.h; y += 32) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(pageDims.w, y);
        ctx.stroke();
      }
    } else if (activePage.pattern === 'dotted') {
      ctx.fillStyle = '#CBD5E1';
      for (let x = 28; x < pageDims.w; x += 28) {
        for (let y = 28; y < pageDims.h; y += 28) {
          ctx.beginPath();
          ctx.arc(x, y, 1.4, 0, Math.PI * 2);
          ctx.fill();
        }
      }
    }

    // Draw visible objects
    for (const obj of activePage.objects) {
      if (layerMap.get(obj.layerId)?.visible === false) continue;
      ctx.save();
      ctx.globalAlpha = obj.opacity;

      if (obj.kind === 'stroke' && obj.points.length >= 2) {
        ctx.strokeStyle = obj.color;
        ctx.lineWidth = obj.strokeWidth;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.beginPath();
        ctx.moveTo(obj.points[0].x, obj.points[0].y);
        for (let i = 1; i < obj.points.length; i++) {
          ctx.lineTo(obj.points[i].x, obj.points[i].y);
        }
        ctx.stroke();
      } else if (obj.kind === 'text') {
        if (obj.fillColor && obj.fillColor !== 'transparent') {
          ctx.fillStyle = obj.fillColor;
          ctx.fillRect(obj.x, obj.y, obj.w, obj.h);
        }
        if (obj.borderColor && obj.borderColor !== 'transparent') {
          ctx.strokeStyle = obj.borderColor;
          ctx.lineWidth = 1.5;
          ctx.strokeRect(obj.x, obj.y, obj.w, obj.h);
        }
        ctx.fillStyle = obj.color;
        ctx.font = `${obj.bold ? '600' : '400'} ${obj.fontSize}px sans-serif`;
        const lines = obj.text.split('\n');
        lines.forEach((ln, idx) => {
          ctx.fillText(ln, obj.x + 14, obj.y + 24 + idx * (obj.fontSize * 1.4), obj.w - 28);
        });
      } else if (obj.kind === 'sticky') {
        ctx.fillStyle = obj.bgColor;
        ctx.fillRect(obj.x, obj.y, obj.w, obj.h);
        ctx.fillStyle = obj.textColor;
        ctx.font = '500 14px sans-serif';
        obj.text.split('\n').forEach((ln, idx) => {
          ctx.fillText(ln, obj.x + 14, obj.y + 26 + idx * 20, obj.w - 28);
        });
      } else if (obj.kind === 'shape') {
        ctx.strokeStyle = obj.strokeColor;
        ctx.lineWidth = obj.strokeWidth;
        if (obj.shape === 'rect') {
          if (obj.fillColor !== 'transparent') {
            ctx.fillStyle = obj.fillColor;
            ctx.fillRect(obj.x, obj.y, obj.w, obj.h);
          }
          ctx.strokeRect(obj.x, obj.y, obj.w, obj.h);
        } else if (obj.shape === 'ellipse') {
          ctx.beginPath();
          ctx.ellipse(obj.x + obj.w / 2, obj.y + obj.h / 2, obj.w / 2, obj.h / 2, 0, 0, Math.PI * 2);
          if (obj.fillColor !== 'transparent') {
            ctx.fillStyle = obj.fillColor;
            ctx.fill();
          }
          ctx.stroke();
        } else {
          ctx.beginPath();
          ctx.moveTo(obj.x + 6, obj.y + obj.h / 2);
          ctx.lineTo(obj.x + obj.w - 10, obj.y + obj.h / 2);
          ctx.stroke();
        }
      }
      ctx.restore();
    }

    const dataUrl = canvas.toDataURL('image/png');
    const fileName = `${notebook.title.replace(/[^\w\s-]/g, '').trim() || 'cuaderno'}-p${notebook.activePageIndex + 1}.png`;

    if (window.lumaDesktop?.saveExport) {
      const res = await window.lumaDesktop.saveExport({
        defaultName: fileName,
        data: dataUrl,
        encoding: 'base64',
      });
      if (res.saved) {
        setExportNotice('Exportado en PNG HD');
        setTimeout(() => setExportNotice(null), 2600);
      }
    } else {
      const a = document.createElement('a');
      a.href = dataUrl;
      a.download = fileName;
      a.click();
      setExportNotice('Descargado PNG HD');
      setTimeout(() => setExportNotice(null), 2600);
    }
  };

  const updatePattern = (pattern: NotebookPaperPattern) => {
    const nextPages = notebook.pages.map((p, idx) =>
      idx === notebook.activePageIndex ? { ...p, pattern } : p
    );
    onChangeNotebook({ ...notebook, pages: nextPages, updatedAt: Date.now() });
  };

  const addNewPage = () => {
    const nextPages = [
      ...notebook.pages,
      {
        id: uid('pg'),
        title: `Página ${notebook.pages.length + 1}`,
        pattern: activePage.pattern,
        backgroundColor: activePage.backgroundColor,
        objects: [],
      },
    ];
    onChangeNotebook({
      ...notebook,
      pages: nextPages,
      activePageIndex: nextPages.length - 1,
      updatedAt: Date.now(),
    });
  };

  const toggleLayerVisible = (layerId: string) => {
    onChangeNotebook({
      ...notebook,
      layers: notebook.layers.map((l) => (l.id === layerId ? { ...l, visible: !l.visible } : l)),
    });
  };

  const toggleLayerLocked = (layerId: string) => {
    onChangeNotebook({
      ...notebook,
      layers: notebook.layers.map((l) => (l.id === layerId ? { ...l, locked: !l.locked } : l)),
    });
  };

  const paperPatternCss = (pattern: NotebookPaperPattern): React.CSSProperties => {
    if (pattern === 'grid') {
      return {
        backgroundImage:
          'linear-gradient(to right, rgba(148,163,184,0.22) 1px, transparent 1px), linear-gradient(to bottom, rgba(148,163,184,0.22) 1px, transparent 1px)',
        backgroundSize: '32px 32px',
      };
    }
    if (pattern === 'dotted') {
      return {
        backgroundImage: 'radial-gradient(rgba(100,116,139,0.34) 1.4px, transparent 1.4px)',
        backgroundSize: '26px 26px',
      };
    }
    if (pattern === 'ruled' || pattern === 'cornell') {
      return {
        backgroundImage: 'linear-gradient(to bottom, transparent 35px, rgba(148,163,184,0.26) 36px)',
        backgroundSize: '100% 36px',
      };
    }
    return {};
  };

  const railButtons: Array<{ id: Exclude<CanvaDrawerTab, null>; label: string; icon: React.ReactNode }> = [
    { id: 'templates', label: 'Papel', icon: <LayoutTemplate className="w-4 h-4" /> },
    { id: 'elements', label: 'Elementos', icon: <Shapes className="w-4 h-4" /> },
    { id: 'text', label: 'Texto', icon: <Type className="w-4 h-4" /> },
    { id: 'draw', label: 'Dibujo', icon: <PenTool className="w-4 h-4" /> },
    { id: 'layers', label: 'Capas', icon: <Layers className="w-4 h-4" /> },
  ];

  const strokes = activePage.objects.filter(
    (o): o is StrokeCanvasObject => o.kind === 'stroke' && layerMap.get(o.layerId)?.visible !== false
  );
  const nonStrokes = activePage.objects.filter(
    (o) => o.kind !== 'stroke' && layerMap.get(o.layerId)?.visible !== false
  );

  return (
    <div className="h-full flex flex-col bg-[var(--luma-bg)] text-[var(--luma-ink)] overflow-hidden select-none">
      {/* Canva-Style Top Contextual Bar */}
      <div className="h-11 px-3 border-b border-[var(--luma-border)] bg-[var(--luma-surface)] flex items-center justify-between gap-2 shrink-0 overflow-x-auto">
        <div className="flex items-center gap-2 min-w-0">
          <input
            value={notebook.title}
            onChange={(e) => onChangeNotebook({ ...notebook, title: e.target.value, updatedAt: Date.now() })}
            className="text-xs font-semibold bg-transparent px-2 py-1 rounded hover:bg-[var(--luma-surface-alt)] focus:bg-[var(--luma-bg)] outline-none w-[160px] sm:w-[210px] truncate"
          />

          <div className="w-px h-4 bg-[var(--luma-border)]" />

          <button
            type="button"
            disabled={history.length === 0}
            onClick={handleUndo}
            title="Deshacer (Ctrl+Z)"
            className="h-7 w-7 rounded-md flex items-center justify-center hover:bg-[var(--luma-surface-alt)] disabled:opacity-35"
          >
            <Undo2 className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            disabled={future.length === 0}
            onClick={handleRedo}
            title="Rehacer (Ctrl+Y)"
            className="h-7 w-7 rounded-md flex items-center justify-center hover:bg-[var(--luma-surface-alt)] disabled:opacity-35"
          >
            <Redo2 className="w-3.5 h-3.5" />
          </button>

          <div className="w-px h-4 bg-[var(--luma-border)]" />

          {/* Quick Cursor / Ink Modes */}
          <button
            type="button"
            onClick={() => setTool('select')}
            title="Seleccionar y mover"
            className={`h-7 px-2 rounded-md text-xs inline-flex items-center gap-1 ${
              tool === 'select'
                ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
            }`}
          >
            <MousePointer2 className="w-3.5 h-3.5" />
            <span className="hidden lg:inline">Seleccionar</span>
          </button>
          <button
            type="button"
            onClick={() => setTool('pen')}
            title="Bolígrafo libre"
            className={`h-7 px-2 rounded-md text-xs inline-flex items-center gap-1 ${
              tool === 'pen'
                ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
            }`}
          >
            <PenTool className="w-3.5 h-3.5" />
            <span className="hidden lg:inline">Lápiz</span>
          </button>
          <button
            type="button"
            onClick={() => setTool('highlighter')}
            title="Resaltador translúcido"
            className={`h-7 px-2 rounded-md text-xs inline-flex items-center gap-1 ${
              tool === 'highlighter'
                ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
            }`}
          >
            <Highlighter className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={() => setTool('eraser')}
            title="Borrador de trazos"
            className={`h-7 px-2 rounded-md text-xs inline-flex items-center gap-1 ${
              tool === 'eraser'
                ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
            }`}
          >
            <Eraser className="w-3.5 h-3.5" />
          </button>

          {/* Selected Object Quick Actions */}
          {selectedObj && (
            <>
              <div className="w-px h-4 bg-[var(--luma-border)]" />
              <button
                type="button"
                onClick={duplicateSelected}
                title="Duplicar (Ctrl+D)"
                className="h-7 px-2 rounded-md text-xs hover:bg-[var(--luma-surface-alt)] inline-flex items-center gap-1"
              >
                <Copy className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={deleteSelected}
                title="Eliminar (Supr)"
                className="h-7 px-2 rounded-md text-xs text-red-500 hover:bg-red-500/10 inline-flex items-center gap-1"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </>
          )}
        </div>

        {/* Right: Zoom & Export */}
        <div className="flex items-center gap-1.5 shrink-0">
          {exportNotice && (
            <span className="text-[11px] font-semibold text-[var(--luma-accent)] mr-1">
              {exportNotice}
            </span>
          )}
          <div className="flex items-center rounded-md border border-[var(--luma-border)] bg-[var(--luma-bg)]">
            <button
              type="button"
              onClick={() => setZoom((z) => Math.max(0.4, +(z - 0.1).toFixed(2)))}
              className="h-7 w-6 flex items-center justify-center hover:bg-[var(--luma-surface-alt)]"
            >
              <ZoomOut className="w-3 h-3" />
            </button>
            <span className="text-[11px] font-mono tabular-nums px-1.5">
              {Math.round(zoom * 100)}%
            </span>
            <button
              type="button"
              onClick={() => setZoom((z) => Math.min(1.8, +(z + 0.1).toFixed(2)))}
              className="h-7 w-6 flex items-center justify-center hover:bg-[var(--luma-surface-alt)]"
            >
              <ZoomIn className="w-3 h-3" />
            </button>
          </div>

          <button
            type="button"
            onClick={() => window.print()}
            title="Imprimir / Guardar PDF"
            className="h-7 w-7 rounded-md border border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)] flex items-center justify-center"
          >
            <Printer className="w-3.5 h-3.5" />
          </button>

          <button
            type="button"
            onClick={exportPageAsPng}
            className="h-7 px-2.5 rounded-md bg-[var(--luma-accent)] text-[var(--luma-on-accent)] text-xs font-semibold inline-flex items-center gap-1.5 hover:opacity-95 whitespace-nowrap"
          >
            <Download className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Exportar PNG</span>
          </button>
        </div>
      </div>

      {/* Main Canva Studio Body: Left Icon Rail + Contextual Drawer + Artboard */}
      <div className="flex-1 min-h-0 flex overflow-hidden">
        {/* Left Vertical Canva Tool Rail */}
        <div
          style={{ width: viewport.canvaRailWidth }}
          className="h-full shrink-0 border-r border-[var(--luma-border)] bg-[var(--luma-surface)] flex flex-col items-center py-2.5 gap-1.5"
        >
          {railButtons.map((btn) => {
            const active = drawerTab === btn.id;
            return (
              <button
                key={btn.id}
                type="button"
                onClick={() => setDrawerTab(active ? null : btn.id)}
                className={`w-[calc(100%-10px)] py-2 rounded-lg flex flex-col items-center justify-center gap-1 text-[10px] font-medium transition-colors ${
                  active
                    ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                    : 'text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
                }`}
              >
                {btn.icon}
                <span className="truncate max-w-full px-0.5">{btn.label}</span>
              </button>
            );
          })}
        </div>

        {/* Slide-Out Canva Contextual Drawer */}
        {drawerTab && (
          <div
            style={{ width: viewport.canvaDrawerWidth }}
            className="h-full shrink-0 border-r border-[var(--luma-border)] bg-[var(--luma-surface)] overflow-y-auto p-3.5 space-y-4 text-xs"
          >
            {drawerTab === 'templates' && (
              <>
                <div>
                  <div className="font-semibold mb-2">Patrón de hoja</div>
                  <div className="grid grid-cols-2 gap-1.5">
                    {(
                      [
                        { id: 'blank', label: 'En blanco' },
                        { id: 'ruled', label: 'Rayado' },
                        { id: 'grid', label: 'Cuadrícula' },
                        { id: 'dotted', label: 'Puntos' },
                        { id: 'cornell', label: 'Cornell' },
                      ] as const
                    ).map((p) => (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => updatePattern(p.id)}
                        className={`p-2 rounded-lg border text-left ${
                          activePage.pattern === p.id
                            ? 'border-[var(--luma-accent)] bg-[var(--luma-accent-soft)] font-semibold'
                            : 'border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)]'
                        }`}
                      >
                        {p.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="pt-2 border-t border-[var(--luma-border)]">
                  <div className="font-semibold mb-2">Formato de página</div>
                  <div className="grid grid-cols-2 gap-1.5">
                    {(Object.keys(PAGE_DIMENSIONS) as NotebookPageSize[]).map((sz) => (
                      <button
                        key={sz}
                        type="button"
                        onClick={() => onChangeNotebook({ ...notebook, pageSize: sz })}
                        className={`p-2 rounded-lg border text-left ${
                          notebook.pageSize === sz
                            ? 'border-[var(--luma-accent)] bg-[var(--luma-accent-soft)] font-semibold'
                            : 'border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)]'
                        }`}
                      >
                        {PAGE_DIMENSIONS[sz].label}
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}

            {drawerTab === 'elements' && (
              <>
                <div>
                  <div className="font-semibold mb-2">Formas y conectores</div>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => addShape('rect')}
                      className="p-2.5 rounded-lg border border-[var(--luma-border)] hover:border-[var(--luma-accent)] flex items-center gap-2"
                    >
                      <Square className="w-4 h-4 text-[var(--luma-accent)]" /> Recuadro
                    </button>
                    <button
                      type="button"
                      onClick={() => addShape('ellipse')}
                      className="p-2.5 rounded-lg border border-[var(--luma-border)] hover:border-[var(--luma-accent)] flex items-center gap-2"
                    >
                      <Circle className="w-4 h-4 text-[var(--luma-accent)]" /> Elipse
                    </button>
                    <button
                      type="button"
                      onClick={() => addShape('arrow')}
                      className="p-2.5 rounded-lg border border-[var(--luma-border)] hover:border-[var(--luma-accent)] flex items-center gap-2"
                    >
                      <ArrowUpRight className="w-4 h-4 text-[var(--luma-accent)]" /> Flecha
                    </button>
                    <button
                      type="button"
                      onClick={() => addShape('line')}
                      className="p-2.5 rounded-lg border border-[var(--luma-border)] hover:border-[var(--luma-accent)] flex items-center gap-2"
                    >
                      <Minus className="w-4 h-4 text-[var(--luma-accent)]" /> Línea
                    </button>
                  </div>
                </div>

                <div>
                  <div className="font-semibold mb-2">Notas adhesivas y tablas</div>
                  <div className="grid grid-cols-2 gap-2">
                    <button
                      type="button"
                      onClick={() => addSticky('#FEF3C7', '#451A03')}
                      className="p-2.5 rounded-lg border border-amber-300 bg-amber-50 text-amber-950 font-medium flex items-center gap-1.5"
                    >
                      <StickyNote className="w-3.5 h-3.5" /> Ámbar
                    </button>
                    <button
                      type="button"
                      onClick={() => addSticky('#DCFCE7', '#14532D')}
                      className="p-2.5 rounded-lg border border-emerald-300 bg-emerald-50 text-emerald-950 font-medium flex items-center gap-1.5"
                    >
                      <StickyNote className="w-3.5 h-3.5" /> Menta
                    </button>
                    <button
                      type="button"
                      onClick={addTable}
                      className="col-span-2 p-2.5 rounded-lg border border-[var(--luma-border)] hover:border-[var(--luma-accent)] flex items-center justify-center gap-2 font-medium"
                    >
                      <Table2 className="w-4 h-4 text-[var(--luma-accent)]" /> Insertar tabla 3×3
                    </button>
                  </div>
                </div>

                <div>
                  <div className="font-semibold mb-2">Stickers de estudio</div>
                  <div className="grid grid-cols-2 gap-1.5">
                    {STICKERS.map((st) => (
                      <button
                        key={st.label}
                        type="button"
                        onClick={() => addSticker(st)}
                        style={{ backgroundColor: st.bg, color: st.color }}
                        className="px-2.5 py-2 rounded-lg font-semibold flex items-center gap-1.5 hover:opacity-90"
                      >
                        <span>{st.symbol}</span>
                        <span>{st.label}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}

            {drawerTab === 'text' && (
              <div className="space-y-2">
                <div className="font-semibold mb-2">Estilos de texto Canva</div>
                <button
                  type="button"
                  onClick={() => addTextPreset('title')}
                  className="w-full p-3 rounded-lg border-2 border-[#1D5BFF] bg-white text-[#1D5BFF] font-bold text-left text-sm"
                >
                  + Añadir Título Banner
                </button>
                <button
                  type="button"
                  onClick={() => addTextPreset('subtitle')}
                  className="w-full p-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] font-semibold text-left"
                >
                  + Añadir Subtítulo
                </button>
                <button
                  type="button"
                  onClick={() => addTextPreset('body')}
                  style={{ fontFamily: 'var(--font-serif)' }}
                  className="w-full p-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] text-left"
                >
                  + Párrafo editorial
                </button>
                <button
                  type="button"
                  onClick={() => addTextPreset('note_red')}
                  className="w-full p-2.5 rounded-lg border border-rose-300 bg-rose-50 text-rose-700 font-semibold text-left"
                >
                  + Nota roja de examen
                </button>
                <button
                  type="button"
                  onClick={() => addTextPreset('note_hand')}
                  style={{ fontFamily: 'var(--font-hand)' }}
                  className="w-full p-2.5 rounded-lg border border-violet-300 bg-violet-50 text-violet-800 text-base text-left"
                >
                  + Nota manuscrita
                </button>
              </div>
            )}

            {drawerTab === 'draw' && (
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <div className="font-semibold">Herramienta de tinta</div>
                  <div className="grid grid-cols-3 gap-1.5">
                    <button
                      type="button"
                      onClick={() => setTool('pen')}
                      className={`p-2 rounded-lg border text-center ${
                        tool === 'pen'
                          ? 'border-[var(--luma-accent)] bg-[var(--luma-accent-soft)] font-semibold'
                          : 'border-[var(--luma-border)]'
                      }`}
                    >
                      Bolígrafo
                    </button>
                    <button
                      type="button"
                      onClick={() => setTool('highlighter')}
                      className={`p-2 rounded-lg border text-center ${
                        tool === 'highlighter'
                          ? 'border-[var(--luma-accent)] bg-[var(--luma-accent-soft)] font-semibold'
                          : 'border-[var(--luma-border)]'
                      }`}
                    >
                      Resaltar
                    </button>
                    <button
                      type="button"
                      onClick={() => setTool('eraser')}
                      className={`p-2 rounded-lg border text-center ${
                        tool === 'eraser'
                          ? 'border-[var(--luma-accent)] bg-[var(--luma-accent-soft)] font-semibold'
                          : 'border-[var(--luma-border)]'
                      }`}
                    >
                      Borrar
                    </button>
                  </div>
                </div>

                <div>
                  <div className="font-semibold mb-2">Paleta de color</div>
                  <div className="grid grid-cols-4 gap-2">
                    {INK_COLORS.map((c) => (
                      <button
                        key={c}
                        type="button"
                        onClick={() => setInkColor(c)}
                        className={`h-8 rounded-lg border border-black/15 ${
                          inkColor === c ? 'ring-2 ring-offset-1 ring-[var(--luma-ink)]' : ''
                        }`}
                        style={{ backgroundColor: c }}
                      />
                    ))}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between">
                    <span className="font-semibold">Grosor del trazo</span>
                    <span className="font-mono tabular-nums text-[var(--luma-ink-muted)]">
                      {inkWidth.toFixed(1)}px
                    </span>
                  </div>
                  <input
                    type="range"
                    min={1.5}
                    max={12}
                    step={0.5}
                    value={inkWidth}
                    onChange={(e) => setInkWidth(parseFloat(e.target.value))}
                    className="w-full accent-[var(--luma-accent)]"
                  />
                </div>
              </div>
            )}

            {drawerTab === 'layers' && (
              <div className="space-y-2">
                <div className="font-semibold mb-2">Capas del lienzo</div>
                {notebook.layers.map((layer) => {
                  const count = activePage.objects.filter((o) => o.layerId === layer.id).length;
                  return (
                    <div
                      key={layer.id}
                      className="p-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] flex items-center justify-between gap-2"
                    >
                      <div className="min-w-0">
                        <div className="font-medium truncate">{layer.name}</div>
                        <div className="text-[11px] text-[var(--luma-ink-muted)] tabular-nums">
                          {count} objetos
                        </div>
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => toggleLayerVisible(layer.id)}
                          title={layer.visible ? 'Ocultar capa' : 'Mostrar capa'}
                          className="p-1.5 rounded hover:bg-[var(--luma-surface-alt)]"
                        >
                          {layer.visible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5 opacity-50" />}
                        </button>
                        <button
                          type="button"
                          onClick={() => toggleLayerLocked(layer.id)}
                          title={layer.locked ? 'Desbloquear capa' : 'Bloquear capa'}
                          className="p-1.5 rounded hover:bg-[var(--luma-surface-alt)]"
                        >
                          {layer.locked ? <Lock className="w-3.5 h-3.5 text-[var(--luma-accent)]" /> : <Unlock className="w-3.5 h-3.5 opacity-55" />}
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Center Interactive Artboard Viewport */}
        <div className="flex-1 min-w-0 flex flex-col overflow-hidden bg-[var(--luma-surface-alt)]/55">
          <div className="flex-1 overflow-auto p-6 sm:p-10 flex justify-center items-start">
            <div
              style={{
                width: pageDims.w * zoom,
                height: pageDims.h * zoom,
              }}
              className="relative shrink-0"
            >
              {/* Scaled Virtual Artboard (1000 x pageH coordinate space) */}
              <div
                onPointerDown={handleArtboardPointerDown}
                onPointerMove={handleArtboardPointerMove}
                onPointerUp={handleArtboardPointerUp}
                onPointerCancel={handleArtboardPointerUp}
                style={{
                  width: pageDims.w,
                  height: pageDims.h,
                  transform: `scale(${zoom})`,
                  transformOrigin: 'top left',
                  backgroundColor: activePage.backgroundColor || '#FFFFFF',
                  ...paperPatternCss(activePage.pattern),
                }}
                className={`relative rounded-md border border-slate-300/80 luma-sheet-shadow overflow-hidden ${
                  tool === 'pen' || tool === 'highlighter'
                    ? 'cursor-crosshair touch-none'
                    : tool === 'eraser'
                      ? 'cursor-cell touch-none'
                      : 'cursor-default'
                }`}
              >
                {/* Cornell Margin Guide */}
                {activePage.pattern === 'cornell' && (
                  <>
                    <div
                      className="absolute top-[120px] bottom-[180px] left-[310px] w-[1.5px] bg-rose-300/80 pointer-events-none"
                    />
                    <div
                      className="absolute left-0 right-0 bottom-[180px] h-[1.5px] bg-rose-300/80 pointer-events-none"
                    />
                  </>
                )}

                {/* SVG Layer for Strokes & Shapes */}
                <svg
                  width={pageDims.w}
                  height={pageDims.h}
                  className="absolute inset-0 pointer-events-none"
                >
                  {strokes.map((s) => (
                    <polyline
                      key={s.id}
                      fill="none"
                      stroke={s.color}
                      strokeWidth={s.strokeWidth}
                      strokeOpacity={s.opacity}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      points={s.points.map((pt) => `${pt.x},${pt.y}`).join(' ')}
                    />
                  ))}
                  {activeStroke && (
                    <polyline
                      fill="none"
                      stroke={activeStroke.color}
                      strokeWidth={activeStroke.strokeWidth}
                      strokeOpacity={activeStroke.opacity}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      points={activeStroke.points.map((pt) => `${pt.x},${pt.y}`).join(' ')}
                    />
                  )}
                </svg>

                {/* Interactive Objects Layer */}
                {nonStrokes.map((obj) => {
                  const selected = obj.id === selectedId;
                  const editing = obj.id === editingId;
                  const locked = layerMap.get(obj.layerId)?.locked === true;

                  return (
                    <div
                      key={obj.id}
                      onPointerDown={(e) => startObjectDrag(e, obj, 'move')}
                      onDoubleClick={(e) => {
                        e.stopPropagation();
                        if (!locked && (obj.kind === 'text' || obj.kind === 'sticky')) {
                          setSelectedId(obj.id);
                          setEditingId(obj.id);
                        }
                      }}
                      style={{
                        left: obj.x,
                        top: obj.y,
                        width: obj.w,
                        height: obj.h,
                        opacity: obj.opacity,
                      }}
                      className={`absolute ${
                        tool === 'select' && !locked ? 'cursor-move' : 'pointer-events-none'
                      } ${selected ? 'ring-2 ring-[#2B64F5] ring-offset-1 z-20' : 'z-10'}`}
                    >
                      {obj.kind === 'text' && (
                        <div
                          style={{
                            backgroundColor: obj.fillColor,
                            borderColor: obj.borderColor,
                            color: obj.color,
                            fontSize: obj.fontSize,
                            fontWeight: obj.bold ? 600 : 400,
                            textAlign: obj.align,
                            fontFamily:
                              obj.fontFamily === 'serif'
                                ? 'var(--font-serif)'
                                : obj.fontFamily === 'mono'
                                  ? 'var(--font-mono)'
                                  : obj.fontFamily === 'hand'
                                    ? 'var(--font-hand)'
                                    : 'var(--font-sans)',
                          }}
                          className="w-full h-full rounded-xl border-2 px-3.5 py-2.5 overflow-hidden leading-snug"
                        >
                          {editing ? (
                            <textarea
                              autoFocus
                              value={obj.text}
                              onChange={(e) => updateObject(obj.id, { text: e.target.value })}
                              onBlur={() => setEditingId(null)}
                              onPointerDown={(e) => e.stopPropagation()}
                              className="w-full h-full bg-transparent outline-none resize-none"
                              style={{ color: obj.color, fontSize: obj.fontSize }}
                            />
                          ) : (
                            <div className="whitespace-pre-wrap break-words">{obj.text}</div>
                          )}
                        </div>
                      )}

                      {obj.kind === 'sticky' && (
                        <div
                          style={{ backgroundColor: obj.bgColor, color: obj.textColor }}
                          className="w-full h-full rounded-lg p-3.5 shadow-md border border-black/10 text-sm leading-snug"
                        >
                          {editing ? (
                            <textarea
                              autoFocus
                              value={obj.text}
                              onChange={(e) => updateObject(obj.id, { text: e.target.value })}
                              onBlur={() => setEditingId(null)}
                              onPointerDown={(e) => e.stopPropagation()}
                              className="w-full h-full bg-transparent outline-none resize-none"
                            />
                          ) : (
                            <div className="whitespace-pre-wrap break-words font-medium">{obj.text}</div>
                          )}
                        </div>
                      )}

                      {obj.kind === 'shape' && (
                        <svg width="100%" height="100%" viewBox={`0 0 ${obj.w} ${obj.h}`}>
                          {obj.shape === 'rect' && (
                            <rect
                              x={2}
                              y={2}
                              width={Math.max(4, obj.w - 4)}
                              height={Math.max(4, obj.h - 4)}
                              rx={12}
                              fill={obj.fillColor}
                              stroke={obj.strokeColor}
                              strokeWidth={obj.strokeWidth}
                            />
                          )}
                          {obj.shape === 'ellipse' && (
                            <ellipse
                              cx={obj.w / 2}
                              cy={obj.h / 2}
                              rx={Math.max(2, obj.w / 2 - 2)}
                              ry={Math.max(2, obj.h / 2 - 2)}
                              fill={obj.fillColor}
                              stroke={obj.strokeColor}
                              strokeWidth={obj.strokeWidth}
                            />
                          )}
                          {obj.shape === 'line' && (
                            <line
                              x1={6}
                              y1={obj.h / 2}
                              x2={obj.w - 6}
                              y2={obj.h / 2}
                              stroke={obj.strokeColor}
                              strokeWidth={obj.strokeWidth}
                              strokeLinecap="round"
                            />
                          )}
                          {obj.shape === 'arrow' && (
                            <g stroke={obj.strokeColor} strokeWidth={obj.strokeWidth} fill="none">
                              <line x1={8} y1={obj.h / 2} x2={obj.w - 14} y2={obj.h / 2} strokeLinecap="round" />
                              <polyline
                                points={`${obj.w - 24},${obj.h / 2 - 10} ${obj.w - 10},${obj.h / 2} ${obj.w - 24},${obj.h / 2 + 10}`}
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </g>
                          )}
                        </svg>
                      )}

                      {obj.kind === 'table' && (
                        <div
                          style={{ borderColor: obj.borderColor }}
                          className="w-full h-full rounded-lg border-2 overflow-hidden bg-white grid"
                        >
                          <div
                            className="grid w-full h-full"
                            style={{
                              gridTemplateColumns: `repeat(${obj.cols}, 1fr)`,
                              gridTemplateRows: `repeat(${obj.rows}, 1fr)`,
                            }}
                          >
                            {obj.cells.map((cell, idx) => {
                              const isHeader = idx < obj.cols;
                              return (
                                <input
                                  key={idx}
                                  value={cell}
                                  onPointerDown={(e) => e.stopPropagation()}
                                  onChange={(e) => {
                                    const nextCells = [...obj.cells];
                                    nextCells[idx] = e.target.value;
                                    updateObject(obj.id, { cells: nextCells });
                                  }}
                                  style={{
                                    backgroundColor: isHeader ? obj.headerBg : '#FFFFFF',
                                    borderColor: obj.borderColor,
                                  }}
                                  className={`px-2 py-1 text-xs border-r border-b outline-none text-slate-900 ${
                                    isHeader ? 'font-bold' : ''
                                  }`}
                                />
                              );
                            })}
                          </div>
                        </div>
                      )}

                      {obj.kind === 'sticker' && (
                        <div
                          style={{ backgroundColor: obj.bgColor, color: obj.color }}
                          className="w-full h-full rounded-full px-3 flex items-center justify-center gap-1.5 text-sm font-bold shadow-xs border border-current/25"
                        >
                          <span>{obj.symbol}</span>
                          <span>{obj.label}</span>
                        </div>
                      )}

                      {/* Corner Resize Handle */}
                      {selected && !locked && (
                        <div
                          onPointerDown={(e) => startObjectDrag(e, obj, 'resize')}
                          className="absolute -bottom-2 -right-2 w-4 h-4 rounded-full bg-white border-2 border-[#2B64F5] cursor-nwse-resize shadow"
                        />
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Bottom Canva Page Strip */}
          <div className="h-10 px-4 border-t border-[var(--luma-border)] bg-[var(--luma-surface)] flex items-center justify-between gap-3 text-xs shrink-0">
            <div className="flex items-center gap-1.5 overflow-x-auto">
              {notebook.pages.map((p, idx) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => onChangeNotebook({ ...notebook, activePageIndex: idx })}
                  className={`h-7 px-2.5 rounded-md tabular-nums whitespace-nowrap transition-colors ${
                    idx === notebook.activePageIndex
                      ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                      : 'text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]'
                  }`}
                >
                  {p.title}
                </button>
              ))}
              <button
                type="button"
                onClick={addNewPage}
                className="h-7 px-2 rounded-md border border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)] inline-flex items-center gap-1 whitespace-nowrap"
              >
                <Plus className="w-3 h-3" /> Página
              </button>
            </div>

            <span className="text-[11px] text-[var(--luma-ink-muted)] tabular-nums hidden sm:inline">
              {pageDims.label} · {activePage.objects.length} elementos · Doble clic en texto para editar
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
