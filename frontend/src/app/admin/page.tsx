"use client";

import { useAuth } from "@/components/auth-provider";
import { AppIcon } from "@/components/app-icon";
import { AdminFrontDesk } from "@/components/admin-front-desk";
import { AdminCustomers } from "@/components/admin-customers";
import { AdminAccessControl } from "@/components/admin-access-control";
import { AdminPolicies } from "@/components/admin-policies";
import { AdminAiOperations } from "@/components/admin-ai-operations";
import { AdminManagement } from "@/components/admin-management";
import { AdminOperations } from "@/components/admin-operations";
import { AdminOverview } from "@/components/admin-overview";
import { availableAdminTabs, type AdminTabId } from "@/lib/admin-permissions";
import { viRole } from "@/lib/vi";
import { useMemo, useState } from "react";

export default function AdminPage() {
  const { user, loading } = useAuth();
  const allowedTabs = useMemo(() => availableAdminTabs(user?.roles), [user?.roles]);
  const [tab, setTab] = useState<AdminTabId>("overview");
  const [sidebarOpen, setSidebarOpen] = useState(false);

  if (loading) {
    return <section className="admin-loading"><span className="spinner" /><p>Đang chuẩn bị không gian làm việc…</p></section>;
  }
  if (!user || allowedTabs.length === 0) {
    return (
      <section className="content-section empty-state premium-empty">
        <span className="empty-icon"><AppIcon name="shield" /></span>
        <small>KHU VỰC NỘI BỘ</small>
        <h1>Không gian dành cho nhân viên khách sạn</h1>
        <p>Tài khoản này chưa được cấp quyền vận hành. Vui lòng liên hệ quản trị viên nếu bạn cần truy cập.</p>
      </section>
    );
  }

  const effectiveTab = allowedTabs.some((item) => item.id === tab) ? tab : allowedTabs[0].id;
  const current = allowedTabs.find((item) => item.id === effectiveTab) ?? allowedTabs[0];
  const primaryRole = user.roles[0] ? viRole(user.roles[0]) : "Nhân viên";

  return (
    <section className={`admin-layout ${sidebarOpen ? "sidebar-open" : ""}`}>
      <aside className="admin-sidebar">
        <div className="admin-sidebar-head">
          <span className="admin-emblem"><AppIcon name="building" /></span>
          <div><small>HOTELAI CONCIERGE</small><strong>Trung tâm điều hành</strong></div>
          <button type="button" onClick={() => setSidebarOpen(false)} aria-label="Đóng menu"><AppIcon name="x" /></button>
        </div>
        <div className="staff-profile">
          <span>{user.fullName.trim().charAt(0).toUpperCase()}</span>
          <div><strong>{user.fullName}</strong><small>{primaryRole}</small></div>
          <i title="Đang trực tuyến" />
        </div>
        <nav className="admin-tabs" aria-label="Nhóm chức năng quản trị">
          <small className="nav-group-label">KHÔNG GIAN LÀM VIỆC</small>
          {allowedTabs.map((item) => (
            <button
              type="button"
              key={item.id}
              className={effectiveTab === item.id ? "active" : ""}
              onClick={() => { setTab(item.id); setSidebarOpen(false); }}
            >
              <span className="admin-tab-icon"><AppIcon name={item.icon} /></span>
              <span><b>{item.label}</b><small>{item.caption}</small></span>
              <AppIcon name="arrow" className="tab-arrow" />
            </button>
          ))}
        </nav>
        <div className="admin-sidebar-foot">
          <span><AppIcon name="shield" /></span>
          <div><strong>Phiên làm việc an toàn</strong><small>Quyền được đồng bộ theo tài khoản</small></div>
        </div>
      </aside>
      <div className="admin-mobile-overlay" onClick={() => setSidebarOpen(false)} />
      <div className="admin-workspace">
        <div className="workspace-topbar">
          <button type="button" className="sidebar-toggle" onClick={() => setSidebarOpen(true)} aria-label="Mở menu điều hành"><AppIcon name="menu" /></button>
          <div>
            <small>KHÔNG GIAN LÀM VIỆC</small>
            <strong>{current.label}</strong>
          </div>
          <div className="workspace-status"><i /> Hệ thống đang hoạt động</div>
        </div>
        <div className="workspace-content">
          {effectiveTab === "overview" && <AdminOverview />}
          {effectiveTab === "frontdesk" && <AdminFrontDesk />}
          {effectiveTab === "inventory" && <AdminManagement mode="inventory" />}
          {effectiveTab === "operations" && <AdminOperations />}
          {effectiveTab === "customers" && <AdminCustomers />}
          {effectiveTab === "access" && <AdminAccessControl />}
          {effectiveTab === "policies" && <AdminPolicies />}
          {effectiveTab === "ai-operations" && <AdminAiOperations />}
          {effectiveTab === "governance" && <AdminManagement mode="governance" />}
        </div>
      </div>
    </section>
  );
}
