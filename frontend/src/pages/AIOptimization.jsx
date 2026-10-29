import { useState, useEffect } from 'react';
import api from '../api/axios';
import { Route, Brain, Database, Activity, CheckCircle, XCircle } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import { StatusBadge } from '../components/ui/Badge';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';
import DynamicReplanningDemo from '../components/pages/DynamicReplanningDemo';

export default function AIOptimization() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchExperimentResult = () => {
    setLoading(true);
    setError('');
    api.get('/experiments/ddqn/latest')
      .then((res) => {
        setData(res.data);
      })
      .catch((err) => {
        if (err.response && err.response.status === 404) {
          setData(null);
        } else {
          setError(parseApiError(err, 'Failed to load DDQN experiment result.'));
        }
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchExperimentResult();
  }, []);

  if (loading) {
    return <LoadingState message="Loading AI experiment results..." />;
  }

  if (error) {
    return (
      <div>
        <PageHeader
          title="AI Route Optimization"
          subtitle="DDQN-PPDRP research evaluation using historical Amazon Last Mile route data."
        />
        <ErrorState error={error} onRetry={fetchExperimentResult} />
      </div>
    );
  }

  if (!data) {
    return (
      <div>
        <PageHeader
          title="AI Route Optimization"
          subtitle="DDQN-PPDRP research evaluation using historical Amazon Last Mile route data."
        />
        <Card>
          <EmptyState
            icon={Brain}
            title="No DDQN experiment results are currently available."
            description="The backend does not currently have a generated experiment_result.json. Run the research experiment in the backend to generate the results."
          />
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="AI Route Optimization"
        subtitle="DDQN-PPDRP research evaluation using historical Amazon Last Mile route data."
      />

      {/* Research Method Flow */}
      <Card title="Research Method Flow" className="mb-6">
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '8px', padding: '16px 0', fontSize: '12px', fontWeight: 'bold' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            <Database size={14} /> Amazon Dataset
          </div>
          <span style={{ color: 'var(--theme-muted)' }}>→</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            <Activity size={14} /> TSM-TW
          </div>
          <span style={{ color: 'var(--theme-muted)' }}>→</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            <Route size={14} /> i-DeepRoute
          </div>
          <span style={{ color: 'var(--theme-muted)' }}>→</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            State Representation
          </div>
          <span style={{ color: 'var(--theme-muted)' }}>→</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            <Brain size={14} /> DDQN + APER
          </div>
          <span style={{ color: 'var(--theme-muted)' }}>→</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            CMR + ED
          </div>
          <span style={{ color: 'var(--theme-muted)' }}>→</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px', backgroundColor: 'var(--theme-elevated)', padding: '8px 12px', borderRadius: '4px', border: '1px solid var(--theme-border)' }}>
            <CheckCircle size={14} color="var(--color-primary)" /> Evaluation
          </div>
        </div>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px', marginBottom: '16px' }}>
        
        {/* Experiment Overview */}
        <Card title="Experiment Overview">
          <table style={{ width: '100%', fontSize: '13px', borderCollapse: 'collapse' }}>
            <tbody>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Random Seed</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.seed}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Total Routes</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.dataset?.totalRoutes}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Training Routes</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.dataset?.trainingRoutes}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Validation Routes</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.dataset?.validationRoutes}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Test Routes</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.dataset?.testRoutes}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Total Stops</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.dataset?.totalStops}</td></tr>
              <tr><td style={{ padding: '8px 0', color: 'var(--theme-muted)' }}>Total Packages</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.dataset?.totalPackages}</td></tr>
            </tbody>
          </table>
        </Card>

        {/* DDQN Configuration */}
        <Card title="DDQN Configuration">
          <table style={{ width: '100%', fontSize: '13px', borderCollapse: 'collapse' }}>
            <tbody>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Training Episodes</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.trainingEpisodes}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Max Steps</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.maxSteps}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Learning Rate</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.learningRate}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Discount Factor</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.discountFactor}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Epsilon Start</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.epsilonStart}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Epsilon Minimum</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.epsilonMinimum}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Epsilon Decay</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.epsilonDecay}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Batch Size</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.batchSize}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Minimum Replay Size</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.minimumReplaySize}</td></tr>
              <tr><td style={{ padding: '8px 0', color: 'var(--theme-muted)' }}>Target Update Interval</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.configuration?.targetUpdateInterval}</td></tr>
            </tbody>
          </table>
        </Card>

      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '16px', marginBottom: '16px' }}>
        
        {/* Training Results */}
        <Card title="Training Results">
          <table style={{ width: '100%', fontSize: '13px', borderCollapse: 'collapse' }}>
            <tbody>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Training Updates</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.training?.updates}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Final Epsilon</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.training?.finalEpsilon}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Average Episode Reward</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.training?.averageReward}</td></tr>
              <tr><td style={{ padding: '8px 0', borderBottom: '1px solid var(--theme-border)', color: 'var(--theme-muted)' }}>Best Episode Reward</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.training?.bestReward}</td></tr>
              <tr><td style={{ padding: '8px 0', color: 'var(--theme-muted)' }}>Average Episode Steps</td><td style={{ textAlign: 'right', fontWeight: 'bold' }}>{data.training?.averageEpisodeSteps}</td></tr>
            </tbody>
          </table>
        </Card>

        {/* Test Evaluation */}
        <Card 
          title="Test Evaluation"
          className="bg-primary/5"
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '1px solid var(--theme-border)' }}>
              <span style={{ fontSize: '14px', fontWeight: '600', color: 'var(--theme-muted)' }}>Test Routes</span>
              <span style={{ fontSize: '16px', fontWeight: 'bold' }}>{data.test?.routes}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '1px solid var(--theme-border)' }}>
              <span style={{ fontSize: '14px', fontWeight: '600', color: 'var(--theme-muted)' }}>CMR (Complete Match Rate)</span>
              <span style={{ fontSize: '16px', fontWeight: 'bold', color: 'var(--color-primary)' }}>{data.test?.cmr}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '1px solid var(--theme-border)' }}>
              <span style={{ fontSize: '14px', fontWeight: '600', color: 'var(--theme-muted)' }}>ED (Edit Distance)</span>
              <span style={{ fontSize: '16px', fontWeight: 'bold', color: 'var(--color-primary)' }}>{data.test?.ed}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '1px solid var(--theme-border)' }}>
              <span style={{ fontSize: '14px', fontWeight: '600', color: 'var(--theme-muted)' }}>Average Reward</span>
              <span style={{ fontSize: '16px', fontWeight: 'bold' }}>{data.test?.averageReward}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'var(--theme-elevated)', padding: '12px', borderRadius: '8px', border: '1px solid var(--theme-border)' }}>
              <span style={{ fontSize: '14px', fontWeight: '600', color: 'var(--theme-muted)' }}>Evaluation Count</span>
              <span style={{ fontSize: '16px', fontWeight: 'bold' }}>{data.test?.routes}</span>
            </div>
          </div>
        </Card>

      </div>

      {/* Dijkstra Comparison Status */}
      <Card title="Dijkstra Comparison Status" className="mb-6">
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '16px' }}>
          <div>
            {data.dijkstra?.available ? (
              <StatusBadge status="COMPLETED" />
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--color-warning)', fontWeight: 'bold', fontSize: '14px', backgroundColor: 'rgba(217, 119, 6, 0.1)', padding: '4px 12px', borderRadius: '16px' }}>
                <XCircle size={16} /> Unavailable
              </div>
            )}
          </div>
          <div style={{ flex: 1 }}>
            <p style={{ margin: 0, fontSize: '14px', color: 'var(--theme-text)' }}>
              {data.dijkstra?.reason || "Comparison metrics are not available."}
            </p>
          </div>
        </div>
      </Card>
      
      <DynamicReplanningDemo />
    </div>
  );
}
