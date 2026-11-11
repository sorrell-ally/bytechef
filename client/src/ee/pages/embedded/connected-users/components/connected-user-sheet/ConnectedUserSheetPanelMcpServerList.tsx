import Button from '@/components/Button/Button';
import ConnectedUserSheetEmptyState from '@/ee/pages/embedded/connected-users/components/connected-user-sheet/ConnectedUserSheetEmptyState';
import ConnectedUserMcpServerListItem from '@/ee/pages/embedded/connected-users/components/connected-user-sheet/connected-user-mcp-server-list/ConnectedUserMcpServerListItem';
import ProvisionMcpServerDialog from '@/ee/pages/embedded/connected-users/components/connected-user-sheet/provision-mcp-server-dialog/ProvisionMcpServerDialog';
import {ConnectedUserIntegrationInstance} from '@/ee/shared/middleware/embedded/connected-user';
import {
    useConnectedUserMcpServersQuery,
    useProvisionableConnectedUserMcpServersQuery,
} from '@/shared/middleware/graphql';
import {PlusIcon, ServerIcon} from 'lucide-react';
import {useState} from 'react';

const ConnectedUserSheetPanelMcpServerList = ({
    connectedUserId,
    connectedUserIntegrationInstances,
}: {
    connectedUserId: number;
    connectedUserIntegrationInstances: ConnectedUserIntegrationInstance[];
}) => {
    const [showProvisionDialog, setShowProvisionDialog] = useState(false);

    const {data, isLoading} = useConnectedUserMcpServersQuery({
        connectedUserId: connectedUserId.toString(),
    });

    const {data: provisionableData} = useProvisionableConnectedUserMcpServersQuery({
        connectedUserId: connectedUserId.toString(),
    });

    if (isLoading) {
        return <div className="py-4 text-sm text-muted-foreground">Loading...</div>;
    }

    const mcpServers = data?.connectedUserMcpServers ?? [];

    return (
        <div>
            {mcpServers.length > 0 ? (
                <>
                    <div className="flex items-center justify-between pb-2">
                        <h3 className="text-sm font-semibold">MCP Servers</h3>

                        <Button
                            icon={<PlusIcon />}
                            label="Provision MCP Server"
                            onClick={() => setShowProvisionDialog(true)}
                            size="sm"
                            variant="secondary"
                        />
                    </div>

                    <div>
                        {mcpServers.map((mcpServer) => (
                            <ConnectedUserMcpServerListItem
                                connectedUserId={connectedUserId}
                                connectedUserIntegrationInstances={connectedUserIntegrationInstances}
                                key={mcpServer.id}
                                mcpServer={mcpServer}
                            />
                        ))}
                    </div>
                </>
            ) : (
                <ConnectedUserSheetEmptyState
                    description="An MCP server lets AI assistants, like Claude or Cursor, call tools on this user's integrations."
                    icon={<ServerIcon />}
                    nothingToProvision={provisionableData?.provisionableConnectedUserMcpServers.length === 0}
                    nothingToProvisionDescription="No MCP servers are defined yet. Create one first, then provision it for this user."
                    onProvision={() => setShowProvisionDialog(true)}
                    pageLink={{label: 'Go to MCP Servers', to: '/embedded/mcp-servers'}}
                    provisionLabel="Provision MCP Server"
                    title="No MCP servers yet"
                />
            )}

            {showProvisionDialog && (
                <ProvisionMcpServerDialog
                    connectedUserId={connectedUserId}
                    onClose={() => setShowProvisionDialog(false)}
                />
            )}
        </div>
    );
};

export default ConnectedUserSheetPanelMcpServerList;
