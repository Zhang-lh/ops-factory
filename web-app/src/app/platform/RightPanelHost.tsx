import FilePreview from './preview/FilePreview'
import CapabilityMarketPanel from './panels/CapabilityMarketPanel'
import { useRightPanel } from './providers/RightPanelContext'
import { usePreview } from './providers/PreviewContext'

export function RightPanelHost() {
    const { previewFile } = usePreview()
    const { isMarketOpen, marketActiveTab, closeMarket, setMarketActiveTab } = useRightPanel()
    const isPreviewOpen = !!previewFile
    const isRightPanelOpen = isMarketOpen || isPreviewOpen

    return (
        <div className={`right-panel-host ${isRightPanelOpen ? 'open' : ''} ${isMarketOpen ? 'drawer' : isPreviewOpen ? 'preview' : ''}`}>
            {isMarketOpen ? (
                <CapabilityMarketPanel
                    isOpen={isMarketOpen}
                    activeTab={marketActiveTab}
                    onClose={closeMarket}
                    onTabChange={setMarketActiveTab}
                />
            ) : (
                <FilePreview embedded />
            )}
        </div>
    )
}
