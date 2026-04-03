import { useTranslation } from 'react-i18next'
import type { McpEntry } from '../../../../../types/mcp'
import { getMcpDisplayName } from '../../../../../types/mcp'
import Button from '../../../../platform/ui/primitives/Button'

interface McpCardProps {
  entry: McpEntry
  onToggle: (name: string, enabled: boolean) => void
  onEdit?: (entry: McpEntry) => void
  onDelete?: (name: string) => void
  isCustom?: boolean
}

export default function McpCard({ entry, onToggle, onEdit, onDelete, isCustom }: McpCardProps) {
  const { t } = useTranslation()
  const displayName = getMcpDisplayName(entry)

  const handleToggle = () => {
    onToggle(entry.name, !entry.enabled)
  }

  return (
    <div className={`mcp-card ${entry.enabled ? 'mcp-card-enabled' : ''}`}>
      <div className="mcp-card-header">
        <div className="mcp-card-title">
          <span className="mcp-card-name">{displayName}</span>
          {isCustom && <span className="mcp-card-badge">{t('mcp.customBadge')}</span>}
        </div>
        <label className="mcp-toggle">
          <input
            type="checkbox"
            checked={entry.enabled}
            onChange={handleToggle}
          />
          <span className="mcp-toggle-slider"></span>
        </label>
      </div>

      <p className="mcp-card-description">
        {entry.description || t('mcp.noDescription')}
      </p>

      {isCustom && (onEdit || onDelete) && (
        <div className="mcp-card-actions">
          {onEdit && (
            <Button
              variant="secondary"
              size="sm"
              block
              onClick={() => onEdit(entry)}
            >
              {t('common.edit')}
            </Button>
          )}
          {onDelete && (
            <Button
              variant="danger"
              tone="quiet"
              size="sm"
              block
              onClick={() => onDelete(entry.name)}
            >
              {t('common.delete')}
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
