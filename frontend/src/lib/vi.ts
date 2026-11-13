const labels: Record<string, string> = {
  ACTIVE: "Đang hoạt động",
  INACTIVE: "Ngừng hoạt động",
  LOCKED: "Đã khóa",
  PENDING: "Đang chờ xử lý",
  PENDING_PAYMENT: "Chờ thanh toán",
  PENDING_CONFIRMATION: "Chờ xác nhận",
  CONFIRMED: "Đã xác nhận",
  CHECKED_IN: "Đang lưu trú",
  CHECKED_OUT: "Đã trả phòng",
  CANCELLED: "Đã hủy",
  CANCELED: "Đã hủy",
  COMPLETED: "Đã hoàn tất",
  SUCCEEDED: "Thành công",
  FAILED: "Không thành công",
  EXPIRED: "Đã hết hạn",
  REFUNDED: "Đã hoàn tiền",
  PARTIALLY_REFUNDED: "Đã hoàn một phần",
  PROCESSING: "Đang xử lý",
  AVAILABLE: "Sẵn sàng",
  OCCUPIED: "Đang có khách",
  WAITING_CLEANING: "Chờ dọn phòng",
  CLEANING: "Đang dọn phòng",
  MAINTENANCE: "Đang bảo trì",
  OUT_OF_SERVICE: "Tạm ngừng sử dụng",
  OPEN: "Đang mở",
  IN_PROGRESS: "Đang thực hiện",
  DONE: "Đã hoàn tất",
  APPROVED: "Đã phê duyệt",
  REJECTED: "Đã từ chối",
  PUBLISHED: "Đang hiển thị",
  HIDDEN: "Đã ẩn",
  DRAFT: "Bản nháp",
  POSITIVE: "Tích cực",
  NEUTRAL: "Trung lập",
  NEGATIVE: "Tiêu cực",
  LOW: "Thấp",
  MEDIUM: "Trung bình",
  HIGH: "Cao",
  CRITICAL: "Khẩn cấp",
  UNUSUAL_PAYMENT_AMOUNT: "Giá trị thanh toán bất thường",
  REPEATED_PAYMENT_FAILURE: "Thanh toán thất bại nhiều lần",
  CANCELLATION_SPIKE: "Số lượt hủy tăng bất thường",
  HIGH_BOOKING_VELOCITY: "Tạo nhiều lượt đặt phòng trong thời gian ngắn",
  REPEATED_CANCELLATION: "Khách hủy phòng nhiều lần",
  CUSTOMER: "Khách hàng",
  RECEPTIONIST: "Lễ tân",
  HOUSEKEEPER: "Nhân viên buồng phòng",
  MANAGER: "Quản lý",
  ADMIN: "Quản trị viên",
  STAFF: "Nhân viên",
  DEPOSIT: "Tiền đặt cọc",
  BALANCE: "Số tiền còn lại",
  FULL: "Toàn bộ hóa đơn",
  ROOM: "Phòng",
  CLEANLINESS: "Vệ sinh",
  SERVICE: "Dịch vụ",
  LOCATION: "Vị trí",
  VALUE: "Mức giá",
  AMENITIES: "Tiện nghi",
  FEMALE: "Nữ",
  MALE: "Nam",
  OTHER: "Khác",
  CCCD: "Căn cước công dân",
  CMND: "Chứng minh nhân dân",
  PASSPORT: "Hộ chiếu",
  CANCELLATION: "Hủy phòng",
  NO_SHOW: "Không đến nhận phòng",
  FEES: "Các loại phí",
  CHECK_IN_OUT: "Nhận và trả phòng",
  CHILD: "Trẻ em",
  SURCHARGE: "Phụ thu",
  GLOBAL: "Toàn khách sạn",
  ROOM_TYPE: "Theo loại phòng",
  PERCENT: "Theo phần trăm",
  FIXED: "Số tiền cố định",
  TRANSFER: "Đổi phòng",
  EXTENSION: "Gia hạn lưu trú",
  CHECK_IN: "Nhận phòng",
  CHECK_OUT: "Trả phòng",
  REFUND: "Hoàn tiền",
  CREATE: "Tạo mới",
  UPDATE: "Cập nhật",
  DELETE: "Xóa",
  LOGIN: "Đăng nhập",
  LOGOUT: "Đăng xuất",
  ASSIGN: "Phân công",
  ROLE_ASSIGNED: "Cập nhật vai trò",
  STATUS_CHANGED: "Thay đổi trạng thái",
  PRICE_APPROVED: "Phê duyệt giá",
  PRICE_REJECTED: "Từ chối giá",
  POLICY_PUBLISHED: "Công bố chính sách",
  INVOICE_ISSUED: "Phát hành hóa đơn",
  PER_NIGHT: "đêm",
  NIGHT: "đêm",
  PER_PERSON: "người",
  PERSON: "người",
  PER_ITEM: "lần",
  ITEM: "lần",
};

const fieldLabels: Record<string, string> = {
  email: "Email",
  password: "Mật khẩu",
  fullName: "Họ và tên",
  phone: "Số điện thoại",
  checkIn: "Ngày nhận phòng",
  checkOut: "Ngày trả phòng",
  adults: "Số người lớn",
  children: "Số trẻ em",
  rooms: "Số phòng",
  roomCount: "Số phòng",
  documentNumber: "Số giấy tờ",
  nationality: "Quốc tịch",
  promotionCode: "Mã khuyến mãi",
};

const permissionLabels: Record<string, string> = {
  "profile:read": "Xem hồ sơ cá nhân",
  "profile:write": "Cập nhật hồ sơ cá nhân",
  "staff:read": "Xem danh sách tài khoản nhân viên",
  "staff:write": "Tạo và cập nhật tài khoản nhân viên",
  "staff:lock": "Khóa và mở khóa tài khoản nhân viên",
  "staff:reset-password": "Yêu cầu đặt lại mật khẩu cho nhân viên",
  "roles:read": "Xem vai trò và quyền truy cập",
  "roles:write": "Tạo, cập nhật vai trò và phân quyền",
  "booking:read": "Xem danh sách đặt phòng",
  "booking:write": "Tạo và cập nhật đặt phòng",
  "stay:write": "Thực hiện nhận phòng và trả phòng",
  "room:write": "Quản lý phòng và tình trạng phòng",
  "pricing:write": "Quản lý giá phòng",
  "report:read": "Xem báo cáo và thống kê",
  "ai:staff": "Sử dụng trợ lý AI dành cho nhân viên",
};

const systemRoleNames: Record<string, string> = {
  CUSTOMER: "Khách hàng",
  RECEPTIONIST: "Nhân viên lễ tân",
  HOUSEKEEPER: "Nhân viên buồng phòng",
  MANAGER: "Quản lý khách sạn",
  ADMIN: "Quản trị viên hệ thống",
};

const systemRoleDescriptions: Record<string, string> = {
  CUSTOMER: "Khách hàng sử dụng dịch vụ của khách sạn",
  RECEPTIONIST: "Nhân viên tiếp nhận đặt phòng và phục vụ tại quầy lễ tân",
  HOUSEKEEPER: "Nhân viên phụ trách vệ sinh, bảo trì và trạng thái phòng",
  MANAGER: "Quản lý hoạt động kinh doanh và vận hành khách sạn",
  ADMIN: "Quản trị toàn bộ tài khoản, vai trò và quyền truy cập hệ thống",
};

export function viLabel(value?: string | null, fallback = "Chưa cập nhật") {
  if (!value) return fallback;
  return labels[value.toUpperCase()] ?? value.replaceAll("_", " ");
}

export function viField(value?: string | null) {
  if (!value) return "Thông tin";
  const normalized = value.split(".").at(-1)?.replace(/\[\d+\]/g, "") ?? value;
  return fieldLabels[normalized] ?? normalized;
}

/**
 * Dữ liệu seed phục vụ E2E vẫn được giữ trong trang quản trị để kiểm tra và dọn dẹp,
 * nhưng không được phép xuất hiện như một sản phẩm thật với khách hoặc lễ tân.
 */
export function isPresentableRoomType(name?: string | null, code?: string | null) {
  const normalizedName = name?.trim() ?? "";
  const normalizedCode = code?.trim() ?? "";
  const generatedPrefix = /^(?:test|e2e|demo|sample)(?:[\s_-].*)?$/i;
  const generatedPhrase = /(?:kiểm thử|thu nghiem|test data)/i;

  // Mã loại phòng do khách sạn quy định có thể chỉ gồm chữ số (ví dụ "123").
  // Chỉ tên thuần số mới bị xem là dữ liệu kiểm thử; mã số không được phép làm
  // một sản phẩm đã công khai biến mất khỏi trang khách hàng.
  const generatedName =
    /^\d+$/.test(normalizedName) ||
    generatedPrefix.test(normalizedName) ||
    generatedPhrase.test(normalizedName);
  const generatedCode =
    generatedPrefix.test(normalizedCode) || generatedPhrase.test(normalizedCode);

  return !generatedName && !generatedCode;
}

export function viRole(value: string) {
  return labels[value.toUpperCase()] ?? "Vai trò tùy chỉnh";
}

export function viPermission(code: string, fallback?: string | null) {
  return permissionLabels[code.toLowerCase()] ?? fallback ?? "Quyền hệ thống";
}

export function viRoleName(code: string, fallback?: string | null) {
  return systemRoleNames[code.toUpperCase()] ?? fallback ?? "Vai trò tùy chỉnh";
}

export function viRoleDescription(code: string, fallback?: string | null) {
  return (
    systemRoleDescriptions[code.toUpperCase()] ??
    fallback ??
    "Chưa có mô tả"
  );
}

export function viTopic(value: string) {
  return labels[value.toUpperCase()] ?? value;
}

export function viDocumentType(value: string) {
  return labels[value.toUpperCase()] ?? value;
}

export function viAiReason(value: string) {
  const normalized = value.trim();
  const direct: Record<string, string> = {
    capacity_fit: "Phù hợp với số khách",
    budget_fit: "Phù hợp ngân sách",
    amenity_match: "Có tiện nghi bạn quan tâm",
    history_match: "Phù hợp với lựa chọn trước đây",
    highly_rated: "Được khách lưu trú đánh giá tốt",
    availability: "Còn phòng trong thời gian đã chọn",
  };
  if (direct[normalized.toLowerCase()]) return direct[normalized.toLowerCase()];
  return normalized
    .replace(/capacity fit/gi, "phù hợp với số khách")
    .replace(/budget fit/gi, "phù hợp ngân sách")
    .replace(/amenit(?:y|ies) match/gi, "tiện nghi phù hợp")
    .replace(/history match/gi, "phù hợp lịch sử lựa chọn")
    .replaceAll("_", " ");
}

export function viGuestText(value?: string | null, fallback = "Chính sách hủy và thay đổi sẽ được hiển thị rõ trước khi xác nhận.") {
  if (!value?.trim()) return fallback;
  if (/Chính sách giá và hủy được Pricing Service cung cấp khi đặt phòng/i.test(value))
    return "Chính sách giá và hủy sẽ được hiển thị rõ trước khi bạn xác nhận đặt phòng.";
  return value
    .trim()
    .replace(/Pricing Service/gi, "hệ thống giá")
    .replace(/Reservation Service/gi, "hệ thống đặt phòng")
    .replace(/Room Inventory Service/gi, "hệ thống phòng")
    .replace(/check-in/gi, "nhận phòng")
    .replace(/check-out/gi, "trả phòng")
    .replace(/booking/gi, "đặt phòng")
    .replace(/no-show/gi, "không đến nhận phòng");
}

export function viPolicy(policy: unknown) {
  if (typeof policy === "string" && policy.trim()) return policy.trim();
  if (policy && typeof policy === "object") {
    const value = policy as Record<string, unknown>;
    for (const key of ["summary", "description", "message", "policyText"]) {
      if (typeof value[key] === "string" && value[key]) return String(value[key]);
    }
    const freeUntil = value.freeCancellationUntil ?? value.freeUntil;
    if (typeof freeUntil === "string") {
      return `Được hủy miễn phí đến ${new Date(freeUntil).toLocaleString("vi-VN")}.`;
    }
  }
  return "Áp dụng chính sách hủy được hiển thị tại thời điểm đặt phòng.";
}

function translateKnownError(message: string, status?: number) {
  const text = message.trim();
  const lower = text.toLocaleLowerCase("vi-VN");

  if (status === 401 || /unauthori[sz]ed|valid bearer|invalid token|token expired/.test(lower))
    return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục.";
  if (status === 403 || /access is denied|forbidden|permission denied/.test(lower))
    return "Bạn không có quyền thực hiện thao tác này. Hãy liên hệ quản trị viên nếu cần hỗ trợ.";
  if (/current_password_invalid|mật khẩu hiện tại không đúng/.test(lower))
    return "Mật khẩu hiện tại không đúng. Vui lòng nhập lại.";
  if (/password_unchanged|mật khẩu mới phải khác/.test(lower))
    return "Mật khẩu mới phải khác mật khẩu hiện tại.";
  if (/guest list must match reservation occupancy/.test(lower))
    return "Cần nhập đủ thông tin cho đúng số người lớn và trẻ em trong đặt phòng.";
  if (/guest ages do not match adult\/child counts/.test(lower))
    return "Ngày sinh chưa khớp nhóm người lớn/trẻ em đã đặt. Người từ 18 tuổi được tính là người lớn.";
  if (/primary guest.*minimum age|primary guest.*age/.test(lower))
    return "Khách chính phải đủ 18 tuổi. Vui lòng kiểm tra lại ngày sinh.";
  if (/nationality/.test(lower) && /must match|invalid|chưa hợp lệ/.test(lower))
    return "Quốc tịch chưa hợp lệ. Vui lòng chọn một quốc gia trong danh sách.";
  if (/early check.?in|before scheduled check.?in/.test(lower))
    return "Booking chưa đến ngày nhận phòng. Nếu khách nhận sớm, hãy bật xác nhận nhận phòng sớm.";
  if (/room-capacity-exceeded|guest count exceeds selected room capacity/.test(lower))
    return "Số khách vượt quá sức chứa của phòng đã chọn.";
  if (/rate_rule_priority_conflict|same priority.*overlapping/.test(lower))
    return "Khoảng ngày này đã có quy tắc giá cùng mức ưu tiên. Hãy chỉnh quy tắc hiện có hoặc chọn khoảng ngày khác.";
  if (/stay period|stay_to|stayto|date range/.test(lower) && /greater|after|invalid|must/.test(lower))
    return "Khoảng ngày chưa hợp lệ: ngày kết thúc phải sau ngày bắt đầu.";
  if (/room type.*not found|unknown room type|room_type_not_found/.test(lower))
    return "Loại phòng chưa đồng bộ sang hệ thống giá. Vui lòng chờ vài giây rồi thử lưu lại.";
  if (/must not be blank|must not be null|required/.test(lower))
    return `${text.replace(/must not be blank|must not be null/gi, "không được để trống")}.`;
  if (status === 404 || /not found|no handler found/.test(lower))
    return "Không tìm thấy dữ liệu được yêu cầu. Dữ liệu có thể đã được thay đổi hoặc xóa.";
  if (status === 409 || /conflict|already exists|duplicate/.test(lower))
    return "Dữ liệu đã thay đổi hoặc đã tồn tại. Vui lòng tải lại trang và thử lại.";
  if (status === 429 || /too many requests|rate limit|quota/.test(lower))
    return "Hệ thống đang nhận quá nhiều yêu cầu. Vui lòng chờ một lát rồi thử lại.";
  if (status && status >= 500)
    return "Hệ thống đang tạm thời gặp sự cố. Vui lòng thử lại sau ít phút.";
  if (/failed to fetch|networkerror|network request|load failed/.test(lower))
    return "Không thể kết nối đến hệ thống. Vui lòng kiểm tra mạng và trạng thái máy chủ.";
  if (/invalid credentials|bad credentials|email or password/.test(lower))
    return "Email hoặc mật khẩu chưa đúng. Vui lòng kiểm tra và thử lại.";
  if (/invalid otp|verification code|otp.*invalid/.test(lower))
    return "Mã xác minh không đúng hoặc đã hết hạn. Vui lòng yêu cầu mã mới.";
  if (/room.*not available|insufficient inventory|no availability/.test(lower))
    return "Phòng vừa được khách khác giữ hoặc không còn đủ số lượng. Vui lòng chọn phương án khác.";
  if (/promotion.*invalid|invalid.*promotion|coupon/.test(lower))
    return "Mã khuyến mãi không hợp lệ, đã hết hạn hoặc chưa đủ điều kiện áp dụng.";
  if (/payment.*expired/.test(lower))
    return "Yêu cầu thanh toán đã hết hạn. Vui lòng tạo yêu cầu thanh toán mới.";

  // Giữ lại thông báo tiếng Việt có ý nghĩa từ nghiệp vụ, nhưng không để lộ dấu vết kỹ thuật.
  const hasVietnamese = /[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i.test(text);
  if (hasVietnamese && !/exception|stack|sql|constraint|http\s*\d|trace/i.test(text))
    return text;

  return "Yêu cầu chưa thể hoàn tất. Vui lòng kiểm tra thông tin và thử lại.";
}

export interface BackendErrorBody {
  detail?: unknown;
  message?: unknown;
  title?: unknown;
  errors?: unknown;
  violations?: unknown;
  correlationId?: string;
  code?: unknown;
  type?: unknown;
}

export function viApiError(
  error: BackendErrorBody | undefined,
  status?: number,
  correlationId?: string | null,
) {
  let raw = "";
  const validation = error?.errors ?? error?.violations;
  if (validation && typeof validation === "object" && !Array.isArray(validation)) {
    const first = Object.entries(validation as Record<string, unknown>)[0];
    if (first) {
      const detail = Array.isArray(first[1]) ? first[1][0] : first[1];
      raw = `${viField(first[0])}: ${String(detail ?? "chưa hợp lệ")}`;
    }
  }
  if (!raw) {
    raw = String(error?.detail ?? error?.message ?? error?.title ?? "");
  }
  const machineCode = String(error?.code ?? error?.type ?? "");
  const friendly = translateKnownError(`${machineCode} ${raw}`.trim(), status);
  const code = correlationId || error?.correlationId;
  return code ? `${friendly} Mã hỗ trợ: ${code}.` : friendly;
}

export function friendlyError(reason: unknown, fallback = "Yêu cầu chưa thể hoàn tất. Vui lòng thử lại.") {
  if (!(reason instanceof Error) || !reason.message) return fallback;
  return reason.message;
}
