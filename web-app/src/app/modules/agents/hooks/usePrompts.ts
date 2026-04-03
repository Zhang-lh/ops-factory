import { useState, useCallback } from 'react'
import { useGoosed } from '../../../platform/providers/GoosedContext'
import type { SystemPromptTemplate, SystemPromptContent } from '../../../../types/systemPrompt'

interface UsePromptsResult {
    templates: SystemPromptTemplate[]
    isLoading: boolean
    error: string | null
    fetchPrompts: () => Promise<void>
    getPrompt: (name: string) => Promise<SystemPromptContent | null>
    savePrompt: (name: string, content: string) => Promise<boolean>
    resetPrompt: (name: string) => Promise<boolean>
    resetAllPrompts: () => Promise<boolean>
}

export function usePrompts(agentId: string | null): UsePromptsResult {
    const { getClient } = useGoosed()
    const [templates, setTemplates] = useState<SystemPromptTemplate[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchPrompts = useCallback(async () => {
        if (!agentId) return
        setIsLoading(true)
        setError(null)

        try {
            const client = getClient(agentId)
            const prompts = await client.listPrompts()
            setTemplates(prompts)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch prompts')
        } finally {
            setIsLoading(false)
        }
    }, [agentId, getClient])

    const getPrompt = useCallback(async (name: string): Promise<SystemPromptContent | null> => {
        if (!agentId) return null
        setError(null)

        try {
            const client = getClient(agentId)
            return await client.getPrompt(name)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to fetch prompt')
            return null
        }
    }, [agentId, getClient])

    const savePrompt = useCallback(async (name: string, content: string): Promise<boolean> => {
        if (!agentId) return false
        setError(null)

        try {
            const client = getClient(agentId)
            await client.savePrompt(name, content)
            // Update local state
            setTemplates(prev =>
                prev.map(t =>
                    t.name === name ? { ...t, is_customized: true, user_content: content } : t
                )
            )
            return true
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to save prompt')
            return false
        }
    }, [agentId, getClient])

    const resetPrompt = useCallback(async (name: string): Promise<boolean> => {
        if (!agentId) return false
        setError(null)

        try {
            const client = getClient(agentId)
            await client.resetPrompt(name)
            // Update local state
            setTemplates(prev =>
                prev.map(t =>
                    t.name === name ? { ...t, is_customized: false, user_content: null } : t
                )
            )
            return true
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to reset prompt')
            return false
        }
    }, [agentId, getClient])

    const resetAllPrompts = useCallback(async (): Promise<boolean> => {
        if (!agentId) return false
        setError(null)

        try {
            const client = getClient(agentId)
            const customized = templates.filter(t => t.is_customized)
            await Promise.all(customized.map(t => client.resetPrompt(t.name)))
            // Update local state
            setTemplates(prev =>
                prev.map(t => ({ ...t, is_customized: false, user_content: null }))
            )
            return true
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to reset all prompts')
            return false
        }
    }, [agentId, getClient, templates])

    return {
        templates,
        isLoading,
        error,
        fetchPrompts,
        getPrompt,
        savePrompt,
        resetPrompt,
        resetAllPrompts,
    }
}
