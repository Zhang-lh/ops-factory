import { useCallback, useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAgentConfig } from '../hooks/useAgentConfig'
import { useToast } from '../../../platform/providers/ToastContext'
import { McpSection } from '../components/mcp'
import { BasicInfoSection } from '../components/info'
import { ModelConfigSection } from '../components/model'
import { SkillMarketDrawer, SkillSection } from '../components/skill'
import { PromptsSection } from '../components/prompt'
import { MemorySection } from '../components/memory'
import SchedulesPanel from '../../../platform/scheduler/SchedulesPanel'
import Button from '../../../platform/ui/primitives/Button'
import PageBackLink from '../../../platform/ui/primitives/PageBackLink'
import { useGoosed } from '../../../platform/providers/GoosedContext'
import type { AgentModelConfig, CreateProviderRequest, UpdateProviderRequest } from '../../../../types/agentConfig'
import type { SkillEntry } from '../../../../types/skill'
import '../styles/agents.css'

/**
 * Map backend English error messages for provider operations to localized i18n keys.
 * Backend returns English-only messages (e.g. "Provider 'xxx' already exists"),
 * so we pattern-match them here and return localized text.
 */
function localizeProviderError(backendError: string, t: (key: string, params?: Record<string, unknown>) => string): string {
    if (/Provider '.+?' already exists/i.test(backendError)) {
        return t('agentConfigure.providerDuplicateName')
    }
    const fieldLengthMatch = backendError.match(/^(Provider name|Display name|Base URL|API key|Model name|Description) must not exceed (\d+) characters$/i)
    if (fieldLengthMatch) {
        return t('agentConfigure.providerFieldTooLong', { field: fieldLengthMatch[1], max: fieldLengthMatch[2] })
    }
    return t('agentConfigure.providerCreateFailed', { error: backendError })
}

/**
 * Localize an array of backend error messages, one per validation rule.
 */
function localizeProviderErrors(errors: string[], t: (key: string, params?: Record<string, unknown>) => string): string {
    return errors.map(err => localizeProviderError(err, t)).join('\n')
}

type ConfigTab = 'basic' | 'model' | 'prompts' | 'mcp' | 'skills' | 'memory' | 'schedules'

export default function AgentConfigure() {
    const { t } = useTranslation()
    const { agentId } = useParams<{ agentId: string }>()
    const navigate = useNavigate()
    const {
        config, isLoading, error, fetchConfig, updateConfig, updateModelConfig, createProvider, updateProvider,
        restartInstances,
    } = useAgentConfig()
    const { showToast } = useToast()
    const { refreshAgents } = useGoosed()

    // Tab state
    const [activeTab, setActiveTab] = useState<ConfigTab>('basic')
    const [isSkillMarketOpen, setIsSkillMarketOpen] = useState(false)
    const [skillRefreshKey, setSkillRefreshKey] = useState(0)
    const [installedSkills, setInstalledSkills] = useState<SkillEntry[]>([])

    // Saved model/provider config only reaches goosed as spawn-time env vars, so running instances
    // need a restart before it takes effect. Holds the running-instance count reported by the save.
    const [restartNotice, setRestartNotice] = useState<number | null>(null)
    const [isRestarting, setIsRestarting] = useState(false)

    // Form state
    const [agentsMd, setAgentsMd] = useState('')
    const [isSavingPrompt, setIsSavingPrompt] = useState(false)

    const handleSkillsLoaded = useCallback((skills: SkillEntry[]) => {
        setInstalledSkills(skills)
    }, [])

    const handleSkillInstalled = useCallback(() => {
        setSkillRefreshKey(value => value + 1)
        void refreshAgents()
    }, [refreshAgents])

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

    useEffect(() => {
        if (activeTab !== 'skills') {
            setIsSkillMarketOpen(false)
        }
    }, [activeTab])

    const handleSavePrompt = async (nextAgentsMd?: string) => {
        if (!agentId) return false
        setIsSavingPrompt(true)

        const content = nextAgentsMd ?? agentsMd
        const result = await updateConfig(agentId, { agentsMd: content })

        if (result.success) {
            setAgentsMd(content)
            showToast('success', t('agentConfigure.promptSaved'))
            setIsSavingPrompt(false)
            return true
        } else {
            showToast('error', result.error || t('agentConfigure.promptSaveFailed'))
        }

        setIsSavingPrompt(false)
        return false
    }

    const applyRestartNotice = (result: { requiresRestart?: boolean; runningInstances?: number }) => {
        if (result.requiresRestart) {
            setRestartNotice(result.runningInstances ?? 0)
        }
    }

    const handleSaveModelConfig = async (updates: AgentModelConfig) => {
        if (!agentId) return false
        const result = await updateModelConfig(agentId, updates)
        if (result.success) {
            showToast('success', t('agentConfigure.modelConfigSaved'))
            applyRestartNotice(result)
            await fetchConfig(agentId)
            await refreshAgents()
            return true
        }
        showToast('error', result.error || t('agentConfigure.modelConfigSaveFailed'))
        return false
    }

    const handleCreateProvider = async (provider: CreateProviderRequest) => {
        if (!agentId) return false
        const result = await createProvider(agentId, provider)
        if (result.success) {
            showToast('success', t('agentConfigure.providerCreated'))
            await fetchConfig(agentId)
            return true
        }
        showToast('error', result.error || t('agentConfigure.providerCreateFailed'))
        return false
    }

    const handleUpdateProvider = async (providerName: string, provider: UpdateProviderRequest) => {
        if (!agentId) return false
        const result = await updateProvider(agentId, providerName, provider)
        if (result.success) {
            showToast('success', t('agentConfigure.providerUpdated'))
            applyRestartNotice(result)
            await fetchConfig(agentId)
            return true
        }
        showToast('error', result.error || t('agentConfigure.providerUpdateFailed'))
        return false
    }

    const handleRestartInstances = async () => {
        if (!agentId) return
        setIsRestarting(true)
        const result = await restartInstances(agentId)
        setIsRestarting(false)
        if (result.success) {
            showToast('success', t('agentConfigure.restartDone', { count: result.stoppedInstances ?? 0 }))
            setRestartNotice(null)
        } else {
            showToast('error', result.error || t('agentConfigure.restartFailed'))
        }
    }

    if (isLoading) {
        return (
            <div className="page-container sidebar-top-page agent-configure-page">
                <div className="agent-configure-loading">{t('agentConfigure.loadingConfig')}</div>
            </div>
        )
    }

    if (error || !config) {
        return (
            <div className="page-container sidebar-top-page agent-configure-page">
                <div className="agent-configure-error">
                    {error || t('agentConfigure.agentNotFound')}
                    <button type="button" onClick={() => navigate('/agents')}>
                        {t('agentConfigure.backToAgents')}
                    </button>
                </div>
            </div>
        )
    }

    // Agent-scoped tabs (apply to everyone) and user-scoped tabs (only affect the current user,
    // e.g. memory). They are split by a divider in the tab bar — mirroring how the sidebar separates
    // nav groups — so scope reads at a glance without inventing new per-tab chrome.
    const agentTabs: { key: ConfigTab; label: string }[] = [
        { key: 'basic', label: t('configTabs.basic') },
        { key: 'model', label: t('configTabs.model') },
        { key: 'prompts', label: t('configTabs.prompts') },
        { key: 'mcp', label: t('configTabs.mcp') },
        { key: 'skills', label: t('configTabs.skills') },
    ]
    const mineTabs: { key: ConfigTab; label: string }[] = [
        { key: 'memory', label: t('configTabs.memory') },
        // { key: 'schedules', label: t('configTabs.schedules') }, // 屏蔽定时任务页签
    ]

    const renderTab = (tab: { key: ConfigTab; label: string }) => (
        <button
            key={tab.key}
            type="button"
            className={`config-tab ${activeTab === tab.key ? 'config-tab-active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
        >
            {tab.label}
        </button>
    )

    return (
        <div className={`agent-configure-workspace ${isSkillMarketOpen ? 'agent-configure-workspace-with-drawer' : ''}`}>
            <div className="agent-configure-scroll-area">
                <div className="page-container sidebar-top-page agent-configure-page">
                    <div className="agent-configure-header">
                        <PageBackLink onClick={() => navigate('/agents')}>
                            {t('agentConfigure.backToAgents')}
                        </PageBackLink>
                        <div className="agent-configure-title-section">
                            <h1 className="agent-configure-title">{config.name}</h1>
                            <span className="agent-configure-id">{config.id}</span>
                        </div>
                    </div>

                    {/* Tab Navigation — agent-scoped tabs, a divider, then user-scoped tabs (memory) */}
                    <div className="config-tabs">
                        {agentTabs.map(renderTab)}
                        <span className="config-tab-divider" aria-hidden="true" />
                        {mineTabs.map(renderTab)}
                    </div>

                    {restartNotice !== null && (
                        <div className="conn-banner conn-banner-warning agent-restart-banner">
                            <span className="agent-restart-banner-text">
                                {restartNotice > 0
                                    ? t('agentConfigure.restartRequired', { count: restartNotice })
                                    : t('agentConfigure.restartNextStart')}
                            </span>
                            <span className="agent-restart-banner-actions">
                                {restartNotice > 0 && (
                                    <Button variant="secondary" size="sm" onClick={handleRestartInstances} disabled={isRestarting}>
                                        {isRestarting ? t('agentConfigure.restarting') : t('agentConfigure.restartNow')}
                                    </Button>
                                )}
                                <Button variant="ghost" size="sm" onClick={() => setRestartNotice(null)} disabled={isRestarting}>
                                    {t('common.close')}
                                </Button>
                            </span>
                        </div>
                    )}

                    {/* Tab Content */}
                    <div className="agent-configure-content">
                        {activeTab === 'basic' && (
                            <BasicInfoSection config={config} />
                        )}

                        {activeTab === 'model' && (
                            <ModelConfigSection
                                config={config}
                                onSave={handleSaveModelConfig}
                                onCreateProvider={handleCreateProvider}
                                onUpdateProvider={handleUpdateProvider}
                            />
                        )}

                        {activeTab === 'prompts' && (
                            <section className="agent-configure-section">
                                <PromptsSection
                                    agentId={agentId || null}
                                    agentsMd={agentsMd}
                                    isSavingPrompt={isSavingPrompt}
                                    onSavePrompt={handleSavePrompt}
                                />
                            </section>
                        )}

                        {activeTab === 'mcp' && (
                            <section className="agent-configure-section">
                                <McpSection agentId={agentId || null} />
                            </section>
                        )}

                        {activeTab === 'skills' && (
                            <section className="agent-configure-section">
                                <SkillSection
                                    agentId={agentId || ''}
                                    onBrowseMarket={() => setIsSkillMarketOpen(true)}
                                    refreshKey={skillRefreshKey}
                                    onSkillsLoaded={handleSkillsLoaded}
                                />
                            </section>
                        )}

                        {activeTab === 'memory' && (
                            <section className="agent-configure-section">
                                <MemorySection agentId={agentId || null} />
                            </section>
                        )}

                        {activeTab === 'schedules' && agentId && (
                            <section className="agent-configure-section">
                                <SchedulesPanel agentId={agentId} embedded />
                            </section>
                        )}
                    </div>
                </div>
            </div>
            <SkillMarketDrawer
                isOpen={isSkillMarketOpen}
                agentId={agentId || ''}
                agentName={config.name}
                installedSkills={installedSkills}
                onClose={() => setIsSkillMarketOpen(false)}
                onInstalled={handleSkillInstalled}
            />
        </div>
    )
}
