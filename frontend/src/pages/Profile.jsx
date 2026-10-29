import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../api/axios';
import { User, Mail, Shield, Calendar, Truck, Phone, FileText, CheckCircle2, AlertCircle } from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import Badge from '../components/ui/Badge';
import LoadingState from '../components/ui/LoadingState';
import ErrorState from '../components/ui/ErrorState';

export default function Profile() {
  const { user } = useAuth();
  const [profileData, setProfileData] = useState(null);
  const [driverData, setDriverData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        setLoading(true);
        // Securely fetch profile using backend principal
        const res = await api.get('/users/profile');
        const data = res.data;
        setProfileData(data);

        // If driver, fetch driver specific details
        if (data.driverId) {
          const driverRes = await api.get(`/drivers/${data.driverId}`);
          setDriverData(driverRes.data);
        }
      } catch (err) {
        setError(err.response?.data?.message || 'Failed to load profile data');
      } finally {
        setLoading(false);
      }
    };

    fetchProfile();
  }, []);

  if (loading) return <LoadingState message="Loading your profile..." />;
  if (error) return <ErrorState title="Profile Error" message={error} onRetry={() => window.location.reload()} />;
  if (!profileData) return null;

  const profileImageUrl =
    user?.profileImage || user?.avatar || user?.picture || user?.avatarUrl || user?.imageUrl || null;

  const getInitials = (name) => {
    if (!name) return 'U';
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  };

  const displayName = profileData.fullName || profileData.username;

  return (
    <div className="page-container" style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}>
      <PageHeader
        title="My Profile"
        subtitle="Manage your personal information and account settings"
      />

      <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', marginTop: '24px' }}>
        <Card>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '24px', padding: '24px' }}>
            <div
              style={{
                width: '80px',
                height: '80px',
                borderRadius: '50%',
                background: 'var(--theme-surface-hover)',
                border: '1px solid var(--theme-border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                overflow: 'hidden',
                flexShrink: 0
              }}
            >
              {profileImageUrl ? (
                <img src={profileImageUrl} alt={displayName} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : (
                <span style={{ fontSize: '28px', fontWeight: 600, color: 'var(--theme-text-muted)' }}>
                  {getInitials(displayName)}
                </span>
              )}
            </div>
            
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <h2 style={{ fontSize: '24px', margin: '0 0 8px 0', color: 'var(--theme-text)' }}>
                    {displayName}
                  </h2>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
                    <Badge variant={profileData.active ? 'success' : 'neutral'}>
                      {profileData.active ? 'Active Account' : 'Inactive'}
                    </Badge>
                  </div>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--theme-text-muted)', fontSize: '14px' }}>
                  <User size={16} />
                  <span><strong>Username:</strong> {profileData.username}</span>
                </div>
                {profileData.email && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--theme-text-muted)', fontSize: '14px' }}>
                    <Mail size={16} />
                    <span><strong>Email:</strong> {profileData.email}</span>
                  </div>
                )}
                {profileData.createdAt && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--theme-text-muted)', fontSize: '14px' }}>
                    <Calendar size={16} />
                    <span><strong>Joined:</strong> {new Date(profileData.createdAt).toLocaleDateString()}</span>
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--theme-text-muted)', fontSize: '14px' }}>
                  <Shield size={16} />
                  <span><strong>User ID:</strong> #{profileData.id}</span>
                </div>
              </div>
            </div>
          </div>
        </Card>

        {driverData && (
          <Card title="Driver Information">
            <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                <Truck size={18} color="var(--color-primary)" />
                <h3 style={{ margin: 0, fontSize: '16px' }}>Delivery Operational Details</h3>
              </div>
              
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px' }}>
                <div>
                  <span style={{ display: 'block', fontSize: '12px', color: 'var(--theme-text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Driver ID</span>
                  <div style={{ fontSize: '14px', fontWeight: 500 }}>#{driverData.id}</div>
                </div>
                
                <div>
                  <span style={{ display: 'block', fontSize: '12px', color: 'var(--theme-text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Operational Status</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    {driverData.status === 'AVAILABLE' ? <CheckCircle2 size={16} color="var(--color-success)" /> : <AlertCircle size={16} color="var(--color-warning)" />}
                    <span style={{ fontSize: '14px', fontWeight: 500 }}>{driverData.status}</span>
                  </div>
                </div>

                {driverData.licenseNumber && (
                  <div>
                    <span style={{ display: 'block', fontSize: '12px', color: 'var(--theme-text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>License Number</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', fontWeight: 500 }}>
                      <FileText size={14} color="var(--theme-text-muted)" />
                      {driverData.licenseNumber}
                    </div>
                  </div>
                )}

                {driverData.phone && (
                  <div>
                    <span style={{ display: 'block', fontSize: '12px', color: 'var(--theme-text-muted)', textTransform: 'uppercase', marginBottom: '4px' }}>Contact Number</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '14px', fontWeight: 500 }}>
                      <Phone size={14} color="var(--theme-text-muted)" />
                      {driverData.phone}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </Card>
        )}
      </div>
    </div>
  );
}
