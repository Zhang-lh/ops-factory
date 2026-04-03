import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { GATEWAY_URL, GATEWAY_SECRET_KEY } from '../../../config/runtime'
import { useUser } from '../providers/UserContext'

interface OnlyOfficePreviewProps {
    name: string
    path: string
    agentId: string
    type: string
    onlyofficeUrl: string
    fileBaseUrl: string
}

declare global {
    interface Window {
        DocsAPI?: {
            DocEditor: new (containerId: string, config: Record<string, unknown>) => OnlyOfficeEditor
        }
    }
}

interface OnlyOfficeEditor {
    destroyEditor: () => void
}

function getDocumentType(fileType: string): string {
    switch (fileType) {
        case 'docx': case 'doc': return 'word'
        case 'xlsx': case 'xls': return 'cell'
        case 'pptx': case 'ppt': return 'slide'
        default: return 'word'
    }
}

const EDITOR_CONTAINER_ID = 'onlyoffice-editor'

export default function OnlyOfficePreview({
    name, path, agentId, type, onlyofficeUrl, fileBaseUrl,
}: OnlyOfficePreviewProps) {
    const { t, i18n } = useTranslation()
    const { userId } = useUser()
    const editorRef = useRef<OnlyOfficeEditor | null>(null)
    const [scriptError, setScriptError] = useState(false)

    useEffect(() => {
        let cancelled = false

        const initEditor = () => {
            if (cancelled || !window.DocsAPI) return

            // Destroy previous editor if exists
            if (editorRef.current) {
                try { editorRef.current.destroyEditor() } catch { /* ignore */ }
                editorRef.current = null
            }

            let fileUrl = `${fileBaseUrl}/agents/${agentId}/files/${encodeURIComponent(path)}?key=${GATEWAY_SECRET_KEY}`
            if (userId) fileUrl += `&uid=${encodeURIComponent(userId)}`

            editorRef.current = new window.DocsAPI.DocEditor(EDITOR_CONTAINER_ID, {
                document: {
                    fileType: type,
                    title: name,
                    url: fileUrl,
                    permissions: { edit: false, download: true, print: true },
                },
                documentType: getDocumentType(type),
                editorConfig: {
                    mode: 'view',
                    lang: i18n.language?.startsWith('zh') ? 'zh' : 'en',
                    customization: {
                        compactHeader: true,
                        toolbarHideFileName: true,
                    },
                },
                type: 'embedded',
                height: '100%',
                width: '100%',
            })
        }

        // Check if API script is already loaded
        if (window.DocsAPI) {
            initEditor()
            return () => {
                cancelled = true
                if (editorRef.current) {
                    try { editorRef.current.destroyEditor() } catch { /* ignore */ }
                    editorRef.current = null
                }
            }
        }

        // Dynamically load the OnlyOffice API script
        const scriptId = 'onlyoffice-api-script'
        let script = document.getElementById(scriptId) as HTMLScriptElement | null

        // Remove any previously failed script tag so we retry cleanly
        const existing = document.getElementById(scriptId) as HTMLScriptElement | null
        if (existing && !window.DocsAPI) {
            existing.remove()
        }

        if (!document.getElementById(scriptId)) {
            script = document.createElement('script')
            script.id = scriptId
            script.src = `${onlyofficeUrl}/web-apps/apps/api/documents/api.js`
            script.async = true
            script.onload = () => {
                if (!cancelled) initEditor()
            }
            script.onerror = () => {
                if (!cancelled) setScriptError(true)
            }
            document.head.appendChild(script)
        } else {
            // Script tag exists and DocsAPI is loaded (checked above)
            initEditor()
        }

        return () => {
            cancelled = true
            if (editorRef.current) {
                try { editorRef.current.destroyEditor() } catch { /* ignore */ }
                editorRef.current = null
            }
        }
    }, [name, path, agentId, type, onlyofficeUrl, fileBaseUrl])

    if (scriptError) {
        let downloadUrl = `${GATEWAY_URL}/agents/${agentId}/files/${encodeURIComponent(path)}?key=${GATEWAY_SECRET_KEY}`
        if (userId) downloadUrl += `&uid=${encodeURIComponent(userId)}`
        return (
            <div className="file-preview-error">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="32" height="32">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
                <p>{t('onlyoffice.loadFailed')}</p>
                <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)' }}>
                    {t('onlyoffice.loadFailedHint')}
                </p>
                <a
                    href={downloadUrl}
                    className="btn btn-secondary"
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{ marginTop: 'var(--spacing-2)' }}
                >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" width="16" height="16" style={{ marginRight: 'var(--spacing-1)' }}>
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                        <polyline points="7 10 12 15 17 10" />
                        <line x1="12" y1="15" x2="12" y2="3" />
                    </svg>
                    {t('onlyoffice.downloadInstead')}
                </a>
            </div>
        )
    }

    return <div id={EDITOR_CONTAINER_ID} style={{ width: '100%', height: '100%' }} />
}
