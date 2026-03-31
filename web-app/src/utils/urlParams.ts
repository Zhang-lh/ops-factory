export function getUrlParams(): URLSearchParams {
    const params = new URLSearchParams(window.location.search)
    const hash = window.location.hash
    const queryIndex = hash.indexOf('?')

    if (queryIndex >= 0) {
        const hashParams = new URLSearchParams(hash.slice(queryIndex + 1))
        for (const [key, value] of hashParams.entries()) {
            if (!params.has(key)) {
                params.set(key, value)
            }
        }
    }

    return params
}

export function getUrlParam(name: string): string | null {
    return getUrlParams().get(name)
}
