import React, { useMemo, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  FolderPlus,
  LayoutGrid,
  List,
  PenTool,
  Pin,
  Plus,
  Search,
  Trash2,
  Upload,
} from 'lucide-react';
import {
  DocumentFormat,
  LumaDocument,
  LumaNotebook,
  SagaPlaylist,
} from '../types';
import { ViewportProfile } from '../hooks/useViewportProfile';

interface LibraryWorkspaceProps {
  mode: 'home' | 'library' | 'sagas' | 'notebooks';
  selectedSagaId?: string;
  viewport: ViewportProfile;
  documents: LumaDocument[];
  playlists: SagaPlaylist[];
  notebooks: LumaNotebook[];
  onOpenDocument: (docId: string) => void;
  onOpenNotebook: (nbId: string) => void;
  onCreateNotebookFromTemplate: (
    tpl: 'blank' | 'ruled' | 'grid' | 'cornell' | 'concept_map' | 'comparison'
  ) => void;
  onImportFiles: () => void;
  onTogglePinDocument: (docId: string) => void;
  onDeleteDocument: (docId: string) => void;
  onDeleteNotebook: (nbId: string) => void;
  onCreateSaga: (name: string, description: string, accentColor: string) => void;
  onToggleDocInSaga: (sagaId: string, docId: string) => void;
  onSelectSaga: (sagaId?: string) => void;
}

const FORMAT_FILTERS: Array<{ id: 'ALL' | DocumentFormat; label: string }> = [
  { id: 'ALL', label: 'Todos' },
  { id: 'EPUB', label: 'EPUB' },
  { id: 'PDF', label: 'PDF / Manhwa' },
  { id: 'DOCX', label: 'DOCX' },
  { id: 'PPTX', label: 'PPTX' },
  { id: 'FB2', label: 'FB2' },
  { id: 'TXT', label: 'TXT' },
];

const SAGA_COLORS = ['#2B64F5', '#C2642B', '#C44672', '#257A5E', '#5B4BDB', '#D97706'];

export const LibraryWorkspace: React.FC<LibraryWorkspaceProps> = ({
  mode,
  selectedSagaId,
  viewport,
  documents,
  playlists,
  notebooks,
  onOpenDocument,
  onOpenNotebook,
  onCreateNotebookFromTemplate,
  onImportFiles,
  onTogglePinDocument,
  onDeleteDocument,
  onDeleteNotebook,
  onCreateSaga,
  onToggleDocInSaga,
  onSelectSaga,
}) => {
  const [search, setSearch] = useState('');
  const [formatFilter, setFormatFilter] = useState<'ALL' | DocumentFormat>('ALL');
  const [viewMode, setViewMode] = useState<'gallery' | 'table'>('gallery');
  const [showNewSagaForm, setShowNewSagaForm] = useState(false);
  const [newSagaName, setNewSagaName] = useState('');
  const [newSagaDesc, setNewSagaDesc] = useState('');
  const [newSagaColor, setNewSagaColor] = useState(SAGA_COLORS[0]);
  const [assignSagaDocId, setAssignSagaDocId] = useState<string | null>(null);

  const sortedDocs = useMemo(
    () => [...documents].sort((a, b) => b.lastOpenedAt - a.lastOpenedAt),
    [documents]
  );

  const heroDoc = sortedDocs[0];

  const activeSaga = playlists.find((p) => p.id === selectedSagaId);

  const filteredDocs = useMemo(() => {
    const q = search.trim().toLowerCase();
    return sortedDocs.filter((doc) => {
      if (mode === 'sagas' && activeSaga && !activeSaga.documentIds.includes(doc.id)) {
        return false;
      }
      if (formatFilter !== 'ALL' && doc.format !== formatFilter) return false;
      if (!q) return true;
      return (
        doc.title.toLowerCase().includes(q) ||
        doc.author.toLowerCase().includes(q) ||
        doc.genre.toLowerCase().includes(q) ||
        doc.format.toLowerCase().includes(q)
      );
    });
  }, [sortedDocs, mode, activeSaga, formatFilter, search]);

  const filteredNotebooks = useMemo(() => {
    const q = search.trim().toLowerCase();
    const list = [...notebooks].sort((a, b) => b.updatedAt - a.updatedAt);
    if (!q) return list;
    return list.filter(
      (n) => n.title.toLowerCase().includes(q) || n.subtitle.toLowerCase().includes(q)
    );
  }, [notebooks, search]);

  const containerPadding =
    viewport.tier === 'compact'
      ? 'px-4 py-5'
      : viewport.tier === 'tablet'
        ? 'px-6 py-6'
        : viewport.tier === 'ultrawide'
          ? 'px-12 py-8'
          : 'px-8 py-7';

  const handleCreateSagaSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = newSagaName.trim();
    if (!clean) return;
    onCreateSaga(clean, newSagaDesc.trim() || 'Colección personalizada', newSagaColor);
    setNewSagaName('');
    setNewSagaDesc('');
    setShowNewSagaForm(false);
  };

  const renderBookGrid = (list: LumaDocument[]) => {
    if (list.length === 0) {
      return (
        <div className="py-14 px-6 text-center rounded-xl border border-dashed border-[var(--luma-border)] bg-[var(--luma-surface)]">
          <p className="text-sm font-medium text-[var(--luma-ink)]">
            No hay obras que coincidan con este filtro.
          </p>
          <p className="text-xs text-[var(--luma-ink-muted)] mt-1">
            Importa archivos EPUB, PDF, DOCX, PPTX, TXT o FB2 desde tu PC o arrástralos a esta ventana.
          </p>
          <button
            type="button"
            onClick={onImportFiles}
            className="mt-4 h-9 px-4 rounded-lg bg-[var(--luma-accent)] text-[var(--luma-on-accent)] text-xs font-semibold inline-flex items-center gap-2 hover:opacity-95"
          >
            <Upload className="w-3.5 h-3.5" />
            Importar archivos
          </button>
        </div>
      );
    }

    if (viewMode === 'table') {
      return (
        <div className="rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] overflow-x-auto">
          <table className="w-full text-left border-collapse min-w-[600px]">
            <thead>
              <tr className="border-b border-[var(--luma-border)] text-[11px] font-semibold text-[var(--luma-ink-muted)]">
                <th className="py-2.5 px-4">Título</th>
                <th className="py-2.5 px-3">Autor</th>
                <th className="py-2.5 px-3">Formato</th>
                <th className="py-2.5 px-3 text-right">Progreso</th>
                <th className="py-2.5 px-3 text-right">Palabras</th>
                <th className="py-2.5 px-4 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {list.map((doc) => (
                <tr
                  key={doc.id}
                  onClick={() => onOpenDocument(doc.id)}
                  className="border-b last:border-b-0 border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)] transition-colors cursor-pointer"
                >
                  <td className="py-2.5 px-4">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span
                        className="w-2.5 h-6 rounded-sm shrink-0"
                        style={{ backgroundColor: doc.coverColor }}
                      />
                      <span className="text-sm font-medium truncate text-[var(--luma-ink)]">
                        {doc.title}
                      </span>
                    </div>
                  </td>
                  <td className="py-2.5 px-3 text-xs text-[var(--luma-ink-muted)] whitespace-nowrap">
                    {doc.author}
                  </td>
                  <td className="py-2.5 px-3 text-xs font-mono text-[var(--luma-ink-muted)] whitespace-nowrap">
                    {doc.badgeTag}
                  </td>
                  <td className="py-2.5 px-3 text-xs text-right tabular-nums font-medium">
                    {Math.round(doc.progressPercent * 100)}%
                  </td>
                  <td className="py-2.5 px-3 text-xs text-right tabular-nums text-[var(--luma-ink-muted)]">
                    {doc.totalWords.toLocaleString('es-ES')}
                  </td>
                  <td
                    className="py-2.5 px-4 text-right whitespace-nowrap"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <button
                      type="button"
                      onClick={() => onTogglePinDocument(doc.id)}
                      title={doc.pinned ? 'Quitar de fijados' : 'Fijar en menú lateral'}
                      className={`p-1.5 rounded hover:bg-[var(--luma-bg)] ${
                        doc.pinned ? 'text-[var(--luma-accent)]' : 'text-[var(--luma-ink-muted)]'
                      }`}
                    >
                      <Pin className="w-3.5 h-3.5" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onDeleteDocument(doc.id)}
                      title="Eliminar de la biblioteca"
                      className="p-1.5 rounded hover:bg-red-500/10 text-[var(--luma-ink-muted)] hover:text-red-500 ml-1"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    }

    return (
      <div
        className="grid gap-5"
        style={{
          gridTemplateColumns: `repeat(auto-fill, minmax(${viewport.cardMinWidth}px, 1fr))`,
        }}
      >
        {list.map((doc) => (
          <article
            key={doc.id}
            onClick={() => onOpenDocument(doc.id)}
            className="group relative flex flex-col rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:border-[var(--luma-accent)]/60 transition-all cursor-pointer overflow-hidden"
          >
            {/* Editorial Book Cover */}
            <div
              className="relative aspect-[4/5] p-4 flex flex-col justify-between text-white overflow-hidden"
              style={{
                background: `linear-gradient(155deg, ${doc.coverColor} 0%, ${doc.coverAccent} 100%)`,
              }}
            >
              <div className="flex items-center justify-between text-[11px] font-medium opacity-90">
                <span className="font-mono tracking-wide">{doc.badgeTag}</span>
                <div
                  className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    type="button"
                    onClick={() => onTogglePinDocument(doc.id)}
                    title={doc.pinned ? 'Desfijar' : 'Fijar'}
                    className="w-6 h-6 rounded-md bg-black/25 hover:bg-black/45 flex items-center justify-center"
                  >
                    <Pin className="w-3 h-3" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setAssignSagaDocId(assignSagaDocId === doc.id ? null : doc.id)}
                    title="Asignar a Saga"
                    className="w-6 h-6 rounded-md bg-black/25 hover:bg-black/45 flex items-center justify-center"
                  >
                    <FolderPlus className="w-3 h-3" />
                  </button>
                  <button
                    type="button"
                    onClick={() => onDeleteDocument(doc.id)}
                    title="Eliminar"
                    className="w-6 h-6 rounded-md bg-black/25 hover:bg-red-600/80 flex items-center justify-center"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              </div>

              <div>
                <h3
                  className="text-base font-semibold leading-snug line-clamp-3"
                  style={{ fontFamily: 'var(--font-serif)' }}
                >
                  {doc.title}
                </h3>
                <p className="text-xs opacity-85 mt-1.5 truncate">{doc.author}</p>
              </div>

              {/* Subtle bottom progress track */}
              <div className="w-full h-1 bg-white/25 rounded-full overflow-hidden">
                <div
                  className="h-full bg-white rounded-full"
                  style={{ width: `${Math.max(4, Math.round(doc.progressPercent * 100))}%` }}
                />
              </div>
            </div>

            {/* Quiet unboxed metadata footer */}
            <div className="px-3.5 py-3 flex items-center justify-between gap-2 text-xs text-[var(--luma-ink-muted)]">
              <span className="truncate">{doc.genre}</span>
              <span className="tabular-nums font-medium text-[var(--luma-ink)] shrink-0">
                {Math.round(doc.progressPercent * 100)}%
              </span>
            </div>

            {/* Inline Saga Assignment Popover */}
            {assignSagaDocId === doc.id && (
              <div
                onClick={(e) => e.stopPropagation()}
                className="px-3.5 py-2.5 border-t border-[var(--luma-border)] bg-[var(--luma-surface-alt)] space-y-1.5"
              >
                <div className="text-[11px] font-semibold text-[var(--luma-ink-muted)]">
                  Añadir a saga:
                </div>
                {playlists.map((pl) => {
                  const inside = pl.documentIds.includes(doc.id);
                  return (
                    <button
                      key={pl.id}
                      type="button"
                      onClick={() => onToggleDocInSaga(pl.id, doc.id)}
                      className="w-full flex items-center justify-between text-xs py-1 px-2 rounded hover:bg-[var(--luma-surface)]"
                    >
                      <span className="truncate">{pl.name}</span>
                      <span className="font-semibold text-[var(--luma-accent)]">
                        {inside ? 'En saga' : '+ Añadir'}
                      </span>
                    </button>
                  );
                })}
              </div>
            )}
          </article>
        ))}
      </div>
    );
  };

  return (
    <div className={`h-full overflow-y-auto bg-[var(--luma-bg)] text-[var(--luma-ink)] ${containerPadding}`}>
      <div className="max-w-[1240px] mx-auto space-y-8">
        {/* Top Workspace Header + Primary Action */}
        <div className="flex flex-wrap items-end justify-between gap-4 pb-5 border-b border-[var(--luma-border)]">
          <div>
            <h1 className="text-2xl md:text-3xl font-bold tracking-tight text-balance">
              {mode === 'home' && 'Buenos días en Luma'}
              {mode === 'library' && 'Biblioteca de Lectura'}
              {mode === 'notebooks' && 'Cuadernos Visuales'}
              {mode === 'sagas' && (activeSaga ? activeSaga.name : 'Sagas y Listas de Lectura')}
            </h1>
            <p className="text-sm text-[var(--luma-ink-muted)] mt-1">
              {mode === 'home' &&
                `${documents.length} obras · ${notebooks.length} cuadernos · ${playlists.length} sagas sincronizadas localmente`}
              {mode === 'library' &&
                'EPUB, PDF, Manhwa, DOCX, PPTX, TXT y FB2 listos para leer, anotar y estudiar.'}
              {mode === 'notebooks' &&
                'Lienzos estilo Canva con plantillas Cornell, mapas conceptuales, capas y exportación HD.'}
              {mode === 'sagas' &&
                (activeSaga
                  ? activeSaga.description
                  : 'Organiza series, temarios universitarios y listas de documentos.')}
            </p>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            <button
              type="button"
              onClick={onImportFiles}
              className="h-9 px-3.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:bg-[var(--luma-surface-alt)] text-xs font-semibold inline-flex items-center gap-2 transition-colors whitespace-nowrap"
            >
              <Upload className="w-3.5 h-3.5 text-[var(--luma-accent)]" />
              <span>Importar archivo</span>
            </button>
            <button
              type="button"
              onClick={() => onCreateNotebookFromTemplate('cornell')}
              className="h-9 px-3.5 rounded-lg bg-[var(--luma-accent)] text-[var(--luma-on-accent)] hover:opacity-95 text-xs font-semibold inline-flex items-center gap-2 transition-opacity whitespace-nowrap"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Nuevo cuaderno</span>
            </button>
          </div>
        </div>

        {/* HOME: Single Focal Anchor — Continue Reading Hero */}
        {mode === 'home' && heroDoc && (
          <section className="rounded-2xl border border-[var(--luma-border)] bg-[var(--luma-surface)] p-5 md:p-6 flex flex-col md:flex-row items-stretch gap-6">
            <div
              onClick={() => onOpenDocument(heroDoc.id)}
              className="w-full md:w-[190px] aspect-[4/3] md:aspect-[3/4] rounded-xl p-4 flex flex-col justify-between text-white shrink-0 cursor-pointer"
              style={{
                background: `linear-gradient(155deg, ${heroDoc.coverColor}, ${heroDoc.coverAccent})`,
              }}
            >
              <span className="text-[11px] font-mono opacity-90">{heroDoc.badgeTag}</span>
              <div>
                <div
                  className="text-base font-semibold leading-snug"
                  style={{ fontFamily: 'var(--font-serif)' }}
                >
                  {heroDoc.title}
                </div>
                <div className="text-xs opacity-80 mt-1">{heroDoc.author}</div>
              </div>
            </div>

            <div className="flex-1 flex flex-col justify-between gap-4 min-w-0">
              <div>
                <div className="text-xs text-[var(--luma-ink-muted)] flex items-center gap-2">
                  <span>Continuar leyendo</span>
                  <span aria-hidden="true">·</span>
                  <span>{heroDoc.genre}</span>
                  <span aria-hidden="true">·</span>
                  <span className="tabular-nums">
                    {Math.round(heroDoc.progressPercent * 100)}% completado
                  </span>
                </div>
                <h2
                  className="text-xl md:text-2xl font-bold mt-2 tracking-tight"
                  style={{ fontFamily: 'var(--font-serif)' }}
                >
                  {heroDoc.title}
                </h2>
                <p className="text-sm text-[var(--luma-ink-muted)] mt-2 max-w-2xl leading-relaxed">
                  {heroDoc.synopsis}
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <button
                  type="button"
                  onClick={() => onOpenDocument(heroDoc.id)}
                  className="h-9 px-4 rounded-lg bg-[var(--luma-accent)] text-[var(--luma-on-accent)] text-xs font-semibold inline-flex items-center gap-2 hover:opacity-95"
                >
                  <span>Abrir lectura</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
                <span className="text-xs text-[var(--luma-ink-muted)] tabular-nums">
                  {heroDoc.chapters.length}{' '}
                  {heroDoc.chapters.length === 1 ? 'sección' : 'secciones'} ·{' '}
                  {heroDoc.totalWords.toLocaleString('es-ES')} palabras
                </span>
              </div>
            </div>
          </section>
        )}

        {/* NOTEBOOKS MODE OR HOME QUICK TEMPLATES */}
        {(mode === 'notebooks' || mode === 'home') && (
          <section className="space-y-4">
            <div className="flex items-center justify-between gap-4">
              <h2 className="text-base font-bold tracking-tight">
                {mode === 'home' ? 'Cuadernos de Estudio (Estudio Canva)' : 'Plantillas rápidas'}
              </h2>
              {mode === 'notebooks' && (
                <div className="relative w-56">
                  <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--luma-ink-muted)]" />
                  <input
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Filtrar cuadernos..."
                    className="w-full h-8 pl-8 pr-3 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)] text-xs outline-none"
                  />
                </div>
              )}
            </div>

            {/* Canva-style Starter Templates Strip */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
              {(
                [
                  { id: 'blank', name: 'Lienzo blanco', desc: 'Papel limpio A4' },
                  { id: 'ruled', name: 'Papel rayado', desc: 'Apuntes clásicos' },
                  { id: 'grid', name: 'Cuadrícula', desc: 'Esquemas técnicos' },
                  { id: 'cornell', name: 'Método Cornell', desc: 'Preguntas y resumen' },
                  { id: 'concept_map', name: 'Mapa conceptual', desc: 'Nodos y flechas' },
                  { id: 'comparison', name: 'Tabla comparativa', desc: 'Matriz 4×3 editable' },
                ] as const
              ).map((tpl) => (
                <button
                  key={tpl.id}
                  type="button"
                  onClick={() => onCreateNotebookFromTemplate(tpl.id)}
                  className="p-3 rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:border-[var(--luma-accent)] text-left transition-colors group"
                >
                  <div className="flex items-center justify-between mb-1.5">
                    <PenTool className="w-4 h-4 text-[var(--luma-accent)]" />
                    <Plus className="w-3.5 h-3.5 text-[var(--luma-ink-muted)] group-hover:text-[var(--luma-accent)]" />
                  </div>
                  <div className="text-xs font-semibold truncate">{tpl.name}</div>
                  <div className="text-[11px] text-[var(--luma-ink-muted)] truncate mt-0.5">
                    {tpl.desc}
                  </div>
                </button>
              ))}
            </div>

            {/* Saved Notebooks List */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 pt-1">
              {filteredNotebooks.map((nb) => (
                <div
                  key={nb.id}
                  onClick={() => onOpenNotebook(nb.id)}
                  className="group p-4 rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:border-[var(--luma-accent)]/70 transition-colors cursor-pointer flex items-center justify-between gap-3"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className="w-11 h-14 rounded-lg flex items-center justify-center text-white shrink-0"
                      style={{ backgroundColor: nb.coverColor }}
                    >
                      <PenTool className="w-5 h-5" />
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-semibold truncate">{nb.title}</div>
                      <div className="text-xs text-[var(--luma-ink-muted)] truncate mt-0.5">
                        {nb.subtitle}
                      </div>
                      <div className="text-[11px] text-[var(--luma-ink-muted)] mt-1 tabular-nums">
                        {nb.pageSize} · {nb.pages.length}{' '}
                        {nb.pages.length === 1 ? 'página' : 'páginas'}
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onDeleteNotebook(nb.id);
                    }}
                    title="Eliminar cuaderno"
                    className="p-1.5 rounded-md opacity-0 group-hover:opacity-100 hover:bg-red-500/10 text-[var(--luma-ink-muted)] hover:text-red-500 transition-opacity shrink-0"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* SAGAS MODE */}
        {mode === 'sagas' && (
          <section className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-1.5">
                <button
                  type="button"
                  onClick={() => onSelectSaga(undefined)}
                  className={`h-8 px-3 rounded-lg text-xs font-medium transition-colors ${
                    !selectedSagaId
                      ? 'bg-[var(--luma-accent)] text-[var(--luma-on-accent)]'
                      : 'bg-[var(--luma-surface)] border border-[var(--luma-border)] text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)]'
                  }`}
                >
                  Todas las sagas
                </button>
                {playlists.map((pl) => (
                  <button
                    key={pl.id}
                    type="button"
                    onClick={() => onSelectSaga(pl.id)}
                    className={`h-8 px-3 rounded-lg text-xs font-medium transition-colors inline-flex items-center gap-2 ${
                      selectedSagaId === pl.id
                        ? 'bg-[var(--luma-accent)] text-[var(--luma-on-accent)]'
                        : 'bg-[var(--luma-surface)] border border-[var(--luma-border)] text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]'
                    }`}
                  >
                    <span
                      className="w-2 h-2 rounded-full"
                      style={{
                        backgroundColor: selectedSagaId === pl.id ? '#FFFFFF' : pl.accentColor,
                      }}
                    />
                    <span>{pl.name}</span>
                    <span className="opacity-70 tabular-nums">{pl.documentIds.length}</span>
                  </button>
                ))}
              </div>

              <button
                type="button"
                onClick={() => setShowNewSagaForm((v) => !v)}
                className="h-8 px-3 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)] hover:bg-[var(--luma-surface-alt)] text-xs font-semibold inline-flex items-center gap-1.5"
              >
                <FolderPlus className="w-3.5 h-3.5 text-[var(--luma-accent)]" />
                <span>Nueva saga</span>
              </button>
            </div>

            {showNewSagaForm && (
              <form
                onSubmit={handleCreateSagaSubmit}
                className="p-4 rounded-xl border border-[var(--luma-border)] bg-[var(--luma-surface)] grid grid-cols-1 md:grid-cols-3 gap-3 items-end"
              >
                <div>
                  <label className="block text-[11px] font-semibold text-[var(--luma-ink-muted)] mb-1">
                    Nombre de la saga
                  </label>
                  <input
                    value={newSagaName}
                    onChange={(e) => setNewSagaName(e.target.value)}
                    placeholder="Ej. Arquitectura de Software"
                    className="w-full h-9 px-3 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] text-xs outline-none"
                    required
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-[var(--luma-ink-muted)] mb-1">
                    Descripción breve
                  </label>
                  <input
                    value={newSagaDesc}
                    onChange={(e) => setNewSagaDesc(e.target.value)}
                    placeholder="Libros técnicos y apuntes"
                    className="w-full h-9 px-3 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-bg)] text-xs outline-none"
                  />
                </div>
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5">
                    {SAGA_COLORS.map((c) => (
                      <button
                        key={c}
                        type="button"
                        onClick={() => setNewSagaColor(c)}
                        className={`w-6 h-6 rounded-full ${
                          newSagaColor === c ? 'ring-2 ring-offset-2 ring-[var(--luma-ink)]' : ''
                        }`}
                        style={{ backgroundColor: c }}
                      />
                    ))}
                  </div>
                  <button
                    type="submit"
                    className="h-9 px-4 rounded-lg bg-[var(--luma-accent)] text-[var(--luma-on-accent)] text-xs font-semibold"
                  >
                    Crear
                  </button>
                </div>
              </form>
            )}
          </section>
        )}

        {/* LIBRARY GRID / TABLE (Home, Library, Sagas) */}
        {mode !== 'notebooks' && (
          <section className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              {/* Interactive format filter segmented tabs */}
              <div className="flex items-center gap-1 p-1 rounded-lg bg-[var(--luma-surface-alt)] overflow-x-auto max-w-full">
                {FORMAT_FILTERS.map((f) => {
                  const active = formatFilter === f.id;
                  return (
                    <button
                      key={f.id}
                      type="button"
                      onClick={() => setFormatFilter(f.id)}
                      className={`px-2.5 py-1 rounded-md text-xs font-medium whitespace-nowrap transition-colors ${
                        active
                          ? 'bg-[var(--luma-surface)] text-[var(--luma-ink)] shadow-xs'
                          : 'text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)]'
                      }`}
                    >
                      {f.label}
                    </button>
                  );
                })}
              </div>

              <div className="flex items-center gap-2">
                <div className="relative w-48 sm:w-56">
                  <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--luma-ink-muted)]" />
                  <input
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    placeholder="Buscar título o autor..."
                    className="w-full h-8 pl-8 pr-3 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)] text-xs outline-none"
                  />
                </div>

                <div className="flex items-center p-0.5 rounded-lg border border-[var(--luma-border)] bg-[var(--luma-surface)]">
                  <button
                    type="button"
                    onClick={() => setViewMode('gallery')}
                    title="Vista Galería"
                    className={`h-7 w-7 rounded-md flex items-center justify-center ${
                      viewMode === 'gallery'
                        ? 'bg-[var(--luma-surface-alt)] text-[var(--luma-accent)]'
                        : 'text-[var(--luma-ink-muted)]'
                    }`}
                  >
                    <LayoutGrid className="w-3.5 h-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setViewMode('table')}
                    title="Vista Tabla Notion"
                    className={`h-7 w-7 rounded-md flex items-center justify-center ${
                      viewMode === 'table'
                        ? 'bg-[var(--luma-surface-alt)] text-[var(--luma-accent)]'
                        : 'text-[var(--luma-ink-muted)]'
                    }`}
                  >
                    <List className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>

            {renderBookGrid(filteredDocs)}
          </section>
        )}

        {/* Quiet Drag & Drop Tip */}
        <footer className="pt-4 border-t border-[var(--luma-border)] flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--luma-ink-muted)]">
          <span className="flex items-center gap-1.5">
            <BookOpen className="w-3.5 h-3.5" />
            Arrastra archivos EPUB, PDF, DOCX, PPTX, FB2 o TXT en cualquier parte de la ventana para añadirlos.
          </span>
          <span className="tabular-nums">Luma Desktop · Modo PC</span>
        </footer>
      </div>
    </div>
  );
};
