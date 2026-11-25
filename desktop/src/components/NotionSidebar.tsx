import React, { useState } from 'react';
import {
  BookOpen,
  ChevronDown,
  ChevronRight,
  FolderKanban,
  Home,
  Library,
  Palette,
  PenTool,
  Pin,
  Plus,
  Search,
  Upload,
  X,
} from 'lucide-react';
import { LumaDocument, LumaNotebook, SagaPlaylist, WorkspaceTabKind } from '../types';
import { ViewportProfile } from '../hooks/useViewportProfile';

interface NotionSidebarProps {
  viewport: ViewportProfile;
  open: boolean;
  width: number;
  activeNav: WorkspaceTabKind;
  activeTargetId?: string;
  documents: LumaDocument[];
  playlists: SagaPlaylist[];
  notebooks: LumaNotebook[];
  themeName: string;
  onCloseDrawer: () => void;
  onResizeWidth: (newWidth: number) => void;
  onNavigate: (kind: 'home' | 'library' | 'sagas' | 'notebooks', sagaId?: string) => void;
  onOpenDocument: (docId: string) => void;
  onOpenNotebook: (nbId: string) => void;
  onImportFiles: () => void;
  onNewNotebook: () => void;
  onOpenCommandPalette: () => void;
  onOpenAppearance: () => void;
}

export const NotionSidebar: React.FC<NotionSidebarProps> = ({
  viewport,
  open,
  width,
  activeNav,
  activeTargetId,
  documents,
  playlists,
  notebooks,
  themeName,
  onCloseDrawer,
  onResizeWidth,
  onNavigate,
  onOpenDocument,
  onOpenNotebook,
  onImportFiles,
  onNewNotebook,
  onOpenCommandPalette,
  onOpenAppearance,
}) => {
  const [pinnedExpanded, setPinnedExpanded] = useState(true);
  const [sagasExpanded, setSagasExpanded] = useState(true);
  const [notebooksExpanded, setNotebooksExpanded] = useState(true);

  const pinnedDocs = documents.filter((d) => d.pinned).slice(0, 5);
  const recentDocs =
    pinnedDocs.length > 0
      ? pinnedDocs
      : [...documents].sort((a, b) => b.lastOpenedAt - a.lastOpenedAt).slice(0, 4);

  const navItems: Array<{
    kind: 'home' | 'library' | 'sagas' | 'notebooks';
    label: string;
    icon: React.ReactNode;
    count?: number;
  }> = [
    { kind: 'home', label: 'Inicio', icon: <Home className="w-4 h-4 shrink-0" /> },
    {
      kind: 'library',
      label: 'Biblioteca',
      icon: <Library className="w-4 h-4 shrink-0" />,
      count: documents.length,
    },
    {
      kind: 'notebooks',
      label: 'Cuadernos',
      icon: <PenTool className="w-4 h-4 shrink-0" />,
      count: notebooks.length,
    },
    {
      kind: 'sagas',
      label: 'Sagas y Listas',
      icon: <FolderKanban className="w-4 h-4 shrink-0" />,
      count: playlists.length,
    },
  ];

  // Compact tablet/split-screen icon rail when sidebar is collapsed or in rail mode
  const showCompactRail =
    !open && (viewport.sidebarMode === 'rail' || viewport.sidebarMode === 'expanded');

  if (showCompactRail) {
    return (
      <aside className="w-[56px] shrink-0 border-r bg-[var(--luma-sidebar)] flex flex-col items-center py-2.5 gap-1.5 select-none">
        <button
          type="button"
          onClick={onOpenCommandPalette}
          title="Buscar (Ctrl+K)"
          className="w-9 h-9 rounded-lg flex items-center justify-center text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)] transition-colors"
        >
          <Search className="w-4 h-4" />
        </button>

        <div className="w-6 h-px bg-[var(--luma-border)] my-1" />

        {navItems.map((item) => {
          const isActive = activeNav === item.kind;
          return (
            <button
              key={item.kind}
              type="button"
              onClick={() => onNavigate(item.kind)}
              title={item.label}
              className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors ${
                isActive
                  ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)]'
                  : 'text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
              }`}
            >
              {item.icon}
            </button>
          );
        })}

        <div className="w-6 h-px bg-[var(--luma-border)] my-1" />

        <button
          type="button"
          onClick={onImportFiles}
          title="Importar documento"
          className="w-9 h-9 rounded-lg flex items-center justify-center text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)] transition-colors"
        >
          <Upload className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={onNewNotebook}
          title="Nuevo cuaderno"
          className="w-9 h-9 rounded-lg flex items-center justify-center text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)] transition-colors"
        >
          <Plus className="w-4 h-4" />
        </button>

        <div className="flex-1" />

        <button
          type="button"
          onClick={onOpenAppearance}
          title={`Apariencia · ${themeName}`}
          className="w-9 h-9 rounded-lg flex items-center justify-center text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)] transition-colors"
        >
          <Palette className="w-4 h-4" />
        </button>
      </aside>
    );
  }

  if (!open) return null;

  const isOverlayDrawer = viewport.sidebarMode === 'drawer' || viewport.sidebarMode === 'rail';

  const startResize = (e: React.MouseEvent) => {
    e.preventDefault();
    const startX = e.clientX;
    const startWidth = width;
    const onMove = (ev: MouseEvent) => {
      const next = Math.min(
        viewport.maxSidebarWidth,
        Math.max(viewport.minSidebarWidth, startWidth + (ev.clientX - startX))
      );
      onResizeWidth(next);
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const sidebarBody = (
    <aside
      style={{ width: isOverlayDrawer ? viewport.recommendedSidebarWidth : width }}
      className="relative h-full shrink-0 border-r bg-[var(--luma-sidebar)] text-[var(--luma-ink)] flex flex-col select-none"
    >
      {/* Workspace Header */}
      <div className="px-3 pt-3 pb-2 flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="text-[13px] font-semibold truncate">Espacio Luma</div>
          <div className="text-[11px] text-[var(--luma-ink-muted)] truncate">
            {viewport.tierLabel}
          </div>
        </div>
        {isOverlayDrawer && (
          <button
            type="button"
            onClick={onCloseDrawer}
            title="Cerrar menú"
            className="h-7 w-7 rounded-md flex items-center justify-center text-[var(--luma-ink-muted)] hover:bg-[var(--luma-surface-alt)]"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* Quick Search Bar */}
      <div className="px-2.5 pb-2">
        <button
          type="button"
          onClick={onOpenCommandPalette}
          className="w-full h-8 px-2.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:bg-[var(--luma-surface-alt)] flex items-center justify-between text-xs text-[var(--luma-ink-muted)] transition-colors"
        >
          <span className="flex items-center gap-2 truncate">
            <Search className="w-3.5 h-3.5 shrink-0" />
            <span className="truncate">Buscar en Luma...</span>
          </span>
          <span className="font-mono text-[10px] opacity-75 shrink-0">Ctrl+K</span>
        </button>
      </div>

      {/* Scrollable Navigation Sections */}
      <div className="flex-1 overflow-y-auto px-2 py-1 space-y-4">
        {/* Primary Nav */}
        <nav className="space-y-0.5">
          {navItems.map((item) => {
            const isActive = activeNav === item.kind;
            return (
              <button
                key={item.kind}
                type="button"
                onClick={() => {
                  onNavigate(item.kind);
                  if (isOverlayDrawer) onCloseDrawer();
                }}
                className={`w-full h-8 px-2.5 rounded-lg flex items-center justify-between text-[13px] transition-colors ${
                  isActive
                    ? 'bg-[var(--luma-accent-soft)] text-[var(--luma-accent)] font-semibold'
                    : 'text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)] font-medium'
                }`}
              >
                <span className="flex items-center gap-2.5 min-w-0">
                  {item.icon}
                  <span className="truncate">{item.label}</span>
                </span>
                {typeof item.count === 'number' && (
                  <span className="text-[11px] tabular-nums text-[var(--luma-ink-muted)]">
                    {item.count}
                  </span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Quick Actions */}
        <div className="grid grid-cols-2 gap-1.5 px-0.5">
          <button
            type="button"
            onClick={() => {
              onImportFiles();
              if (isOverlayDrawer) onCloseDrawer();
            }}
            className="h-8 px-2 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:bg-[var(--luma-surface-alt)] flex items-center justify-center gap-1.5 text-xs font-medium text-[var(--luma-ink)] transition-colors whitespace-nowrap"
          >
            <Upload className="w-3.5 h-3.5 text-[var(--luma-accent)] shrink-0" />
            <span className="truncate">Importar</span>
          </button>
          <button
            type="button"
            onClick={() => {
              onNewNotebook();
              if (isOverlayDrawer) onCloseDrawer();
            }}
            className="h-8 px-2 rounded-lg bg-[var(--luma-accent)] text-[var(--luma-on-accent)] hover:opacity-95 flex items-center justify-center gap-1.5 text-xs font-semibold transition-opacity whitespace-nowrap"
          >
            <Plus className="w-3.5 h-3.5 shrink-0" />
            <span className="truncate">Cuaderno</span>
          </button>
        </div>

        {/* Pinned / Recent Reading */}
        <div>
          <button
            type="button"
            onClick={() => setPinnedExpanded((v) => !v)}
            className="w-full px-1.5 py-1 flex items-center gap-1 text-[11px] font-semibold text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)]"
          >
            {pinnedExpanded ? (
              <ChevronDown className="w-3 h-3" />
            ) : (
              <ChevronRight className="w-3 h-3" />
            )}
            <span>{pinnedDocs.length > 0 ? 'Fijados' : 'Lecturas recientes'}</span>
          </button>
          {pinnedExpanded && (
            <div className="mt-0.5 space-y-0.5">
              {recentDocs.map((doc) => {
                const isSelected = activeNav === 'reader' && activeTargetId === doc.id;
                return (
                  <button
                    key={doc.id}
                    type="button"
                    onClick={() => {
                      onOpenDocument(doc.id);
                      if (isOverlayDrawer) onCloseDrawer();
                    }}
                    className={`w-full h-8 px-2 rounded-lg flex items-center gap-2 text-xs transition-colors ${
                      isSelected
                        ? 'bg-[var(--luma-surface)] text-[var(--luma-ink)] font-semibold border border-[var(--luma-border)]'
                        : 'text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
                    }`}
                  >
                    <span
                      className="w-2 h-2 rounded-sm shrink-0"
                      style={{ backgroundColor: doc.coverColor }}
                    />
                    <span className="truncate flex-1 text-left">{doc.title}</span>
                    {doc.pinned && <Pin className="w-3 h-3 opacity-50 shrink-0" />}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Notebooks Tree */}
        <div>
          <div className="flex items-center justify-between px-1.5 py-1">
            <button
              type="button"
              onClick={() => setNotebooksExpanded((v) => !v)}
              className="flex items-center gap-1 text-[11px] font-semibold text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)]"
            >
              {notebooksExpanded ? (
                <ChevronDown className="w-3 h-3" />
              ) : (
                <ChevronRight className="w-3 h-3" />
              )}
              <span>Cuadernos</span>
            </button>
            <button
              type="button"
              onClick={onNewNotebook}
              title="Crear cuaderno"
              className="p-0.5 rounded hover:bg-[var(--luma-surface-alt)] text-[var(--luma-ink-muted)]"
            >
              <Plus className="w-3.5 h-3.5" />
            </button>
          </div>
          {notebooksExpanded && (
            <div className="mt-0.5 space-y-0.5">
              {notebooks.map((nb) => {
                const isSelected = activeNav === 'studio' && activeTargetId === nb.id;
                return (
                  <button
                    key={nb.id}
                    type="button"
                    onClick={() => {
                      onOpenNotebook(nb.id);
                      if (isOverlayDrawer) onCloseDrawer();
                    }}
                    className={`w-full h-8 px-2 rounded-lg flex items-center gap-2 text-xs transition-colors ${
                      isSelected
                        ? 'bg-[var(--luma-surface)] text-[var(--luma-ink)] font-semibold border border-[var(--luma-border)]'
                        : 'text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
                    }`}
                  >
                    <PenTool className="w-3.5 h-3.5 shrink-0" style={{ color: nb.coverColor }} />
                    <span className="truncate flex-1 text-left">{nb.title}</span>
                    <span className="text-[10px] tabular-nums opacity-65">{nb.pages.length}p</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Sagas / Playlists Tree */}
        <div>
          <button
            type="button"
            onClick={() => setSagasExpanded((v) => !v)}
            className="w-full px-1.5 py-1 flex items-center gap-1 text-[11px] font-semibold text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)]"
          >
            {sagasExpanded ? (
              <ChevronDown className="w-3 h-3" />
            ) : (
              <ChevronRight className="w-3 h-3" />
            )}
            <span>Sagas y Listas</span>
          </button>
          {sagasExpanded && (
            <div className="mt-0.5 space-y-0.5">
              {playlists.map((pl) => (
                <button
                  key={pl.id}
                  type="button"
                  onClick={() => {
                    onNavigate('sagas', pl.id);
                    if (isOverlayDrawer) onCloseDrawer();
                  }}
                  className="w-full h-8 px-2 rounded-lg flex items-center gap-2 text-xs text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)] transition-colors"
                >
                  <BookOpen className="w-3.5 h-3.5 shrink-0" style={{ color: pl.accentColor }} />
                  <span className="truncate flex-1 text-left">{pl.name}</span>
                  <span className="text-[10px] tabular-nums opacity-65">
                    {pl.documentIds.length}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Bottom Appearance Footer */}
      <div className="p-2 border-t border-[var(--luma-border)]">
        <button
          type="button"
          onClick={onOpenAppearance}
          className="w-full h-9 px-2.5 rounded-lg hover:bg-[var(--luma-surface-alt)] flex items-center justify-between text-xs transition-colors"
        >
          <span className="flex items-center gap-2 min-w-0">
            <Palette className="w-4 h-4 text-[var(--luma-accent)] shrink-0" />
            <span className="truncate font-medium text-[var(--luma-ink)]">Apariencia</span>
          </span>
          <span className="text-[11px] text-[var(--luma-ink-muted)] truncate max-w-[96px]">
            {themeName}
          </span>
        </button>
      </div>

      {/* Desktop Resize Handle */}
      {!isOverlayDrawer && (
        <div
          onMouseDown={startResize}
          title="Arrastra para ajustar el ancho del menú"
          className="absolute top-0 right-0 w-1.5 h-full cursor-col-resize hover:bg-[var(--luma-accent)]/35 transition-colors"
        />
      )}
    </aside>
  );

  if (isOverlayDrawer) {
    return (
      <div className="fixed inset-0 z-40 flex">
        <div
          onClick={onCloseDrawer}
          className="fixed inset-0 bg-black/35 backdrop-blur-[1px]"
        />
        <div className="relative z-10 h-full shadow-2xl">{sidebarBody}</div>
      </div>
    );
  }

  return sidebarBody;
};
