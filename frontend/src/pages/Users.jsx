import { useState, useEffect, useCallback } from 'react';
import { userApi } from '../api/axios';
import { useAuth } from '../context/AuthContext';
import {
  Shield,
  Plus,
  ShieldAlert,
  UserCheck,
  Search,
  CheckCircle2,
  XCircle,
  Lock,
  Unlock,
  RotateCcw,
  Check
} from 'lucide-react';
import PageHeader from '../components/ui/PageHeader';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Select from '../components/ui/Select';
import { Badge, StatusBadge } from '../components/ui/Badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import ConfirmDialog from '../components/ui/ConfirmDialog';
import Pagination from '../components/ui/Pagination';
import EmptyState from '../components/ui/EmptyState';
import LoadingState from '../components/ui/LoadingState';
import ErrorState, { parseApiError } from '../components/ui/ErrorState';

export default function Users() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');
  const [actionError, setActionError] = useState('');

  // Search & Filter State
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [verifiedFilter, setVerifiedFilter] = useState('ALL');
  const [blockedFilter, setBlockedFilter] = useState('ALL');

  // Pagination State
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  // Add User Modal State
  const [showAddModal, setShowAddModal] = useState(false);
  const [createError, setCreateError] = useState('');
  const [createLoading, setCreateLoading] = useState(false);
  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    fullName: '',
    role: 'USER',
    phone: '',
    licenseNumber: '',
  });

  // Confirm Action Dialog State
  const [confirmDialog, setConfirmDialog] = useState({
    isOpen: false,
    title: '',
    message: '',
    confirmLabel: '',
    confirmVariant: 'danger',
    actionType: null,
    targetUser: null,
    loading: false,
  });

  const fetchUsers = useCallback(async () => {
    if (currentUser?.role !== 'ADMIN') return;
    setLoading(true);
    setFetchError('');

    try {
      const params = {
        page,
        size: pageSize,
        sort: 'id,asc',
      };

      if (search.trim()) params.search = search.trim();
      if (roleFilter !== 'ALL') params.role = roleFilter;
      if (statusFilter === 'ACTIVE') params.active = true;
      if (statusFilter === 'INACTIVE') params.active = false;
      if (verifiedFilter === 'VERIFIED') params.isVerified = true;
      if (verifiedFilter === 'UNVERIFIED') params.isVerified = false;
      if (blockedFilter === 'BLOCKED') params.isBlocked = true;
      if (blockedFilter === 'UNBLOCKED') params.isBlocked = false;

      const res = await userApi.getUsers(params);
      const data = res.data;

      if (data && Array.isArray(data.content)) {
        setUsers(data.content);
        setTotalPages(data.page?.totalPages ?? data.totalPages ?? 1);
        setTotalElements(data.page?.totalElements ?? data.totalElements ?? 0);
      } else if (Array.isArray(data)) {
        setUsers(data);
        setTotalPages(1);
        setTotalElements(data.length);
      } else {
        setUsers([]);
        setTotalPages(1);
        setTotalElements(0);
      }
    } catch (err) {
      setFetchError(parseApiError(err, 'Failed to load user accounts'));
    } finally {
      setLoading(false);
    }
  }, [currentUser, page, pageSize, search, roleFilter, statusFilter, verifiedFilter, blockedFilter]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const handleResetFilters = () => {
    setSearch('');
    setRoleFilter('ALL');
    setStatusFilter('ALL');
    setVerifiedFilter('ALL');
    setBlockedFilter('ALL');
    setPage(0);
  };

  const handleSearchChange = (e) => {
    setSearch(e.target.value);
    setPage(0);
  };

  const handleRoleFilterChange = (e) => {
    setRoleFilter(e.target.value);
    setPage(0);
  };

  const handleStatusFilterChange = (e) => {
    setStatusFilter(e.target.value);
    setPage(0);
  };

  const handleVerifiedFilterChange = (e) => {
    setVerifiedFilter(e.target.value);
    setPage(0);
  };

  const handleBlockedFilterChange = (e) => {
    setBlockedFilter(e.target.value);
    setPage(0);
  };

  const openCreateModal = () => {
    setForm({
      username: '',
      email: '',
      password: '',
      fullName: '',
      role: 'USER',
      phone: '',
      licenseNumber: '',
    });
    setCreateError('');
    setShowAddModal(true);
  };

  const executeCreateUser = async (payload) => {
    setCreateLoading(true);
    setCreateError('');
    try {
      const res = await userApi.createUser(payload);
      setShowAddModal(false);
      const created = res.data;
      const statusText = created.active ? 'ACTIVE' : 'INACTIVE';
      const verifiedText = created.isVerified ? 'VERIFIED' : 'UNVERIFIED';
      setActionSuccess(`Account '${created.username}' created successfully (ID: ${created.id}, Role: ${created.role}, Status: ${statusText}, Verification: ${verifiedText}).`);
      setTimeout(() => setActionSuccess(''), 6000);
      fetchUsers();
    } catch (err) {
      setCreateError(parseApiError(err, 'Failed to create user account'));
    } finally {
      setCreateLoading(false);
    }
  };

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setCreateError('');

    const trimmedUsername = form.username.trim();
    if (!trimmedUsername || trimmedUsername.length < 3) {
      setCreateError('Username must be at least 3 characters.');
      return;
    }
    if (!form.email || !form.email.includes('@')) {
      setCreateError('A valid email address is required.');
      return;
    }
    if (!form.password || form.password.length < 8) {
      setCreateError('Password must be at least 8 characters.');
      return;
    }
    if (form.role === 'DRIVER' && (!form.licenseNumber || !form.licenseNumber.trim())) {
      setCreateError('Driver license number is required for Driver accounts.');
      return;
    }

    const payload = {
      username: trimmedUsername,
      email: form.email.trim(),
      password: form.password,
      fullName: form.fullName?.trim() || null,
      role: form.role,
    };

    if (form.role === 'DRIVER') {
      payload.phone = form.phone?.trim() || null;
      payload.licenseNumber = form.licenseNumber.trim();
    }

    // Role-specific confirmation for ADMIN creation
    if (form.role === 'ADMIN') {
      setConfirmDialog({
        isOpen: true,
        title: 'Confirm Administrator Account Creation',
        message: `Are you sure you want to create an account with full System Administrator (ADMIN) privileges for '${trimmedUsername}' (${form.email.trim()})? This account will have unrestricted access to all operations, master data, and user management.`,
        confirmLabel: 'Confirm & Create Admin',
        confirmVariant: 'primary',
        actionType: 'create_admin',
        targetPayload: payload,
        targetUser: { username: trimmedUsername },
        loading: false,
      });
      return;
    }

    await executeCreateUser(payload);
  };

  const promptVerify = (target) => {
    setConfirmDialog({
      isOpen: true,
      title: `Verify User Account`,
      message: `Are you sure you want to verify account '${target.username}' (${target.role})? This will mark the user as an eligible and verified operator.`,
      confirmLabel: 'Verify Account',
      confirmVariant: 'primary',
      actionType: 'verify',
      targetUser: target,
      loading: false,
    });
  };

  const promptUnverify = (target) => {
    const isSelf = currentUser?.id === target.id || currentUser?.username === target.username;
    if (isSelf) {
      setActionError('Administrators cannot unverify their own account.');
      setTimeout(() => setActionError(''), 4000);
      return;
    }

    setConfirmDialog({
      isOpen: true,
      title: `Revoke Verification`,
      message: `Are you sure you want to unverify account '${target.username}' (${target.role})? Unverifying removes verified status without blocking or deactivating the account.`,
      confirmLabel: 'Unverify Account',
      confirmVariant: 'warning',
      actionType: 'unverify',
      targetUser: target,
      loading: false,
    });
  };

  const promptBlock = (target) => {
    const isSelf = currentUser?.id === target.id || currentUser?.username === target.username;
    if (isSelf) {
      setActionError('Administrators cannot block their own account.');
      setTimeout(() => setActionError(''), 4000);
      return;
    }

    setConfirmDialog({
      isOpen: true,
      title: `Block User Account`,
      message: `Are you sure you want to block account '${target.username}' (${target.role})? Blocking immediately prevents new logins and revokes API access for existing active sessions.`,
      confirmLabel: 'Block Account',
      confirmVariant: 'danger',
      actionType: 'block',
      targetUser: target,
      loading: false,
    });
  };

  const promptUnblock = (target) => {
    setConfirmDialog({
      isOpen: true,
      title: `Unblock User Account`,
      message: `Are you sure you want to unblock account '${target.username}'? The account will be permitted to authenticate and access the system again.`,
      confirmLabel: 'Unblock Account',
      confirmVariant: 'primary',
      actionType: 'unblock',
      targetUser: target,
      loading: false,
    });
  };

  const executeConfirmAction = async () => {
    const { actionType, targetUser, targetPayload } = confirmDialog;
    if (!actionType) return;

    if (actionType === 'create_admin') {
      setConfirmDialog((prev) => ({ ...prev, isOpen: false }));
      if (targetPayload) {
        await executeCreateUser(targetPayload);
      }
      return;
    }

    if (!targetUser) return;

    setConfirmDialog((prev) => ({ ...prev, loading: true }));
    setActionError('');
    setActionSuccess('');

    try {
      if (actionType === 'verify') {
        await userApi.verifyUser(targetUser.id);
        setActionSuccess(`User '${targetUser.username}' has been successfully verified.`);
      } else if (actionType === 'unverify') {
        await userApi.unverifyUser(targetUser.id);
        setActionSuccess(`User '${targetUser.username}' verification status has been revoked.`);
      } else if (actionType === 'block') {
        await userApi.blockUser(targetUser.id);
        setActionSuccess(`User '${targetUser.username}' has been blocked.`);
      } else if (actionType === 'unblock') {
        await userApi.unblockUser(targetUser.id);
        setActionSuccess(`User '${targetUser.username}' has been unblocked.`);
      }

      setConfirmDialog((prev) => ({ ...prev, isOpen: false }));
      setTimeout(() => setActionSuccess(''), 4000);
      await fetchUsers();
    } catch (err) {
      setActionError(parseApiError(err, `Failed to execute ${actionType} action`));
      setConfirmDialog((prev) => ({ ...prev, isOpen: false }));
      setTimeout(() => setActionError(''), 5000);
    } finally {
      setConfirmDialog((prev) => ({ ...prev, loading: false }));
    }
  };

  if (currentUser?.role !== 'ADMIN') {
    return (
      <div>
        <PageHeader title="User Management" subtitle="Administrator privileges required" />
        <Card>
          <EmptyState
            icon={ShieldAlert}
            title="Access Restricted"
            description="System user management is strictly restricted to administrator accounts."
          />
        </Card>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="User Management"
        subtitle="Manage user accounts, verification eligibility, and security access states"
        action={
          <Button
            variant="primary"
            icon={Plus}
            onClick={openCreateModal}
            id="add-user-btn"
          >
            Add User
          </Button>
        }
      />

      {/* Notifications */}
      {actionSuccess && (
        <div className="alert alert-success mb-4" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Check size={16} />
          <span>{actionSuccess}</span>
        </div>
      )}

      {actionError && (
        <div className="alert alert-error mb-4" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <ShieldAlert size={16} />
          <span>{actionError}</span>
        </div>
      )}

      {fetchError && (
        <ErrorState
          error={fetchError}
          onRetry={fetchUsers}
          className="mb-4"
        />
      )}

      {/* Search and Filters Bar */}
      <Card className="mb-4" style={{ padding: '16px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12, alignItems: 'flex-end' }}>
          <div>
            <Input
              label="Search Users"
              id="user-search"
              placeholder="Username, email, or name..."
              icon={Search}
              value={search}
              onChange={handleSearchChange}
            />
          </div>

          <div>
            <Select
              label="Role"
              id="role-filter"
              value={roleFilter}
              onChange={handleRoleFilterChange}
            >
              <option value="ALL">All Roles</option>
              <option value="ADMIN">ADMIN</option>
              <option value="DRIVER">DRIVER</option>
              <option value="USER">USER</option>
            </Select>
          </div>

          <div>
            <Select
              label="Account Status"
              id="status-filter"
              value={statusFilter}
              onChange={handleStatusFilterChange}
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
            </Select>
          </div>

          <div>
            <Select
              label="Verification"
              id="verified-filter"
              value={verifiedFilter}
              onChange={handleVerifiedFilterChange}
            >
              <option value="ALL">All Verification</option>
              <option value="VERIFIED">VERIFIED</option>
              <option value="UNVERIFIED">UNVERIFIED</option>
            </Select>
          </div>

          <div>
            <Select
              label="Security State"
              id="blocked-filter"
              value={blockedFilter}
              onChange={handleBlockedFilterChange}
            >
              <option value="ALL">All Security States</option>
              <option value="UNBLOCKED">UNBLOCKED</option>
              <option value="BLOCKED">BLOCKED</option>
            </Select>
          </div>

          <div style={{ display: 'flex', gap: 8, paddingBottom: 2 }}>
            <Button
              variant="secondary"
              icon={RotateCcw}
              onClick={handleResetFilters}
              id="reset-filters-btn"
              title="Reset all filters"
            >
              Reset
            </Button>
          </div>
        </div>
      </Card>

      {/* Users Table / States */}
      {loading ? (
        <LoadingState message="Loading system users..." />
      ) : users.length === 0 ? (
        <Card>
          <EmptyState
            icon={UserCheck}
            title="No users found"
            description="No user accounts match the specified search query or filter criteria."
          />
        </Card>
      ) : (
        <Card style={{ overflow: 'hidden' }}>
          <Table>
            <TableHeader>
              <tr>
                <TableHead>User ID</TableHead>
                <TableHead>Username</TableHead>
                <TableHead>Email Address</TableHead>
                <TableHead>Full Name</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Active</TableHead>
                <TableHead>Verification</TableHead>
                <TableHead>Security State</TableHead>
                <TableHead>Registered</TableHead>
                <TableHead style={{ textAlign: 'right' }}>Actions</TableHead>
              </tr>
            </TableHeader>
            <TableBody>
              {users.map((u) => {
                const isSelf = currentUser?.id === u.id || currentUser?.username === u.username;
                const isVerified = Boolean(u.isVerified ?? u.verified);
                const isBlocked = Boolean(u.isBlocked ?? u.blocked);

                return (
                  <TableRow key={u.id}>
                    <TableCell style={{ fontWeight: 700 }}>#{u.id}</TableCell>
                    <TableCell style={{ fontWeight: 600 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span>{u.username}</span>
                        {isSelf && (
                          <span
                            style={{
                              fontSize: 10,
                              backgroundColor: 'var(--color-primary-bg)',
                              color: 'var(--color-primary)',
                              padding: '2px 6px',
                              borderRadius: 4,
                              fontWeight: 700,
                            }}
                          >
                            YOU
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{u.fullName || '—'}</TableCell>
                    <TableCell>
                      <StatusBadge status={u.role} />
                    </TableCell>
                    <TableCell>
                      <Badge variant={u.active ? 'success' : 'danger'}>
                        {u.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={isVerified ? 'success' : 'warning'}>
                        {isVerified ? 'Verified' : 'Unverified'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={isBlocked ? 'danger' : 'default'}>
                        {isBlocked ? 'Blocked' : 'Unblocked'}
                      </Badge>
                    </TableCell>
                    <TableCell style={{ fontSize: 11, color: 'var(--theme-muted)' }}>
                      {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
                    </TableCell>
                    <TableCell style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end' }}>
                        {/* Verification Action */}
                        {!isVerified ? (
                          <Button
                            variant="secondary"
                            size="sm"
                            icon={CheckCircle2}
                            onClick={() => promptVerify(u)}
                            id={`verify-btn-${u.id}`}
                            title="Verify this account"
                          >
                            Verify
                          </Button>
                        ) : (
                          <Button
                            variant="secondary"
                            size="sm"
                            icon={XCircle}
                            onClick={() => promptUnverify(u)}
                            disabled={isSelf}
                            id={`unverify-btn-${u.id}`}
                            title={isSelf ? 'Cannot unverify your own account' : 'Revoke verification'}
                          >
                            Unverify
                          </Button>
                        )}

                        {/* Block / Unblock Action */}
                        {!isBlocked ? (
                          <Button
                            variant="danger"
                            size="sm"
                            icon={Lock}
                            onClick={() => promptBlock(u)}
                            disabled={isSelf}
                            id={`block-btn-${u.id}`}
                            title={isSelf ? 'Cannot block your own account' : 'Block this account'}
                          >
                            Block
                          </Button>
                        ) : (
                          <Button
                            variant="secondary"
                            size="sm"
                            icon={Unlock}
                            onClick={() => promptUnblock(u)}
                            id={`unblock-btn-${u.id}`}
                            title="Unblock this account"
                          >
                            Unblock
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>

          {/* Pagination */}
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            totalItems={totalElements}
            pageSize={pageSize}
            onPageChange={(newPage) => setPage(newPage)}
          />
        </Card>
      )}

      {/* Confirmation Dialog */}
      <ConfirmDialog
        isOpen={confirmDialog.isOpen}
        onClose={() => setConfirmDialog((prev) => ({ ...prev, isOpen: false }))}
        onConfirm={executeConfirmAction}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmLabel={confirmDialog.confirmLabel}
        confirmVariant={confirmDialog.confirmVariant}
        loading={confirmDialog.loading}
      />

      {/* Add User Modal */}
      <Modal
        isOpen={showAddModal}
        onClose={() => setShowAddModal(false)}
        title="Register New System User"
        footer={
          <div className="modal-footer">
            <Button variant="secondary" onClick={() => setShowAddModal(false)} disabled={createLoading}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleCreateSubmit} loading={createLoading} id="create-user-submit-btn">
              {form.role === 'ADMIN' ? 'Review & Create Admin' : 'Create User'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateSubmit}>
          {createError && <div className="alert alert-error mb-4">{createError}</div>}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Input
              label="Username"
              id="user-username"
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              required
              minLength={3}
            />
            <Input
              label="Email"
              id="user-email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Input
              label="Password (min 8 chars)"
              id="user-password"
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
              minLength={8}
            />
            <Input
              label="Full Name"
              id="user-fullname"
              value={form.fullName}
              onChange={(e) => setForm({ ...form, fullName: e.target.value })}
            />
          </div>

          <Select
            label="System Role"
            id="user-role"
            value={form.role}
            onChange={(e) => setForm({ ...form, role: e.target.value })}
            required
          >
            <option value="USER">USER</option>
            <option value="DRIVER">DRIVER</option>
            <option value="ADMIN">ADMIN</option>
          </Select>

          {form.role === 'ADMIN' && (
            <div className="alert alert-info mt-2" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.85rem' }}>
              <Shield size={16} />
              <span>System Administrators receive immediate full access to all system operations, security states, and master data. Explicit confirmation is required upon submission.</span>
            </div>
          )}

          {form.role === 'DRIVER' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 8 }}>
              <Input
                label="Driver Phone"
                id="driver-user-phone"
                value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
              />
              <Input
                label="License Number"
                id="driver-user-license"
                value={form.licenseNumber}
                onChange={(e) => setForm({ ...form, licenseNumber: e.target.value })}
                required
              />
            </div>
          )}
        </form>
      </Modal>
    </div>
  );
}
