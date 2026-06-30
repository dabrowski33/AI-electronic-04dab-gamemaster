import React, { useState } from 'react';
import Header from './components/Header';
import Dashboard from './components/Dashboard';
import NewExperimentForm from './components/NewExperimentForm';
import ExperimentDetail from './components/ExperimentDetail';

type View = 'dashboard' | 'new-experiment' | 'experiment-detail';

const App: React.FC = () => {
  const [activeView, setActiveView] = useState<View>('dashboard');
  const [selectedExperimentId, setSelectedExperimentId] = useState<string | null>(null);
  // Incrementing this key causes Dashboard to re-fetch its experiment list
  const [dashboardRefreshKey, setDashboardRefreshKey] = useState(0);

  const navigateTo = (view: 'dashboard' | 'new-experiment') => {
    setActiveView(view);
    setSelectedExperimentId(null);
  };

  const handleSelectExperiment = (id: string) => {
    setSelectedExperimentId(id);
    setActiveView('experiment-detail');
  };

  const handleNewExperimentSuccess = () => {
    setDashboardRefreshKey((k) => k + 1);
    setActiveView('dashboard');
  };

  return (
    <>
      <Header activeView={activeView} onNavigate={navigateTo} />

      <main>
        {activeView === 'dashboard' && (
          <Dashboard
            onSelectExperiment={handleSelectExperiment}
            onNewExperiment={() => setActiveView('new-experiment')}
            refreshKey={dashboardRefreshKey}
          />
        )}

        {activeView === 'new-experiment' && (
          <NewExperimentForm
            onSuccess={handleNewExperimentSuccess}
            onCancel={() => setActiveView('dashboard')}
          />
        )}

        {activeView === 'experiment-detail' && selectedExperimentId && (
          <ExperimentDetail
            experimentId={selectedExperimentId}
            onBack={() => setActiveView('dashboard')}
          />
        )}
      </main>
    </>
  );
};

export default App;
