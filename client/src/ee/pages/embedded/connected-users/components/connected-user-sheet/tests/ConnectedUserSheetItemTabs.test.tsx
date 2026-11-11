import ConnectedUserSheetItemTabs from '@/ee/pages/embedded/connected-users/components/connected-user-sheet/ConnectedUserSheetItemTabs';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it} from 'vitest';

describe('ConnectedUserSheetItemTabs', () => {
    it('shows the first tab and switches to another on click', async () => {
        render(
            <ConnectedUserSheetItemTabs
                tabs={[
                    {content: <div>workflow list</div>, label: 'Workflows', value: 'workflows'},
                    {content: <div>call list</div>, label: 'Actions', value: 'actions'},
                ]}
            />
        );

        expect(screen.getByRole('tab', {name: 'Workflows', selected: true})).toBeInTheDocument();
        expect(screen.getByText('workflow list')).toBeInTheDocument();
        expect(screen.queryByText('call list')).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('tab', {name: 'Actions'}));

        expect(screen.getByText('call list')).toBeInTheDocument();
    });
});
