"use client";

import { api, ApiRequestError } from "@/lib/api";
import { AppIcon } from "@/components/app-icon";
import { useAuth } from "@/components/auth-provider";
import type {
  DestinationCatalogItem,
  Room,
  UserProfile,
} from "@/lib/types";
import {
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { viLabel } from "@/lib/vi";
import { useFlashNotifications, useUi } from "@/components/ui-provider";
import { hasAnyRole } from "@/lib/admin-permissions";

interface Task {
  id: string;
  roomId: string;
  roomNumber: string;
  assignedTo?: string;
  status: string;
  priority: string;
  note?: string;
  version: number;
}
interface Maintenance {
  id: string;
  roomId: string;
  roomNumber: string;
  title: string;
  severity: string;
  status: string;
  resolution?: string;
  version: number;
}
interface UserPage {
  content: UserProfile[];
}

export function AdminOperations() {
  const { user } = useAuth();
  const { formDialog } = useUi();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [maintenance, setMaintenance] = useState<Maintenance[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [destinations, setDestinations] = useState<DestinationCatalogItem[]>(
    [],
  );
  const [destinationFilter, setDestinationFilter] = useState("ALL");
  const [housekeepers, setHousekeepers] = useState<UserProfile[]>([]);
  const [assignees, setAssignees] = useState<Record<string, string>>({});
  const [form, setForm] = useState({
    roomId: "",
    title: "",
    description: "",
    severity: "MEDIUM",
  });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const mutationInFlight = useRef(false);
  const canManageTeam = hasAnyRole(user?.roles, ["MANAGER"]);
  useFlashNotifications(error, notice);

  const load = useCallback(async () => {
    try {
      const [taskData, maintenanceData, roomData, staffData, destData] =
        await Promise.all([
          api.get<Task[]>("/housekeeping/tasks"),
          api.get<Maintenance[]>("/maintenance"),
          api.get<Room[]>("/admin/hotels/1/rooms"),
          canManageTeam
            ? api.get<UserPage>("/admin/staff?size=100")
            : Promise.resolve({ content: [] }),
          api
            .get<DestinationCatalogItem[]>("/availability/destinations")
            .catch(() => []),
        ]);
      setTasks(taskData);
      setMaintenance(maintenanceData);
      setRooms(roomData);
      if (destData.length > 0) setDestinations(destData);
      setHousekeepers(
        staffData.content.filter((staff) =>
          staff.roles.some((role) =>
            ["HOUSEKEEPER", "MANAGER", "ADMIN"].includes(role),
          ),
        ),
      );
    } catch (reason) {
      setError((reason as Error).message);
    }
  }, [canManageTeam]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function taskStatus(
    id: string,
    status: string,
    expectedVersion: number,
  ) {
    await perform(
      () =>
        api.patch(`/housekeeping/tasks/${id}/status`, {
          status,
          expectedVersion,
        }),
      "Đã cập nhật công việc buồng phòng.",
    );
  }

  async function assign(id: string, expectedVersion: number) {
    const userId = assignees[id];
    if (!userId) {
      setError("Hãy chọn nhân viên buồng phòng.");
      return;
    }
    await perform(
      () =>
        api.patch(`/housekeeping/tasks/${id}/assign`, {
          assignedTo: userId,
          expectedVersion,
        }),
      "Đã phân công nhiệm vụ.",
    );
  }

  async function report(event: FormEvent) {
    event.preventDefault();
    const completed = await perform(
      () => api.post("/maintenance", form),
      "Đã khóa phòng và tạo yêu cầu bảo trì.",
    );
    if (completed) {
      setForm({ roomId: "", title: "", description: "", severity: "MEDIUM" });
    }
  }

  async function resolve(id: string, expectedVersion: number) {
    const values = await formDialog({
      title: "Hoàn tất yêu cầu bảo trì",
      description:
        "Ghi lại nội dung đã xử lý trước khi đưa phòng về trạng thái sẵn sàng.",
      confirmLabel: "Hoàn tất bảo trì",
      fields: [
        {
          name: "note",
          label: "Nội dung xử lý",
          type: "textarea",
          required: true,
          placeholder: "Ví dụ: Đã thay khóa cửa và kiểm tra vận hành…",
        },
      ],
    });
    if (!values) return;
    await perform(
      () =>
        api.post(`/maintenance/${id}/resolve`, {
          note: values.note,
          expectedVersion,
        }),
      "Đã hoàn tất bảo trì và mở bán lại phòng.",
    );
  }

  async function perform(action: () => Promise<unknown>, message: string) {
    if (mutationInFlight.current) return false;
    mutationInFlight.current = true;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      await action();
      await load();
      setNotice(message);
      return true;
    } catch (reason) {
      if (reason instanceof ApiRequestError && reason.status === 409) {
        await load().catch(() => undefined);
        setError(
          "Dữ liệu vừa được cập nhật ở một thao tác khác. Hệ thống đã nạp phiên bản mới nhất; vui lòng kiểm tra và thực hiện lại.",
        );
      } else {
        setError((reason as Error).message);
      }
      return false;
    } finally {
      mutationInFlight.current = false;
      setSaving(false);
    }
  }

  const roomMap = useMemo(
    () => new Map(rooms.map((r) => [String(r.id), r])),
    [rooms],
  );

  const filteredRooms = useMemo(
    () =>
      destinationFilter === "ALL"
        ? rooms
        : rooms.filter(
            (r) => (r.destinationCode ?? "PHU_QUOC") === destinationFilter,
          ),
    [rooms, destinationFilter],
  );

  const filteredTasks = useMemo(
    () =>
      destinationFilter === "ALL"
        ? tasks
        : tasks.filter((t) => {
            const room = roomMap.get(String(t.roomId));
            return (room?.destinationCode ?? "PHU_QUOC") === destinationFilter;
          }),
    [tasks, destinationFilter, roomMap],
  );

  const filteredMaintenance = useMemo(
    () =>
      destinationFilter === "ALL"
        ? maintenance
        : maintenance.filter((m) => {
            const room = roomMap.get(String(m.roomId));
            return (room?.destinationCode ?? "PHU_QUOC") === destinationFilter;
          }),
    [maintenance, destinationFilter, roomMap],
  );

  return (
    <>
      {saving ? (
        <div className="saving-banner" role="status" aria-live="polite">
          <span className="loading-spinner" aria-hidden="true" />
          <div>
            <strong>Đang lưu và đồng bộ dữ liệu</strong>
            <small>Danh sách sẽ tự cập nhật ngay khi hoàn tất.</small>
          </div>
        </div>
      ) : null}
      <div className="workspace-heading">
        <div>
          <span>BUỒNG PHÒNG VÀ BẢO TRÌ</span>
          <h1>Vận hành phòng</h1>
          <p>
            Theo dõi phòng từ lúc khách trả đến khi sẵn sàng đón khách mới theo
            từng cơ sở.
          </p>
        </div>
        <label className="compact-field">
          Cơ sở / Điểm đến
          <select
            value={destinationFilter}
            onChange={(event) => setDestinationFilter(event.target.value)}
          >
            <option value="ALL">Toàn chuỗi (10 cơ sở)</option>
            {destinations.map((dest) => (
              <option key={dest.code} value={dest.code}>
                {dest.name} · {dest.propertyName}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="operation-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>BUỒNG PHÒNG</span>
              <h2>Danh sách công việc</h2>
            </div>
            <b>{filteredTasks.length}</b>
          </div>
          <div className="task-list">
            {filteredTasks.map((task) => {
              const roomInfo = roomMap.get(String(task.roomId));
              return (
                <article className="task-card" key={task.id}>
                  <div
                    className={`room-token priority-${task.priority.toLowerCase()}`}
                  >
                    {task.roomNumber}
                  </div>
                  <div>
                    <strong>{task.note || "Dọn và kiểm tra phòng"}</strong>
                    <p>
                      <AppIcon name="map-pin" className="inline-pin-icon" />
                      {roomInfo?.destinationName ?? "Phú Quốc"} ·{" "}
                      {viLabel(task.priority)} · {viLabel(task.status)}
                    </p>
                  </div>
                  <div className="row-actions">
                    {!task.assignedTo &&
                      hasAnyRole(user?.roles, ["MANAGER"]) && (
                        <>
                          <select
                            value={assignees[task.id] ?? ""}
                            onChange={(event) =>
                              setAssignees({
                                ...assignees,
                                [task.id]: event.target.value,
                              })
                            }
                          >
                            <option value="">Chọn nhân viên</option>
                            {housekeepers.map((staff) => (
                              <option key={staff.id} value={staff.id}>
                                {staff.fullName}
                              </option>
                            ))}
                          </select>
                          <button
                            onClick={() => void assign(task.id, task.version)}
                          >
                            Phân công
                          </button>
                        </>
                      )}
                    {task.status === "OPEN" && (
                      <button
                        onClick={() =>
                          void taskStatus(task.id, "IN_PROGRESS", task.version)
                        }
                      >
                        Bắt đầu
                      </button>
                    )}
                    {task.status === "IN_PROGRESS" && (
                      <button
                        className="primary"
                        onClick={() =>
                          void taskStatus(task.id, "COMPLETED", task.version)
                        }
                      >
                        Hoàn tất
                      </button>
                    )}
                  </div>
                </article>
              );
            })}
            {!filteredTasks.length && (
              <p className="muted">Không có công việc đang chờ.</p>
            )}
          </div>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>BẢO TRÌ</span>
              <h2>Sự cố đang mở</h2>
            </div>
            <b>{filteredMaintenance.length}</b>
          </div>
          <div className="task-list">
            {filteredMaintenance.map((item) => {
              const roomInfo = roomMap.get(String(item.roomId));
              return (
                <article className="task-card" key={item.id}>
                  <div
                    className={`room-token severity-${item.severity.toLowerCase()}`}
                  >
                    {item.roomNumber}
                  </div>
                  <div>
                    <strong>{item.title}</strong>
                    <p>
                      <AppIcon name="map-pin" className="inline-pin-icon" />
                      {roomInfo?.destinationName ?? "Phú Quốc"} ·{" "}
                      {viLabel(item.severity)} · {viLabel(item.status)}
                    </p>
                  </div>
                  {canManageTeam && (
                    <button onClick={() => void resolve(item.id, item.version)}>
                      Xử lý xong
                    </button>
                  )}
                </article>
              );
            })}
            {!filteredMaintenance.length && (
              <p className="muted">Không có sự cố cần xử lý.</p>
            )}
          </div>
          <form className="form-grid compact-form" onSubmit={report}>
            <h3>Báo sự cố mới</h3>
            <div className="form-row">
              <label>
                Phòng
                <select
                  required
                  value={form.roomId}
                  onChange={(event) =>
                    setForm({ ...form, roomId: event.target.value })
                  }
                >
                  <option value="">Chọn phòng</option>
                  {filteredRooms.map((room) => (
                    <option key={room.id} value={room.id}>
                      [{room.destinationName ?? "Phú Quốc"}] Phòng{" "}
                      {room.roomNumber}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Mức độ
                <select
                  value={form.severity}
                  onChange={(event) =>
                    setForm({ ...form, severity: event.target.value })
                  }
                >
                  {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => (
                    <option key={value} value={value}>
                      {viLabel(value)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <label>
              Tiêu đề
              <input
                required
                value={form.title}
                onChange={(event) =>
                  setForm({ ...form, title: event.target.value })
                }
              />
            </label>
            <label>
              Mô tả
              <textarea
                value={form.description}
                onChange={(event) =>
                  setForm({ ...form, description: event.target.value })
                }
              />
            </label>
            <button className="primary">Tạo yêu cầu</button>
          </form>
        </section>
      </div>
    </>
  );
}
