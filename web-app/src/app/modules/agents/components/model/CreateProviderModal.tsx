import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Button from '../../../../platform/ui/primitives/Button'
import type { CreateProviderRequest, LlmProvider, UpdateProviderRequest } from '../../../../../types/agentConfig'
import { formatProviderEngine } from './providerDisplay'
import { removeLeadingZeros, validateContextLimit } from '../../../../../utils/model-validation'

interface CreateProviderModalProps {
    mode: 'create' | 'edit'
    provider?: LlmProvider
    onClose: () => void
    onCreate?: (provider: CreateProviderRequest) => Promise<boolean>
    onUpdate?: (providerName: string, provider: UpdateProviderRequest) => Promise<boolean>
}

function firstModelName(provider?: LlmProvider): string {
    return provider?.models?.[0]?.name || ''
}

function firstContextLimit(provider?: LlmProvider): string {
    const contextLimit = provider?.models?.[0]?.context_limit
    return contextLimit === undefined || contextLimit === '' ? '' : String(contextLimit)
}

function ReadonlyProviderField({ label, value }: { label: string; value?: string }) {
    return (
        <label className="form-group">
            <span className="form-label">{label}</span>
            <input className="form-input agent-provider-readonly-input" value={value || '-'} readOnly />
        </label>
    )
}

export default function CreateProviderModal({ mode, provider, onClose, onCreate, onUpdate }: CreateProviderModalProps) {
    const { t } = useTranslation()
    const isEdit = mode === 'edit'
    const [name, setName] = useState(provider?.name || '')
    const [displayName, setDisplayName] = useState(provider?.display_name || '')
    const [baseUrl, setBaseUrl] = useState(provider?.base_url || '')
    const [apiKey, setApiKey] = useState('')
    const [modelName, setModelName] = useState(firstModelName(provider))
    const [contextLimit, setContextLimit] = useState(firstContextLimit(provider))
    const [description, setDescription] = useState(provider?.description || '')
    const [isSaving, setIsSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const handleContextLimitChange = (value: string) => {
        const filtered = value.replace(/\D/g, '')
        setContextLimit(removeLeadingZeros(filtered))
    }

    const validationErrors = useMemo(() => {
        const errors: Record<string, string> = {}
        const trimmedBaseUrl = baseUrl.trim()
        const trimmedContextLimit = contextLimit.trim()

        if (!isEdit) {
            const trimmedName = name.trim()
            if (!trimmedName) {
                errors.name = t('agentConfigure.providerNameRequired')
            } else if (!/^[A-Za-z0-9._-]+$/.test(trimmedName)) {
                errors.name = t('agentConfigure.providerNameFormat')
            }
        }

        if (!trimmedBaseUrl) {
            errors.baseUrl = t('agentConfigure.baseUrlRequired')
        } else if (!/^https?:\/\/[^\s]*$/i.test(trimmedBaseUrl)) {
            errors.baseUrl = t('agentConfigure.baseUrlFormat')
        }

        if (trimmedContextLimit) {
            const error = validateContextLimit(trimmedContextLimit)
            if (error === 'format') {
                errors.contextLimit = t('agentConfigure.contextLimitFormat')
            } else if (error === 'range') {
                errors.contextLimit = t('agentConfigure.contextLimitRange')
            }
        }

        return errors
    }, [baseUrl, contextLimit, isEdit, name, t])

    const isValid = Object.keys(validationErrors).length === 0

    const buildProviderPayload = (): UpdateProviderRequest => ({
        base_url: baseUrl.trim(),
        api_key: apiKey.trim(),
        description: description.trim(),
        models: [{
            name: modelName.trim(),
            ...(contextLimit.trim() ? { context_limit: contextLimit.trim() } : {}),
        }],
    })

    const handleSave = async () => {
        setError(null)
        if (!isValid) {
            const firstError = Object.values(validationErrors)[0]
            setError(firstError)
            return
        }
        setIsSaving(true)
        const payload = buildProviderPayload()
        const success = isEdit
            ? Boolean(provider?.name && onUpdate && await onUpdate(provider.name, payload))
            : Boolean(onCreate && await onCreate({
                name: name.trim(),
                display_name: displayName.trim() || name.trim(),
                ...payload,
            })
        )
        setIsSaving(false)
        if (success) {
            onClose()
        }
    }

    return (
        <div className="modal-overlay">
            <div className="modal modal-wide">
                <div className="modal-header">
                    <h2 className="modal-title">{isEdit ? t('agentConfigure.editProvider') : t('agentConfigure.createProvider')}</h2>
                    <button type="button" className="modal-close" onClick={onClose}>&times;</button>
                </div>
                <div className="modal-body">
                    {error && <div className="agents-alert agents-alert-error">{error}</div>}
                    <div className="agent-provider-form-grid">
                        {isEdit && (
                            <ReadonlyProviderField label={t('agentConfigure.providerFile')} value={provider?.fileName} />
                        )}
                        {isEdit ? (
                            <ReadonlyProviderField label={t('agentConfigure.providerName')} value={name} />
                        ) : (
                            <label className="form-group">
                                <span className="form-label">{t('agentConfigure.providerName')} <span className="form-required">*</span></span>
                                <input className="form-input" value={name} onChange={event => setName(event.target.value)} />
                                {validationErrors.name && <span className="form-error">{validationErrors.name}</span>}
                            </label>
                        )}
                        {isEdit ? (
                            <ReadonlyProviderField label={t('agentConfigure.providerDisplayName')} value={displayName} />
                        ) : (
                            <label className="form-group">
                                <span className="form-label">{t('agentConfigure.providerDisplayName')}</span>
                                <input className="form-input" value={displayName} onChange={event => setDisplayName(event.target.value)} />
                            </label>
                        )}
                        <ReadonlyProviderField label={t('agentConfigure.providerEngine')} value={formatProviderEngine(provider?.engine)} />
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.apiKey')}</span>
                            <input
                                className="form-input"
                                type="password"
                                value={apiKey}
                                onChange={event => setApiKey(event.target.value)}
                                placeholder={t('agentConfigure.apiKeyPlaceholder')}
                            />
                        </label>
                        <label className="form-group agent-provider-form-wide">
                            <span className="form-label">{t('agentConfigure.baseUrl')} <span className="form-required">*</span></span>
                            <input className="form-input" value={baseUrl} onChange={event => setBaseUrl(event.target.value)} placeholder={t('agentConfigure.baseUrlPlaceholder')} />
                            {validationErrors.baseUrl && <span className="form-error">{validationErrors.baseUrl}</span>}
                        </label>
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.modelName')}</span>
                            <input className="form-input" value={modelName} onChange={event => setModelName(event.target.value)} />
                        </label>
                        <label className="form-group">
                            <span className="form-label">{t('agentConfigure.contextLimit')}</span>
                            <input
                                className="form-input"
                                type="text"
                                inputMode="numeric"
                                value={contextLimit}
                                onChange={event => handleContextLimitChange(event.target.value)}
                                placeholder="200000"
                            />
                            {validationErrors.contextLimit && <span className="form-error">{validationErrors.contextLimit}</span>}
                        </label>
                        <label className="form-group agent-provider-form-wide">
                            <span className="form-label">{t('agentConfigure.providerDescription')}</span>
                            <textarea className="form-input" value={description} onChange={event => setDescription(event.target.value)} />
                        </label>
                    </div>
                </div>
                <div className="modal-footer">
                    <Button variant="secondary" onClick={onClose} disabled={isSaving}>{t('common.cancel')}</Button>
                    <Button variant="primary" onClick={handleSave} disabled={isSaving || !isValid}>
                        {(() => {
                            if (isSaving) return t('agentConfigure.savingProvider')
                            if (isEdit) return t('common.save')
                            return t('agentConfigure.createAction')
                        })()}
                    </Button>
                </div>
            </div>
        </div>
    )
}
