import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import {ReactNode} from 'react';

interface ConnectedUserSheetItemTabI {
    content: ReactNode;
    label: string;
    value: string;
}

interface ConnectedUserSheetItemTabsProps {
    tabs: ConnectedUserSheetItemTabI[];
}

const tabsTriggerClassName =
    'rounded-none border-0 border-b-2 border-transparent px-3 py-2 text-sm text-content-neutral-secondary shadow-none hover:text-content-brand-primary data-[state=active]:border-stroke-brand-primary data-[state=active]:bg-transparent data-[state=active]:text-content-brand-primary data-[state=active]:shadow-none group-data-[variant=default]/tabs-list:data-[state=active]:shadow-none';

const ConnectedUserSheetItemTabs = ({tabs}: ConnectedUserSheetItemTabsProps) => (
    <Tabs className="w-full border-t border-border/50" defaultValue={tabs[0]?.value}>
        <TabsList className="h-auto w-full justify-start rounded-none border-b border-border/50 bg-transparent p-0 px-3">
            {tabs.map((tab) => (
                <TabsTrigger className={tabsTriggerClassName} key={tab.value} value={tab.value}>
                    {tab.label}
                </TabsTrigger>
            ))}
        </TabsList>

        {tabs.map((tab) => (
            <TabsContent className="px-3 pb-2" key={tab.value} value={tab.value}>
                {tab.content}
            </TabsContent>
        ))}
    </Tabs>
);

export default ConnectedUserSheetItemTabs;
