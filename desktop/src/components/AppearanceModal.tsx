import React from 'react';
import { Check, Monitor, Moon, Sun, X } from 'lucide-react';
import { InterfaceDensity, ReadingThemeId, WorkspacePreferences } from '../types';
import { LUMA_THEMES } from '../themes';
import { ViewportProfile } from '../hooks/useViewportProfile';

interface AppearanceModalProps {
  open: boolean;
  prefs: WorkspacePreferences;
  viewport: ViewportProfile;
  onClose: () => void;
  onUpdatePrefs: (patch: Partial<WorkspacePreferences>) => void;
}

export const AppearanceModal: React.FC<AppearanceModalProps> = ({
  open,
  prefs,
  viewport,
  onClose,
  onUpdatePrefs,
}) => {
  if (!open) return null;

  const lightThemes = LUMA_THEMES.filter((t) => !t.isDark);
  const darkThemes = LUMA_THEMES.filter((t) => t.isDark);

  const densityOptions: Array<{ id: InterfaceDensity; label: string; desc: string }> = [
    { id: 'auto', label: 'Automática', desc: 'Se adapta a la resolución' },
    { id: 'compact', label: 'Compacta', desc: 'Más contenido visible' },
    { id: 'comfortable', label: 'Cómoda', desc: 'Equilibrio estándar' },
    { id: 'spacious', label: 'Amplia', desc: 'Respiración editorial' },
  ];

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 bg-black/45 backdrop-blur-[2px] flex items-center justify-center p-4"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[740px] max-h-[86vh] flex flex-col rounded-2xl border border-[var(--luma-border)] bg-[var(--luma-surface)] text-[var(--luma-ink)] shadow-2xl overflow-hidden"
      >
        <div className="px-6 py-4 border-b border-[var(--luma-border)] flex items-center justify-between gap-4 shrink-0">
          <div>
            <h2 className="text-base font-bold tracking-tight">Apariencia y Comodidad Visual</h2>
            <p className="text-xs text-[var(--luma-ink-muted)] mt-0.5 tabular-nums">
              17 paletas suaves de Luma · Pantalla actual: {viewport.width}×{viewport.height}px ·{' '}
              {viewport.tierLabel}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="h-8 w-8 rounded-lg flex items-center justify-center text-[var(--luma-ink-muted)] hover:text-[var(--luma-ink)] hover:bg-[var(--luma-surface-alt)]"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">
          {/* Daytime soft paper themes */}
          <section>
            <div className="flex items-center gap-2 text-xs font-semibold text-[var(--luma-ink-muted)] mb-3">
              <Sun className="w-3.5 h-3.5" />
              <span>Claros y papel cálido ({lightThemes.length})</span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
              {lightThemes.map((t) => {
                const selected = prefs.theme === t.id;
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => onUpdatePrefs({ theme: t.id })}
                    className={`p-3 rounded-xl border text-left transition-all ${
                      selected
                        ? 'ring-2 ring-[var(--luma-accent)] border-[var(--luma-accent)]'
                        : 'border-[var(--luma-border)] hover:border-[var(--luma-ink-muted)]/50'
                    }`}
                    style={{ backgroundColor: t.bg, color: t.ink }}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span
                        className="w-5 h-5 rounded-full border border-black/10 flex items-center justify-center"
                        style={{ backgroundColor: t.accent, color: t.onAccent }}
                      >
                        {selected && <Check className="w-3 h-3" />}
                      </span>
                      <span
                        className="w-6 h-2.5 rounded-sm border border-black/10"
                        style={{ backgroundColor: t.surface }}
                      />
                    </div>
                    <div className="text-xs font-semibold truncate">{t.name}</div>
                    <div className="text-[11px] opacity-75 truncate">{t.comfortTag}</div>
                  </button>
                );
              })}
            </div>
          </section>

          {/* Nocturnal themes */}
          <section>
            <div className="flex items-center gap-2 text-xs font-semibold text-[var(--luma-ink-muted)] mb-3">
              <Moon className="w-3.5 h-3.5" />
              <span>Nocturnos sin fatiga ({darkThemes.length})</span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
              {darkThemes.map((t) => {
                const selected = prefs.theme === t.id;
                return (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => onUpdatePrefs({ theme: t.id })}
                    className={`p-3 rounded-xl border text-left transition-all ${
                      selected
                        ? 'ring-2 ring-[var(--luma-accent)] border-[var(--luma-accent)]'
                        : 'border-[var(--luma-border)] hover:border-[var(--luma-ink-muted)]/50'
                    }`}
                    style={{ backgroundColor: t.bg, color: t.ink }}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span
                        className="w-5 h-5 rounded-full flex items-center justify-center"
                        style={{ backgroundColor: t.accent, color: t.onAccent }}
                      >
                        {selected && <Check className="w-3 h-3" />}
                      </span>
                      <span
                        className="w-6 h-2.5 rounded-sm border border-white/10"
                        style={{ backgroundColor: t.surface }}
                      />
                    </div>
                    <div className="text-xs font-semibold truncate">{t.name}</div>
                    <div className="text-[11px] opacity-75 truncate">{t.comfortTag}</div>
                  </button>
                );
              })}
            </div>
          </section>

          {/* Optical Eye Comfort */}
          <section className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 border-t border-[var(--luma-border)]">
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold">Filtro cálido anti-luz azul</span>
                <span className="font-mono tabular-nums text-[var(--luma-ink-muted)]">
                  {Math.round(prefs.warmFilterStrength * 100)}%
                </span>
              </div>
              <input
                type="range"
                min={0}
                max={0.45}
                step={0.05}
                value={prefs.warmFilterStrength}
                onChange={(e) => onUpdatePrefs({ warmFilterStrength: parseFloat(e.target.value) })}
                className="w-full accent-[var(--luma-accent)]"
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold">Atenuación nocturna suave</span>
                <span className="font-mono tabular-nums text-[var(--luma-ink-muted)]">
                  {Math.round(prefs.nightDimming * 100)}%
                </span>
              </div>
              <input
                type="range"
                min={0}
                max={0.35}
                step={0.05}
                value={prefs.nightDimming}
                onChange={(e) => onUpdatePrefs({ nightDimming: parseFloat(e.target.value) })}
                className="w-full accent-[var(--luma-accent)]"
              />
            </div>
          </section>

          {/* Adaptive Menu Density */}
          <section className="pt-2 border-t border-[var(--luma-border)]">
            <div className="flex items-center gap-2 text-xs font-semibold text-[var(--luma-ink-muted)] mb-3">
              <Monitor className="w-3.5 h-3.5" />
              <span>Proporción del menú y densidad de interfaz</span>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {densityOptions.map((opt) => {
                const active = prefs.density === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => onUpdatePrefs({ density: opt.id })}
                    className={`p-2.5 rounded-xl border text-left transition-colors ${
                      active
                        ? 'bg-[var(--luma-accent-soft)] border-[var(--luma-accent)] text-[var(--luma-ink)]'
                        : 'bg-[var(--luma-bg)] border-[var(--luma-border)] hover:bg-[var(--luma-surface-alt)]'
                    }`}
                  >
                    <div className="text-xs font-semibold">{opt.label}</div>
                    <div className="text-[11px] text-[var(--luma-ink-muted)] mt-0.5">{opt.desc}</div>
                  </button>
                );
              })}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
};
