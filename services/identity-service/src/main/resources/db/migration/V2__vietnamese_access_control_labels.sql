UPDATE permissions
SET description = CASE code
    WHEN 'profile:read' THEN 'Xem hồ sơ cá nhân'
    WHEN 'profile:write' THEN 'Cập nhật hồ sơ cá nhân'
    WHEN 'staff:read' THEN 'Xem danh sách tài khoản nhân viên'
    WHEN 'staff:write' THEN 'Tạo và cập nhật tài khoản nhân viên'
    WHEN 'staff:lock' THEN 'Khóa và mở khóa tài khoản nhân viên'
    WHEN 'staff:reset-password' THEN 'Yêu cầu đặt lại mật khẩu cho nhân viên'
    WHEN 'roles:read' THEN 'Xem vai trò và quyền truy cập'
    WHEN 'roles:write' THEN 'Tạo, cập nhật vai trò và phân quyền'
    WHEN 'booking:read' THEN 'Xem danh sách đặt phòng'
    WHEN 'booking:write' THEN 'Tạo và cập nhật đặt phòng'
    WHEN 'stay:write' THEN 'Thực hiện nhận phòng và trả phòng'
    WHEN 'room:write' THEN 'Quản lý phòng và tình trạng phòng'
    WHEN 'pricing:write' THEN 'Quản lý giá phòng'
    WHEN 'report:read' THEN 'Xem báo cáo và thống kê'
    WHEN 'ai:staff' THEN 'Sử dụng trợ lý AI dành cho nhân viên'
    ELSE description
END;

UPDATE roles
SET name = CASE code
        WHEN 'CUSTOMER' THEN 'Khách hàng'
        WHEN 'RECEPTIONIST' THEN 'Nhân viên lễ tân'
        WHEN 'HOUSEKEEPER' THEN 'Nhân viên buồng phòng'
        WHEN 'MANAGER' THEN 'Quản lý khách sạn'
        WHEN 'ADMIN' THEN 'Quản trị viên hệ thống'
        ELSE name
    END,
    description = CASE code
        WHEN 'CUSTOMER' THEN 'Khách hàng sử dụng dịch vụ của khách sạn'
        WHEN 'RECEPTIONIST' THEN 'Nhân viên tiếp nhận đặt phòng và phục vụ tại quầy lễ tân'
        WHEN 'HOUSEKEEPER' THEN 'Nhân viên phụ trách vệ sinh, bảo trì và trạng thái phòng'
        WHEN 'MANAGER' THEN 'Quản lý hoạt động kinh doanh và vận hành khách sạn'
        WHEN 'ADMIN' THEN 'Quản trị toàn bộ tài khoản, vai trò và quyền truy cập hệ thống'
        ELSE description
    END
WHERE system_role = TRUE;
