import { useState, useCallback } from 'react'
import { GATEWAY_URL, gatewayHeaders } from '../../../../config/runtime'
import { getErrorMessage } from '../../../../utils/errorMessages'
import { useUser } from '../../../platform/providers/UserContext'

export interface MemoryFile {
    category: string
    content: string
}

interface UseMemoryResult {
    files: MemoryFile[]
    isLoading: boolean
    error: string | null
    fetchMemory: (agentId: string) => Promise<void>
    saveFile: (agentId: string, category: string, content: string) => Promise<boolean>
    deleteFile: (agentId: string, category: string) => Promise<boolean>
    createFile: (agentId: string, category: string) => Promise<boolean>
}

export function useMemory(): UseMemoryResult {
    const { userId } = useUser()
    const [files, setFiles] = useState<MemoryFile[]>([])
    const [isLoading, setIsLoading] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const fetchMemory = useCallback(async (agentId: string) => {
        setIsLoading(true)
        setError(null)
        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/memory`, {
                headers: gatewayHeaders(userId),
                signal: AbortSignal.timeout(10000),
            })
            if (!res.ok) {
                throw new Error(`HTTP ${res.status}: ${await res.text()}`)
            }
            const data = await res.json()
            setFiles(data.files || [])
        } catch (err) {
            setError(getErrorMessage(err))
        } finally {
            setIsLoading(false)
        }
    }, [userId])

    const saveFile = useCallback(async (agentId: string, category: string, content: string): Promise<boolean> => {
        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/memory/${category}`, {
                method: 'PUT',
                headers: gatewayHeaders(userId),
                body: JSON.stringify({ content }),
                signal: AbortSignal.timeout(10000),
            })
            const data = await res.json()
            if (!res.ok || !data.success) {
                setError(data.error || 'Failed to save memory file')
                return false
            }
            // Update local state
            setFiles(prev => {
                const idx = prev.findIndex(f => f.category === category)
                if (idx >= 0) {
                    const updated = [...prev]
                    updated[idx] = { category, content }
                    return updated
                }
                return [...prev, { category, content }]
            })
            return true
        } catch (err) {
            setError(getErrorMessage(err))
            return false
        }
    }, [userId])

    const deleteFile = useCallback(async (agentId: string, category: string): Promise<boolean> => {
        try {
            const res = await fetch(`${GATEWAY_URL}/agents/${agentId}/memory/${category}`, {
                method: 'DELETE',
                headers: gatewayHeaders(userId),
                signal: AbortSignal.timeout(10000),
            })
            const data = await res.json()
            if (!res.ok || !data.success) {
                setError(data.error || 'Failed to delete memory file')
                return false
            }
            setFiles(prev => prev.filter(f => f.category !== category))
            return true
        } catch (err) {
            setError(getErrorMessage(err))
            return false
        }
    }, [userId])

    const createFile = useCallback(async (agentId: string, category: string): Promise<boolean> => {
        return saveFile(agentId, category, '')
    }, [saveFile])

    return { files, isLoading, error, fetchMemory, saveFile, deleteFile, createFile }
}
