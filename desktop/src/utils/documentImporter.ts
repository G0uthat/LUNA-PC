import JSZip from 'jszip';
import { DocumentFormat, LumaDocument, ParsedBlock, ParsedChapter, SlidePage } from '../types';

const COVER_PALETTES: Array<[string, string]> = [
  ['#2B64F5', '#1D4AC0'],
  ['#0D9488', '#115E59'],
  ['#C2642B', '#93481B'],
  ['#5B4BDB', '#4234AD'],
  ['#C44672', '#962E53'],
  ['#D97706', '#92400E'],
  ['#257A5E', '#195944'],
];

function pickPalette(seed: string): [string, string] {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) {
    hash = (hash * 31 + seed.charCodeAt(i)) | 0;
  }
  return COVER_PALETTES[Math.abs(hash) % COVER_PALETTES.length];
}

function uid(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`;
}

function countWords(chapters: ParsedChapter[], slides?: SlidePage[]): number {
  let total = 0;
  for (const ch of chapters) {
    for (const b of ch.blocks) {
      total += b.text.trim().split(/\s+/).filter(Boolean).length;
    }
  }
  if (slides) {
    for (const s of slides) {
      total += (s.title + ' ' + (s.subtitle ?? '') + ' ' + s.bullets.join(' ')).trim().split(/\s+/).filter(Boolean).length;
    }
  }
  return Math.max(1, total);
}

function detectFormat(fileName: string): DocumentFormat {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
  switch (ext) {
    case 'epub':
      return 'EPUB';
    case 'pdf':
      return 'PDF';
    case 'docx':
      return 'DOCX';
    case 'pptx':
      return 'PPTX';
    case 'fb2':
      return 'FB2';
    default:
      return 'TXT';
  }
}

function parsePlainTextOrMarkdown(raw: string, fallbackTitle: string): ParsedChapter[] {
  const lines = raw.replace(/\r\n/g, '\n').split('\n');
  const chapters: ParsedChapter[] = [];
  let currentTitle = fallbackTitle;
  let currentBlocks: ParsedBlock[] = [];

  const flush = () => {
    if (currentBlocks.length > 0) {
      chapters.push({
        id: uid('ch'),
        title: currentTitle,
        blocks: currentBlocks,
      });
      currentBlocks = [];
    }
  };

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    if (trimmed.startsWith('# ')) {
      flush();
      currentTitle = trimmed.slice(2).trim();
      currentBlocks.push({ id: uid('b'), type: 'h1', text: currentTitle });
    } else if (trimmed.startsWith('## ')) {
      currentBlocks.push({ id: uid('b'), type: 'h2', text: trimmed.slice(3).trim() });
    } else if (trimmed.startsWith('> ')) {
      currentBlocks.push({ id: uid('b'), type: 'quote', text: trimmed.slice(2).trim() });
    } else if (/^[-*•]\s+/.test(trimmed)) {
      currentBlocks.push({ id: uid('b'), type: 'list', text: trimmed.replace(/^[-*•]\s+/, '') });
    } else {
      currentBlocks.push({ id: uid('b'), type: 'p', text: trimmed });
    }
  }
  flush();

  if (chapters.length === 0) {
    return [
      {
        id: uid('ch'),
        title: fallbackTitle,
        blocks: [{ id: uid('b'), type: 'p', text: raw.trim() || 'Documento vacío.' }],
      },
    ];
  }
  return chapters;
}

function htmlToBlocks(htmlString: string): { title: string; blocks: ParsedBlock[] } {
  const parser = new DOMParser();
  const doc = parser.parseFromString(htmlString, 'text/html');
  const title = doc.querySelector('h1, h2, title')?.textContent?.trim() || 'Capítulo';
  const blocks: ParsedBlock[] = [];
  const elements = doc.body?.querySelectorAll('h1, h2, h3, p, blockquote, li') ?? [];
  elements.forEach((el) => {
    const text = el.textContent?.replace(/\s+/g, ' ').trim() ?? '';
    if (!text) return;
    const tag = el.tagName.toLowerCase();
    const type: ParsedBlock['type'] =
      tag === 'h1'
        ? 'h1'
        : tag === 'h2' || tag === 'h3'
          ? 'h2'
          : tag === 'blockquote'
            ? 'quote'
            : tag === 'li'
              ? 'list'
              : 'p';
    blocks.push({ id: uid('b'), type, text });
  });
  return { title, blocks };
}

async function parseEpub(bytes: Uint8Array, fallbackTitle: string): Promise<{
  title: string;
  author: string;
  chapters: ParsedChapter[];
}> {
  const zip = await JSZip.loadAsync(bytes);
  let opfPath = '';
  const containerFile = zip.file('META-INF/container.xml');
  if (containerFile) {
    const containerXml = await containerFile.async('text');
    const match = containerXml.match(/full-path="([^"]+)"/i);
    if (match) opfPath = match[1];
  }

  let title = fallbackTitle;
  let author = 'Autor importado';
  const chapters: ParsedChapter[] = [];

  if (opfPath && zip.file(opfPath)) {
    const opfXml = await zip.file(opfPath)!.async('text');
    const parser = new DOMParser();
    const opfDoc = parser.parseFromString(opfXml, 'application/xml');
    const dcTitle = opfDoc.getElementsByTagNameNS('*', 'title')[0]?.textContent?.trim();
    const dcCreator = opfDoc.getElementsByTagNameNS('*', 'creator')[0]?.textContent?.trim();
    if (dcTitle) title = dcTitle;
    if (dcCreator) author = dcCreator;

    const baseDir = opfPath.includes('/') ? opfPath.slice(0, opfPath.lastIndexOf('/') + 1) : '';
    const manifestMap = new Map<string, string>();
    const items = Array.from(opfDoc.getElementsByTagName('item'));
    for (const item of items) {
      const id = item.getAttribute('id');
      const href = item.getAttribute('href');
      if (id && href) manifestMap.set(id, href);
    }

    const spineRefs = Array.from(opfDoc.getElementsByTagName('itemref'));
    for (const ref of spineRefs) {
      const idref = ref.getAttribute('idref');
      if (!idref) continue;
      const href = manifestMap.get(idref);
      if (!href) continue;
      const fullPath = decodeURIComponent(baseDir + href);
      const entry = zip.file(fullPath) || zip.file(href);
      if (!entry) continue;
      const xhtml = await entry.async('text');
      const parsed = htmlToBlocks(xhtml);
      if (parsed.blocks.length > 0) {
        chapters.push({
          id: uid('ch'),
          title: parsed.title,
          blocks: parsed.blocks,
        });
      }
    }
  }

  if (chapters.length === 0) {
    const htmlEntries = Object.keys(zip.files).filter((k) => /\.(x?html?|htm)$/i.test(k)).sort();
    for (const key of htmlEntries.slice(0, 40)) {
      const content = await zip.files[key].async('text');
      const parsed = htmlToBlocks(content);
      if (parsed.blocks.length > 0) {
        chapters.push({ id: uid('ch'), title: parsed.title, blocks: parsed.blocks });
      }
    }
  }

  return {
    title,
    author,
    chapters:
      chapters.length > 0
        ? chapters
        : [{ id: uid('ch'), title, blocks: [{ id: uid('b'), type: 'p', text: 'EPUB importado correctamente.' }] }],
  };
}

async function parseDocx(bytes: Uint8Array, fallbackTitle: string): Promise<{
  title: string;
  author: string;
  chapters: ParsedChapter[];
}> {
  const zip = await JSZip.loadAsync(bytes);
  let title = fallbackTitle;
  let author = 'Documento Word';

  const core = zip.file('docProps/core.xml');
  if (core) {
    const xml = await core.async('text');
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    const t = doc.getElementsByTagNameNS('*', 'title')[0]?.textContent?.trim();
    const a = doc.getElementsByTagNameNS('*', 'creator')[0]?.textContent?.trim();
    if (t) title = t;
    if (a) author = a;
  }

  const bodyFile = zip.file('word/document.xml');
  const blocks: ParsedBlock[] = [];
  if (bodyFile) {
    const xml = await bodyFile.async('text');
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    const paragraphs = Array.from(doc.getElementsByTagNameNS('*', 'p'));
    for (const p of paragraphs) {
      const texts = Array.from(p.getElementsByTagNameNS('*', 't'))
        .map((n) => n.textContent ?? '')
        .join('')
        .trim();
      if (!texts) continue;
      const pStyle = p.getElementsByTagNameNS('*', 'pStyle')[0]?.getAttribute('w:val') ?? '';
      const type: ParsedBlock['type'] = /heading1|title/i.test(pStyle)
        ? 'h1'
        : /heading2|heading3|subtitle/i.test(pStyle)
          ? 'h2'
          : 'p';
      blocks.push({ id: uid('b'), type, text: texts });
    }
  }

  return {
    title,
    author,
    chapters: [
      {
        id: uid('ch'),
        title,
        blocks:
          blocks.length > 0
            ? blocks
            : [{ id: uid('b'), type: 'p', text: 'Documento DOCX importado.' }],
      },
    ],
  };
}

async function parsePptx(bytes: Uint8Array, fallbackTitle: string): Promise<{
  title: string;
  author: string;
  slides: SlidePage[];
  chapters: ParsedChapter[];
}> {
  const zip = await JSZip.loadAsync(bytes);
  const slideKeys = Object.keys(zip.files)
    .filter((k) => /^ppt\/slides\/slide\d+\.xml$/i.test(k))
    .sort((a, b) => {
      const na = parseInt(a.match(/\d+/)?.[0] ?? '0', 10);
      const nb = parseInt(b.match(/\d+/)?.[0] ?? '0', 10);
      return na - nb;
    });

  const slides: SlidePage[] = [];
  const chapters: ParsedChapter[] = [];

  for (let i = 0; i < slideKeys.length; i++) {
    const xml = await zip.files[slideKeys[i]].async('text');
    const doc = new DOMParser().parseFromString(xml, 'application/xml');
    const paragraphs = Array.from(doc.getElementsByTagNameNS('*', 'p'))
      .map((p) =>
        Array.from(p.getElementsByTagNameNS('*', 't'))
          .map((t) => t.textContent ?? '')
          .join('')
          .trim()
      )
      .filter(Boolean);

    const slideTitle = paragraphs[0] || `Diapositiva ${i + 1}`;
    const bullets = paragraphs.slice(1);
    slides.push({
      id: uid('slide'),
      title: slideTitle,
      bgColor: i === 0 ? '#16243D' : '#FFFCF6',
      textColor: i === 0 ? '#FFFFFF' : '#1A2233',
      accentColor: i === 0 ? '#D97706' : '#2B64F5',
      bullets,
      notes: '',
    });

    chapters.push({
      id: uid('ch'),
      title: slideTitle,
      blocks: [
        { id: uid('b'), type: 'h1', text: slideTitle },
        ...bullets.map((line) => ({ id: uid('b'), type: 'list' as const, text: line })),
      ],
    });
  }

  return {
    title: slides[0]?.title || fallbackTitle,
    author: 'Presentación PPTX',
    slides,
    chapters:
      chapters.length > 0
        ? chapters
        : [{ id: uid('ch'), title: fallbackTitle, blocks: [{ id: uid('b'), type: 'p', text: 'Presentación importada.' }] }],
  };
}

function parseFb2(raw: string, fallbackTitle: string): {
  title: string;
  author: string;
  chapters: ParsedChapter[];
} {
  const doc = new DOMParser().parseFromString(raw, 'application/xml');
  const title = doc.querySelector('book-title')?.textContent?.trim() || fallbackTitle;
  const first = doc.querySelector('author first-name')?.textContent?.trim() ?? '';
  const last = doc.querySelector('author last-name')?.textContent?.trim() ?? '';
  const author = [first, last].filter(Boolean).join(' ') || 'Autor FB2';

  const sections = Array.from(doc.querySelectorAll('body > section'));
  const chapters: ParsedChapter[] = sections.map((sec, idx) => {
    const chTitle = sec.querySelector('title')?.textContent?.replace(/\s+/g, ' ').trim() || `Capítulo ${idx + 1}`;
    const blocks: ParsedBlock[] = [];
    blocks.push({ id: uid('b'), type: 'h1', text: chTitle });
    sec.querySelectorAll('p').forEach((p) => {
      const txt = p.textContent?.trim();
      if (txt && txt !== chTitle) {
        blocks.push({ id: uid('b'), type: 'p', text: txt });
      }
    });
    return { id: uid('ch'), title: chTitle, blocks };
  });

  return {
    title,
    author,
    chapters:
      chapters.length > 0
        ? chapters
        : parsePlainTextOrMarkdown(doc.body?.textContent ?? raw, title),
  };
}

function base64ToUint8Array(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export async function importDocumentFromBytes(
  fileName: string,
  bytes: Uint8Array,
  base64Override?: string
): Promise<LumaDocument> {
  const format = detectFormat(fileName);
  const cleanTitle = fileName.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim() || 'Documento importado';
  const [coverColor, coverAccent] = pickPalette(fileName);
  const now = Date.now();

  if (format === 'PDF') {
    let b64 = base64Override;
    if (!b64) {
      let bin = '';
      const chunk = 0x8000;
      for (let i = 0; i < bytes.length; i += chunk) {
        bin += String.fromCharCode(...bytes.subarray(i, i + chunk));
      }
      b64 = btoa(bin);
    }
    return {
      id: uid('doc'),
      title: cleanTitle,
      author: 'Archivo PDF local',
      format: 'PDF',
      badgeTag: 'PDF',
      coverColor,
      coverAccent,
      genre: 'Importado',
      year: String(new Date().getFullYear()),
      synopsis: 'Documento PDF importado desde tu PC, listo para lectura y anotación directa.',
      totalWords: 450,
      currentUnit: 0,
      progressPercent: 0,
      lastOpenedAt: now,
      addedAt: now,
      pdfDataUrl: `data:application/pdf;base64,${b64}`,
      chapters: [
        {
          id: uid('ch'),
          title: cleanTitle,
          blocks: [
            {
              id: uid('b'),
              type: 'p',
              text: 'Visualizando documento PDF nativo en Luma Desktop.',
            },
          ],
        },
      ],
    };
  }

  if (format === 'EPUB') {
    const parsed = await parseEpub(bytes, cleanTitle);
    return {
      id: uid('doc'),
      title: parsed.title,
      author: parsed.author,
      format: 'EPUB',
      badgeTag: 'EPUB',
      coverColor,
      coverAccent,
      genre: 'Libro EPUB',
      year: String(new Date().getFullYear()),
      synopsis: 'Libro EPUB importado desde tu equipo.',
      totalWords: countWords(parsed.chapters),
      currentUnit: 0,
      progressPercent: 0,
      lastOpenedAt: now,
      addedAt: now,
      chapters: parsed.chapters,
    };
  }

  if (format === 'DOCX') {
    const parsed = await parseDocx(bytes, cleanTitle);
    return {
      id: uid('doc'),
      title: parsed.title,
      author: parsed.author,
      format: 'DOCX',
      badgeTag: 'DOCX · A4',
      coverColor,
      coverAccent,
      genre: 'Documento Word',
      year: String(new Date().getFullYear()),
      synopsis: 'Documento DOCX listo para revisión en formato hoja A4 o lectura fluida.',
      totalWords: countWords(parsed.chapters),
      currentUnit: 0,
      progressPercent: 0,
      lastOpenedAt: now,
      addedAt: now,
      chapters: parsed.chapters,
    };
  }

  if (format === 'PPTX') {
    const parsed = await parsePptx(bytes, cleanTitle);
    return {
      id: uid('doc'),
      title: parsed.title,
      author: parsed.author,
      format: 'PPTX',
      badgeTag: 'PPTX 16:9',
      coverColor,
      coverAccent,
      genre: 'Presentación',
      year: String(new Date().getFullYear()),
      synopsis: 'Presentación PowerPoint convertida a láminas interactivas en Luma.',
      totalWords: countWords(parsed.chapters, parsed.slides),
      currentUnit: 0,
      progressPercent: 0,
      lastOpenedAt: now,
      addedAt: now,
      slides: parsed.slides,
      chapters: parsed.chapters,
    };
  }

  const text = new TextDecoder('utf-8').decode(bytes);
  if (format === 'FB2') {
    const parsed = parseFb2(text, cleanTitle);
    return {
      id: uid('doc'),
      title: parsed.title,
      author: parsed.author,
      format: 'FB2',
      badgeTag: 'FB2',
      coverColor,
      coverAccent,
      genre: 'Ficción FB2',
      year: String(new Date().getFullYear()),
      synopsis: 'Libro FictionBook 2 importado desde tu PC.',
      totalWords: countWords(parsed.chapters),
      currentUnit: 0,
      progressPercent: 0,
      lastOpenedAt: now,
      addedAt: now,
      chapters: parsed.chapters,
    };
  }

  const chapters = parsePlainTextOrMarkdown(text, cleanTitle);
  return {
    id: uid('doc'),
    title: cleanTitle,
    author: 'Texto local',
    format: 'TXT',
    badgeTag: 'TXT',
    coverColor,
    coverAccent,
    genre: 'Apuntes',
    year: String(new Date().getFullYear()),
    synopsis: 'Archivo de texto plano o Markdown importado a tu biblioteca Luma.',
    totalWords: countWords(chapters),
    currentUnit: 0,
    progressPercent: 0,
    lastOpenedAt: now,
    addedAt: now,
    chapters,
  };
}

export async function importFromBase64File(file: {
  name: string;
  base64: string;
}): Promise<LumaDocument> {
  const bytes = base64ToUint8Array(file.base64);
  return importDocumentFromBytes(file.name, bytes, file.base64);
}

export async function importFromBrowserFile(file: File): Promise<LumaDocument> {
  const buffer = await file.arrayBuffer();
  return importDocumentFromBytes(file.name, new Uint8Array(buffer));
}
