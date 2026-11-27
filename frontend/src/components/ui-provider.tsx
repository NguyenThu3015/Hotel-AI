"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { AppIcon, type IconName } from "./app-icon";

export type ToastTone = "success" | "error" | "warning" | "info";

interface ToastInput {
  title: string;
  message?: string;
  tone?: ToastTone;
  duration?: number;
}

interface ToastItem extends Required<Omit<ToastInput, "message">> {
  id: string;
  message?: string;
}

export interface DialogField {
  name: string;
  label: string;
  type?: "text" | "number" | "textarea" | "select" | "multiselect";
  placeholder?: string;
  defaultValue?: string;
  required?: boolean;
  min?: number;
  options?: Array<{ value: string; label: string }>;
  hint?: string;
}

interface ConfirmDialogOptions {
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: "default" | "danger";
}

interface FormDialogOptions extends ConfirmDialogOptions {
  fields: DialogField[];
}

type ActiveDialog =
  | ({ kind: "confirm"; resolve: (value: boolean) => void } & ConfirmDialogOptions)
  | ({
      kind: "form";
      resolve: (value: Record<string, string> | null) => void;
    } & FormDialogOptions);

interface UiContextValue {
  notify(input: ToastInput): void;
  confirmDialog(options: ConfirmDialogOptions): Promise<boolean>;
  formDialog(options: FormDialogOptions): Promise<Record<string, string> | null>;
}

const UiContext = createContext<UiContextValue | null>(null);

const toneMeta: Record<ToastTone, { icon: IconName; label: string }> = {
  success: { icon: "check", label: "Thành công" },
  error: { icon: "alert", label: "Có lỗi xảy ra" },
  warning: { icon: "alert", label: "Cần lưu ý" },
  info: { icon: "info", label: "Thông tin" },
};

function ToastCard({ item, onClose }: { item: ToastItem; onClose(): void }) {
  useEffect(() => {
    const timer = window.setTimeout(onClose, item.duration);
    return () => window.clearTimeout(timer);
  }, [item.duration, onClose]);

  const meta = toneMeta[item.tone];
  return (
    <article className={`app-toast toast-${item.tone}`} role="status">
      <span className="toast-symbol" aria-hidden="true">
        <AppIcon name={meta.icon} />
      </span>
      <div className="toast-copy">
        <small>{meta.label}</small>
        <strong>{item.title}</strong>
        {item.message && <p>{item.message}</p>}
      </div>
      <button
        type="button"
        className="toast-close"
        onClick={onClose}
        aria-label="Đóng thông báo"
      >
        <AppIcon name="x" />
      </button>
      <span
        className="toast-progress"
        style={{ animationDuration: `${item.duration}ms` }}
      />
    </article>
  );
}

function DialogSurface({
  dialog,
  onClose,
}: {
  dialog: ActiveDialog;
  onClose(value: boolean | Record<string, string> | null): void;
}) {
  const [values, setValues] = useState<Record<string, string>>(() =>
    dialog.kind === "form"
      ? Object.fromEntries(
          dialog.fields.map((field) => [field.name, field.defaultValue ?? ""]),
        )
      : {},
  );

  function submit(event: React.FormEvent) {
    event.preventDefault();
    onClose(dialog.kind === "form" ? values : true);
  }

  return (
    <div
      className="dialog-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget)
          onClose(dialog.kind === "form" ? null : false);
      }}
    >
      <section
        className="app-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="app-dialog-title"
      >
        <div className="dialog-accent" aria-hidden="true" />
        <header className="dialog-header">
          <span
            className={`dialog-icon ${dialog.tone === "danger" ? "danger" : ""}`}
            aria-hidden="true"
          >
            <AppIcon name={dialog.tone === "danger" ? "alert" : "sparkles"} />
          </span>
          <div>
            <small>HOTELAI OPERATIONS</small>
            <h2 id="app-dialog-title">{dialog.title}</h2>
            {dialog.description && <p>{dialog.description}</p>}
          </div>
          <button
            type="button"
            className="dialog-x"
            onClick={() => onClose(dialog.kind === "form" ? null : false)}
            aria-label="Đóng"
          >
            <AppIcon name="x" />
          </button>
        </header>
        <form onSubmit={submit}>
          {dialog.kind === "form" && (
            <div className="dialog-fields">
              {dialog.fields.map((field) => (
                <label key={field.name}>
                  <span>
                    {field.label}
                    {field.required && <b aria-hidden="true"> *</b>}
                  </span>
                  {field.type === "select" ? (
                    <select
                      required={field.required}
                      value={values[field.name] ?? ""}
                      onChange={(event) =>
                        setValues({ ...values, [field.name]: event.target.value })
                      }
                    >
                      {!field.required && <option value="">Chưa chọn</option>}
                      {field.options?.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  ) : field.type === "multiselect" ? (
                    <div className="dialog-option-grid">
                      {field.options?.map((option) => {
                        const selected = (values[field.name] ?? "")
                          .split(",")
                          .filter(Boolean);
                        return (
                          <label
                            key={option.value}
                            className={
                              selected.includes(option.value) ? "selected" : ""
                            }
                          >
                            <input
                              type="checkbox"
                              checked={selected.includes(option.value)}
                              onChange={(event) => {
                                const next = event.target.checked
                                  ? [...selected, option.value]
                                  : selected.filter(
                                      (value) => value !== option.value,
                                    );
                                setValues({
                                  ...values,
                                  [field.name]: next.join(","),
                                });
                              }}
                            />
                            <span>{option.label}</span>
                          </label>
                        );
                      })}
                    </div>
                  ) : field.type === "textarea" ? (
                    <textarea
                      required={field.required}
                      placeholder={field.placeholder}
                      value={values[field.name] ?? ""}
                      onChange={(event) =>
                        setValues({ ...values, [field.name]: event.target.value })
                      }
                    />
                  ) : (
                    <input
                      type={field.type === "number" ? "number" : "text"}
                      min={field.min}
                      required={field.required}
                      autoFocus={dialog.fields[0]?.name === field.name}
                      placeholder={field.placeholder}
                      value={values[field.name] ?? ""}
                      onChange={(event) =>
                        setValues({ ...values, [field.name]: event.target.value })
                      }
                    />
                  )}
                  {field.hint && <small>{field.hint}</small>}
                </label>
              ))}
            </div>
          )}
          <footer className="dialog-actions">
            <button
              type="button"
              className="secondary"
              onClick={() => onClose(dialog.kind === "form" ? null : false)}
            >
              {dialog.cancelLabel ?? "Hủy"}
            </button>
            <button
              type="submit"
              className={
                dialog.tone === "danger" ? "danger-button" : "primary"
              }
            >
              {dialog.confirmLabel ?? "Xác nhận"}
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

export function UiProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [dialog, setDialog] = useState<ActiveDialog | null>(null);

  const removeToast = useCallback((id: string) => {
    setToasts((items) => items.filter((item) => item.id !== id));
  }, []);

  const notify = useCallback((input: ToastInput) => {
    const item: ToastItem = {
      id: crypto.randomUUID(),
      title: input.title,
      message: input.message,
      tone: input.tone ?? "info",
      duration: input.duration ?? (input.tone === "error" ? 7000 : 4800),
    };
    setToasts((items) => [...items.slice(-3), item]);
  }, []);

  const confirmDialog = useCallback(
    (options: ConfirmDialogOptions) =>
      new Promise<boolean>((resolve) =>
        setDialog({ kind: "confirm", ...options, resolve }),
      ),
    [],
  );

  const formDialog = useCallback(
    (options: FormDialogOptions) =>
      new Promise<Record<string, string> | null>((resolve) =>
        setDialog({ kind: "form", ...options, resolve }),
      ),
    [],
  );

  const closeDialog = useCallback(
    (value: boolean | Record<string, string> | null) => {
      setDialog((current) => {
        if (!current) return null;
        if (current.kind === "confirm") current.resolve(Boolean(value));
        else current.resolve(value && typeof value === "object" ? value : null);
        return null;
      });
    },
    [],
  );

  const value = useMemo(
    () => ({ notify, confirmDialog, formDialog }),
    [notify, confirmDialog, formDialog],
  );

  return (
    <UiContext.Provider value={value}>
      {children}
      <div className="toast-viewport" aria-live="polite" aria-atomic="true">
        {toasts.map((item) => (
          <ToastCard
            key={item.id}
            item={item}
            onClose={() => removeToast(item.id)}
          />
        ))}
      </div>
      {dialog && (
        <DialogSurface
          key={`${dialog.kind}-${dialog.title}`}
          dialog={dialog}
          onClose={closeDialog}
        />
      )}
    </UiContext.Provider>
  );
}

export function useUi() {
  const context = useContext(UiContext);
  if (!context) throw new Error("useUi must be used inside UiProvider");
  return context;
}

export function useFlashNotifications(error?: string, success?: string) {
  const { notify } = useUi();
  const lastError = useRef("");
  const lastSuccess = useRef("");

  useEffect(() => {
    if (error && error !== lastError.current) {
      lastError.current = error;
      notify({
        title: "Không thể hoàn tất thao tác",
        message: error,
        tone: "error",
      });
    }
    if (!error) lastError.current = "";
  }, [error, notify]);

  useEffect(() => {
    if (success && success !== lastSuccess.current) {
      lastSuccess.current = success;
      notify({ title: "Đã cập nhật", message: success, tone: "success" });
    }
    if (!success) lastSuccess.current = "";
  }, [success, notify]);
}
