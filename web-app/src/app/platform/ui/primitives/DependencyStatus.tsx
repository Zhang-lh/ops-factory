import type { ReactNode, SVGProps } from 'react'

type DependencyStatusTone = 'info' | 'warning' | 'error'

interface DependencyStatusProps {
    dependency: string
    title: string
    description: string
    tone?: DependencyStatusTone
    meta?: ReactNode
    action?: ReactNode
    className?: string
}

function StatusIcon({ tone, ...props }: SVGProps<SVGSVGElement> & { tone: DependencyStatusTone }) {
    if (tone === 'error') {
        return (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true" {...props}>
                <circle cx="12" cy="12" r="9" />
                <path d="M15 9l-6 6M9 9l6 6" />
            </svg>
        )
    }

    if (tone === 'warning') {
        return (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true" {...props}>
                <path d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126z" />
                <path d="M12 15.75h.007v.008H12v-.008z" />
            </svg>
        )
    }

    return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true" {...props}>
            <circle cx="12" cy="12" r="9" />
            <path d="M12 8v4" />
            <path d="M12 16h.01" />
        </svg>
    )
}

export default function DependencyStatus({
    dependency,
    title,
    description,
    tone = 'info',
    meta,
    action,
    className,
}: DependencyStatusProps) {
    const classes = ['dependency-status', `dependency-status-${tone}`, className].filter(Boolean).join(' ')

    return (
        <section className={classes}>
            <div className="dependency-status-icon">
                <StatusIcon tone={tone} width="18" height="18" />
            </div>
            <div className="dependency-status-body">
                <div className="dependency-status-header">
                    <div className="dependency-status-copy">
                        <span className="dependency-status-pill">{dependency}</span>
                        <h3 className="dependency-status-title">{title}</h3>
                    </div>
                    {action ? <div className="dependency-status-action">{action}</div> : null}
                </div>
                <p className="dependency-status-description">{description}</p>
                {meta ? <div className="dependency-status-meta">{meta}</div> : null}
            </div>
        </section>
    )
}
