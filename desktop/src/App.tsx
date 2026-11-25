import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  HighlightNote,
  LumaDocument,
  LumaNotebook,
  LumaWorkspaceState,
  WorkspacePreferences,
  WorkspaceTab,
} from './types';
import { applyThemeToDocument, getThemeSpec } from './themes';
import { createNotebookFromTemplate, createSeedWorkspace } from './data/seedWorkspace';
import { useViewportProfile } from './hooks/useViewportProfile';
import { importFromBase64File, importFromBrowserFile } from './utils/documentImporter';
import { TitlebarAndTabs } from './components/TitlebarAndTabs';
import { NotionSidebar } from './components/NotionSidebar';
import { LibraryWorkspace } from './components/LibraryWorkspace';
import { ReaderWorkspace } from './components/ReaderWorkspace';
import { NotebookStudio } from './components/NotebookStudio';
import { CommandPalette } from './components/CommandPalette';
import { AppearanceModal } from './components/AppearanceModal';

const STORAGE_KEY = 'luma_desktop_workspace_v1';

export default function App() {
  const [workspace, setWorkspace] = useState<LumaWorkspaceState>(() => createSeedWorkspace());
  const [hydrated, setHydrated] = useState(false);
  const [tabs, setTabs] = useState<WorkspaceTab[]>([
    { id: 'tab_home', kind: 'home', title: 'Inicio' },
  ]);
  const [activeTabId, setActiveTabId] = useState('tab_home');
  const [selectedSagaId, setSelectedSagaId] = useState<string | undefined>(undefined);
  const [splitEnabled, setSplitEnabled] = useState(false);
  const [splitNotebookId, setSplitNotebookId] = useState<string | null>(null);
  const [isZenMode, setIsZenMode] = useState(false);
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [isDraggingFile, setIsDraggingFile] = useState(false);
  const [platform, setPlatform] = useState('win32');
  const [isMaximized, setIsMaximized] = useState(false);
  const [drawerOpenMobile, setDrawerOpenMobile] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const viewport = useViewportProfile(workspace.prefs.density);

  // Load persisted workspace (Electron disk file or browser localStorage)
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        let raw: string | null = null;
        if (window.lumaDesktop?.loadWorkspace) {
          raw = await window.lumaDesktop.loadWorkspace();
        } else {
          raw = localStorage.getItem(STORAGE_KEY);
        }
        if (!cancelled && raw) {
          const parsed = JSON.parse(raw) as Partial<LumaWorkspaceState>;
          const seed = createSeedWorkspace();
          setWorkspace({
            documents: Array.isArray(parsed.documents) && parsed.documents.length > 0 ? parsed.documents : seed.documents,
            playlists: Array.isArray(parsed.playlists) ? parsed.playlists : seed.playlists,
            notebooks: Array.isArray(parsed.notebooks) && parsed.notebooks.length > 0 ? parsed.notebooks : seed.notebooks,
            highlights: Array.isArray(parsed.highlights) ? parsed.highlights : seed.highlights,
            bookmarks: Array.isArray(parsed.bookmarks) ? parsed.bookmarks : seed.bookmarks,
            prefs: { ...seed.prefs, ...(parsed.prefs ?? {}) },
          });
        }
      } catch {
        // Fallback to seed workspace
      } finally {
        if (!cancelled) setHydrated(true);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  // Sync Electron platform & maximize state
  useEffect(() => {
    if (!window.lumaDesktop) return;
    window.lumaDesktop.getPlatform().then(setPlatform).catch(() => undefined);
    window.lumaDesktop.isMaximized().then(setIsMaximized).catch(() => undefined);
    const unsub = window.lumaDesktop.onWindowState((st) => setIsMaximized(st.maximized));
    return unsub;
  }, []);

  // Persist workspace debounced
  useEffect(() => {
    if (!hydrated) return;
    const timer = window.setTimeout(() => {
      try {
        const serialized = JSON.stringify(workspace);
        if (window.lumaDesktop?.saveWorkspace) {
          window.lumaDesktop.saveWorkspace(serialized);
        } else {
          localStorage.setItem(STORAGE_KEY, serialized);
        }
      } catch {
        // Ignore storage quota issues for huge PDFs
      }
    }, 350);
    return () => window.clearTimeout(timer);
  }, [workspace, hydrated]);

  // Apply active Luma theme variables
  useEffect(() => {
    applyThemeToDocument(workspace.prefs.theme);
  }, [workspace.prefs.theme]);

  // Global keyboard shortcuts (Ctrl+K Command Palette, Ctrl+\ Sidebar, Escape Zen)
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setCommandPaletteOpen((v) => !v);
      } else if ((e.ctrlKey || e.metaKey) && e.key === '\\') {
        e.preventDefault();
        toggleSidebar();
      } else if (e.key === 'Escape' && isZenMode) {
        setIsZenMode(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  const activeTab = useMemo(
    () => tabs.find((t) => t.id === activeTabId) ?? tabs[0],
    [tabs, activeTabId]
  );

  const updatePrefs = useCallback((patch: Partial<WorkspacePreferences>) => {
    setWorkspace((prev) => ({ ...prev, prefs: { ...prev.prefs, ...patch } }));
  }, []);

  const toggleSidebar = useCallback(() => {
    if (viewport.sidebarMode === 'drawer' || viewport.sidebarMode === 'rail') {
      setDrawerOpenMobile((v) => !v);
    } else {
      updatePrefs({ sidebarCollapsed: !workspace.prefs.sidebarCollapsed });
    }
  }, [viewport.sidebarMode, workspace.prefs.sidebarCollapsed, updatePrefs]);

  const sidebarIsOpen = isZenMode
    ? false
    : viewport.sidebarMode === 'drawer' || viewport.sidebarMode === 'rail'
      ? drawerOpenMobile
      : !workspace.prefs.sidebarCollapsed;

  const effectiveSidebarWidth = Math.min(
    viewport.maxSidebarWidth,
    Math.max(
      viewport.minSidebarWidth,
      workspace.prefs.sidebarWidth || viewport.recommendedSidebarWidth
    )
  );

  // Navigation & Tab Management
  const navigatePrimary = (
    kind: 'home' | 'library' | 'sagas' | 'notebooks',
    sagaId?: string
  ) => {
    if (kind === 'sagas') setSelectedSagaId(sagaId);
    const tabId = `tab_${kind}`;
    const labelMap = {
      home: 'Inicio',
      library: 'Biblioteca',
      sagas: 'Sagas',
      notebooks: 'Cuadernos',
    };
    setTabs((prev) => {
      if (prev.some((t) => t.id === tabId)) return prev;
      return [...prev, { id: tabId, kind, title: labelMap[kind] }];
    });
    setActiveTabId(tabId);
  };

  const openDocument = (docId: string) => {
    const doc = workspace.documents.find((d) => d.id === docId);
    if (!doc) return;
    const tabId = `doc_${docId}`;
    setTabs((prev) => {
      if (prev.some((t) => t.id === tabId)) return prev;
      return [...prev, { id: tabId, kind: 'reader', title: doc.title, targetId: docId }];
    });
    setActiveTabId(tabId);
    setWorkspace((prev) => ({
      ...prev,
      documents: prev.documents.map((d) =>
        d.id === docId ? { ...d, lastOpenedAt: Date.now() } : d
      ),
    }));
  };

  const openNotebook = (nbId: string) => {
    const nb = workspace.notebooks.find((n) => n.id === nbId);
    if (!nb) return;
    const tabId = `nb_${nbId}`;
    setTabs((prev) => {
      if (prev.some((t) => t.id === tabId)) return prev;
      return [...prev, { id: tabId, kind: 'studio', title: nb.title, targetId: nbId }];
    });
    setActiveTabId(tabId);
    // Auto-collapse wide sidebar on smaller laptops so the Canva artboard breathes
    if (viewport.tier === 'laptop' && !workspace.prefs.sidebarCollapsed) {
      updatePrefs({ sidebarCollapsed: true });
    }
  };

  const closeTab = (tabId: string) => {
    setTabs((prev) => {
      const next = prev.filter((t) => t.id !== tabId);
      if (activeTabId === tabId && next.length > 0) {
        setActiveTabId(next[next.length - 1].id);
      }
      return next.length > 0 ? next : [{ id: 'tab_home', kind: 'home', title: 'Inicio' }];
    });
  };

  const handleCreateNotebook = (
    tpl: 'blank' | 'ruled' | 'grid' | 'cornell' | 'concept_map' | 'comparison' = 'cornell'
  ) => {
    const nb = createNotebookFromTemplate(tpl);
    setWorkspace((prev) => ({ ...prev, notebooks: [nb, ...prev.notebooks] }));
    openNotebook(nb.id);
  };

  const appendImportedDocs = (docs: LumaDocument[]) => {
    if (docs.length === 0) return;
    setWorkspace((prev) => ({ ...prev, documents: [...docs, ...prev.documents] }));
    openDocument(docs[0].id);
  };

  const handleImportFiles = async () => {
    if (window.lumaDesktop?.pickFiles) {
      const picked = await window.lumaDesktop.pickFiles('document');
      if (picked.length === 0) return;
      const imported: LumaDocument[] = [];
      for (const f of picked) {
        imported.push(await importFromBase64File(f));
      }
      appendImportedDocs(imported);
    } else {
      fileInputRef.current?.click();
    }
  };

  const handleBrowserFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    if (files.length === 0) return;
    const imported: LumaDocument[] = [];
    for (const f of files) {
      imported.push(await importFromBrowserFile(f));
    }
    appendImportedDocs(imported);
  };

  const handleDropFiles = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingFile(false);
    const files = Array.from(e.dataTransfer.files ?? []);
    if (files.length === 0) return;
    const imported: LumaDocument[] = [];
    for (const f of files) {
      imported.push(await importFromBrowserFile(f));
    }
    appendImportedDocs(imported);
  };

  const handleUpdateDocument = (docId: string, updater: (doc: LumaDocument) => LumaDocument) => {
    setWorkspace((prev) => ({
      ...prev,
      documents: prev.documents.map((d) => (d.id === docId ? updater(d) : d)),
    }));
  };

  const handleChangeNotebook = (updated: LumaNotebook) => {
    setWorkspace((prev) => ({
      ...prev,
      notebooks: prev.notebooks.map((n) => (n.id === updated.id ? updated : n)),
    }));
    setTabs((prev) =>
      prev.map((t) => (t.kind === 'studio' && t.targetId === updated.id ? { ...t, title: updated.title } : t))
    );
  };

  const handleAddHighlight = (item: Omit<HighlightNote, 'id' | 'createdAt'>) => {
    const created: HighlightNote = {
      ...item,
      id: `hl_${Date.now().toString(36)}`,
      createdAt: Date.now(),
    };
    setWorkspace((prev) => ({ ...prev, highlights: [created, ...prev.highlights] }));
  };

  const handleDeleteHighlight = (id: string) => {
    setWorkspace((prev) => ({
      ...prev,
      highlights: prev.highlights.filter((h) => h.id !== id),
    }));
  };

  const handleToggleBookmark = (docId: string, unitIndex: number, label: string) => {
    setWorkspace((prev) => {
      const exists = prev.bookmarks.find(
        (b) => b.documentId === docId && b.unitIndex === unitIndex
      );
      if (exists) {
        return { ...prev, bookmarks: prev.bookmarks.filter((b) => b.id !== exists.id) };
      }
      return {
        ...prev,
        bookmarks: [
          {
            id: `bm_${Date.now().toString(36)}`,
            documentId: docId,
            unitIndex,
            label,
            createdAt: Date.now(),
          },
          ...prev.bookmarks,
        ],
      };
    });
  };

  const handleToggleSplit = () => {
    setSplitEnabled((prev) => {
      const next = !prev;
      if (next && !splitNotebookId && workspace.notebooks.length > 0) {
        setSplitNotebookId(workspace.notebooks[0].id);
      }
      return next;
    });
  };

  const activeTheme = getThemeSpec(workspace.prefs.theme);
  const activeDocument =
    activeTab.kind === 'reader'
      ? workspace.documents.find((d) => d.id === activeTab.targetId)
      : undefined;
  const activeNotebook =
    activeTab.kind === 'studio'
      ? workspace.notebooks.find((n) => n.id === activeTab.targetId)
      : undefined;
  const splitNotebook =
    workspace.notebooks.find((n) => n.id === splitNotebookId) ?? workspace.notebooks[0];

  const showSplitPane = splitEnabled && viewport.canSplitView && activeTab.kind !== 'studio' && Boolean(splitNotebook);

  return (
    <div
      onDragOver={(e) => {
        e.preventDefault();
        setIsDraggingFile(true);
      }}
      onDragLeave={(e) => {
        if (e.currentTarget === e.target) setIsDraggingFile(false);
      }}
      onDrop={handleDropFiles}
      className="h-screen w-screen flex flex-col overflow-hidden bg-[var(--luma-bg)] text-[var(--luma-ink)] relative"
    >
      {/* Hidden browser file picker fallback */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".epub,.pdf,.docx,.pptx,.txt,.md,.fb2"
        onChange={handleBrowserFileChange}
        className="hidden"
      />

      {/* Top Native Frameless PC Titlebar + Notion/Arc Tabs */}
      {!isZenMode && (
        <TitlebarAndTabs
          tabs={tabs}
          activeTabId={activeTab.id}
          viewport={viewport}
          platform={platform}
          isMaximized={isMaximized}
          isZenMode={isZenMode}
          splitEnabled={splitEnabled}
          sidebarOpen={sidebarIsOpen}
          onSelectTab={setActiveTabId}
          onCloseTab={closeTab}
          onNewNotebook={() => handleCreateNotebook('cornell')}
          onToggleSidebar={toggleSidebar}
          onToggleSplit={handleToggleSplit}
          onToggleZen={() => setIsZenMode((v) => !v)}
          onOpenCommandPalette={() => setCommandPaletteOpen(true)}
        />
      )}

      {/* Main Proportional Body: Notion Sidebar + Primary Viewport (+ Optional Split Notebook) */}
      <div className="flex-1 min-h-0 flex overflow-hidden relative">
        {!isZenMode && (
          <NotionSidebar
            viewport={viewport}
            open={sidebarIsOpen}
            width={effectiveSidebarWidth}
            activeNav={activeTab.kind}
            activeTargetId={activeTab.targetId}
            documents={workspace.documents}
            playlists={workspace.playlists}
            notebooks={workspace.notebooks}
            themeName={activeTheme.name}
            onCloseDrawer={() => setDrawerOpenMobile(false)}
            onResizeWidth={(w) => updatePrefs({ sidebarWidth: w })}
            onNavigate={navigatePrimary}
            onOpenDocument={openDocument}
            onOpenNotebook={openNotebook}
            onImportFiles={handleImportFiles}
            onNewNotebook={() => handleCreateNotebook('cornell')}
            onOpenCommandPalette={() => setCommandPaletteOpen(true)}
            onOpenAppearance={() => setAppearanceOpen(true)}
          />
        )}

        {/* Primary Content Area */}
        <main className="flex-1 min-w-0 h-full flex overflow-hidden">
          <div className={showSplitPane ? 'w-1/2 min-w-0 h-full border-r border-[var(--luma-border)]' : 'flex-1 min-w-0 h-full'}>
            {activeTab.kind === 'reader' && activeDocument ? (
              <ReaderWorkspace
                document={activeDocument}
                prefs={workspace.prefs}
                viewport={viewport}
                highlights={workspace.highlights}
                bookmarks={workspace.bookmarks}
                compactSplit={showSplitPane}
                onUpdateDocument={(fn) => handleUpdateDocument(activeDocument.id, fn)}
                onUpdatePrefs={updatePrefs}
                onAddHighlight={handleAddHighlight}
                onDeleteHighlight={handleDeleteHighlight}
                onToggleBookmark={handleToggleBookmark}
                onOpenAppearance={() => setAppearanceOpen(true)}
              />
            ) : activeTab.kind === 'studio' && activeNotebook ? (
              <NotebookStudio
                notebook={activeNotebook}
                viewport={viewport}
                onChangeNotebook={handleChangeNotebook}
              />
            ) : (
              <LibraryWorkspace
                mode={
                  activeTab.kind === 'library' ||
                  activeTab.kind === 'sagas' ||
                  activeTab.kind === 'notebooks'
                    ? activeTab.kind
                    : 'home'
                }
                selectedSagaId={selectedSagaId}
                viewport={viewport}
                documents={workspace.documents}
                playlists={workspace.playlists}
                notebooks={workspace.notebooks}
                onOpenDocument={openDocument}
                onOpenNotebook={openNotebook}
                onCreateNotebookFromTemplate={handleCreateNotebook}
                onImportFiles={handleImportFiles}
                onTogglePinDocument={(docId) =>
                  handleUpdateDocument(docId, (d) => ({ ...d, pinned: !d.pinned }))
                }
                onDeleteDocument={(docId) => {
                  setWorkspace((prev) => ({
                    ...prev,
                    documents: prev.documents.filter((d) => d.id !== docId),
                    playlists: prev.playlists.map((p) => ({
                      ...p,
                      documentIds: p.documentIds.filter((id) => id !== docId),
                    })),
                  }));
                  closeTab(`doc_${docId}`);
                }}
                onDeleteNotebook={(nbId) => {
                  setWorkspace((prev) => ({
                    ...prev,
                    notebooks: prev.notebooks.filter((n) => n.id !== nbId),
                  }));
                  closeTab(`nb_${nbId}`);
                }}
                onCreateSaga={(name, description, accentColor) => {
                  const id = `pl_${Date.now().toString(36)}`;
                  setWorkspace((prev) => ({
                    ...prev,
                    playlists: [
                      ...prev.playlists,
                      { id, name, description, accentColor, documentIds: [], createdAt: Date.now() },
                    ],
                  }));
                  setSelectedSagaId(id);
                }}
                onToggleDocInSaga={(sagaId, docId) => {
                  setWorkspace((prev) => ({
                    ...prev,
                    playlists: prev.playlists.map((p) => {
                      if (p.id !== sagaId) return p;
                      const exists = p.documentIds.includes(docId);
                      return {
                        ...p,
                        documentIds: exists
                          ? p.documentIds.filter((id) => id !== docId)
                          : [...p.documentIds, docId],
                      };
                    }),
                  }));
                }}
                onSelectSaga={setSelectedSagaId}
              />
            )}
          </div>

          {/* Side-by-Side Split View: Canva Study Notebook */}
          {showSplitPane && splitNotebook && (
            <div className="w-1/2 min-w-0 h-full flex flex-col">
              <NotebookStudio
                notebook={splitNotebook}
                viewport={viewport}
                compactSplit
                onChangeNotebook={handleChangeNotebook}
              />
            </div>
          )}
        </main>

        {/* Floating Exit Zen Mode Button */}
        {isZenMode && (
          <button
            type="button"
            onClick={() => setIsZenMode(false)}
            className="fixed top-3 right-3 z-40 h-8 px-3 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)]/90 text-xs font-medium shadow-md hover:bg-[var(--luma-surface)]"
          >
            Salir de Enfoque (ESC)
          </button>
        )}
      </div>

      {/* Drag & Drop File Overlay */}
      {isDraggingFile && (
        <div className="fixed inset-0 z-50 bg-[var(--luma-accent)]/15 backdrop-blur-[2px] border-2 border-dashed border-[var(--luma-accent)] flex items-center justify-center pointer-events-none">
          <div className="px-6 py-4 rounded-xl bg-[var(--luma-surface)] border border-[var(--luma-border)] shadow-xl text-sm font-semibold">
            Suelta tus archivos EPUB, PDF, DOCX, PPTX, TXT o FB2 para importarlos a Luma
          </div>
        </div>
      )}

      {/* Command Palette (Ctrl+K) */}
      <CommandPalette
        open={commandPaletteOpen}
        documents={workspace.documents}
        notebooks={workspace.notebooks}
        playlists={workspace.playlists}
        canSplitView={viewport.canSplitView}
        onClose={() => setCommandPaletteOpen(false)}
        onOpenDocument={openDocument}
        onOpenNotebook={openNotebook}
        onSelectTheme={(id) => updatePrefs({ theme: id })}
        onImportFiles={handleImportFiles}
        onNewNotebook={() => handleCreateNotebook('cornell')}
        onToggleSplit={handleToggleSplit}
        onOpenSagas={(sagaId) => navigatePrimary('sagas', sagaId)}
      />

      {/* Appearance & 17 Luma Themes Modal */}
      <AppearanceModal
        open={appearanceOpen}
        prefs={workspace.prefs}
        viewport={viewport}
        onClose={() => setAppearanceOpen(false)}
        onUpdatePrefs={updatePrefs}
      />

      {/* Optical Blue-Light & Night Dimming Overlay (pointer-events-none) */}
      {workspace.prefs.warmFilterStrength > 0.01 && (
        <div
          className="fixed inset-0 pointer-events-none z-[60]"
          style={{
            backgroundColor: '#FF9E3D',
            opacity: workspace.prefs.warmFilterStrength * 0.45,
          }}
        />
      )}
      {workspace.prefs.nightDimming > 0.01 && (
        <div
          className="fixed inset-0 pointer-events-none z-[60]"
          style={{
            backgroundColor: '#000000',
            opacity: workspace.prefs.nightDimming * 0.5,
          }}
        />
      )}
    </div>
  );
}
