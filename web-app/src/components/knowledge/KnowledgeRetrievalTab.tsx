import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { KNOWLEDGE_SERVICE_URL } from '../../config/runtime'
import { useToast } from '../../contexts/ToastContext'
import type {
    KnowledgeCapabilities,
    KnowledgeDefaults,
    KnowledgeDocumentSummary,
    KnowledgeProfileDetail,
    KnowledgeSource,
    PagedResponse,
} from '../../types/knowledge'

type RetrievalMode = 'semantic' | 'lexical' | 'hybrid'

interface RetrievalSettings {
    mode: RetrievalMode
    topK: number
    scoreThresholdEnabled: boolean
    scoreThreshold: number
}

interface RetrievalSearchHit {
    chunkId: string
    documentId: string
    sourceId: string
    title: string
    titlePath: string[]
    snippet: string
    score: number
    lexicalScore: number
    semanticScore: number
    fusionScore: number
    pageFrom: number | null
    pageTo: number | null
}

interface RetrievalSearchResponse {
    query: string
    hits: RetrievalSearchHit[]
    total: number
}

interface RetrievalFetchNeighbor {
    position: string
    chunkId: string
    text: string
}

interface RetrievalFetchResponse {
    chunkId: string
    documentId: string
    sourceId: string
    title: string
    titlePath: string[]
    text: string
    markdown: string
    keywords: string[]
    pageFrom: number | null
    pageTo: number | null
    previousChunkId: string | null
    nextChunkId: string | null
    neighbors: RetrievalFetchNeighbor[] | null
}

interface RetrievalHistoryEntry {
    id: string
    query: string
    mode: RetrievalMode
    topK: number
    scoreThresholdEnabled: boolean
    scoreThreshold: number
    createdAt: string
}

interface RetrievalDisplayHit extends RetrievalSearchHit {
    documentName: string
    displayScore: number
    displayPercent: number
    signalLevel: number
}

interface KnowledgeRetrievalTabProps {
    source: KnowledgeSource
    capabilities: KnowledgeCapabilities | null
    defaults: KnowledgeDefaults | null
    retrievalProfileDetail: KnowledgeProfileDetail | null
}

const QUERY_MAX_LENGTH = 200
const HISTORY_LIMIT = 8
const TOP_K_MIN = 1
const TOP_K_MAX = 10
const SCORE_THRESHOLD_MIN = 0
const SCORE_THRESHOLD_MAX = 1
const SCORE_THRESHOLD_STEP = 0.01

function clamp(value: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, value))
}

function formatDateTime(value: string): string {
    return new Date(value).toLocaleString(undefined, {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    })
}

function formatScore(value: number): string {
    return value.toFixed(2)
}

function normalizeRetrievalMode(value: string | null | undefined): RetrievalMode | null {
    switch (value?.toLowerCase()) {
    case 'semantic':
    case 'vector':
        return 'semantic'
    case 'lexical':
    case 'keyword':
    case 'keywords':
    case 'full_text':
        return 'lexical'
    case 'hybrid':
        return 'hybrid'
    default:
        return null
    }
}

function getStorageKey(sourceId: string): string {
    return `opsfactory:knowledge:retrieval-history:${sourceId}:v1`
}

function loadHistory(storageKey: string): RetrievalHistoryEntry[] {
    if (typeof window === 'undefined') return []

    try {
        const raw = window.localStorage.getItem(storageKey)
        if (!raw) return []
        const parsed = JSON.parse(raw) as RetrievalHistoryEntry[]
        return Array.isArray(parsed) ? parsed : []
    } catch {
        return []
    }
}

function saveHistory(storageKey: string, entries: RetrievalHistoryEntry[]): void {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(storageKey, JSON.stringify(entries))
}

function getProfileMode(profile: KnowledgeProfileDetail | null, defaults: KnowledgeDefaults | null): RetrievalMode {
    const profileMode = profile?.config && typeof profile.config === 'object' && 'retrieval' in profile.config
        ? normalizeRetrievalMode(((profile.config.retrieval as Record<string, unknown>)?.mode as string | undefined) || undefined)
        : null

    if (profileMode) return profileMode

    const defaultMode = normalizeRetrievalMode(defaults?.retrieval.mode)
    return defaultMode || 'hybrid'
}

function getTopK(defaults: KnowledgeDefaults | null): number {
    return clamp(defaults?.retrieval.finalTopK ?? 3, TOP_K_MIN, TOP_K_MAX)
}

function getSupportedModes(capabilities: KnowledgeCapabilities | null): Set<RetrievalMode> {
    const modes = (capabilities?.retrievalModes || [])
        .map(mode => normalizeRetrievalMode(mode))
        .filter((mode): mode is RetrievalMode => Boolean(mode))

    if (modes.length === 0) {
        return new Set<RetrievalMode>(['semantic', 'lexical', 'hybrid'])
    }

    return new Set(modes)
}

function buildInitialSettings(
    capabilities: KnowledgeCapabilities | null,
    defaults: KnowledgeDefaults | null,
    retrievalProfileDetail: KnowledgeProfileDetail | null
): RetrievalSettings {
    const supportedModes = getSupportedModes(capabilities)
    const preferredMode = getProfileMode(retrievalProfileDetail, defaults)
    const fallbackMode = supportedModes.has(preferredMode)
        ? preferredMode
        : supportedModes.has('hybrid')
            ? 'hybrid'
            : supportedModes.has('semantic')
                ? 'semantic'
                : 'lexical'

    return {
        mode: fallbackMode,
        topK: getTopK(defaults),
        scoreThresholdEnabled: true,
        scoreThreshold: 0.2,
    }
}

function getModeLabelKey(mode: RetrievalMode): string {
    switch (mode) {
    case 'semantic':
        return 'knowledge.retrievalModeSemantic'
    case 'lexical':
        return 'knowledge.retrievalModeLexical'
    case 'hybrid':
        return 'knowledge.retrievalModeHybrid'
    }
}

function getModeDescriptionKey(mode: RetrievalMode): string {
    switch (mode) {
    case 'semantic':
        return 'knowledge.retrievalModeSemanticDescription'
    case 'lexical':
        return 'knowledge.retrievalModeLexicalDescription'
    case 'hybrid':
        return 'knowledge.retrievalModeHybridDescription'
    }
}

function getDocumentName(documentId: string, names: Record<string, string>): string {
    return names[documentId] || documentId
}

function buildDisplayResults(
    hits: RetrievalSearchHit[],
    documentNames: Record<string, string>,
    settings: RetrievalSettings
): RetrievalDisplayHit[] {
    const maxScore = hits.reduce((currentMax, hit) => Math.max(currentMax, hit.score || 0), 0)
    const useRawScore = maxScore > 0 && maxScore <= 1.0001

    return hits
        .map(hit => {
            const normalized = maxScore > 0
                ? clamp(useRawScore ? hit.score : hit.score / maxScore, 0, 1)
                : 0
            return {
                ...hit,
                documentName: getDocumentName(hit.documentId, documentNames),
                displayScore: normalized,
                displayPercent: Math.round(normalized * 100),
                signalLevel: Math.max(1, Math.ceil(normalized * 5)),
            }
        })
        .filter(hit => !settings.scoreThresholdEnabled || hit.displayScore >= settings.scoreThreshold)
}

function upsertHistoryEntry(entries: RetrievalHistoryEntry[], entry: RetrievalHistoryEntry): RetrievalHistoryEntry[] {
    const remaining = entries.filter(item =>
        !(
            item.query === entry.query
            && item.mode === entry.mode
            && item.topK === entry.topK
            && item.scoreThresholdEnabled === entry.scoreThresholdEnabled
            && item.scoreThreshold === entry.scoreThreshold
        )
    )

    return [entry, ...remaining].slice(0, HISTORY_LIMIT)
}

function buildPageRange(pageFrom: number | null, pageTo: number | null, fallback: string): string {
    if (pageFrom === null || pageFrom === undefined) return fallback
    if (pageTo === null || pageTo === undefined || pageTo === pageFrom) return String(pageFrom)
    return `${pageFrom}-${pageTo}`
}

function RetrievalSettingsModal({
    supportedModes,
    settings,
    onClose,
    onSave,
}: {
    supportedModes: Set<RetrievalMode>
    settings: RetrievalSettings
    onClose: () => void
    onSave: (settings: RetrievalSettings) => void
}) {
    const { t } = useTranslation()
    const [draft, setDraft] = useState<RetrievalSettings>(settings)

    const modeOptions: RetrievalMode[] = ['hybrid', 'semantic', 'lexical']

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className="modal knowledge-retrieval-settings-modal"
                onClick={event => event.stopPropagation()}
            >
                <div className="modal-header">
                    <div>
                        <h2 className="modal-title">{t('knowledge.retrievalSettingsTitle')}</h2>
                        <p className="knowledge-retrieval-modal-subtitle">{t('knowledge.retrievalSettingsDescription')}</p>
                    </div>
                    <button className="modal-close" onClick={onClose}>&times;</button>
                </div>

                <div className="modal-body knowledge-retrieval-settings-body">
                    <section className="knowledge-retrieval-settings-section">
                        <div className="knowledge-retrieval-settings-section-head">
                            <h3 className="knowledge-retrieval-settings-section-title">{t('knowledge.retrievalMethodLabel')}</h3>
                            <p className="knowledge-retrieval-settings-section-description">{t('knowledge.retrievalMethodHint')}</p>
                        </div>
                        <div className="knowledge-retrieval-mode-options">
                            {modeOptions.map(mode => {
                                const supported = supportedModes.has(mode)
                                return (
                                    <label
                                        key={mode}
                                        className={[
                                            'knowledge-retrieval-mode-option',
                                            draft.mode === mode ? 'active' : '',
                                            !supported ? 'disabled' : '',
                                        ].filter(Boolean).join(' ')}
                                    >
                                        <input
                                            type="radio"
                                            name="retrieval-mode"
                                            checked={draft.mode === mode}
                                            onChange={() => setDraft(current => ({ ...current, mode }))}
                                            disabled={!supported}
                                        />
                                        <div className="knowledge-retrieval-mode-option-body">
                                            <div className="knowledge-retrieval-mode-option-title-row">
                                                <span className="knowledge-retrieval-mode-option-title">{t(getModeLabelKey(mode))}</span>
                                                {mode === 'hybrid' && (
                                                    <span className="knowledge-retrieval-mode-pill">{t('knowledge.retrievalModeRecommended')}</span>
                                                )}
                                            </div>
                                            <span className="knowledge-retrieval-mode-option-description">
                                                {t(getModeDescriptionKey(mode))}
                                            </span>
                                        </div>
                                    </label>
                                )
                            })}
                        </div>
                    </section>

                    <section className="knowledge-retrieval-settings-section">
                        <div className="knowledge-retrieval-settings-section-head">
                            <h3 className="knowledge-retrieval-settings-section-title">{t('knowledge.retrievalSettingsParamsTitle')}</h3>
                            <p className="knowledge-retrieval-settings-section-description">{t('knowledge.retrievalSettingsParamsDescription')}</p>
                        </div>

                        <div className="knowledge-retrieval-settings-grid">
                            <div className="knowledge-retrieval-parameter-card">
                                <div className="knowledge-retrieval-parameter-head">
                                    <label className="form-label" htmlFor="retrieval-top-k-input">{t('knowledge.retrievalTopKLabel')}</label>
                                    <span className="knowledge-retrieval-parameter-value">{draft.topK}</span>
                                </div>
                                <p className="knowledge-form-help">{t('knowledge.retrievalTopKHint')}</p>
                                <div className="knowledge-retrieval-range-row">
                                    <input
                                        id="retrieval-top-k-input"
                                        className="form-input knowledge-retrieval-number-input"
                                        type="number"
                                        min={TOP_K_MIN}
                                        max={TOP_K_MAX}
                                        value={draft.topK}
                                        onChange={event => setDraft(current => ({
                                            ...current,
                                            topK: clamp(Number(event.target.value) || TOP_K_MIN, TOP_K_MIN, TOP_K_MAX),
                                        }))}
                                    />
                                    <input
                                        className="knowledge-retrieval-range-input"
                                        type="range"
                                        min={TOP_K_MIN}
                                        max={TOP_K_MAX}
                                        value={draft.topK}
                                        onChange={event => setDraft(current => ({
                                            ...current,
                                            topK: clamp(Number(event.target.value), TOP_K_MIN, TOP_K_MAX),
                                        }))}
                                    />
                                </div>
                            </div>

                            <div className="knowledge-retrieval-parameter-card">
                                <div className="knowledge-retrieval-parameter-head">
                                    <div className="knowledge-retrieval-threshold-head">
                                        <label className="form-label" htmlFor="retrieval-threshold-input">{t('knowledge.retrievalThresholdLabel')}</label>
                                        <label className="mcp-toggle">
                                            <input
                                                type="checkbox"
                                                checked={draft.scoreThresholdEnabled}
                                                onChange={event => setDraft(current => ({
                                                    ...current,
                                                    scoreThresholdEnabled: event.target.checked,
                                                }))}
                                            />
                                            <span className="mcp-toggle-slider" />
                                        </label>
                                    </div>
                                    <span className="knowledge-retrieval-parameter-value">
                                        {draft.scoreThresholdEnabled ? formatScore(draft.scoreThreshold) : t('knowledge.retrievalThresholdOff')}
                                    </span>
                                </div>
                                <p className="knowledge-form-help">{t('knowledge.retrievalThresholdHint')}</p>
                                <div className={`knowledge-retrieval-range-row ${!draft.scoreThresholdEnabled ? 'is-disabled' : ''}`}>
                                    <input
                                        id="retrieval-threshold-input"
                                        className="form-input knowledge-retrieval-number-input"
                                        type="number"
                                        min={SCORE_THRESHOLD_MIN}
                                        max={SCORE_THRESHOLD_MAX}
                                        step={SCORE_THRESHOLD_STEP}
                                        value={draft.scoreThreshold}
                                        disabled={!draft.scoreThresholdEnabled}
                                        onChange={event => setDraft(current => ({
                                            ...current,
                                            scoreThreshold: clamp(Number(event.target.value) || 0, SCORE_THRESHOLD_MIN, SCORE_THRESHOLD_MAX),
                                        }))}
                                    />
                                    <input
                                        className="knowledge-retrieval-range-input"
                                        type="range"
                                        min={SCORE_THRESHOLD_MIN}
                                        max={SCORE_THRESHOLD_MAX}
                                        step={SCORE_THRESHOLD_STEP}
                                        value={draft.scoreThreshold}
                                        disabled={!draft.scoreThresholdEnabled}
                                        onChange={event => setDraft(current => ({
                                            ...current,
                                            scoreThreshold: clamp(Number(event.target.value), SCORE_THRESHOLD_MIN, SCORE_THRESHOLD_MAX),
                                        }))}
                                    />
                                </div>
                            </div>
                        </div>
                    </section>
                </div>

                <div className="modal-footer">
                    <button className="btn btn-secondary" onClick={onClose}>
                        {t('common.cancel')}
                    </button>
                    <button className="btn btn-primary" onClick={() => onSave(draft)}>
                        {t('common.save')}
                    </button>
                </div>
            </div>
        </div>
    )
}

function RetrievalDetailModal({
    hit,
    detail,
    mode,
    loading,
    error,
    onClose,
}: {
    hit: RetrievalDisplayHit
    detail: RetrievalFetchResponse | null
    mode: RetrievalMode
    loading: boolean
    error: string | null
    onClose: () => void
}) {
    const { t } = useTranslation()
    const content = detail?.text || detail?.markdown || ''

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className="modal knowledge-retrieval-detail-modal"
                onClick={event => event.stopPropagation()}
            >
                <div className="modal-header">
                    <div className="knowledge-retrieval-detail-header-copy">
                        <h2 className="modal-title">{t('knowledge.retrievalDetailTitle')}</h2>
                        <p className="knowledge-retrieval-detail-subtitle">
                            <span className="knowledge-retrieval-detail-chunk">{hit.chunkId}</span>
                            <span className="knowledge-retrieval-detail-separator">·</span>
                            <span className="knowledge-retrieval-detail-document">{hit.documentName}</span>
                        </p>
                    </div>
                    <div className="knowledge-retrieval-detail-header-meta">
                        <span className="knowledge-retrieval-score-pill">{formatScore(hit.displayScore)}</span>
                        <button className="modal-close" onClick={onClose}>&times;</button>
                    </div>
                </div>

                <div className="modal-body knowledge-retrieval-detail-body">
                    {loading ? (
                        <div className="knowledge-doc-preview-empty">{t('knowledge.retrievalDetailLoading')}</div>
                    ) : error ? (
                        <div className="agents-alert agents-alert-error">{error}</div>
                    ) : (
                        <>
                            <section className="knowledge-retrieval-detail-section knowledge-retrieval-detail-section-content">
                                <h3 className="knowledge-retrieval-detail-section-title">{t('knowledge.retrievalDetailContent')}</h3>
                                <div className="knowledge-retrieval-detail-content-panel">
                                    <div className="knowledge-retrieval-detail-content-text">{content || t('knowledge.notAvailable')}</div>
                                </div>
                            </section>

                            <section className="knowledge-retrieval-detail-section">
                                <h3 className="knowledge-retrieval-detail-section-title">{t('knowledge.retrievalDetailKeywords')}</h3>
                                {detail?.keywords && detail.keywords.length > 0 ? (
                                    <div className="resource-card-tags knowledge-retrieval-keywords">
                                        {detail.keywords.map(keyword => (
                                            <span key={keyword} className="resource-card-tag">#{keyword}</span>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="knowledge-section-empty">{t('knowledge.notAvailable')}</p>
                                )}
                            </section>

                            <section className="knowledge-retrieval-detail-section">
                                <h3 className="knowledge-retrieval-detail-section-title">{t('knowledge.retrievalDetailMetadata')}</h3>
                                <div className="knowledge-kv-grid knowledge-retrieval-detail-grid">
                                    <div className="knowledge-kv-item">
                                        <span className="knowledge-kv-label">{t('knowledge.retrievalDetailDocument')}</span>
                                        <span className="knowledge-kv-value">{hit.documentName}</span>
                                    </div>
                                    <div className="knowledge-kv-item">
                                        <span className="knowledge-kv-label">{t('knowledge.retrievalDetailMode')}</span>
                                        <span className="knowledge-kv-value">{t(getModeLabelKey(mode))}</span>
                                    </div>
                                    <div className="knowledge-kv-item">
                                        <span className="knowledge-kv-label">{t('knowledge.retrievalDetailPageRange')}</span>
                                        <span className="knowledge-kv-value">
                                            {buildPageRange(detail?.pageFrom ?? hit.pageFrom, detail?.pageTo ?? hit.pageTo, t('knowledge.notAvailable'))}
                                        </span>
                                    </div>
                                    <div className="knowledge-kv-item">
                                        <span className="knowledge-kv-label">{t('knowledge.retrievalScoreLabel')}</span>
                                        <span className="knowledge-kv-value">{formatScore(hit.displayScore)}</span>
                                    </div>
                                </div>
                            </section>
                        </>
                    )}
                </div>
            </div>
        </div>
    )
}

export default function KnowledgeRetrievalTab({
    source,
    capabilities,
    defaults,
    retrievalProfileDetail,
}: KnowledgeRetrievalTabProps) {
    const { t } = useTranslation()
    const { showToast } = useToast()
    const supportedModes = useMemo(() => getSupportedModes(capabilities), [capabilities])
    const storageKey = useMemo(() => getStorageKey(source.id), [source.id])
    const [settings, setSettings] = useState<RetrievalSettings>(() => buildInitialSettings(capabilities, defaults, retrievalProfileDetail))
    const [query, setQuery] = useState('')
    const [history, setHistory] = useState<RetrievalHistoryEntry[]>(() => loadHistory(storageKey))
    const [documentNames, setDocumentNames] = useState<Record<string, string>>({})
    const [hits, setHits] = useState<RetrievalSearchHit[]>([])
    const [hasSearched, setHasSearched] = useState(false)
    const [searching, setSearching] = useState(false)
    const [searchError, setSearchError] = useState<string | null>(null)
    const [settingsOpen, setSettingsOpen] = useState(false)
    const [detailTarget, setDetailTarget] = useState<RetrievalDisplayHit | null>(null)
    const [detail, setDetail] = useState<RetrievalFetchResponse | null>(null)
    const [detailLoading, setDetailLoading] = useState(false)
    const [detailError, setDetailError] = useState<string | null>(null)

    useEffect(() => {
        setSettings(buildInitialSettings(capabilities, defaults, retrievalProfileDetail))
        setQuery('')
        setHits([])
        setHasSearched(false)
        setSearchError(null)
        setDetailTarget(null)
        setDetail(null)
        setDetailError(null)
    }, [source.id])

    useEffect(() => {
        setHistory(loadHistory(storageKey))
    }, [storageKey])

    useEffect(() => {
        saveHistory(storageKey, history)
    }, [history, storageKey])

    useEffect(() => {
        let cancelled = false

        const loadDocumentNames = async () => {
            try {
                const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/documents?sourceId=${source.id}&page=1&pageSize=100`)
                const data = await response.json().catch(() => null) as PagedResponse<KnowledgeDocumentSummary> | { message?: string } | null

                if (!response.ok) {
                    throw new Error(data && typeof data === 'object' && 'message' in data ? String(data.message || response.statusText) : response.statusText)
                }

                if (cancelled) return

                const items = (data as PagedResponse<KnowledgeDocumentSummary>).items || []
                setDocumentNames(Object.fromEntries(items.map(item => [item.id, item.name])))
            } catch {
                if (!cancelled) {
                    setDocumentNames({})
                }
            }
        }

        void loadDocumentNames()

        return () => {
            cancelled = true
        }
    }, [source.id])

    const displayResults = useMemo(
        () => buildDisplayResults(hits, documentNames, settings),
        [documentNames, hits, settings]
    )

    const executeSearch = useCallback(async (nextQuery?: string, nextSettings?: RetrievalSettings) => {
        const effectiveQuery = (nextQuery ?? query).trim()
        const effectiveSettings = nextSettings ?? settings

        if (!effectiveQuery) {
            setSearchError(t('knowledge.retrievalQueryRequired'))
            return
        }

        setSearching(true)
        setSearchError(null)
        setDetailTarget(null)
        setDetail(null)
        setDetailError(null)
        setHasSearched(true)

        try {
            const body: Record<string, unknown> = {
                query: effectiveQuery,
                sourceIds: [source.id],
                topK: effectiveSettings.topK,
                override: {
                    mode: effectiveSettings.mode,
                    includeScores: true,
                },
            }

            if (source.retrievalProfileId) {
                body.retrievalProfileId = source.retrievalProfileId
            }

            const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/search`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(body),
            })
            const data = await response.json().catch(() => null) as RetrievalSearchResponse | { message?: string } | null

            if (!response.ok) {
                throw new Error(data && typeof data === 'object' && 'message' in data ? String(data.message || response.statusText) : response.statusText)
            }

            const nextHits = (data as RetrievalSearchResponse).hits || []
            setHits(nextHits)
            setHistory(current => upsertHistoryEntry(current, {
                id: `${Date.now()}:${effectiveSettings.mode}:${effectiveQuery}`,
                query: effectiveQuery,
                mode: effectiveSettings.mode,
                topK: effectiveSettings.topK,
                scoreThresholdEnabled: effectiveSettings.scoreThresholdEnabled,
                scoreThreshold: effectiveSettings.scoreThreshold,
                createdAt: new Date().toISOString(),
            }))
        } catch (err) {
            const message = err instanceof Error ? err.message : t('errors.unknown')
            setHits([])
            setSearchError(message)
            showToast('error', message)
        } finally {
            setSearching(false)
        }
    }, [query, settings, showToast, source.id, source.retrievalProfileId, t])

    useEffect(() => {
        if (!detailTarget) {
            setDetail(null)
            setDetailError(null)
            setDetailLoading(false)
            return
        }

        let cancelled = false

        const loadDetail = async () => {
            setDetailLoading(true)
            setDetailError(null)

            try {
                const response = await fetch(`${KNOWLEDGE_SERVICE_URL}/ops-knowledge/fetch/${detailTarget.chunkId}?includeNeighbors=true&neighborWindow=1`)
                const data = await response.json().catch(() => null) as RetrievalFetchResponse | { message?: string } | null

                if (!response.ok) {
                    throw new Error(data && typeof data === 'object' && 'message' in data ? String(data.message || response.statusText) : response.statusText)
                }

                if (!cancelled) {
                    setDetail(data as RetrievalFetchResponse)
                }
            } catch (err) {
                if (!cancelled) {
                    setDetail(null)
                    setDetailError(t('knowledge.retrievalDetailLoadFailed', {
                        error: err instanceof Error ? err.message : t('errors.unknown'),
                    }))
                }
            } finally {
                if (!cancelled) {
                    setDetailLoading(false)
                }
            }
        }

        void loadDetail()

        return () => {
            cancelled = true
        }
    }, [detailTarget])

    const handleSaveSettings = useCallback((nextSettings: RetrievalSettings) => {
        setSettings(nextSettings)
        setSettingsOpen(false)

        if (query.trim()) {
            void executeSearch(query, nextSettings)
        }
    }, [executeSearch, query])

    const handleReplayHistory = useCallback((entry: RetrievalHistoryEntry) => {
        const replaySettings: RetrievalSettings = {
            mode: entry.mode,
            topK: entry.topK,
            scoreThresholdEnabled: entry.scoreThresholdEnabled,
            scoreThreshold: entry.scoreThreshold,
        }

        setQuery(entry.query)
        setSettings(replaySettings)
        void executeSearch(entry.query, replaySettings)
    }, [executeSearch])

    const bindingName = retrievalProfileDetail?.name || source.retrievalProfileId || t('knowledge.notBound')
    const thresholdSummary = settings.scoreThresholdEnabled
        ? `>= ${formatScore(settings.scoreThreshold)}`
        : t('knowledge.retrievalThresholdOff')

    return (
        <>
            <div className="knowledge-retrieval-layout">
                <div className="knowledge-retrieval-column">
                    <section className="knowledge-section-card">
                        <div className="knowledge-section-header">
                            <div>
                                <h2 className="knowledge-section-title">{t('knowledge.retrievalTitle')}</h2>
                                <p className="knowledge-section-description">{t('knowledge.retrievalDescription')}</p>
                            </div>
                            <button
                                type="button"
                                className="btn btn-secondary"
                                onClick={() => setSettingsOpen(true)}
                            >
                                {t('knowledge.retrievalSettings')}
                            </button>
                        </div>

                        <div className="form-group">
                            <label className="form-label" htmlFor="knowledge-retrieval-query">{t('knowledge.retrievalQueryLabel')}</label>
                            <div className="knowledge-retrieval-query-shell">
                                <textarea
                                    id="knowledge-retrieval-query"
                                    className="form-input knowledge-retrieval-query"
                                    rows={5}
                                    maxLength={QUERY_MAX_LENGTH}
                                    placeholder={t('knowledge.retrievalQueryPlaceholder')}
                                    value={query}
                                    onChange={event => setQuery(event.target.value)}
                                />
                                <span className="knowledge-retrieval-query-count">
                                    {query.length}/{QUERY_MAX_LENGTH}
                                </span>
                            </div>
                        </div>

                        <div className="knowledge-retrieval-summary-panel">
                            <div className="knowledge-retrieval-summary-line">
                                <span className="knowledge-retrieval-summary-line-label">{t('knowledge.retrievalBaseConfigLabel')}</span>
                                <div className="resource-card-tags">
                                    <span className="resource-card-tag">{bindingName}</span>
                                </div>
                            </div>
                            <div className="knowledge-retrieval-summary-line">
                                <span className="knowledge-retrieval-summary-line-label">{t('knowledge.retrievalRunConfigLabel')}</span>
                                <div className="resource-card-tags">
                                    <span className="resource-card-tag">{t(getModeLabelKey(settings.mode))}</span>
                                    <span className="resource-card-tag">{`TopK ${settings.topK}`}</span>
                                    <span className="resource-card-tag">{`${t('knowledge.retrievalThresholdShort')} ${thresholdSummary}`}</span>
                                </div>
                            </div>
                        </div>

                        <div className="knowledge-retrieval-actions">
                            <button
                                type="button"
                                className="btn btn-primary"
                                onClick={() => void executeSearch()}
                                disabled={searching || !query.trim()}
                            >
                                {searching ? t('knowledge.retrievalRunning') : t('knowledge.retrievalRun')}
                            </button>
                        </div>
                    </section>

                    <section className="knowledge-section-card">
                        <div className="knowledge-section-header">
                            <div>
                                <h2 className="knowledge-section-title">{t('knowledge.retrievalHistoryTitle')}</h2>
                                <p className="knowledge-section-description">{t('knowledge.retrievalHistoryDescription')}</p>
                            </div>
                        </div>

                        {history.length === 0 ? (
                            <p className="knowledge-section-empty">{t('knowledge.retrievalHistoryEmpty')}</p>
                        ) : (
                            <div className="knowledge-retrieval-history-table">
                                <div className="knowledge-retrieval-history-head">
                                    <span>{t('knowledge.retrievalHistoryColumnQuery')}</span>
                                    <span>{t('knowledge.retrievalHistoryColumnMode')}</span>
                                    <span>{t('knowledge.retrievalHistoryColumnTime')}</span>
                                </div>
                                {history.map(entry => (
                                    <button
                                        key={entry.id}
                                        type="button"
                                        className="knowledge-retrieval-history-row"
                                        onClick={() => handleReplayHistory(entry)}
                                    >
                                        <span className="knowledge-retrieval-history-query">{entry.query}</span>
                                        <span>{t(getModeLabelKey(entry.mode))}</span>
                                        <span>{formatDateTime(entry.createdAt)}</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </section>
                </div>

                <div className="knowledge-retrieval-column">
                    <section className="knowledge-section-card">
                        <div className="knowledge-section-header">
                            <div>
                                <h2 className="knowledge-section-title">{t('knowledge.retrievalResultsTitle', { count: displayResults.length })}</h2>
                                <p className="knowledge-section-description">{t('knowledge.retrievalResultsDescription')}</p>
                            </div>
                        </div>

                        {searchError && (
                            <div className="conn-banner conn-banner-error">
                                {t('common.connectionError', { error: searchError })}
                            </div>
                        )}

                        {!hasSearched ? (
                            <div className="knowledge-doc-empty">{t('knowledge.retrievalEmptyDescription')}</div>
                        ) : searching ? (
                            <div className="knowledge-doc-empty">{t('common.loading')}</div>
                        ) : displayResults.length === 0 ? (
                            <div className="knowledge-doc-empty">
                                {hits.length > 0 && settings.scoreThresholdEnabled
                                    ? t('knowledge.retrievalNoResultsThreshold')
                                    : t('knowledge.retrievalNoResults')}
                            </div>
                        ) : (
                            <div className="knowledge-retrieval-results-shell">
                                <div className="knowledge-retrieval-results">
                                    {displayResults.map(hit => (
                                        <article key={hit.chunkId} className="knowledge-retrieval-result-card">
                                            <div className="knowledge-retrieval-result-head">
                                                <div className="knowledge-retrieval-result-headline">
                                                    <span className="knowledge-retrieval-chunk-id">{hit.chunkId}</span>
                                                    <span className="knowledge-retrieval-result-meta">
                                                        {t('knowledge.retrievalScoreLabel')} {formatScore(hit.displayScore)}
                                                    </span>
                                                </div>
                                                <div className="knowledge-retrieval-score-group">
                                                    <span className="knowledge-retrieval-mode-tag">{t(getModeLabelKey(settings.mode))}</span>
                                                </div>
                                            </div>

                                            <div className="knowledge-retrieval-result-body">
                                                <strong className="knowledge-retrieval-result-title">{hit.documentName}</strong>
                                                <p className="knowledge-retrieval-result-snippet">{hit.snippet || hit.title || hit.chunkId}</p>
                                            </div>

                                            <div className="knowledge-retrieval-result-foot">
                                                <div className="knowledge-retrieval-result-source">
                                                    <span className="knowledge-retrieval-result-source-name">
                                                        {hit.title || t('knowledge.notAvailable')}
                                                    </span>
                                                    <span className="knowledge-retrieval-result-meta">
                                                        {t('knowledge.retrievalPageShort')} {buildPageRange(hit.pageFrom, hit.pageTo, t('knowledge.notAvailable'))}
                                                    </span>
                                                </div>
                                                <button
                                                    type="button"
                                                    className="knowledge-retrieval-open-btn"
                                                    onClick={() => setDetailTarget(hit)}
                                                >
                                                    {t('common.open')}
                                                </button>
                                            </div>

                                            <div className="knowledge-retrieval-visual">
                                                <div className="knowledge-retrieval-signal" aria-hidden="true">
                                                    {Array.from({ length: 5 }).map((_, index) => (
                                                        <span
                                                            key={`${hit.chunkId}-${index}`}
                                                            className={index < hit.signalLevel ? 'active' : ''}
                                                        />
                                                    ))}
                                                </div>
                                                <div className="knowledge-retrieval-bar">
                                                    <span style={{ width: `${hit.displayPercent}%` }} />
                                                </div>
                                            </div>
                                        </article>
                                    ))}
                                </div>
                            </div>
                        )}
                    </section>
                </div>
            </div>

            {settingsOpen && (
                <RetrievalSettingsModal
                    supportedModes={supportedModes}
                    settings={settings}
                    onClose={() => setSettingsOpen(false)}
                    onSave={handleSaveSettings}
                />
            )}

            {detailTarget && (
                <RetrievalDetailModal
                    hit={detailTarget}
                    detail={detail}
                    mode={settings.mode}
                    loading={detailLoading}
                    error={detailError}
                    onClose={() => setDetailTarget(null)}
                />
            )}
        </>
    )
}
