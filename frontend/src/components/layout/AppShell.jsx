import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Navbar, { WorkspaceHeader } from './Navbar';

export default function AppShell() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <div className="app-shell">
      <Navbar mobileMenuOpen={mobileMenuOpen} setMobileMenuOpen={setMobileMenuOpen} />
      <main className="app-main">
        <WorkspaceHeader setMobileMenuOpen={setMobileMenuOpen} />
        <div className="page-content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
