import type { ScreenVersion, ScreenVersionDiff } from '../../../../api/analyticsApi';
import type { ScreenConfig, ScreenGlobalVariable } from '../../types';
import { GlobalVariableManager } from '../GlobalVariableManager';
import { LinkageGraphPanel } from '../LinkageGraphPanel';
import { PublishResultModal } from '../PublishResultModal';
import { ScreenConflictPanel, type ScreenUpdateConflict } from '../ScreenConflictPanel';
import { ScreenVersionComparePanel } from '../ScreenVersionComparePanel';
import { ScreenVersionComparePickerPanel } from '../ScreenVersionComparePickerPanel';
import { ScreenVersionRollbackPanel } from '../ScreenVersionRollbackPanel';
import { VersionHistoryPanel } from '../VersionHistoryPanel';
import type { PublishInfo } from './helpers';

interface ScreenHeaderPanelsProps {
    config: ScreenConfig;
    selectedIds: string[];
    cycleWarnings: string[];
    showVariableManager: boolean;
    onCloseVariableManager: () => void;
    onChangeVariables: (next: ScreenGlobalVariable[]) => void;
    publishModalOpen: boolean;
    onClosePublishModal: () => void;
    publishInfo: PublishInfo | null;
    isOwner: boolean;
    showConflictPanel: boolean;
    lastConflict: ScreenUpdateConflict | null;
    conflictLoading: boolean;
    onCloseConflictPanel: () => void;
    onReloadLatestDraft: () => void | Promise<void>;
    onSelectConflictComponents: (ids: string[]) => void;
    showVersionComparePanel: boolean;
    versionDiff: ScreenVersionDiff | null;
    onCloseVersionComparePanel: () => void;
    showVersionComparePicker: boolean;
    versionCandidates: ScreenVersion[];
    isLoadingVersions: boolean;
    onCloseVersionComparePicker: () => void;
    onConfirmVersionCompare: (fromVersionId: string, toVersionId: string) => void | Promise<void>;
    showVersionRollbackPanel: boolean;
    onCloseVersionRollbackPanel: () => void;
    onConfirmVersionRollback: (versionId: string) => void | Promise<void>;
    showVersionHistoryPanel: boolean;
    onCloseVersionHistoryPanel: () => void;
    showLinkageGraph: boolean;
    onCloseLinkageGraph: () => void;
}

export function ScreenHeaderPanels({
    config,
    selectedIds,
    cycleWarnings,
    showVariableManager,
    onCloseVariableManager,
    onChangeVariables,
    publishModalOpen,
    onClosePublishModal,
    publishInfo,
    isOwner,
    showConflictPanel,
    lastConflict,
    conflictLoading,
    onCloseConflictPanel,
    onReloadLatestDraft,
    onSelectConflictComponents,
    showVersionComparePanel,
    versionDiff,
    onCloseVersionComparePanel,
    showVersionComparePicker,
    versionCandidates,
    isLoadingVersions,
    onCloseVersionComparePicker,
    onConfirmVersionCompare,
    showVersionRollbackPanel,
    onCloseVersionRollbackPanel,
    onConfirmVersionRollback,
    showVersionHistoryPanel,
    onCloseVersionHistoryPanel,
    showLinkageGraph,
    onCloseLinkageGraph,
}: ScreenHeaderPanelsProps) {
    return (
        <>
            <GlobalVariableManager
                open={showVariableManager}
                variables={config.globalVariables ?? []}
                cycleWarnings={cycleWarnings}
                onClose={onCloseVariableManager}
                onChange={onChangeVariables}
            />

            <PublishResultModal
                open={publishModalOpen}
                onClose={onClosePublishModal}
                publishInfo={publishInfo}
                isOwner={isOwner}
            />

            <ScreenConflictPanel
                open={showConflictPanel}
                conflict={lastConflict}
                loading={conflictLoading}
                onClose={onCloseConflictPanel}
                onReloadLatest={onReloadLatestDraft}
                onSelectConflictComponents={onSelectConflictComponents}
            />

            <ScreenVersionComparePanel
                open={showVersionComparePanel}
                diff={versionDiff}
                onClose={onCloseVersionComparePanel}
            />

            <ScreenVersionComparePickerPanel
                open={showVersionComparePicker}
                versions={versionCandidates}
                loading={isLoadingVersions}
                onClose={onCloseVersionComparePicker}
                onCompare={onConfirmVersionCompare}
            />

            <ScreenVersionRollbackPanel
                open={showVersionRollbackPanel}
                versions={versionCandidates}
                loading={isLoadingVersions}
                onClose={onCloseVersionRollbackPanel}
                onRollback={onConfirmVersionRollback}
            />

            <VersionHistoryPanel
                open={showVersionHistoryPanel}
                versions={versionCandidates}
                currentConfig={config}
                loading={isLoadingVersions}
                onClose={onCloseVersionHistoryPanel}
                onRollback={onConfirmVersionRollback}
                onCompare={onConfirmVersionCompare}
            />

            {showLinkageGraph ? (
                <LinkageGraphPanel
                    config={config}
                    selectedIds={selectedIds}
                    onClose={onCloseLinkageGraph}
                />
            ) : null}
        </>
    );
}
