// System prompt template types (goosed /config/prompts API)

export interface SystemPromptTemplate {
    name: string
    description: string
    default_content: string
    user_content: string | null
    is_customized: boolean
}

export interface SystemPromptContent {
    name: string
    content: string
    default_content: string
    is_customized: boolean
}
