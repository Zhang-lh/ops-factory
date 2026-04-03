import React from 'react'
import ReactDOM from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import App from './App'
import { UserProvider } from './app/platform/providers/UserContext'
import { GoosedProvider } from './app/platform/providers/GoosedContext'
import { ToastProvider } from './app/platform/providers/ToastContext'
import ErrorBoundary from './app/platform/runtime/ErrorBoundary'
import { initializeRuntimeConfig } from './config/runtime'
import './i18n'
import './App.css'
import './app/platform/styles/UIPrimitives.css'
import './app/platform/styles/SharedStates.css'
import './app/platform/styles/SegmentedFilter.css'

const root = ReactDOM.createRoot(document.getElementById('root')!)

async function bootstrap() {
    try {
        await initializeRuntimeConfig()
    } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        console.error('Failed to initialize runtime config:', error)
        root.render(
            <React.StrictMode>
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    minHeight: '100vh',
                    padding: '2rem',
                    textAlign: 'center',
                    color: 'var(--color-text-primary, #32353b)',
                }}
                >
                    <div>
                        <h1 style={{ fontSize: '1.5rem', marginBottom: '0.75rem' }}>Failed to initialize app</h1>
                        <p style={{ color: 'var(--color-text-secondary, #606c7a)' }}>{message}</p>
                    </div>
                </div>
            </React.StrictMode>,
        )
        return
    }

    root.render(
        <React.StrictMode>
            <ErrorBoundary>
                <HashRouter>
                    <ToastProvider>
                        <UserProvider>
                            <GoosedProvider>
                                <App />
                            </GoosedProvider>
                        </UserProvider>
                    </ToastProvider>
                </HashRouter>
            </ErrorBoundary>
        </React.StrictMode>,
    )
}

void bootstrap()
