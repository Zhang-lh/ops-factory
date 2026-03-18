import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAgentConfig } from '../hooks/useAgentConfig'
import { useToast } from '../contexts/ToastContext'
import { McpSection } from '../components/mcp'
import { SkillSection } from '../components/skill'
import { PromptsSection } from '../components/prompt'
import { MemorySection } from '../components/memory'
import CapabilityMarketPanel from '../components/market/CapabilityMarketPanel'

type ConfigTab = 'overview' | 'prompts' | 'mcp' | 'skills' | 'memory'

export default function AgentConfigure() {
    const { t } = useTranslation()
    const { agentId } = useParams<{ agentId: string }>()
    const navigate = useNavigate()
    const { config, isLoading, error, fetchConfig, updateConfig } = useAgentConfig()
    const { showToast } = useToast()

    // Tab state
    const [activeTab, setActiveTab] = useState<ConfigTab>('overview')

    // Market State
    const [isMarketOpen, setIsMarketOpen] = useState(false)
    const [marketActiveTab, setMarketActiveTab] = useState<'all' | 'mcp' | 'skill'>('all')

    const handleBrowseMarket = (tab: 'all' | 'mcp' | 'skill' = 'all') => {
        setMarketActiveTab(tab)
        setIsMarketOpen(true)
    }

    // Form state
    const [agentsMd, setAgentsMd] = useState('')
    const [isSavingPrompt, setIsSavingPrompt] = useState(false)

    useEffect(() => {
        if (agentId) {
            fetchConfig(agentId)
        }
    }, [agentId, fetchConfig])

    useEffect(() => {
        if (config) {
            setAgentsMd(config.agentsMd)
        }
    }, [config])

    const handleSavePrompt = async () => {
        if (!agentId) return
        setIsSavingPrompt(true)

        const result = await updateConfig(agentId, { agentsMd })

        if (result.success) {
            showToast('success', t('agentConfigure.promptSaved'))
        } else {
            showToast('error', result.error || t('agentConfigure.promptSaveFailed'))
        }

        setIsSavingPrompt(false)
    }

    if (isLoading) {
        return (
            <div className="page-container agent-configure-page">
                <div className="agent-configure-loading">{t('agentConfigure.loadingConfig')}</div>
            </div>
        )
    }

    if (error || !config) {
        return (
            <div className="page-container agent-configure-page">
                <div className="agent-configure-error">
                    {error || t('agentConfigure.agentNotFound')}
                    <button type="button" onClick={() => navigate('/agents')}>
                        {t('agentConfigure.backToAgents')}
                    </button>
                </div>
            </div>
        )
    }

    const tabs: { key: ConfigTab; label: string }[] = [
        { key: 'overview', label: t('configTabs.overview') },
        { key: 'prompts', label: t('configTabs.prompts') },
        { key: 'mcp', label: t('configTabs.mcp') },
        { key: 'skills', label: t('configTabs.skills') },
        { key: 'memory', label: t('configTabs.memory') },
    ]

    return (
        <div style={{ display: 'flex', width: '100%', height: '100%', overflow: 'hidden', position: 'relative' }}>
            <div 
                className="agent-configure-scroll-area"
                style={{ 
                    flex: isMarketOpen ? '0 0 45%' : '1', 
                    maxWidth: isMarketOpen ? '45%' : '100%',
                    transition: 'all 0.3s ease',
                    overflowY: 'auto',
                    overflowX: 'hidden'
                }}
            >
                <div className="page-container agent-configure-page" style={{ margin: isMarketOpen ? '0' : '0 auto', maxWidth: isMarketOpen ? '100%' : '800px', transition: 'max-width 0.3s ease' }}>
                    <div className="agent-configure-header">
                <button
                    type="button"
                    className="agent-configure-back"
                    onClick={() => navigate('/agents')}
                >
                    {t('agentConfigure.backToAgents')}
                </button>
                <div className="agent-configure-title-section">
                    <h1 className="agent-configure-title">{config.name}</h1>
                    <span className="agent-configure-id">{config.id}</span>
                </div>
            </div>

            {/* Tab Navigation */}
            <div className="config-tabs">
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        type="button"
                        className={`config-tab ${activeTab === tab.key ? 'config-tab-active' : ''}`}
                        onClick={() => setActiveTab(tab.key)}
                    >
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Tab Content */}
            <div className="agent-configure-content">
                {activeTab === 'overview' && (
                    <section className="agent-configure-section">
                        <h2 className="agent-configure-section-title">{t('agentConfigure.agentPromptTitle')}</h2>
                        <p className="agent-configure-section-desc">
                            {t('agentConfigure.agentPromptDesc')}
                        </p>
                        <div className="agent-prompt-editor">
                            <textarea
                                value={agentsMd}
                                onChange={(e) => setAgentsMd(e.target.value)}
                                placeholder={t('agentConfigure.promptPlaceholder')}
                                rows={15}
                            />
                        </div>
                        <div className="agent-configure-actions">
                            <button
                                type="button"
                                className="btn btn-primary"
                                onClick={handleSavePrompt}
                                disabled={isSavingPrompt}
                            >
                                {isSavingPrompt ? t('agentConfigure.saving') : t('agentConfigure.savePrompt')}
                            </button>
                        </div>
                    </section>
                )}

                {activeTab === 'prompts' && (
                    <section className="agent-configure-section">
                        <PromptsSection agentId={agentId || null} />
                    </section>
                )}

                {activeTab === 'mcp' && (
                    <section className="agent-configure-section">
                        <McpSection agentId={agentId || null} onBrowseMarket={() => handleBrowseMarket('mcp')} />
                    </section>
                )}

                {activeTab === 'skills' && (
                    <section className="agent-configure-section">
                        <SkillSection agentId={agentId || ''} onBrowseMarket={() => handleBrowseMarket('skill')} />
                    </section>
                )}

                {activeTab === 'memory' && (
                    <section className="agent-configure-section">
                        <MemorySection agentId={agentId || null} />
                    </section>
                )}
            </div>
            </div>
            </div>

            {/* Capability Market Panel Drawer */}
            <div 
                className="market-drawer-wrapper"
                style={{
                    width: isMarketOpen ? '55%' : '0',
                    flex: isMarketOpen ? '1' : 'none',
                    backgroundColor: '#fff',
                    borderLeft: isMarketOpen ? '1px solid var(--color-border)' : 'none',
                    boxShadow: isMarketOpen ? '-10px 0 30px rgba(0,0,0,0.03)' : 'none',
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    transition: 'all 0.3s ease',
                    opacity: isMarketOpen ? 1 : 0,
                    minWidth: 0
                }}
            >
                <CapabilityMarketPanel 
                    isOpen={isMarketOpen}
                    activeTab={marketActiveTab} 
                    onClose={() => setIsMarketOpen(false)} 
                    onTabChange={setMarketActiveTab}
                />
            </div>
        </div>
    )
}
