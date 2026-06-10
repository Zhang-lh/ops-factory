import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Button from '../../../../platform/ui/primitives/Button'
import { useToast } from '../../../../platform/providers/ToastContext'
import type { McpAddRequest, McpEntry, McpType } from '../../../../../types/mcp'

interface AddMcpModalProps {
  isOpen: boolean
  onClose: () => void
  onSubmit: (request: McpAddRequest) => Promise<void>
  mode?: 'add' | 'edit'
  initialEntry?: McpEntry | null
}

type ConnectionType = 'stdio' | 'streamable_http'
type EnvVarRow = { key: string; value: string; fromExisting?: boolean }

const MCP_NAME_MAX_LENGTH = 100

export default function AddMcpModal({
  isOpen,
  onClose,
  onSubmit,
  mode = 'add',
  initialEntry = null,
}: AddMcpModalProps) {
  const { t } = useTranslation()
  const { showToast } = useToast()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [connectionType, setConnectionType] = useState<ConnectionType>('stdio')
  const [command, setCommand] = useState('')
  const [args, setArgs] = useState('')
  const [uri, setUri] = useState('')
  const [envVars, setEnvVars] = useState<EnvVarRow[]>([])
  const [timeout, setTimeout] = useState('300')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isEditMode = mode === 'edit'

  useEffect(() => {
    if (!isOpen) return

    if (isEditMode && initialEntry) {
      setName(initialEntry.name)
      setDescription(initialEntry.description || '')
      setConnectionType(initialEntry.type === 'streamable_http' ? 'streamable_http' : 'stdio')
      setCommand(initialEntry.cmd || '')
      setArgs(initialEntry.args?.join(' ') || '')
      setUri(initialEntry.uri || '')
      const existingEnvKeys = initialEntry.env_keys || Object.keys(initialEntry.envs || {})
      setEnvVars(existingEnvKeys.map((key) => ({ key, value: '', fromExisting: true })))
      setTimeout(String(initialEntry.timeout || 300))
      setError(null)
      return
    }

    resetForm()
  }, [isOpen, isEditMode, initialEntry])
  const resetForm = () => {
    setName('')
    setDescription('')
    setConnectionType('stdio')
    setCommand('')
    setArgs('')
    setUri('')
    setEnvVars([])
    setTimeout('300')
    setError(null)
  }

  const validationErrors = useMemo(() => {
    const errors: Record<string, string> = {}
    const trimmedName = name.trim()

    if (!trimmedName) {
      errors.name = t('mcp.nameRequired')
    } else if (trimmedName.length > MCP_NAME_MAX_LENGTH) {
      errors.name = t('mcp.nameTooLong')
    } else if (!/^[-A-Za-z0-9._ ]+$/.test(trimmedName)) {
      errors.name = t('mcp.nameInvalid')
    }

    if (connectionType === 'stdio' && !command.trim()) {
      errors.command = t('mcp.commandRequired')
    }

    if (connectionType === 'streamable_http' && !uri.trim()) {
      errors.uri = t('mcp.uriRequired')
    }

    for (const { key, value, fromExisting } of envVars) {
      const trimmedKey = key.trim()
      const trimmedValue = value.trim()
      if (!trimmedKey && !trimmedValue) continue
      if (!trimmedKey && trimmedValue) {
        errors.env = t('mcp.envKeyRequired')
        break
      }
      if (trimmedKey && !trimmedValue && !fromExisting) {
        errors.env = t('mcp.envValueRequired', { key: trimmedKey })
        break
      }
    }

    return errors
  }, [name, connectionType, command, uri, envVars, t])

  const isValid = Object.keys(validationErrors).length === 0

  const handleClose = () => {
    resetForm()
    onClose()
  }

  const addEnvVar = () => {
    setEnvVars([...envVars, { key: '', value: '' }])
  }

  const removeEnvVar = (index: number) => {
    setEnvVars(envVars.filter((_, i) => i !== index))
  }

  const updateEnvVar = (index: number, field: 'key' | 'value', value: string) => {
    setEnvVars(envVars.map((env, i) =>
      i === index ? { ...env, [field]: value } : env
    ))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!isValid) {
      const firstError = Object.values(validationErrors)[0]
      setError(firstError)
      return
    }

    // Build env_keys + envs payload
    const trimmedName = name.trim()
    const envKeysSet = new Set<string>()
    const envs: Record<string, string> = {}
    for (const { key, value, fromExisting } of envVars) {
      const trimmedKey = key.trim()
      const trimmedValue = value.trim()

      if (!trimmedKey && !trimmedValue) {
        continue
      }

      if (trimmedKey) {
        envKeysSet.add(trimmedKey)
      }

      if (trimmedKey && trimmedValue) {
        envs[trimmedKey] = value
      }
    }
    const envKeys = Array.from(envKeysSet)

    const request: McpAddRequest = {
      name: trimmedName,
      enabled: isEditMode ? (initialEntry?.enabled ?? true) : true,
      type: connectionType as McpType,
      description: description.trim() || undefined,
      timeout: parseInt(timeout, 10) || 300,
      env_keys: isEditMode || envKeys.length > 0 ? envKeys : undefined,
      // Include bundled flag in edit mode so backend treats this as an update
      ...(isEditMode && initialEntry?.bundled !== undefined && {
        bundled: initialEntry.bundled,
      }),
      ...(connectionType === 'stdio' && {
        cmd: command.trim(),
        args: args.trim() ? args.trim().split(/\s+/) : [],
        envs: Object.keys(envs).length > 0 ? envs : undefined,
      }),
      ...(connectionType === 'streamable_http' && {
        uri: uri.trim(),
        envs: Object.keys(envs).length > 0 ? envs : undefined,
      }),
    }

    setIsSubmitting(true)
    try {
      await onSubmit(request)
      showToast('success', t('mcp.configUpdatedRestarting'))
      handleClose()
    } catch (err) {
      let fallbackMessage: string
      if (isEditMode) {
        fallbackMessage = t('mcp.updateFailed')
      } else {
        fallbackMessage = t('mcp.addFailed')
      }
      const nextError = err instanceof Error ? err.message : fallbackMessage
      setError(nextError)
      showToast('error', nextError)
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOpen) return null

  return (
    <div className="modal-overlay" onClick={handleClose}>
      <div className="modal modal-wide mcp-modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">{isEditMode ? t('mcp.editTitle') : t('mcp.addTitle')}</h2>
          <button
            type="button"
            className="modal-close"
            onClick={handleClose}
            aria-label={t('common.close')}
          >
            &times;
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            {error && (
              <div className="agents-alert agents-alert-error" style={{ marginBottom: 'var(--spacing-4)' }}>
                {error}
              </div>
            )}

            {!isEditMode && (
              <label className="form-group">
                <span className="form-label">
                  {t('mcp.name')} <span className="form-required">*</span>
                </span>
                <input
                  type="text"
                  className="form-input"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder={t('mcp.namePlaceholder')}
                  maxLength={MCP_NAME_MAX_LENGTH}
                />
                {validationErrors.name && <span className="form-error">{validationErrors.name}</span>}
              </label>
            )}
            {isEditMode && (
              <div className="form-group">
                <label className="form-label">
                  {t('mcp.name')} <span className="form-required">*</span>
                </label>
                <input
                  type="text"
                  className="form-input"
                  value={name}
                  disabled
                />
                <p className="mcp-form-hint">
                  {t('mcp.nameCannotChange')}
                </p>
              </div>
            )}

            <div className="form-group">
              <label className="form-label">{t('mcp.description')}</label>
              <input
                type="text"
                className="form-input"
                value={description}
                onChange={e => setDescription(e.target.value)}
                placeholder={t('mcp.descriptionPlaceholder')}
              />
            </div>

            <div className="form-group">
              <label className="form-label">{t('mcp.connectionType')}</label>
              <div className="mcp-form-radio-group">
                <label className="mcp-form-radio">
                  <input
                    type="radio"
                    name="connectionType"
                    value="stdio"
                    checked={connectionType === 'stdio'}
                    onChange={() => setConnectionType('stdio')}
                  />
                  <span>{t('mcp.stdio')}</span>
                </label>
                <label className="mcp-form-radio">
                  <input
                    type="radio"
                    name="connectionType"
                    value="streamable_http"
                    checked={connectionType === 'streamable_http'}
                    onChange={() => setConnectionType('streamable_http')}
                  />
                  <span>{t('mcp.streamableHttp')}</span>
                </label>
              </div>
            </div>

            {connectionType === 'stdio' && (
              <>
                <label className="form-group">
                  <span className="form-label">
                    {t('mcp.command')} <span className="form-required">*</span>
                  </span>
                  <input
                    type="text"
                    className="form-input"
                    value={command}
                    onChange={e => setCommand(e.target.value)}
                    placeholder="python"
                  />
                  {validationErrors.command && <span className="form-error">{validationErrors.command}</span>}
                </label>

                <div className="form-group">
                  <label className="form-label">{t('mcp.arguments')}</label>
                  <input
                    type="text"
                    className="form-input"
                    value={args}
                    onChange={e => setArgs(e.target.value)}
                    placeholder="-m my_mcp_server"
                  />
                  <p className="mcp-form-hint">
                    {t('mcp.argumentsHint')}
                  </p>
                </div>
              </>
            )}

            {connectionType === 'streamable_http' && (
              <label className="form-group">
                <span className="form-label">
                  {t('mcp.uri')} <span className="form-required">*</span>
                </span>
                <input
                  type="text"
                  className="form-input"
                  value={uri}
                  onChange={e => setUri(e.target.value)}
                  placeholder="http://localhost:8080/mcp"
                />
                {validationErrors.uri && <span className="form-error">{validationErrors.uri}</span>}
              </label>
            )}

            <div className="form-group">
              <div className="mcp-env-header">
                <label className="form-label" style={{ marginBottom: 0 }}>{t('mcp.envVars')}</label>
                <button
                  type="button"
                  className="mcp-form-add-btn"
                  onClick={addEnvVar}
                >
                  {t('mcp.envAdd')}
                </button>
              </div>
              {envVars.length > 0 && (
                <div className="mcp-form-env-list">
                  {envVars.map((env, index) => (
                    <div key={index} className="mcp-form-env-row">
                      <input
                        type="text"
                        className="form-input mcp-form-env-key"
                        value={env.key}
                        onChange={e => updateEnvVar(index, 'key', e.target.value)}
                        placeholder={t('mcp.envKeyPlaceholder')}
                        disabled={env.fromExisting}
                        title={env.key}
                      />
                      <input
                        type="text"
                        className="form-input mcp-form-env-value"
                        value={env.value}
                        onChange={e => updateEnvVar(index, 'value', e.target.value)}
                        placeholder={env.fromExisting ? t('mcp.envKeepCurrentValue') : t('mcp.envValuePlaceholder')}
                      />
                      <button
                        type="button"
                        className="mcp-form-remove-btn"
                        onClick={() => removeEnvVar(index)}
                      >
                        &times;
                      </button>
                    </div>
                  ))}
                </div>
              )}
              {validationErrors.env && (
                <span className="form-error">{validationErrors.env}</span>
              )}
              {isEditMode && envVars.some(env => env.fromExisting) && (
                <p className="mcp-form-hint">
                  {t('mcp.envExistingHint')}
                </p>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">{t('mcp.timeout')}</label>
              <input
                type="number"
                className="form-input mcp-form-timeout"
                value={timeout}
                onChange={e => setTimeout(e.target.value)}
                min="1"
                max="3600"
              />
            </div>

          </div>

          <div className="modal-footer">
            <Button
              variant="secondary"
              onClick={handleClose}
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={isSubmitting || !isValid}
            >
              {(() => {
                    if (isSubmitting) return isEditMode ? t('agentConfigure.saving') : t('mcp.adding')
                    return isEditMode ? t('mcp.saveChanges') : t('mcp.addServer')
                })()}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
