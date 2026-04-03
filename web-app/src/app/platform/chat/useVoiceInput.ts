import { useState, useRef, useCallback, useEffect } from 'react'

export type VoiceInputState = 'inactive' | 'listening'

interface SpeechRecognitionEvent {
    results: SpeechRecognitionResultList
    resultIndex: number
}

interface SpeechRecognitionErrorEvent {
    error: string
    message?: string
}

interface SpeechRecognitionInstance extends EventTarget {
    continuous: boolean
    interimResults: boolean
    lang: string
    start(): void
    stop(): void
    abort(): void
    onresult: ((event: SpeechRecognitionEvent) => void) | null
    onerror: ((event: SpeechRecognitionErrorEvent) => void) | null
    onend: (() => void) | null
    onstart: (() => void) | null
}

interface SpeechRecognitionConstructor {
    new(): SpeechRecognitionInstance
}

function getSpeechRecognition(): SpeechRecognitionConstructor | null {
    const w = window as unknown as Record<string, unknown>
    return (w.SpeechRecognition || w.webkitSpeechRecognition) as SpeechRecognitionConstructor | null
}

interface UseVoiceInputOptions {
    onTranscript: (text: string) => void
    lang?: string
}

interface UseVoiceInputReturn {
    state: VoiceInputState
    isSupported: boolean
    startListening: () => void
    stopListening: () => void
    error: string | null
}

function isCJK(code: number): boolean {
    return (code >= 0x4e00 && code <= 0x9fff) ||   // CJK Unified Ideographs
           (code >= 0x3000 && code <= 0x303f) ||   // CJK Symbols and Punctuation
           (code >= 0xff00 && code <= 0xffef)      // Fullwidth Forms
}

function isRemovableBetweenCJK(code: number): boolean {
    return code <= 0x20 ||                          // Control chars + ASCII space
           code === 0xa0 ||                         // No-Break Space
           (code >= 0x2000 && code <= 0x200f) ||   // En/Em spaces, Zero-Width chars
           code === 0x202f || code === 0x205f ||   // Narrow NBSP, Medium Math Space
           code === 0x3000 ||                       // Ideographic Space
           code === 0xfeff ||                       // BOM / ZWNBSP
           code === 0x1680 ||                       // Ogham Space
           code === 0x2028 || code === 0x2029 ||   // Line / Paragraph Separator
           code === 0x2060                          // Word Joiner
}

function cleanCJKSpaces(text: string): string {
    // Use Array.from to decompose the string into individual characters first.
    // This avoids V8 cons-string issues where charCodeAt on a += concatenated
    // string can malfunction during indexed iteration.
    const chars = Array.from(text)
    const result: string[] = []
    for (let i = 0; i < chars.length; i++) {
        const code = chars[i].charCodeAt(0)
        if (isRemovableBetweenCJK(code)) {
            const prevCode = result.length > 0 ? result[result.length - 1].charCodeAt(0) : -1
            let j = i
            while (j < chars.length && isRemovableBetweenCJK(chars[j].charCodeAt(0))) j++
            const nextCode = j < chars.length ? chars[j].charCodeAt(0) : -1
            if (isCJK(prevCode) && isCJK(nextCode)) {
                i = j - 1 // skip; for-loop will increment
            } else {
                result.push(chars[i])
            }
        } else {
            result.push(chars[i])
        }
    }
    return result.join('')
}

export function useVoiceInput({ onTranscript, lang = 'zh-CN' }: UseVoiceInputOptions): UseVoiceInputReturn {
    const [state, setState] = useState<VoiceInputState>('inactive')
    const [error, setError] = useState<string | null>(null)
    const recognitionRef = useRef<SpeechRecognitionInstance | null>(null)
    const lastTranscriptRef = useRef<string>('')
    const onTranscriptRef = useRef(onTranscript)
    onTranscriptRef.current = onTranscript

    const isSupported = !!getSpeechRecognition()

    const stopListening = useCallback(() => {
        if (recognitionRef.current) {
            // Use abort() instead of stop() — stop() asks the browser to
            // finalize results, which re-introduces CJK spaces in a new
            // onresult event. abort() stops immediately without finalization;
            // onend will re-emit the last cleaned transcript from the ref.
            recognitionRef.current.abort()
        }
    }, [])

    const startListening = useCallback(() => {
        const SpeechRecognition = getSpeechRecognition()
        if (!SpeechRecognition) {
            setError('Speech recognition not supported')
            return
        }

        // Stop any existing instance
        if (recognitionRef.current) {
            recognitionRef.current.abort()
            recognitionRef.current = null
        }

        setError(null)
        const recognition = new SpeechRecognition()
        recognition.continuous = true
        recognition.interimResults = true
        recognition.lang = lang

        recognition.onstart = () => {
            setState('listening')
        }

        recognition.onresult = (event: SpeechRecognitionEvent) => {
            let transcript = ''
            for (let i = 0; i < event.results.length; i++) {
                transcript += event.results[i][0].transcript
            }
            // Web Speech API often inserts spaces between CJK characters — strip them.
            if (lang.startsWith('zh')) {
                transcript = cleanCJKSpaces(transcript)
            }
            if (transcript) {
                lastTranscriptRef.current = transcript
                onTranscriptRef.current(transcript)
            }
        }

        recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
            if (event.error === 'no-speech') {
                // Silently ignore no-speech — user just didn't say anything
                return
            }
            if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
                setError('mic-permission-denied')
            } else if (event.error === 'aborted') {
                // User cancelled — not an error
                return
            } else {
                setError(event.error)
            }
            setState('inactive')
        }

        recognition.onend = () => {
            // Re-emit the last cleaned transcript as a safety net —
            // browser event ordering between onresult/onend varies,
            // and the finalized transcript may reintroduce CJK spaces.
            if (lastTranscriptRef.current) {
                onTranscriptRef.current(lastTranscriptRef.current)
            }
            lastTranscriptRef.current = ''
            setState('inactive')
            recognitionRef.current = null
        }

        recognitionRef.current = recognition

        try {
            recognition.start()
        } catch {
            setError('Failed to start speech recognition')
            setState('inactive')
        }
    }, [lang])

    // Cleanup on unmount
    useEffect(() => {
        return () => {
            if (recognitionRef.current) {
                recognitionRef.current.abort()
                recognitionRef.current = null
            }
        }
    }, [])

    return { state, isSupported, startListening, stopListening, error }
}
