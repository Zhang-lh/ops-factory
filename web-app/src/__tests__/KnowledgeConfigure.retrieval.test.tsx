import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import KnowledgeConfigure from '../pages/KnowledgeConfigure'

const showToast = vi.fn()
const searchRequests: Array<Record<string, unknown>> = []

vi.mock('react-i18next', () => ({
    initReactI18next: {
        type: '3rdParty',
        init: () => {},
    },
    useTranslation: () => ({
        t: (key: string, params?: Record<string, unknown>) => {
            if (params?.name) return `${key}:${String(params.name)}`
            if (params?.error) return `${key}:${String(params.error)}`
            return key
        },
    }),
}))

vi.mock('../contexts/ToastContext', () => ({
    useToast: () => ({
        showToast,
    }),
}))

vi.mock('../contexts/PreviewContext', () => ({
    usePreview: () => ({
        openInlinePreview: vi.fn(),
        previewFile: null,
        closePreview: vi.fn(),
    }),
}))

vi.mock('../config/runtime', () => ({
    KNOWLEDGE_SERVICE_URL: 'http://127.0.0.1:8092',
}))

const baseSource = {
    id: 'src_001',
    name: '产品文档库',
    description: '产品手册与 FAQ',
    status: 'ACTIVE',
    storageMode: 'MANAGED',
    indexProfileId: 'ip_default',
    retrievalProfileId: 'rp_default',
    createdAt: '2026-03-25T10:00:00Z',
    updatedAt: '2026-03-25T10:00:00Z',
}

describe('KnowledgeConfigure retrieval tab', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        searchRequests.length = 0
        window.localStorage.clear()

        vi.stubGlobal('fetch', vi.fn((input: string | URL | Request, init?: RequestInit) => {
            const url = String(input)
            const method = init?.method ?? 'GET'

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => baseSource,
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/sources/src_001/stats')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        sourceId: 'src_001',
                        documentCount: 12,
                        indexedDocumentCount: 10,
                        failedDocumentCount: 1,
                        processingDocumentCount: 1,
                        chunkCount: 234,
                        userEditedChunkCount: 3,
                        lastIngestionAt: '2026-03-25T12:30:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/capabilities')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        retrievalModes: ['semantic', 'lexical', 'hybrid'],
                        fusionModes: ['rrf'],
                        chunkModes: ['hierarchical'],
                        expandModes: ['ordinal_neighbors'],
                        analyzers: ['smartcn'],
                        editableChunkFields: ['title', 'keywords', 'text'],
                        featureFlags: {
                            allowChunkEdit: true,
                            allowChunkDelete: true,
                            allowExplain: true,
                            allowRequestOverride: false,
                        },
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/system/defaults')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        ingest: {
                            maxFileSizeMb: 100,
                            allowedContentTypes: ['application/pdf', 'text/markdown'],
                            deduplication: 'sha256',
                            skipExistingByDefault: true,
                        },
                        chunking: {
                            mode: 'hierarchical',
                            targetTokens: 512,
                            overlapTokens: 64,
                            respectHeadings: true,
                            keepTablesWhole: true,
                        },
                        retrieval: {
                            mode: 'semantic',
                            lexicalTopK: 50,
                            semanticTopK: 50,
                            finalTopK: 3,
                            fusionMode: 'rrf',
                            rrfK: 60,
                        },
                        features: {
                            allowChunkEdit: true,
                            allowChunkDelete: true,
                            allowExplain: true,
                            allowRequestOverride: false,
                        },
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/index/ip_default')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'ip_default',
                        name: '默认索引配置',
                        config: {
                            chunking: { mode: 'hierarchical', targetTokens: 512 },
                        },
                        createdAt: '2026-03-24T10:00:00Z',
                        updatedAt: '2026-03-24T10:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.endsWith('/ops-knowledge/profiles/retrieval/rp_default')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        id: 'rp_default',
                        name: '默认召回配置',
                        config: {
                            retrieval: { mode: 'semantic', fusionMode: 'rrf' },
                            result: { finalTopK: 3, snippetLength: 180 },
                        },
                        createdAt: '2026-03-24T10:00:00Z',
                        updatedAt: '2026-03-24T10:00:00Z',
                    }),
                } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/documents?sourceId=src_001&page=1&pageSize=100')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        items: [
                            {
                                id: 'doc_001',
                                sourceId: 'src_001',
                                name: '模型选型在+V100+环境下评估结论.pdf',
                                contentType: 'application/pdf',
                                title: '模型选型在+V100+环境下评估结论',
                                status: 'INDEXED',
                                indexStatus: 'INDEXED',
                                fileSizeBytes: 1024,
                                chunkCount: 8,
                                userEditedChunkCount: 0,
                                createdAt: '2026-03-25T10:00:00Z',
                                updatedAt: '2026-03-25T10:05:00Z',
                            },
                        ],
                        page: 1,
                        pageSize: 100,
                        total: 1,
                    }),
                } as Response)
            }

            if (method === 'POST' && url.endsWith('/ops-knowledge/search')) {
                const requestBody = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>
                searchRequests.push(requestBody)
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        query: requestBody.query,
                        total: 1,
                        hits: [
                            {
                                chunkId: 'chk_001',
                                documentId: 'doc_001',
                                sourceId: 'src_001',
                                title: '关键结论',
                                titlePath: ['模型选型', '结论'],
                                snippet: '当前唯一建议继续推进的模型是 Qwen3-32B。',
                                score: 0.7,
                                lexicalScore: 0.4,
                                semanticScore: 0.7,
                                fusionScore: 0.7,
                                pageFrom: 8,
                                pageTo: 8,
                            },
                        ],
                    }),
                } as Response)
            }

            if (method === 'GET' && url.includes('/ops-knowledge/fetch/chk_001')) {
                return Promise.resolve({
                    ok: true,
                    json: async () => ({
                        chunkId: 'chk_001',
                        documentId: 'doc_001',
                        sourceId: 'src_001',
                        title: '关键结论',
                        titlePath: ['模型选型', '结论'],
                        text: '当前唯一建议继续推进的模型是 Qwen3-32B。Cohere Command-R 不适合 Goose / tools。',
                        markdown: '当前唯一建议继续推进的模型是 Qwen3-32B。',
                        keywords: ['Qwen3', '32B', 'Cohere'],
                        pageFrom: 8,
                        pageTo: 8,
                        previousChunkId: null,
                        nextChunkId: 'chk_002',
                        neighbors: [],
                    }),
                } as Response)
            }

            return Promise.resolve({
                ok: false,
                status: 404,
                json: async () => ({ message: 'not found' }),
            } as Response)
        }))
    })

    it('runs retrieval search, updates settings, and opens detail modal', async () => {
        render(
            <MemoryRouter initialEntries={['/knowledge/src_001?tab=retrieval']}>
                <Routes>
                    <Route path="/knowledge/:sourceId" element={<KnowledgeConfigure />} />
                </Routes>
            </MemoryRouter>
        )

        await screen.findByText('knowledge.retrievalTitle')

        fireEvent.change(screen.getByLabelText('knowledge.retrievalQueryLabel'), {
            target: { value: 'qwen3' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalRun' }))

        await screen.findByText('模型选型在+V100+环境下评估结论.pdf')
        expect(searchRequests.at(0)?.override).toEqual({
            mode: 'semantic',
            includeScores: true,
        })

        fireEvent.click(screen.getByRole('button', { name: 'knowledge.retrievalSettings' }))
        await screen.findByText('knowledge.retrievalSettingsTitle')

        fireEvent.click(screen.getByText('knowledge.retrievalModeLexical'))
        fireEvent.change(screen.getByLabelText('knowledge.retrievalTopKLabel'), {
            target: { value: '5' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'common.save' }))

        await waitFor(() => {
            expect(searchRequests).toHaveLength(2)
        })

        expect(searchRequests.at(1)?.override).toEqual({
            mode: 'lexical',
            includeScores: true,
        })
        expect(searchRequests.at(1)?.topK).toBe(5)

        fireEvent.click(screen.getByRole('button', { name: 'common.open' }))
        await screen.findByText('knowledge.retrievalDetailTitle')
        expect((await screen.findAllByText(/Qwen3-32B/)).length).toBeGreaterThan(0)

        const rawHistory = window.localStorage.getItem('opsfactory:knowledge:retrieval-history:src_001:v1')
        expect(rawHistory).toContain('qwen3')
        expect(rawHistory).toContain('lexical')
    })
})
