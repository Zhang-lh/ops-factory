import { createContext, useContext, useState, useCallback, type ReactNode } from 'react'

interface SidebarContextValue {
    isCollapsed: boolean
    toggleSidebar: () => void
    setCollapsed: (collapsed: boolean) => void
}

const SidebarContext = createContext<SidebarContextValue | null>(null)

const STORAGE_KEY = 'sidebar-collapsed'

export function SidebarProvider({ children }: { children: ReactNode }) {
    const [isCollapsed, setIsCollapsed] = useState(() => {
        try {
            return localStorage.getItem(STORAGE_KEY) === 'true'
        } catch {
            return false
        }
    })

    const setCollapsed = useCallback((collapsed: boolean) => {
        setIsCollapsed(collapsed)
        try {
            localStorage.setItem(STORAGE_KEY, String(collapsed))
        } catch { /* ignore */ }
    }, [])

    const toggleSidebar = useCallback(() => {
        setCollapsed(!isCollapsed)
    }, [isCollapsed, setCollapsed])

    return (
        <SidebarContext.Provider value={{ isCollapsed, toggleSidebar, setCollapsed }}>
            {children}
        </SidebarContext.Provider>
    )
}

export function useSidebar() {
    const ctx = useContext(SidebarContext)
    if (!ctx) {
        throw new Error('useSidebar must be used within a SidebarProvider')
    }
    return ctx
}
