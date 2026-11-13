"use client";

import { api } from "@/lib/api";
import type { PageResponse, UserProfile } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";
import {
  viLabel,
  viPermission,
  viRoleDescription,
  viRoleName,
} from "@/lib/vi";
import { useFlashNotifications, useUi } from "@/components/ui-provider";

interface Role {
  id: string;
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  permissions: string[];
}

interface Permission {
  code: string;
  description: string;
}

const emptyRole = {
  id: "",
  code: "",
  name: "",
  description: "",
  permissions: [] as string[],
};

export function AdminAccessControl() {
  const { confirmDialog } = useUi();
  const [staff, setStaff] = useState<UserProfile[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [assignments, setAssignments] = useState<Record<string, string[]>>({});
  const [roleForm, setRoleForm] = useState(emptyRole);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  useFlashNotifications(error, notice);

  async function load() {
    const [staffPage, roleList, permissionList] = await Promise.all([
      api.get<PageResponse<UserProfile>>("/admin/staff?size=100"),
      api.get<Role[]>("/admin/roles"),
      api.get<Permission[]>("/admin/permissions"),
    ]);
    setStaff(staffPage.content);
    setRoles(roleList);
    setPermissions(permissionList);
    setAssignments(
      Object.fromEntries(
        staffPage.content.map((user) => [String(user.id), [...user.roles]]),
      ),
    );
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load().catch((reason: Error) => setError(reason.message));
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  async function perform(action: () => Promise<unknown>, message: string) {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await action();
      setNotice(message);
      await load();
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  function editRole(role: Role) {
    setRoleForm({
      id: role.id,
      code: role.code,
      name: role.name,
      description: role.description ?? "",
      permissions: [...role.permissions],
    });
  }

  async function saveRole(event: FormEvent) {
    event.preventDefault();
    const payload = {
      name: roleForm.name,
      description: roleForm.description,
      permissions: roleForm.permissions,
    };
    await perform(
      () =>
        roleForm.id
          ? api.put(`/admin/roles/${roleForm.id}`, payload)
          : api.post("/admin/roles", { ...payload, code: roleForm.code }),
      roleForm.id ? "Đã cập nhật vai trò." : "Đã tạo vai trò tùy chỉnh.",
    );
    setRoleForm(emptyRole);
  }

  async function removeRole(role: Role) {
    const confirmed = await confirmDialog({
      title: `Xóa vai trò ${viRoleName(role.code, role.name)}`,
      description: `Vai trò ${role.code} sẽ bị xóa vĩnh viễn. Các tài khoản đang sử dụng vai trò này có thể mất quyền truy cập.`,
      confirmLabel: "Xóa vai trò",
      tone: "danger",
    });
    if (!confirmed) return;
    await perform(
      () => api.delete(`/admin/roles/${role.id}`),
      "Đã xóa vai trò tùy chỉnh.",
    );
  }

  async function saveAssignment(user: UserProfile) {
    const selected = assignments[String(user.id)] ?? [];
    if (!selected.length) {
      setError("Nhân viên phải có ít nhất một vai trò.");
      return;
    }
    await perform(
      () =>
        api.put(`/admin/staff/${user.id}/roles`, {
          roles: selected,
        }),
      `Đã cập nhật vai trò cho ${user.fullName}; các phiên cũ đã bị thu hồi.`,
    );
  }

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>VAI TRÒ VÀ QUYỀN TRUY CẬP</span>
          <h1>Quản trị quyền truy cập</h1>
          <p>
            Quản lý vai trò và gán quyền an toàn cho từng nhóm nhân viên.
          </p>
        </div>
      </div>
      <div className="management-grid governance-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>THÔNG TIN VAI TRÒ</span>
              <h2>{roleForm.id ? "Sửa vai trò" : "Tạo vai trò"}</h2>
            </div>
          </div>
          <form className="form-grid compact-form" onSubmit={saveRole}>
            <label>
              Mã vai trò
              <input
                required
                minLength={3}
                disabled={Boolean(roleForm.id)}
                value={roleForm.code}
                onChange={(event) =>
                  setRoleForm({
                    ...roleForm,
                    code: event.target.value.toUpperCase(),
                  })
                }
                placeholder="Ví dụ: GIAM_SAT_LE_TAN"
              />
            </label>
            <label>
              Tên hiển thị
              <input
                required
                value={roleForm.name}
                onChange={(event) =>
                  setRoleForm({ ...roleForm, name: event.target.value })
                }
              />
            </label>
            <label>
              Mô tả
              <textarea
                value={roleForm.description}
                onChange={(event) =>
                  setRoleForm({
                    ...roleForm,
                    description: event.target.value,
                  })
                }
              />
            </label>
            <fieldset>
              <legend>Quyền được phép sử dụng</legend>
              <div className="permission-grid">
                {permissions.map((permission) => (
                  <label key={permission.code}>
                    <input
                      type="checkbox"
                      checked={roleForm.permissions.includes(permission.code)}
                      onChange={(event) =>
                        setRoleForm({
                          ...roleForm,
                          permissions: event.target.checked
                            ? [...roleForm.permissions, permission.code]
                            : roleForm.permissions.filter(
                                (value) => value !== permission.code,
                              ),
                        })
                      }
                    />
                    <span>
                      <b>
                        {viPermission(permission.code, permission.description)}
                      </b>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>
            <div className="row-actions">
              <button className="primary" disabled={loading}>
                {roleForm.id ? "Lưu thay đổi" : "Tạo vai trò"}
              </button>
              {roleForm.id && (
                <button type="button" onClick={() => setRoleForm(emptyRole)}>
                  Hủy sửa
                </button>
              )}
            </div>
          </form>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>DANH MỤC VAI TRÒ</span>
              <h2>Danh mục vai trò</h2>
            </div>
            <b>{roles.length}</b>
          </div>
          <div className="review-list">
            {roles.map((role) => (
              <article key={role.id}>
                <div>
                  <div className="review-meta">
                    <b>{viRoleName(role.code, role.name)}</b>
                    {role.systemRole && <span className="status">Hệ thống</span>}
                  </div>
                  <p>{viRoleDescription(role.code, role.description)}</p>
                  <small>{role.permissions.length ? `${role.permissions.length} quyền được cấp` : "Chưa được cấp quyền"}</small>
                </div>
                {!role.systemRole && (
                  <div className="row-actions">
                    <button type="button" onClick={() => editRole(role)}>
                      Sửa
                    </button>
                    <button type="button" onClick={() => void removeRole(role)}>
                      Xóa
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        </section>

        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>PHÂN QUYỀN NHÂN VIÊN</span>
              <h2>Gán vai trò nhân viên</h2>
            </div>
            <b>{staff.length}</b>
          </div>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Nhân viên</th>
                  <th>Trạng thái</th>
                  <th>Vai trò</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {staff.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <strong>{user.fullName}</strong>
                      <small>{user.email}</small>
                    </td>
                    <td>
                      <span className="status">{viLabel(user.status)}</span>
                    </td>
                    <td>
                      <div className="row-actions">
                        {roles
                          .filter((role) => role.code !== "CUSTOMER")
                          .map((role) => (
                            <label key={role.id}>
                              <input
                                type="checkbox"
                                checked={(
                                  assignments[String(user.id)] ?? []
                                ).includes(role.code)}
                                onChange={(event) => {
                                  const current =
                                    assignments[String(user.id)] ?? [];
                                  setAssignments({
                                    ...assignments,
                                    [String(user.id)]: event.target.checked
                                      ? [...current, role.code]
                                      : current.filter(
                                          (value) => value !== role.code,
                                        ),
                                  });
                                }}
                              />
                              {viRoleName(role.code, role.name)}
                            </label>
                          ))}
                      </div>
                    </td>
                    <td>
                      <button
                        type="button"
                        disabled={loading}
                        onClick={() => void saveAssignment(user)}
                      >
                        Lưu quyền
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}
