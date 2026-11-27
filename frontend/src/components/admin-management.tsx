"use client";

import { api, ApiRequestError } from "@/lib/api";
import { AppIcon } from "@/components/app-icon";
import type {
  DestinationCatalogItem,
  Review,
  Room,
  RoomType,
  Service,
  UserProfile,
} from "@/lib/types";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { viLabel, viRole, viTopic } from "@/lib/vi";
import { useFlashNotifications, useUi } from "@/components/ui-provider";
import { useAuth } from "@/components/auth-provider";

const FALLBACK_DESTINATIONS: DestinationCatalogItem[] = [
  { code: "HA_LONG", name: "Vịnh Hạ Long", province: "Quảng Ninh", region: "BAC", regionLabel: "Miền Bắc", styleTag: "Biển đảo", propertyName: "HotelAI Hạ Long Bay", landmark: "Bán đảo Tuần Châu", description: "Biệt thự ven vịnh tầm nhìn kỳ quan thiên nhiên thế giới.", displayOrder: 1, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 4800000 },
  { code: "SAPA", name: "Sapa", province: "Lào Cai", region: "BAC", regionLabel: "Miền Bắc", styleTag: "Săn mây & Núi cao", propertyName: "HotelAI Sapa Cloud", landmark: "Thung lũng Mường Hoa", description: "Chalet săn mây nhìn thẳng đỉnh Fansipan và ruộng bậc thang.", displayOrder: 2, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 4200000 },
  { code: "HA_NOI", name: "Hà Nội", province: "Thủ đô Hà Nội", region: "BAC", regionLabel: "Miền Bắc", styleTag: "Di sản & Đô thị", propertyName: "HotelAI Hà Nội Heritage", landmark: "Hồ Hoàn Kiếm · Phố Cổ", description: "Không gian Đông Dương sang trọng giữa trung tâm văn hóa ngàn năm.", displayOrder: 3, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 3600000 },
  { code: "DA_NANG", name: "Đà Nẵng", province: "TP. Đà Nẵng", region: "TRUNG", regionLabel: "Miền Trung", styleTag: "Biển đảo", propertyName: "HotelAI Đà Nẵng Peninsula", landmark: "Bán đảo Sơn Trà · Mỹ Khê", description: "Khu nghỉ dưỡng biệt lập tựa rừng nguyên sinh hướng biển Đông.", displayOrder: 4, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 4500000 },
  { code: "HOI_AN", name: "Hội An", province: "Quảng Nam", region: "TRUNG", regionLabel: "Miền Trung", styleTag: "Di sản & Đô thị", propertyName: "HotelAI Hội An Riverside", landmark: "Sông Thu Bồn · Phố Cổ", description: "Villa ven sông mang đậm hơi thở thương cảng cổ và đèn lồng.", displayOrder: 5, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 3900000 },
  { code: "NHA_TRANG", name: "Nha Trang", province: "Khánh Hòa", region: "TRUNG", regionLabel: "Miền Trung", styleTag: "Biển đảo", propertyName: "HotelAI Nha Trang Coral", landmark: "Vịnh Ninh Vân · Hòn Tre", description: "Biệt thự trên đá và hồ bơi vô cực ôm trọn vịnh san hô xanh ngọc.", displayOrder: 6, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 4600000 },
  { code: "DA_LAT", name: "Đà Lạt", province: "Lâm Đồng", region: "TRUNG", regionLabel: "Miền Trung", styleTag: "Săn mây & Núi cao", propertyName: "HotelAI Đà Lạt Pine", landmark: "Hồ Tuyền Lâm · Đồi Thông", description: "Dinh thự kiến trúc Pháp ẩn mình giữa rừng thông sương mờ.", displayOrder: 7, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 3800000 },
  { code: "PHU_QUOC", name: "Phú Quốc", province: "Kiên Giang", region: "NAM", regionLabel: "Miền Nam", styleTag: "Biển đảo", propertyName: "HotelAI Phú Quốc", landmark: "Bãi Kem · Nam Đảo", description: "Thiên đường biệt thự biển 5 sao chuẩn quốc tế đón hoàng hôn.", displayOrder: 8, active: true, roomTypeCount: 2, availableRooms: 12, minNightlyPrice: 5200000 },
  { code: "HCM", name: "TP. Hồ Chí Minh", province: "TP. Hồ Chí Minh", region: "NAM", regionLabel: "Miền Nam", styleTag: "Di sản & Đô thị", propertyName: "HotelAI Sài Gòn Riverside", landmark: "Bến Bạch Đằng · Quận 1", description: "Skyline suite hiện đại nhìn toàn cảnh sông Sài Gòn hoa lệ.", displayOrder: 9, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 4100000 },
  { code: "VUNG_TAU", name: "Vũng Tàu", province: "Bà Rịa - Vũng Tàu", region: "NAM", regionLabel: "Miền Nam", styleTag: "Biển đảo", propertyName: "HotelAI Vũng Tàu Ocean", landmark: "Bãi Sau · Hồ Tràm", description: "Khu nghỉ dưỡng biển gần Sài Gòn lý tưởng cho kỳ nghỉ cuối tuần.", displayOrder: 10, active: true, roomTypeCount: 1, availableRooms: 6, minNightlyPrice: 3400000 },
];

interface Hotel {
  id: number;
  name: string;
  address: string;
  description?: string;
  phone?: string;
  email?: string;
  active: boolean;
  version: number;
}
interface Amenity {
  id: number | string;
  code?: string;
  name: string;
}
interface Floor {
  id: string;
  code: string;
  name: string;
}
interface Role {
  id: number | string;
  code?: string;
  name: string;
  description: string;
}
interface UserPage {
  content: UserProfile[];
}
interface Promotion {
  id: number | string;
  code: string;
  name: string;
  discountType: string;
  discountValue: number;
  usedCount: number;
  active: boolean;
}
interface AuditEntry {
  id: number;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  createdAt: string;
}
interface AuditPage {
  content: AuditEntry[];
}
interface AdminReviewApi {
  id: string;
  reservationReference?: string;
  rating: number;
  comment?: string;
  topics?: string[];
  status: string;
  hotelResponse?: { content: string } | null;
  aiAnalysis?: {
    sentiment: string;
    score: number;
    topics: string[];
    urgency: string;
    confidence: number;
    draftResponse: string;
    modelVersion: string;
    provider: string;
    analyzedAt: string;
  } | null;
  createdAt: string;
}
interface ReviewPage {
  content: AdminReviewApi[];
}

interface AdminRoomTypeDto {
  id: string;
  code: string;
  name: string;
  description: string;
  maxAdults: number;
  maxChildren: number;
  maxOccupancy: number;
  sizeSqm?: number | null;
  bedDescription?: string | null;
  policySummary?: string | null;
  published: boolean;
  active: boolean;
  amenities: Array<{ id: string; name: string }>;
  version: number;
  destinationCode?: string;
  destinationName?: string;
  propertyName?: string;
}

interface AdminRoomDto {
  id: string;
  roomTypeId: string;
  roomNumber: string;
  floor: { id: string; name: string };
  status: string;
  version: number;
  destinationCode?: string;
  destinationName?: string;
  propertyName?: string;
}

interface AdminServiceDto {
  id: string;
  code: string;
  name: string;
  unit: string;
  currentPrice: { unitPrice: number };
}

export function AdminManagement({
  mode,
}: {
  mode: "inventory" | "governance";
}) {
  return mode === "inventory" ? (
    <InventoryManagement />
  ) : (
    <GovernanceManagement />
  );
}

function InventoryManagement() {
  const { confirmDialog, formDialog } = useUi();
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [destinations, setDestinations] = useState<DestinationCatalogItem[]>(
    FALLBACK_DESTINATIONS,
  );
  const [destinationFilter, setDestinationFilter] = useState<string>("ALL");
  const [destForm, setDestForm] = useState({
    code: "",
    name: "",
    province: "",
    region: "BAC",
    styleTag: "Biển đảo",
    propertyName: "",
    landmark: "",
    description: "",
    displayOrder: "11",
  });
  const [typeDetails, setTypeDetails] = useState<AdminRoomTypeDto[]>([]);
  const [types, setTypes] = useState<RoomType[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [amenities, setAmenities] = useState<Amenity[]>([]);
  const [floors, setFloors] = useState<Floor[]>([]);
  const [services, setServices] = useState<Service[]>([]);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [roomForm, setRoomForm] = useState({
    roomTypeId: "",
    roomNumber: "",
    floorId: "",
  });
  const [rateForm, setRateForm] = useState({
    roomTypeId: "",
    date: "",
    price: "",
    inventory: "",
    minStay: "1",
    closed: false,
  });
  const [bulkRate, setBulkRate] = useState({
    roomTypeId: "",
    from: "",
    to: "",
    weekdayMultiplier: "1",
    weekendMultiplier: "1.2",
    inventory: "",
    minStay: "1",
  });
  const [serviceForm, setServiceForm] = useState({
    code: "",
    name: "",
    unit: "SUẤT",
    price: "",
  });
  const [promoForm, setPromoForm] = useState({
    code: "",
    name: "",
    discountType: "PERCENT",
    discountValue: "10",
    validFrom: "",
    validTo: "",
    maxDiscount: "",
    minBookingAmount: "",
    usageLimit: "",
  });
  const [amenityName, setAmenityName] = useState("");
  const [typeForm, setTypeForm] = useState({
    code: "",
    name: "",
    description: "",
    maxAdults: "2",
    maxChildren: "0",
    basePrice: "",
    sizeM2: "",
    bedType: "KING",
    destinationCode: "PHU_QUOC",
    amenityIds: [] as string[],
  });
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const mutationInFlight = useRef(false);
  useFlashNotifications(error, notice);

  const load = useCallback(async () => {
    const results = await Promise.allSettled([
      api.get<Hotel[]>("/catalog/hotels"),
      api.get<AdminRoomTypeDto[]>("/admin/room-types"),
      api.get<AdminRoomDto[]>("/admin/rooms"),
      api.get<Amenity[]>("/catalog/amenities"),
      api.get<Floor[]>("/admin/floors"),
      api.get<AdminServiceDto[]>("/admin/hotel-services"),
      api.get<Promotion[]>("/admin/pricing/promotions"),
      api
        .get<DestinationCatalogItem[]>("/admin/hotels/destinations")
        .catch(() =>
          api.get<DestinationCatalogItem[]>("/availability/destinations"),
        ),
    ] as const);
    const [
      hotelResult,
      typeResult,
      roomResult,
      amenityResult,
      floorResult,
      serviceResult,
      promotionResult,
      destinationResult,
    ] = results;

    if (results.every((result) => result.status === "rejected")) {
      const firstFailure = results.find(
        (result): result is PromiseRejectedResult =>
          result.status === "rejected",
      );
      throw firstFailure?.reason instanceof Error
        ? firstFailure.reason
        : new Error("Không thể tải dữ liệu quản lý khách sạn.");
    }

    if (hotelResult.status === "fulfilled") setHotels(hotelResult.value);
    if (
      destinationResult.status === "fulfilled" &&
      destinationResult.value.length > 0
    ) {
      setDestinations(destinationResult.value);
    }
    if (roomResult.status === "fulfilled") {
      setRooms(
        roomResult.value.map((room) => ({
          id: room.id,
          roomTypeId: room.roomTypeId,
          roomNumber: room.roomNumber,
          floor: room.floor.name,
          floorId: room.floor.id,
          status: room.status,
          version: room.version,
          destinationCode: room.destinationCode,
          destinationName: room.destinationName,
          propertyName: room.propertyName,
        })),
      );
    }
    if (amenityResult.status === "fulfilled") setAmenities(amenityResult.value);
    if (floorResult.status === "fulfilled") setFloors(floorResult.value);
    if (serviceResult.status === "fulfilled") {
      setServices(
        serviceResult.value.map((service) => ({
          id: service.id,
          hotelId: 1,
          code: service.code,
          name: service.name,
          unit: service.unit,
          price: Number(service.currentPrice.unitPrice),
        })),
      );
    }
    if (promotionResult.status === "fulfilled") {
      setPromotions(promotionResult.value);
    }

    if (typeResult.status === "fulfilled") {
      const typeData = typeResult.value;
      const rateLists = await Promise.all(
        typeData.map((type) =>
          api
            .get<Array<{ amount: number; stayTo: string }>>(
              `/admin/pricing/base-rates?roomTypeId=${type.id}`,
            )
            .catch(() => []),
        ),
      );
      setTypeDetails(typeData);
      setTypes(
        typeData.map((type, index) => ({
          id: type.id,
          hotelId: 1,
          code: type.code,
          name: type.name,
          description: type.description,
          maxAdults: type.maxAdults,
          maxChildren: type.maxChildren,
          basePrice: Number(rateLists[index]?.at(-1)?.amount ?? 0),
          sizeM2: type.sizeSqm ?? undefined,
          bedType: type.bedDescription ?? undefined,
          amenities: type.amenities.map((amenity) => amenity.name),
          published: type.published,
          active: type.active,
          version: type.version,
          destinationCode: type.destinationCode,
          destinationName: type.destinationName,
          propertyName: type.propertyName,
        })),
      );
      const firstTypeId = String(typeData[0]?.id ?? "");
      setRoomForm((current) => ({
        ...current,
        roomTypeId: current.roomTypeId || firstTypeId,
        floorId:
          current.floorId ||
          String(
            floorResult.status === "fulfilled"
              ? (floorResult.value[0]?.id ?? "")
              : "",
          ),
      }));
      setRateForm((current) => ({
        ...current,
        roomTypeId: current.roomTypeId || firstTypeId,
      }));
      setBulkRate((current) => ({
        ...current,
        roomTypeId: current.roomTypeId || firstTypeId,
      }));
    }
  }, []);
  useEffect(() => {
    const timer = window.setTimeout(
      () => void load().catch((reason: Error) => setError(reason.message)),
      0,
    );
    return () => window.clearTimeout(timer);
  }, [load]);

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
  async function addRoom(event: FormEvent) {
    event.preventDefault();
    const completed = await perform(
      () =>
        api.post("/admin/rooms", {
          roomTypeId: roomForm.roomTypeId,
          roomNumber: roomForm.roomNumber,
          floorId: roomForm.floorId,
          note: null,
        }),
      "Đã thêm phòng vật lý.",
    );
    if (completed) setRoomForm({ ...roomForm, roomNumber: "" });
  }
  async function setRate(event: FormEvent) {
    event.preventDefault();
    const exclusiveEnd = new Date(`${rateForm.date}T00:00:00Z`);
    exclusiveEnd.setUTCDate(exclusiveEnd.getUTCDate() + 1);
    await perform(
      () =>
        api.post(
          "/admin/pricing/base-rates",
          {
            roomTypeId: rateForm.roomTypeId,
            amount: Number(rateForm.price),
            stayFrom: rateForm.date,
            stayTo: exclusiveEnd.toISOString().slice(0, 10),
            bookingEffectiveFrom: new Date().toISOString(),
            bookingEffectiveTo: null,
            outlierConfirmed: true,
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã cập nhật giá bán cho ngày đã chọn.",
    );
  }
  async function applyRateRule(event: FormEvent) {
    event.preventDefault();
    await perform(() => {
      const rule = (
        name: string,
        value: number,
        daysOfWeek: string[],
        priority: number,
      ) =>
        api.post(
          "/admin/pricing/rules",
          {
            ruleKey: null,
            roomTypeId: bulkRate.roomTypeId,
            name,
            priceMode: "MULTIPLIER",
            value,
            priority,
            stayFrom: bulkRate.from,
            stayTo: bulkRate.to,
            daysOfWeek,
            minGuests: null,
            maxGuests: null,
            bookingEffectiveFrom: new Date().toISOString(),
            bookingEffectiveTo: null,
            outlierConfirmed: true,
          },
          { "Idempotency-Key": crypto.randomUUID() },
        );
      return Promise.all([
        rule(
          "Hệ số ngày thường",
          Number(bulkRate.weekdayMultiplier),
          ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
          100,
        ),
        rule(
          "Hệ số cuối tuần",
          Number(bulkRate.weekendMultiplier),
          ["SATURDAY", "SUNDAY"],
          110,
        ),
      ]);
    }, "Đã áp dụng quy tắc giá cho toàn bộ khoảng ngày.");
  }
  async function addService(event: FormEvent) {
    event.preventDefault();
    await perform(
      () =>
        api.post("/admin/hotel-services", {
          code: serviceForm.code.toUpperCase(),
          name: serviceForm.name,
          description: serviceForm.name,
          unit: serviceForm.unit,
          serviceDays: [
            "MONDAY",
            "TUESDAY",
            "WEDNESDAY",
            "THURSDAY",
            "FRIDAY",
            "SATURDAY",
            "SUNDAY",
          ],
          serviceStartTime: "00:00:00",
          serviceEndTime: "23:59:00",
          serviceConditions: null,
          active: true,
          unitPrice: Number(serviceForm.price),
          taxRate: 0,
          currency: "VND",
          priceEffectiveAt: new Date().toISOString(),
        }),
      "Đã thêm dịch vụ khách sạn.",
    );
  }
  async function addPromotion(event: FormEvent) {
    event.preventDefault();
    await perform(
      () =>
        api.post(
          "/admin/pricing/promotions",
          {
            promotionKey: null,
            code: promoForm.code,
            name: promoForm.name,
            discountType: promoForm.discountType,
            discountValue: Number(promoForm.discountValue),
            validFrom: new Date(promoForm.validFrom).toISOString(),
            validTo: new Date(promoForm.validTo).toISOString(),
            maxDiscount: promoForm.maxDiscount
              ? Number(promoForm.maxDiscount)
              : null,
            minimumSubtotal: promoForm.minBookingAmount
              ? Number(promoForm.minBookingAmount)
              : null,
            minimumNights: null,
            maximumNights: null,
            applicableRoomTypeIds: [],
            totalQuota: promoForm.usageLimit
              ? Number(promoForm.usageLimit)
              : null,
            perCustomerQuota: 1,
            budgetAmount: null,
            active: true,
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã tạo chương trình khuyến mãi.",
    );
  }
  async function updateHotel(event: FormEvent) {
    event.preventDefault();
    const hotel = hotels[0];
    if (!hotel) return;
    await perform(
      () =>
        api.put(`/admin/hotels/${hotel.id}`, {
          name: hotel.name,
          address: hotel.address,
          description: hotel.description,
          phone: hotel.phone,
          email: hotel.email,
          active: hotel.active,
          expectedVersion: hotel.version,
        }),
      "Đã cập nhật thông tin khách sạn.",
    );
  }
  async function addImage(typeId: number | string) {
    const values = await formDialog({
      title: "Thêm ảnh hạng phòng",
      description:
        "Dán đường dẫn ảnh công khai có chất lượng tốt. Ảnh ngang tỷ lệ 4:3 hoặc 16:10 sẽ hiển thị đẹp nhất.",
      confirmLabel: "Thêm ảnh",
      fields: [
        {
          name: "url",
          label: "Đường dẫn ảnh",
          required: true,
          placeholder: "https://example.com/phong-deluxe.webp",
        },
      ],
    });
    const url = values?.url.trim();
    if (!url) return;
    await perform(
      () =>
        api.post(`/admin/room-types/${typeId}/images`, {
          url,
          altText: "Ảnh loại phòng",
          mediaType: url.toLowerCase().endsWith(".png")
            ? "image/png"
            : url.toLowerCase().match(/\.jpe?g(?:\?|$)/)
              ? "image/jpeg"
              : "image/webp",
          byteSize: 1024,
          displayOrder: 0,
        }),
      "Đã thêm ảnh phòng.",
    );
  }
  async function addAmenity(event: FormEvent) {
    event.preventDefault();
    const completed = await perform(
      () =>
        api.post("/admin/amenities", {
          code: `AMENITY_${crypto.randomUUID().slice(0, 8).toUpperCase()}`,
          name: amenityName,
          description: "",
          iconUrl: null,
        }),
      "Đã thêm tiện nghi.",
    );
    if (completed) setAmenityName("");
  }
  async function addRoomType(event: FormEvent) {
    event.preventDefault();
    const completed = await perform(async () => {
      // Chỉ công khai sau khi giá cơ bản đã được Pricing Service tiếp nhận.
      const created = await api.post<AdminRoomTypeDto>("/admin/room-types", {
        code: typeForm.code,
        name: typeForm.name,
        description: typeForm.description || typeForm.name,
        maxAdults: Number(typeForm.maxAdults),
        maxChildren: Number(typeForm.maxChildren),
        maxOccupancy: Number(typeForm.maxAdults) + Number(typeForm.maxChildren),
        sizeSqm: typeForm.sizeM2 ? Number(typeForm.sizeM2) : null,
        bedDescription: typeForm.bedType,
        published: false,
        amenityIds: typeForm.amenityIds,
        destinationCode: typeForm.destinationCode,
      });
      const baseRatePayload = {
        roomTypeId: created.id,
        amount: Number(typeForm.basePrice),
        stayFrom: new Date().toISOString().slice(0, 10),
        stayTo: "2099-12-31",
        bookingEffectiveFrom: new Date().toISOString(),
        bookingEffectiveTo: null,
        outlierConfirmed: true,
      };
      let lastError: unknown;
      for (let attempt = 0; attempt < 8; attempt += 1) {
        try {
          await api.post("/admin/pricing/base-rates", baseRatePayload, {
            "Idempotency-Key": crypto.randomUUID(),
          });
          lastError = undefined;
          break;
        } catch (reason) {
          lastError = reason;
          await new Promise((resolve) => window.setTimeout(resolve, 500));
        }
      }
      if (lastError) {
        throw new Error(
          `Loại phòng đã được lưu ở trạng thái nháp vì giá khởi điểm chưa đồng bộ. ${
            (lastError as Error).message
          } Hãy lưu lại giá trước khi công khai cho khách.`,
        );
      }

      await api.put(`/admin/room-types/${created.id}`, {
        code: created.code,
        name: created.name,
        description: created.description,
        maxAdults: created.maxAdults,
        maxChildren: created.maxChildren,
        maxOccupancy: created.maxOccupancy,
        sizeSqm: created.sizeSqm ?? null,
        bedDescription: created.bedDescription ?? null,
        published: true,
        active: true,
        amenityIds: created.amenities.map((amenity) => amenity.id),
        expectedVersion: created.version,
        destinationCode: created.destinationCode ?? typeForm.destinationCode,
      });
    }, "Đã tạo loại phòng, đồng bộ giá và công khai cho khách.");
    if (completed) {
      setTypeForm({
        ...typeForm,
        code: "",
        name: "",
        description: "",
        basePrice: "",
      });
    }
  }
  async function toggleRoomTypePublication(type: RoomType) {
    const details = typeDetails.find(
      (candidate) => String(candidate.id) === String(type.id),
    );
    if (!details) {
      setError(
        "Không tìm thấy dữ liệu mới nhất của loại phòng. Vui lòng tải lại trang và thử lại.",
      );
      return;
    }

    const hasPrice = type.basePrice > 0;
    const hasRoom = rooms.some(
      (room) => String(room.roomTypeId) === String(type.id),
    );
    const nextPublished = !Boolean(type.published);

    if (nextPublished && (!hasPrice || !hasRoom)) {
      const missing = [
        !hasPrice ? "giá bán" : "",
        !hasRoom ? "ít nhất một phòng vật lý" : "",
      ].filter(Boolean);
      setError(
        `Chưa thể công khai ${type.name}. Bạn cần bổ sung ${missing.join(
          " và ",
        )} trước.`,
      );
      return;
    }

    if (!nextPublished) {
      const confirmed = await confirmDialog({
        title: `Ngừng công khai ${type.name}?`,
        description:
          "Loại phòng sẽ không còn xuất hiện trong danh mục và kết quả tìm kiếm của khách. Các booking đã tạo vẫn được giữ nguyên.",
        confirmLabel: "Ngừng công khai",
        tone: "danger",
      });
      if (!confirmed) return;
    }

    await perform(
      () =>
        api.put(`/admin/room-types/${details.id}`, {
          code: details.code,
          name: details.name,
          description: details.description,
          maxAdults: details.maxAdults,
          maxChildren: details.maxChildren,
          maxOccupancy: details.maxOccupancy,
          sizeSqm: details.sizeSqm ?? null,
          bedDescription: details.bedDescription ?? null,
          policySummary: details.policySummary ?? null,
          published: nextPublished,
          active: nextPublished ? true : details.active,
          amenityIds: details.amenities.map((amenity) => amenity.id),
          expectedVersion: details.version,
          destinationCode: details.destinationCode,
        }),
      nextPublished
        ? `Đã công khai ${type.name} cho khách hàng.`
        : `Đã ngừng công khai ${type.name}.`,
    );
  }
  async function changeRoomStatus(room: Room) {
    const values = await formDialog({
      title: `Cập nhật phòng ${room.roomNumber}`,
      description:
        "Chọn trạng thái vận hành mới cho phòng. Hệ thống sẽ tự cập nhật khả năng bán phòng.",
      confirmLabel: "Cập nhật trạng thái",
      fields: [
        {
          name: "status",
          label: "Trạng thái mới",
          type: "select",
          required: true,
          defaultValue: room.status,
          options: [
            "AVAILABLE",
            "RESERVED",
            "OCCUPIED",
            "WAITING_CLEANING",
            "MAINTENANCE",
          ].map((value) => ({ value, label: viLabel(value) })),
        },
      ],
    });
    const status = values?.status;
    if (!status || status === room.status) return;
    if (status === "MAINTENANCE" || status === "WAITING_CLEANING") {
      const confirmed = await confirmDialog({
        title: `Tạm ngừng bán phòng ${room.roomNumber}`,
        description:
          "Phòng sẽ được loại khỏi tồn kho đang bán. Các đặt phòng đã xác nhận vẫn được giữ nguyên.",
        confirmLabel: "Xác nhận cập nhật",
        tone: "danger",
      });
      if (!confirmed) return;
    }
    await perform(
      () =>
        api.patch(`/admin/rooms/${room.id}/status`, {
          status,
          reason: "Cập nhật từ trung tâm vận hành",
          expectedVersion: room.version ?? 0,
        }),
      "Đã cập nhật trạng thái phòng.",
    );
  }

  async function editDestination(dest: DestinationCatalogItem) {
    const values = await formDialog({
      title: `Cập nhật cơ sở: ${dest.name}`,
      description:
        "Chỉnh sửa tên cơ sở, địa danh biểu tượng, phong cách nghỉ dưỡng và trạng thái hiển thị trên toàn hệ thống.",
      confirmLabel: "Lưu cơ sở",
      fields: [
        {
          name: "propertyName",
          label: "Tên cơ sở HotelAI",
          required: true,
          defaultValue: dest.propertyName,
        },
        {
          name: "landmark",
          label: "Địa danh / Vị trí",
          required: true,
          defaultValue: dest.landmark,
        },
        {
          name: "styleTag",
          label: "Phong cách nghỉ dưỡng",
          type: "select",
          required: true,
          defaultValue: dest.styleTag,
          options: [
            { value: "Biển đảo", label: "Biển đảo" },
            { value: "Săn mây & Núi cao", label: "Săn mây & Núi cao" },
            { value: "Di sản & Đô thị", label: "Di sản & Đô thị" },
          ],
        },
        {
          name: "description",
          label: "Mô tả điểm đến",
          type: "textarea",
          required: true,
          defaultValue: dest.description,
        },
        {
          name: "active",
          label: "Trạng thái vận hành",
          type: "select",
          required: true,
          defaultValue: dest.active ? "true" : "false",
          options: [
            { value: "true", label: "Đang mở bán trên toàn quốc" },
            { value: "false", label: "Tạm ẩn khỏi bộ sưu tập" },
          ],
        },
      ],
    });
    if (!values) return;
    await perform(
      () =>
        api.put(`/admin/hotels/destinations/${dest.code}`, {
          code: dest.code,
          name: dest.name,
          province: dest.province,
          region: dest.region,
          regionLabel: dest.regionLabel,
          styleTag: values.styleTag,
          propertyName: values.propertyName.trim(),
          landmark: values.landmark.trim(),
          description: values.description.trim(),
          displayOrder: dest.displayOrder,
          active: values.active === "true",
        }),
      `Đã cập nhật thông tin cơ sở ${values.propertyName}.`,
    );
  }

  async function addDestination(event: FormEvent) {
    event.preventDefault();
    const regionLabel =
      destForm.region === "BAC"
        ? "Miền Bắc"
        : destForm.region === "TRUNG"
          ? "Miền Trung"
          : "Miền Nam";
    const completed = await perform(
      () =>
        api.post("/admin/hotels/destinations", {
          code: destForm.code.trim().toUpperCase(),
          name: destForm.name.trim(),
          province: destForm.province.trim(),
          region: destForm.region,
          regionLabel,
          styleTag: destForm.styleTag,
          propertyName: destForm.propertyName.trim(),
          landmark: destForm.landmark.trim(),
          description: destForm.description.trim() || destForm.propertyName.trim(),
          displayOrder: Number(destForm.displayOrder) || destinations.length + 1,
          active: true,
        }),
      `Đã thêm điểm đến mới: ${destForm.name}.`,
    );
    if (completed) {
      setDestForm({
        code: "",
        name: "",
        province: "",
        region: "BAC",
        styleTag: "Biển đảo",
        propertyName: "",
        landmark: "",
        description: "",
        displayOrder: String(destinations.length + 2),
      });
    }
  }

  async function changeRoomTypeDestination(type: RoomType) {
    const details = typeDetails.find(
      (candidate) => String(candidate.id) === String(type.id),
    );
    if (!details) return;
    const values = await formDialog({
      title: `Chuyển cơ sở cho ${type.name}`,
      description:
        "Chọn điểm đến / cơ sở HotelAI mà hạng phòng này trực thuộc. Mọi phòng vật lý thuộc hạng phòng sẽ tự động đồng bộ theo cơ sở mới.",
      confirmLabel: "Cập nhật cơ sở",
      fields: [
        {
          name: "destinationCode",
          label: "Điểm đến / Cơ sở",
          type: "select",
          required: true,
          defaultValue: type.destinationCode ?? "PHU_QUOC",
          options: destinations.map((dest) => ({
            value: dest.code,
            label: `[${dest.regionLabel}] ${dest.propertyName} (${dest.name} · ${dest.province})`,
          })),
        },
      ],
    });
    const nextDest = values?.destinationCode;
    if (!nextDest || nextDest === type.destinationCode) return;
    await perform(
      () =>
        api.put(`/admin/room-types/${details.id}`, {
          code: details.code,
          name: details.name,
          description: details.description,
          maxAdults: details.maxAdults,
          maxChildren: details.maxChildren,
          maxOccupancy: details.maxOccupancy,
          sizeSqm: details.sizeSqm ?? null,
          bedDescription: details.bedDescription ?? null,
          policySummary: details.policySummary ?? null,
          published: details.published,
          active: details.active,
          amenityIds: details.amenities.map((amenity) => amenity.id),
          expectedVersion: details.version,
          destinationCode: nextDest,
        }),
      `Đã chuyển ${type.name} sang cơ sở mới.`,
    );
  }

  const typeMap = useMemo(
    () => new Map(types.map((t) => [String(t.id), t])),
    [types],
  );

  const filteredTypes = useMemo(
    () =>
      destinationFilter === "ALL"
        ? types
        : types.filter(
            (type) =>
              (type.destinationCode ?? "PHU_QUOC") === destinationFilter,
          ),
    [types, destinationFilter],
  );

  const filteredRooms = useMemo(
    () =>
      destinationFilter === "ALL"
        ? rooms
        : rooms.filter((room) => {
            const roomDest =
              room.destinationCode ??
              typeMap.get(String(room.roomTypeId))?.destinationCode ??
              "PHU_QUOC";
            return roomDest === destinationFilter;
          }),
    [rooms, destinationFilter, typeMap],
  );

  return (
    <>
      <div className="workspace-heading">
        <div>
          <span>CHUỖI CƠ SỞ · KHO PHÒNG · GIÁ · KHUYẾN MÃI</span>
          <h1>Kho phòng và doanh thu</h1>
          <p>
            Quản lý dữ liệu bán phòng tập trung trên 10 điểm đến Việt Nam, rõ
            tồn kho theo từng cơ sở.
          </p>
        </div>
        <label className="compact-field">
          Lọc theo Cơ sở / Điểm đến
          <select
            value={destinationFilter}
            onChange={(event) => {
              const next = event.target.value;
              setDestinationFilter(next);
              if (next !== "ALL") {
                setTypeForm((current) => ({ ...current, destinationCode: next }));
              }
            }}
          >
            <option value="ALL">
              Toàn chuỗi · Tất cả {destinations.length} điểm đến
            </option>
            <optgroup label="Miền Bắc">
              {destinations
                .filter((d) => d.region === "BAC")
                .map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.name} · {d.propertyName}
                  </option>
                ))}
            </optgroup>
            <optgroup label="Miền Trung">
              {destinations
                .filter((d) => d.region === "TRUNG")
                .map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.name} · {d.propertyName}
                  </option>
                ))}
            </optgroup>
            <optgroup label="Miền Nam">
              {destinations
                .filter((d) => d.region === "NAM")
                .map((d) => (
                  <option key={d.code} value={d.code}>
                    {d.name} · {d.propertyName}
                  </option>
                ))}
            </optgroup>
          </select>
        </label>
      </div>
      {saving && (
        <div className="saving-banner" role="status" aria-live="polite">
          <span className="spinner" />
          <div>
            <strong>Đang lưu và đồng bộ dữ liệu</strong>
            <small>Danh sách sẽ tự cập nhật ngay khi hoàn tất.</small>
          </div>
        </div>
      )}
      <div className="management-grid" aria-busy={saving}>
        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>THƯƠNG HIỆU & CHUỖI 10 ĐIỂM ĐẾN</span>
              <h2>Thông tin cơ sở & Khu nghỉ dưỡng toàn quốc</h2>
            </div>
            <small>{destinations.length} cơ sở Bắc · Trung · Nam</small>
          </div>
          {hotels[0] && (
            <form className="form-row hotel-form" onSubmit={updateHotel}>
              <label>
                Tên
                <input
                  value={hotels[0].name}
                  onChange={(event) =>
                    setHotels([{ ...hotels[0], name: event.target.value }])
                  }
                />
              </label>
              <label>
                Địa chỉ
                <input
                  value={hotels[0].address}
                  onChange={(event) =>
                    setHotels([{ ...hotels[0], address: event.target.value }])
                  }
                />
              </label>
              <label>
                Điện thoại
                <input
                  value={hotels[0].phone ?? ""}
                  onChange={(event) =>
                    setHotels([{ ...hotels[0], phone: event.target.value }])
                  }
                />
              </label>
              <button className="primary" disabled={saving}>
                {saving ? "Đang lưu…" : "Lưu thay đổi"}
              </button>
            </form>
          )}
          <div className="product-cards" style={{ marginTop: "16px" }}>
            {destinations.map((dest) => {
              const typeCount = types.filter(
                (t) => (t.destinationCode ?? "PHU_QUOC") === dest.code,
              ).length;
              const roomCount = rooms.filter((r) => {
                const code =
                  r.destinationCode ??
                  typeMap.get(String(r.roomTypeId))?.destinationCode ??
                  "PHU_QUOC";
                return code === dest.code;
              }).length;
              return (
                <article key={dest.code}>
                  <div>
                    <span>
                      {dest.regionLabel} · {dest.styleTag}
                    </span>
                    <h3>
                      <AppIcon name="map-pin" className="inline-pin-icon" />
                      {dest.propertyName} ({dest.name})
                    </h3>
                    <p>
                      {dest.landmark} · {dest.province}
                    </p>
                    <p className="catalog-readiness">
                      <span className={dest.active ? "ready" : "draft"}>
                        {dest.active ? "Đang mở bán" : "Tạm ẩn"}
                      </span>
                      <span className="ready">{typeCount} hạng phòng</span>
                      <span className="ready">{roomCount} phòng vật lý</span>
                    </p>
                  </div>
                  <div className="product-card-actions">
                    <button
                      type="button"
                      className={
                        destinationFilter === dest.code ? "primary" : "secondary"
                      }
                      onClick={() =>
                        setDestinationFilter(
                          destinationFilter === dest.code ? "ALL" : dest.code,
                        )
                      }
                    >
                      {destinationFilter === dest.code
                        ? "Đang lọc cơ sở này"
                        : "Lọc kho phòng"}
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      disabled={saving}
                      onClick={() => void editDestination(dest)}
                    >
                      Sửa cơ sở
                    </button>
                  </div>
                </article>
              );
            })}
          </div>
          <details className="management-details">
            <summary>+ Thêm điểm đến / cơ sở HotelAI mới</summary>
            <form
              className="form-grid compact-form"
              onSubmit={addDestination}
              style={{ marginTop: "12px" }}
            >
              <div className="form-row">
                <label>
                  Mã điểm đến
                  <input
                    required
                    pattern="[A-Za-z0-9_-]+"
                    placeholder="VD: QUY_NHON"
                    value={destForm.code}
                    onChange={(e) =>
                      setDestForm({
                        ...destForm,
                        code: e.target.value.toUpperCase(),
                      })
                    }
                  />
                </label>
                <label>
                  Tên điểm đến
                  <input
                    required
                    placeholder="VD: Quy Nhơn"
                    value={destForm.name}
                    onChange={(e) =>
                      setDestForm({ ...destForm, name: e.target.value })
                    }
                  />
                </label>
                <label>
                  Tỉnh / Thành phố
                  <input
                    required
                    placeholder="VD: Bình Định"
                    value={destForm.province}
                    onChange={(e) =>
                      setDestForm({ ...destForm, province: e.target.value })
                    }
                  />
                </label>
              </div>
              <div className="form-row">
                <label>
                  Vùng miền
                  <select
                    value={destForm.region}
                    onChange={(e) =>
                      setDestForm({ ...destForm, region: e.target.value })
                    }
                  >
                    <option value="BAC">Miền Bắc</option>
                    <option value="TRUNG">Miền Trung</option>
                    <option value="NAM">Miền Nam</option>
                  </select>
                </label>
                <label>
                  Phong cách nghỉ dưỡng
                  <select
                    value={destForm.styleTag}
                    onChange={(e) =>
                      setDestForm({ ...destForm, styleTag: e.target.value })
                    }
                  >
                    <option value="Biển đảo">Biển đảo</option>
                    <option value="Săn mây & Núi cao">Săn mây & Núi cao</option>
                    <option value="Di sản & Đô thị">Di sản & Đô thị</option>
                  </select>
                </label>
                <label>
                  Tên cơ sở HotelAI
                  <input
                    required
                    placeholder="VD: HotelAI Quy Nhơn Bay"
                    value={destForm.propertyName}
                    onChange={(e) =>
                      setDestForm({ ...destForm, propertyName: e.target.value })
                    }
                  />
                </label>
                <label>
                  Địa danh nổi bật
                  <input
                    required
                    placeholder="VD: Kỳ Co · Eo Gió"
                    value={destForm.landmark}
                    onChange={(e) =>
                      setDestForm({ ...destForm, landmark: e.target.value })
                    }
                  />
                </label>
              </div>
              <label>
                Mô tả điểm đến
                <input
                  required
                  placeholder="Mô tả ngắn gọn trải nghiệm nghỉ dưỡng đặc trưng tại điểm đến"
                  value={destForm.description}
                  onChange={(e) =>
                    setDestForm({ ...destForm, description: e.target.value })
                  }
                />
              </label>
              <button className="primary" disabled={saving}>
                Thêm cơ sở vào chuỗi
              </button>
            </form>
          </details>
        </section>
        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>LOẠI PHÒNG & TIỆN NGHI</span>
              <h2>
                Sản phẩm đang bán{" "}
                {destinationFilter !== "ALL"
                  ? `(${destinations.find((d) => d.code === destinationFilter)?.name ?? destinationFilter})`
                  : `(${filteredTypes.length} hạng phòng)`}
              </h2>
            </div>
            <small>{amenities.length} tiện nghi</small>
          </div>
          <div className="product-cards">
            {filteredTypes.map((type) => {
              const hasPrice = type.basePrice > 0;
              const hasRoom = rooms.some(
                (room) => String(room.roomTypeId) === String(type.id),
              );
              const canPublish = hasPrice && hasRoom;
              const destInfo = destinations.find(
                (d) => d.code === (type.destinationCode ?? "PHU_QUOC"),
              );
              return (
                <article key={type.id}>
                  <div>
                    <span>
                      <AppIcon name="map-pin" className="inline-pin-icon" />
                      {type.propertyName ??
                        destInfo?.propertyName ??
                        "HotelAI Phú Quốc"}{" "}
                      · {type.destinationName ?? destInfo?.name ?? "Phú Quốc"}
                    </span>
                    <h3>{type.name}</h3>
                    <p>
                      {type.maxAdults} người lớn · {type.amenities.join(" · ")}
                    </p>
                    <p className="catalog-readiness">
                      <span className={type.published ? "ready" : "draft"}>
                        {type.published ? "Đã công khai" : "Bản nháp"}
                      </span>
                      <span className={hasPrice ? "ready" : "warning"}>
                        {hasPrice ? "Đã có giá" : "Chưa có giá"}
                      </span>
                      <span className={hasRoom ? "ready" : "warning"}>
                        {hasRoom ? "Đã có phòng" : "Chưa có phòng"}
                      </span>
                    </p>
                  </div>
                  <strong>
                    {type.basePrice > 0
                      ? `${new Intl.NumberFormat("vi-VN").format(type.basePrice)}đ`
                      : "Chưa mở giá"}
                  </strong>
                  <div className="product-card-actions">
                    <button
                      type="button"
                      className={type.published ? "secondary" : "primary"}
                      disabled={saving}
                      onClick={() => void toggleRoomTypePublication(type)}
                      title={
                        !type.published && !canPublish
                          ? "Cần có giá bán và ít nhất một phòng vật lý trước khi công khai"
                          : undefined
                      }
                    >
                      {type.published ? "Ngừng công khai" : "Công khai"}
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      disabled={saving}
                      onClick={() => void changeRoomTypeDestination(type)}
                    >
                      Đổi cơ sở
                    </button>
                    <button
                      type="button"
                      className="secondary"
                      disabled={saving}
                      onClick={() => void addImage(type.id)}
                    >
                      + Ảnh
                    </button>
                  </div>
                  {!type.published && !canPublish && (
                    <small className="catalog-publish-hint">
                      Bổ sung{" "}
                      {!hasPrice && !hasRoom
                        ? "giá và phòng"
                        : !hasPrice
                          ? "giá bán"
                          : "phòng vật lý"}{" "}
                      để công khai.
                    </small>
                  )}
                </article>
              );
            })}
          </div>
          <details className="management-details">
            <summary>+ Tạo loại phòng hoặc tiện nghi</summary>
            <div className="management-details-grid">
              <form className="form-grid compact-form" onSubmit={addRoomType}>
                <h3>Loại phòng mới</h3>
                <label>
                  Điểm đến / Cơ sở trực thuộc
                  <select
                    required
                    value={typeForm.destinationCode}
                    onChange={(event) =>
                      setTypeForm({
                        ...typeForm,
                        destinationCode: event.target.value,
                      })
                    }
                  >
                    <optgroup label="Miền Bắc">
                      {destinations
                        .filter((d) => d.region === "BAC")
                        .map((d) => (
                          <option key={d.code} value={d.code}>
                            {d.name} ({d.province}) · {d.propertyName}
                          </option>
                        ))}
                    </optgroup>
                    <optgroup label="Miền Trung">
                      {destinations
                        .filter((d) => d.region === "TRUNG")
                        .map((d) => (
                          <option key={d.code} value={d.code}>
                            {d.name} ({d.province}) · {d.propertyName}
                          </option>
                        ))}
                    </optgroup>
                    <optgroup label="Miền Nam">
                      {destinations
                        .filter((d) => d.region === "NAM")
                        .map((d) => (
                          <option key={d.code} value={d.code}>
                            {d.name} ({d.province}) · {d.propertyName}
                          </option>
                        ))}
                    </optgroup>
                  </select>
                </label>
                <div className="form-row">
                  <label>
                    Mã
                    <input
                      required
                      pattern="[A-Za-z0-9_-]+"
                      maxLength={50}
                      placeholder="VD: DELUXE_TWIN"
                      title="Chỉ dùng chữ không dấu, số, dấu gạch ngang hoặc gạch dưới"
                      value={typeForm.code}
                      onChange={(event) =>
                        setTypeForm({
                          ...typeForm,
                          code: event.target.value.toUpperCase(),
                        })
                      }
                    />
                  </label>
                  <label>
                    Tên
                    <input
                      required
                      value={typeForm.name}
                      onChange={(event) =>
                        setTypeForm({ ...typeForm, name: event.target.value })
                      }
                    />
                  </label>
                </div>
                <label>
                  Mô tả
                  <input
                    value={typeForm.description}
                    onChange={(event) =>
                      setTypeForm({
                        ...typeForm,
                        description: event.target.value,
                      })
                    }
                  />
                </label>
                <div className="form-row">
                  <label>
                    Người lớn
                    <input
                      type="number"
                      min="1"
                      value={typeForm.maxAdults}
                      onChange={(event) =>
                        setTypeForm({
                          ...typeForm,
                          maxAdults: event.target.value,
                        })
                      }
                    />
                  </label>
                  <label>
                    Trẻ em
                    <input
                      type="number"
                      min="0"
                      value={typeForm.maxChildren}
                      onChange={(event) =>
                        setTypeForm({
                          ...typeForm,
                          maxChildren: event.target.value,
                        })
                      }
                    />
                  </label>
                  <label>
                    Giá cơ bản
                    <input
                      type="number"
                      min="1000"
                      step="1000"
                      required
                      value={typeForm.basePrice}
                      onChange={(event) =>
                        setTypeForm({
                          ...typeForm,
                          basePrice: event.target.value,
                        })
                      }
                    />
                  </label>
                </div>
                <fieldset className="check-field">
                  <legend>Tiện nghi</legend>
                  {amenities.map((amenity) => (
                    <label key={amenity.id}>
                      <input
                        type="checkbox"
                        checked={typeForm.amenityIds.includes(
                          String(amenity.id),
                        )}
                        onChange={(event) =>
                          setTypeForm({
                            ...typeForm,
                            amenityIds: event.target.checked
                              ? [...typeForm.amenityIds, String(amenity.id)]
                              : typeForm.amenityIds.filter(
                                  (id) => id !== String(amenity.id),
                                ),
                          })
                        }
                      />
                      {amenity.name}
                    </label>
                  ))}
                </fieldset>
                <button className="primary" disabled={saving}>
                  {saving ? "Đang tạo…" : "Tạo loại phòng"}
                </button>
              </form>
              <form className="form-grid compact-form" onSubmit={addAmenity}>
                <h3>Tiện nghi mới</h3>
                <label>
                  Tên tiện nghi
                  <input
                    required
                    value={amenityName}
                    onChange={(event) => setAmenityName(event.target.value)}
                  />
                </label>
                <button className="secondary" disabled={saving}>
                  Thêm tiện nghi
                </button>
              </form>
            </div>
          </details>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>PHÒNG VẬT LÝ</span>
              <h2>{filteredRooms.length} phòng</h2>
            </div>
          </div>
          <div className="room-chips">
            {filteredRooms.map((room) => {
              const roomDestName =
                room.destinationName ??
                typeMap.get(String(room.roomTypeId))?.destinationName ??
                "Phú Quốc";
              return (
                <button
                  key={room.id}
                  className={`room-chip ${room.status.toLowerCase()}`}
                  disabled={saving}
                  onClick={() => void changeRoomStatus(room)}
                  title={`${room.roomNumber} · ${roomDestName} · ${viLabel(room.status)}`}
                >
                  {room.roomNumber}
                  <small>
                    {roomDestName} · {viLabel(room.status)}
                  </small>
                </button>
              );
            })}
          </div>
          <form className="form-grid compact-form" onSubmit={addRoom}>
            <h3>Thêm phòng</h3>
            <label>
              Loại phòng
              <select
                value={roomForm.roomTypeId}
                onChange={(event) =>
                  setRoomForm({ ...roomForm, roomTypeId: event.target.value })
                }
              >
                {Array.from(
                  new Set(
                    filteredTypes.map(
                      (type) => type.destinationName ?? "Phú Quốc",
                    ),
                  ),
                ).map((destLabel) => (
                  <optgroup label={destLabel} key={destLabel}>
                    {filteredTypes
                      .filter(
                        (type) =>
                          (type.destinationName ?? "Phú Quốc") === destLabel,
                      )
                      .map((type) => (
                        <option value={type.id} key={type.id}>
                          {type.name}
                        </option>
                      ))}
                  </optgroup>
                ))}
              </select>
            </label>
            <div className="form-row">
              <label>
                Số phòng
                <input
                  required
                  value={roomForm.roomNumber}
                  onChange={(event) =>
                    setRoomForm({ ...roomForm, roomNumber: event.target.value })
                  }
                />
              </label>
              <label>
                Tầng
                <select
                  required
                  value={roomForm.floorId}
                  onChange={(event) =>
                    setRoomForm({ ...roomForm, floorId: event.target.value })
                  }
                >
                  {floors.map((floor) => (
                    <option key={floor.id} value={floor.id}>
                      {floor.name}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <button className="primary" disabled={saving}>
              {saving ? "Đang thêm…" : "Thêm phòng"}
            </button>
          </form>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>GIÁ ĐỘNG</span>
              <h2>Giá theo ngày</h2>
            </div>
          </div>
          <form className="form-grid compact-form" onSubmit={setRate}>
            <label>
              Loại phòng
              <select
                value={rateForm.roomTypeId}
                onChange={(event) =>
                  setRateForm({ ...rateForm, roomTypeId: event.target.value })
                }
              >
                {Array.from(
                  new Set(
                    filteredTypes.map(
                      (type) => type.destinationName ?? "Phú Quốc",
                    ),
                  ),
                ).map((destLabel) => (
                  <optgroup label={destLabel} key={destLabel}>
                    {filteredTypes
                      .filter(
                        (type) =>
                          (type.destinationName ?? "Phú Quốc") === destLabel,
                      )
                      .map((type) => (
                        <option value={type.id} key={type.id}>
                          {type.name}
                        </option>
                      ))}
                  </optgroup>
                ))}
              </select>
            </label>
            <div className="form-row">
              <label>
                Ngày
                <input
                  type="date"
                  required
                  value={rateForm.date}
                  onChange={(event) =>
                    setRateForm({ ...rateForm, date: event.target.value })
                  }
                />
              </label>
              <label>
                Giá
                <input
                  type="number"
                  min="1000"
                  step="1000"
                  required
                  value={rateForm.price}
                  onChange={(event) =>
                    setRateForm({ ...rateForm, price: event.target.value })
                  }
                />
              </label>
            </div>
            <p className="form-hint">
              Giá này chỉ áp dụng cho đêm đã chọn. Tồn mở bán được hệ thống tự
              tính từ phòng vật lý, phòng đang giữ và phòng đang bảo trì.
            </p>
            <button className="primary" disabled={saving}>
              Lưu giá ngày
            </button>
          </form>
          <details className="management-details">
            <summary>Giá cuối tuần / mùa cao điểm</summary>
            <form className="form-grid compact-form" onSubmit={applyRateRule}>
              <label>
                Loại phòng
                <select
                  value={bulkRate.roomTypeId}
                  onChange={(event) =>
                    setBulkRate({ ...bulkRate, roomTypeId: event.target.value })
                  }
                >
                  {filteredTypes.map((type) => (
                    <option value={type.id} key={type.id}>
                      [{type.destinationName ?? "Phú Quốc"}] {type.name}
                    </option>
                  ))}
                </select>
              </label>
              <div className="form-row">
                <label>
                  Từ ngày
                  <input
                    type="date"
                    required
                    value={bulkRate.from}
                    onChange={(event) =>
                      setBulkRate({ ...bulkRate, from: event.target.value })
                    }
                  />
                </label>
                <label>
                  Đến ngày
                  <input
                    type="date"
                    required
                    min={bulkRate.from}
                    value={bulkRate.to}
                    onChange={(event) =>
                      setBulkRate({ ...bulkRate, to: event.target.value })
                    }
                  />
                </label>
              </div>
              <div className="form-row">
                <label>
                  Hệ số ngày thường
                  <input
                    type="number"
                    step="0.05"
                    min="0.1"
                    value={bulkRate.weekdayMultiplier}
                    onChange={(event) =>
                      setBulkRate({
                        ...bulkRate,
                        weekdayMultiplier: event.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Hệ số cuối tuần
                  <input
                    type="number"
                    step="0.05"
                    min="0.1"
                    value={bulkRate.weekendMultiplier}
                    onChange={(event) =>
                      setBulkRate({
                        ...bulkRate,
                        weekendMultiplier: event.target.value,
                      })
                    }
                  />
                </label>
              </div>
              <button className="secondary" disabled={saving}>
                Áp dụng theo khoảng ngày
              </button>
            </form>
          </details>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>DỊCH VỤ</span>
              <h2>Danh mục bổ sung</h2>
            </div>
            <b>{services.length}</b>
          </div>
          <ul className="simple-list">
            {services.map((service) => (
              <li key={service.id}>
                <span>{service.name}</span>
                <strong>
                  {new Intl.NumberFormat("vi-VN").format(service.price)}đ/
                  {service.unit}
                </strong>
              </li>
            ))}
          </ul>
          <form className="form-grid compact-form" onSubmit={addService}>
            <h3>Thêm dịch vụ</h3>
            <div className="form-row">
              <label>
                Mã
                <input
                  required
                  value={serviceForm.code}
                  onChange={(event) =>
                    setServiceForm({ ...serviceForm, code: event.target.value })
                  }
                />
              </label>
              <label>
                Tên
                <input
                  required
                  value={serviceForm.name}
                  onChange={(event) =>
                    setServiceForm({ ...serviceForm, name: event.target.value })
                  }
                />
              </label>
            </div>
            <div className="form-row">
              <label>
                Đơn vị
                <input
                  required
                  value={serviceForm.unit}
                  onChange={(event) =>
                    setServiceForm({ ...serviceForm, unit: event.target.value })
                  }
                />
              </label>
              <label>
                Giá
                <input
                  type="number"
                  required
                  value={serviceForm.price}
                  onChange={(event) =>
                    setServiceForm({
                      ...serviceForm,
                      price: event.target.value,
                    })
                  }
                />
              </label>
            </div>
            <button className="primary" disabled={saving}>
              Thêm dịch vụ
            </button>
          </form>
        </section>
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span>KHUYẾN MÃI</span>
              <h2>Mã ưu đãi</h2>
            </div>
            <b>{promotions.length}</b>
          </div>
          <ul className="simple-list">
            {promotions.map((promotion) => (
              <li key={promotion.id}>
                <span>
                  <b>{promotion.code}</b> · {promotion.name}
                </span>
                <strong>
                  {promotion.discountValue}
                  {promotion.discountType === "PERCENT" ? "%" : "đ"}
                </strong>
              </li>
            ))}
          </ul>
          <form className="form-grid compact-form" onSubmit={addPromotion}>
            <h3>Tạo khuyến mãi</h3>
            <div className="form-row">
              <label>
                Mã
                <input
                  required
                  value={promoForm.code}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      code: event.target.value.toUpperCase(),
                    })
                  }
                />
              </label>
              <label>
                Tên
                <input
                  required
                  value={promoForm.name}
                  onChange={(event) =>
                    setPromoForm({ ...promoForm, name: event.target.value })
                  }
                />
              </label>
            </div>
            <div className="form-row">
              <label>
                Loại
                <select
                  value={promoForm.discountType}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      discountType: event.target.value,
                    })
                  }
                >
                  <option value="PERCENT">Theo phần trăm</option>
                  <option value="FIXED">Số tiền cố định</option>
                </select>
              </label>
              <label>
                Giá trị
                <input
                  type="number"
                  required
                  value={promoForm.discountValue}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      discountValue: event.target.value,
                    })
                  }
                />
              </label>
            </div>
            <div className="form-row">
              <label>
                Từ ngày
                <input
                  type="datetime-local"
                  required
                  value={promoForm.validFrom}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      validFrom: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                Đến ngày
                <input
                  type="datetime-local"
                  required
                  value={promoForm.validTo}
                  onChange={(event) =>
                    setPromoForm({ ...promoForm, validTo: event.target.value })
                  }
                />
              </label>
            </div>
            <div className="form-row">
              <label>
                Giảm tối đa
                <input
                  type="number"
                  value={promoForm.maxDiscount}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      maxDiscount: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                Đơn tối thiểu
                <input
                  type="number"
                  value={promoForm.minBookingAmount}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      minBookingAmount: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                Lượt sử dụng
                <input
                  type="number"
                  min="1"
                  value={promoForm.usageLimit}
                  onChange={(event) =>
                    setPromoForm({
                      ...promoForm,
                      usageLimit: event.target.value,
                    })
                  }
                />
              </label>
            </div>
            <button className="primary" disabled={saving}>
              Tạo mã
            </button>
          </form>
        </section>
      </div>
    </>
  );
}

function GovernanceManagement() {
  const { user: currentUser } = useAuth();
  const { formDialog } = useUi();
  const isAdmin = currentUser?.roles.includes("ADMIN") ?? false;
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditEntry[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const mutationInFlight = useRef(false);
  useFlashNotifications(error, notice);
  const [staffForm, setStaffForm] = useState({
    email: "",
    fullName: "",
    phone: "",
    role: "RECEPTIONIST",
  });

  const load = useCallback(async () => {
    const results = await Promise.allSettled([
      isAdmin
        ? api.get<UserPage>("/admin/staff?size=100")
        : Promise.resolve({ content: [] }),
      isAdmin ? api.get<Role[]>("/admin/roles") : Promise.resolve([]),
      api.get<ReviewPage>("/admin/reviews?size=100"),
      isAdmin
        ? api.get<AuditPage>("/admin/audit-logs?size=20")
        : Promise.resolve({ content: [] }),
    ]);
    if (results[0].status === "fulfilled") setUsers(results[0].value.content);
    if (results[1].status === "fulfilled") setRoles(results[1].value);
    if (results[2].status === "fulfilled")
      setReviews(
        results[2].value.content.map((review) => ({
          id: review.id,
          bookingCode: review.reservationReference ?? "—",
          rating: review.rating,
          comment: review.comment,
          status: review.status,
          sentiment: review.aiAnalysis?.sentiment,
          sentimentScore: review.aiAnalysis?.score,
          sentimentTopics: review.aiAnalysis?.topics ?? [],
          urgency: review.aiAnalysis?.urgency,
          aiConfidence: review.aiAnalysis?.confidence,
          aiDraftResponse: review.aiAnalysis?.draftResponse,
          aiModelVersion: review.aiAnalysis?.modelVersion,
          aiProvider: review.aiAnalysis?.provider,
          hotelResponse: review.hotelResponse?.content,
          createdAt: review.createdAt,
        })),
      );
    if (results[3].status === "fulfilled")
      setAuditLogs(results[3].value.content);
    if (results.every((result) => result.status === "rejected"))
      setError(
        "Tài khoản hiện tại chưa có quyền quản trị người dùng hoặc đánh giá.",
      );
  }, [isAdmin]);
  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);
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
  async function changeRoles(user: UserProfile) {
    const current = roles
      .filter((role) =>
        user.roles.includes(role.code ?? role.name.toUpperCase()),
      )
      .map((role) => role.code ?? role.id)
      .join(",");
    const values = await formDialog({
      title: `Phân quyền cho ${user.fullName}`,
      description:
        "Chỉ các khu vực phù hợp với vai trò được chọn mới xuất hiện trong trung tâm điều hành.",
      confirmLabel: "Lưu phân quyền",
      fields: [
        {
          name: "roles",
          label: "Vai trò được cấp",
          type: "multiselect",
          required: true,
          defaultValue: current,
          options: roles
            .filter((role) => role.code !== "CUSTOMER")
            .map((role) => ({
              value: String(role.code ?? role.id).toUpperCase(),
              label: role.name,
            })),
          hint: "Tài khoản phải có ít nhất một vai trò nhân viên.",
        },
      ],
    });
    const value = values?.roles;
    if (!value) return;
    await perform(
      () =>
        api.put(`/admin/staff/${user.id}/roles`, {
          roles: value
            .split(",")
            .map((item) => item.trim().toUpperCase())
            .filter(Boolean),
        }),
      "Đã cập nhật phân quyền.",
    );
  }
  async function createStaff(event: FormEvent) {
    event.preventDefault();
    const recipient = staffForm.email.trim();
    const completed = await perform(
      () =>
        api.post(
          "/admin/staff",
          {
            email: staffForm.email,
            fullName: staffForm.fullName,
            phone: staffForm.phone || undefined,
            roles: [staffForm.role],
          },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      `Đã tạo tài khoản. Hướng dẫn được gửi tới ${recipient}. Nhân viên mở Inbox/Spam, hoặc vào trang Đăng nhập → Quên mật khẩu để nhận mã mới.`,
    );
    if (completed) {
      setStaffForm({
        email: "",
        fullName: "",
        phone: "",
        role: "RECEPTIONIST",
      });
    }
  }
  async function respond(review: Review) {
    const values = await formDialog({
      title: "Phản hồi đánh giá của khách",
      description: `Phản hồi công khai cho đánh giá ${review.bookingCode}. Hãy giữ nội dung lịch sự, cụ thể và hướng đến giải pháp.`,
      confirmLabel: "Đăng phản hồi",
      fields: [
        {
          name: "content",
          label: "Nội dung phản hồi",
          type: "textarea",
          required: true,
          defaultValue: review.hotelResponse ?? review.aiDraftResponse ?? "",
          placeholder: "Cảm ơn quý khách đã chia sẻ…",
        },
      ],
    });
    const content = values?.content.trim();
    if (!content) return;
    await perform(
      () =>
        api.put(
          `/admin/reviews/${review.id}/response`,
          { content },
          { "Idempotency-Key": crypto.randomUUID() },
        ),
      "Đã phản hồi đánh giá.",
    );
  }

  const negativeRate = reviews.length
    ? Math.round(
        (reviews.filter((review) => review.sentiment === "NEGATIVE").length /
          reviews.length) *
          100,
      )
    : 0;
  const topicCounts = reviews
    .flatMap((review) => review.sentimentTopics ?? [])
    .reduce<Record<string, number>>(
      (counts, topic) => ({ ...counts, [topic]: (counts[topic] ?? 0) + 1 }),
      {},
    );
  const topTopic = Object.entries(topicCounts).sort(
    (left, right) => right[1] - left[1],
  )[0];

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
          <span>TÀI KHOẢN, ĐÁNH GIÁ VÀ NHẬT KÝ</span>
          <h1>Khách hàng và hệ thống</h1>
          <p>
            Phân quyền minh bạch, lắng nghe trải nghiệm và kiểm soát nội dung.
          </p>
        </div>
      </div>
      <div className="management-grid governance-grid">
        {isAdmin && (
          <section className="panel span-2">
            <div className="panel-heading">
              <div>
                <span>NGƯỜI DÙNG VÀ PHÂN QUYỀN</span>
                <h2>Quản lý tài khoản</h2>
              </div>
              <b>{users.length}</b>
            </div>
            <form className="form-grid compact-form" onSubmit={createStaff}>
              <h3>Tạo tài khoản nhân viên</h3>
              <label>
                Họ tên
                <input
                  required
                  value={staffForm.fullName}
                  onChange={(event) =>
                    setStaffForm({ ...staffForm, fullName: event.target.value })
                  }
                />
              </label>
              <label>
                Email
                <input
                  type="email"
                  required
                  value={staffForm.email}
                  onChange={(event) =>
                    setStaffForm({ ...staffForm, email: event.target.value })
                  }
                />
              </label>
              <label>
                Điện thoại
                <input
                  value={staffForm.phone}
                  onChange={(event) =>
                    setStaffForm({ ...staffForm, phone: event.target.value })
                  }
                />
              </label>
              <label>
                Vai trò ban đầu
                <select
                  value={staffForm.role}
                  onChange={(event) =>
                    setStaffForm({ ...staffForm, role: event.target.value })
                  }
                >
                  {roles
                    .filter((role) => role.code !== "CUSTOMER")
                    .map((role) => (
                      <option key={role.id} value={role.code ?? role.name}>
                        {role.name}
                      </option>
                    ))}
                </select>
              </label>
              <button className="primary">Tạo nhân viên</button>
            </form>
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Người dùng</th>
                    <th>Vai trò</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <tr key={user.id}>
                      <td>
                        <strong>{user.fullName}</strong>
                        <small>{user.email}</small>
                      </td>
                      <td>{user.roles.map(viRole).join(", ")}</td>
                      <td>
                        <span className="status">{viLabel(user.status)}</span>
                      </td>
                      <td>
                        <div className="row-actions">
                          <button onClick={() => void changeRoles(user)}>
                            Phân quyền
                          </button>
                          <button
                            onClick={() =>
                              void perform(
                                () =>
                                  api.patch(`/admin/staff/${user.id}/status`, {
                                    status:
                                      user.status === "ACTIVE"
                                        ? "LOCKED"
                                        : "ACTIVE",
                                  }),
                                "Đã cập nhật trạng thái tài khoản.",
                              )
                            }
                          >
                            {user.status === "ACTIVE" ? "Khóa" : "Mở"}
                          </button>
                          <button
                            onClick={() =>
                              void perform(
                                () =>
                                  api.post(
                                    `/admin/staff/${user.id}/reset-password`,
                                  ),
                                "Đã gửi yêu cầu đặt lại mật khẩu.",
                              )
                            }
                          >
                            Đặt lại mật khẩu
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
        <section className="panel span-2">
          <div className="panel-heading">
            <div>
              <span>PHÂN TÍCH CẢM XÚC KHÁCH HÀNG</span>
              <h2>Đánh giá của khách</h2>
            </div>
            <div className="sentiment-summary">
              <span>
                <b>{negativeRate}%</b> tiêu cực
              </span>
              <span>
                <b>{topTopic ? viTopic(topTopic[0]) : "—"}</b> chủ đề nổi bật
              </span>
              <span>
                <b>{reviews.length}</b> đánh giá
              </span>
            </div>
          </div>
          <div className="review-list">
            {reviews.map((review) => (
              <article key={review.id}>
                <div className="review-score">
                  <strong>{review.rating}</strong>
                  <small>/5</small>
                </div>
                <div>
                  <div className="review-meta">
                    <b>{review.bookingCode}</b>
                    <span
                      className={`sentiment sentiment-${review.sentiment?.toLowerCase()}`}
                    >
                      {review.sentiment
                        ? viLabel(review.sentiment)
                        : "Chưa phân tích"}
                    </span>
                    {review.sentimentTopics?.map((topic) => (
                      <span key={topic} className="topic">
                        {viTopic(topic)}
                      </span>
                    ))}
                    {review.urgency && (
                      <span
                        className={`risk risk-${review.urgency.toLowerCase()}`}
                      >
                        {viLabel(review.urgency)}
                      </span>
                    )}
                  </div>
                  <p>{review.comment || "Không có nội dung"}</p>
                  {review.aiModelVersion && (
                    <small>
                      Độ tin cậy phân tích{" "}
                      {Math.round((review.aiConfidence ?? 0) * 100)}%
                    </small>
                  )}
                  {review.hotelResponse && (
                    <blockquote>Khách sạn: {review.hotelResponse}</blockquote>
                  )}
                </div>
                <div className="row-actions">
                  <button onClick={() => void respond(review)}>Phản hồi</button>
                  <button
                    onClick={() =>
                      void perform(
                        () =>
                          api.patch(
                            `/admin/reviews/${review.id}/moderation`,
                            {
                              action:
                                review.status === "PUBLISHED"
                                  ? "HIDE"
                                  : "PUBLISH",
                              reason: "Kiểm duyệt từ trung tâm vận hành",
                            },
                            { "Idempotency-Key": crypto.randomUUID() },
                          ),
                        "Đã kiểm duyệt đánh giá.",
                      )
                    }
                  >
                    {review.status === "PUBLISHED" ? "Ẩn" : "Hiện"}
                  </button>
                </div>
              </article>
            ))}
          </div>
        </section>
        {isAdmin && (
          <section className="panel span-2">
            <div className="panel-heading">
              <div>
                <span>NHẬT KÝ KIỂM SOÁT</span>
                <h2>Thao tác quan trọng gần đây</h2>
              </div>
              <b>{auditLogs.length}</b>
            </div>
            <div className="audit-timeline">
              {auditLogs.map((entry) => (
                <article key={entry.id}>
                  <i />
                  <div>
                    <strong>{viLabel(entry.action)}</strong>
                    <p>
                      Mã bản ghi {entry.entityId} · {entry.details}
                    </p>
                  </div>
                  <time>
                    {new Date(entry.createdAt).toLocaleString("vi-VN")}
                  </time>
                </article>
              ))}
            </div>
          </section>
        )}
      </div>
    </>
  );
}
