import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { UserProvider, useUser } from '../app/platform/providers/UserContext'

const STORAGE_KEY = 'opsfactory:userId'

// Helper to render UserProvider and expose its internal state
function UserDisplay() {
    const { userId } = useUser()
    return <div data-testid="user-id">{userId ?? 'none'}</div>
}

function renderWithProviders(initialPath: string) {
    // Set window.location.search to simulate URL params
    const url = new URL(`http://localhost${initialPath}`)
    Object.defineProperty(window, 'location', {
        value: { ...window.location, search: url.search },
        writable: true,
    })

    return render(
        <MemoryRouter initialEntries={[initialPath]}>
            <UserProvider>
                <UserDisplay />
            </UserProvider>
        </MemoryRouter>
    )
}

describe('Embed mode — URL param auth', () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    it('reads uid from URL param and sets it in context', () => {
        renderWithProviders('/chat?embed=true&uid=embed-alice')
        expect(screen.getByTestId('user-id')).toHaveTextContent('embed-alice')
    })

    it('persists uid from URL to localStorage', () => {
        renderWithProviders('/chat?embed=true&uid=embed-bob')
        expect(localStorage.getItem(STORAGE_KEY)).toBe('embed-bob')
    })

    it('falls back to localStorage when no URL userId', () => {
        localStorage.setItem(STORAGE_KEY, 'stored-user')
        renderWithProviders('/chat')
        expect(screen.getByTestId('user-id')).toHaveTextContent('stored-user')
    })

    it('URL uid overrides localStorage value', () => {
        localStorage.setItem(STORAGE_KEY, 'old-user')
        renderWithProviders('/chat?embed=true&uid=new-user')
        expect(screen.getByTestId('user-id')).toHaveTextContent('new-user')
        expect(localStorage.getItem(STORAGE_KEY)).toBe('new-user')
    })

    it('falls back to admin when no uid or localStorage exists', () => {
        renderWithProviders('/chat')
        expect(screen.getByTestId('user-id')).toHaveTextContent('admin')
    })
})
