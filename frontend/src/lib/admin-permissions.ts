import type { IconName } from "@/components/app-icon";

export type AdminTabId =
  | "overview" | "frontdesk" | "inventory" | "operations" | "customers"
  | "access" | "policies" | "ai-operations" | "governance";

export interface AdminTabDefinition {
  id: AdminTabId;
  label: string;
  caption: string;
  icon: IconName;
  roles: string[];
}

const inheritedRoles: Record<string, string[]> = {
  ADMIN: ["ADMIN", "MANAGER", "RECEPTIONIST", "HOUSEKEEPER"],
  MANAGER: ["MANAGER", "RECEPTIONIST", "HOUSEKEEPER"],
  RECEPTIONIST: ["RECEPTIONIST"],
  HOUSEKEEPER: ["HOUSEKEEPER"],
  CUSTOMER: ["CUSTOMER"],
};

export const adminTabs: AdminTabDefinition[] = [
  { id: "overview", label: "Tổng quan", caption: "Hiệu suất & dự báo", icon: "chart", roles: ["MANAGER"] },
  { id: "frontdesk", label: "Bàn lễ tân", caption: "Đặt phòng & lưu trú", icon: "calendar", roles: ["RECEPTIONIST"] },
  { id: "inventory", label: "Kho phòng & giá", caption: "Sản phẩm & doanh thu", icon: "bed", roles: ["MANAGER"] },
  { id: "operations", label: "Vận hành phòng", caption: "Dọn phòng & bảo trì", icon: "broom", roles: ["HOUSEKEEPER"] },
  { id: "customers", label: "Khách hàng", caption: "Hồ sơ & chăm sóc", icon: "users", roles: ["RECEPTIONIST"] },
  { id: "access", label: "Phân quyền", caption: "Vai trò & tài khoản", icon: "shield", roles: ["ADMIN"] },
  { id: "policies", label: "Chính sách", caption: "Quy định khách sạn", icon: "clipboard", roles: ["MANAGER"] },
  { id: "ai-operations", label: "Trung tâm AI", caption: "Trợ lý & tối ưu", icon: "sparkles", roles: ["MANAGER"] },
  { id: "governance", label: "Quản trị hệ thống", caption: "Đánh giá & nhật ký", icon: "settings", roles: ["MANAGER"] },
];

export function effectiveRoles(userRoles: string[] | undefined) {
  const roles = new Set<string>();
  userRoles?.forEach((role) => {
    const normalized = role.toUpperCase();
    (inheritedRoles[normalized] ?? [normalized]).forEach((value) => roles.add(value));
  });
  return roles;
}

export function hasAnyRole(userRoles: string[] | undefined, allowedRoles: string[]) {
  const roles = effectiveRoles(userRoles);
  return allowedRoles.some((role) => roles.has(role.toUpperCase()));
}

export function availableAdminTabs(userRoles: string[] | undefined) {
  return adminTabs.filter((tab) => hasAnyRole(userRoles, tab.roles));
}

export function isHotelStaff(userRoles: string[] | undefined) {
  return availableAdminTabs(userRoles).length > 0;
}
