"use client";

import { api } from "@/lib/api";
import type { DestinationCatalogItem, RoomOption } from "@/lib/types";
import { FormEvent, useEffect, useState } from "react";
import { useAuth } from "@/components/auth-provider";
import { useRouter } from "next/navigation";
import {
  isPresentableRoomType,
  viAiReason,
  viDocumentType,
  viGuestText,
  viLabel,
} from "@/lib/vi";
import { useFlashNotifications } from "@/components/ui-provider";
import { AppIcon } from "@/components/app-icon";
import Link from "next/link";

interface InventoryAmenity {
  id: string;
  name: string;
}

interface InventoryRoomType {
  id: string;
  code: string;
  name: string;
}

interface HotelServiceCatalogItem {
  id: string;
  code: string;
  name: string;
  unit: string;
  currentPrice: {
    unitPrice: number;
    currency: string;
  };
}

const CURATED_LUXURY_SERVICES: HotelServiceCatalogItem[] = [
  {
    id: "curated-limo-transfer",
    code: "LIMO_VIP",
    name: "Đưa đón Sân bay Limousine Riêng & Fast-Track",
    unit: "chuyến",
    currentPrice: { unitPrice: 1850000, currency: "VND" },
  },
  {
    id: "curated-onsen-spa",
    code: "SERENITY_ONSEN",
    name: "Liệu trình Thủy trị liệu Onsen & Đá nóng 90 phút",
    unit: "khách",
    currentPrice: { unitPrice: 2450000, currency: "VND" },
  },
  {
    id: "curated-sunset-dinner",
    code: "AURELIA_TASTING",
    name: "Thực đơn Fine Dining 7 Món Hoàng hôn bên Vịnh",
    unit: "bàn đôi",
    currentPrice: { unitPrice: 4900000, currency: "VND" },
  },
  {
    id: "curated-champagne-ritual",
    code: "ROYAL_WELCOME",
    name: "Nghi thức Champagne Chào mừng & Hoa tươi Nghệ thuật",
    unit: "bộ",
    currentPrice: { unitPrice: 2150000, currency: "VND" },
  },
];

interface AvailabilityItem {
  roomTypeId: string;
  code: string;
  name: string;
  description: string;
  maxAdults: number;
  maxChildren: number;
  availableRooms: number;
  price: {
    pricingStatus: string;
    subtotal: number | null;
    tax: number | null;
    serviceFee: number | null;
    total: number | null;
  };
  amenities: InventoryAmenity[];
  images: Array<{ url: string; active: boolean }>;
  policySummary: string;
  destinationCode?: string;
  destinationName?: string;
  province?: string;
  region?: string;
  regionLabel?: string;
  styleTag?: string;
  propertyName?: string;
  landmark?: string;
}

interface AvailabilityResponse {
  content: AvailabilityItem[];
}

interface ReservationCreated {
  id: string;
  reference: string;
}

interface VietnamDestination {
  code: string;
  name: string;
  province: string;
  region: "BAC" | "TRUNG" | "NAM";
  regionLabel: string;
  styleTag: "Biển đảo" | "Săn mây & Núi cao" | "Di sản & Đô thị";
  vibe: string;
  propertyTitle: string;
  landmark: string;
  coordinates: string;
  climate: string;
  signatureExperience: string;
  gradient: string;
  featured?: boolean;
  matchKeywords: string[];
}

const VIETNAM_DESTINATIONS: VietnamDestination[] = [
  {
    code: "PHU_QUOC",
    name: "Phú Quốc",
    province: "Kiên Giang",
    region: "NAM",
    regionLabel: "Miền Nam",
    styleTag: "Biển đảo",
    vibe: "Biệt thự biển nhiệt đới với bãi cát trắng mịn như kem và hồ bơi vô cực hướng hoàng hôn riêng tư.",
    propertyTitle: "HotelAI Phú Quốc Emerald Bay",
    landmark: "Bãi Kem · Nam Đảo Phú Quốc",
    coordinates: "10°01'18\"N · 104°01'42\"E",
    climate: "28°C · Nắng vàng biển ngọc",
    signatureExperience: "Biệt thự hồ bơi vô cực & Du thuyền hoàng hôn",
    gradient:
      "linear-gradient(160deg, rgba(4, 24, 20, 0.18) 0%, rgba(4, 24, 20, 0.55) 48%, rgba(3, 18, 15, 0.94) 100%), url(/images/hotelai-coastal-hero-v2.png)",
    featured: true,
    matchKeywords: ["PRESIDENTIAL", "SUITE", "VILLA", "PHU_QUOC"],
  },
  {
    code: "DA_NANG",
    name: "Đà Nẵng",
    province: "Bán đảo Sơn Trà",
    region: "TRUNG",
    regionLabel: "Miền Trung",
    styleTag: "Biển đảo",
    vibe: "Tuyệt tác kiến trúc tựa sơn hướng thủy giữa rừng nguyên sinh Sơn Trà và vịnh biển ngọc bích.",
    propertyTitle: "HotelAI Đà Nẵng Peninsula Resort",
    landmark: "Bãi Bắc · Bán đảo Sơn Trà",
    coordinates: "16°07'32\"N · 108°18'14\"E",
    climate: "26°C · Gió biển thanh mát",
    signatureExperience: "Biệt thự sườn đồi & Nhà hàng Fine Dining trên vách đá",
    gradient:
      "linear-gradient(160deg, rgba(8, 38, 48, 0.2) 0%, rgba(5, 26, 32, 0.58) 48%, rgba(3, 18, 22, 0.94) 100%), url(/images/hotelai-coastal-hero-v2.png)",
    featured: true,
    matchKeywords: ["DELUXE", "FAMILY", "DA_NANG"],
  },
  {
    code: "HA_LONG",
    name: "Vịnh Hạ Long",
    province: "Quảng Ninh",
    region: "BAC",
    regionLabel: "Miền Bắc",
    styleTag: "Biển đảo",
    vibe: "Tầm nhìn toàn cảnh kỳ quan thiên nhiên thế giới với bến thủy phi cơ, du thuyền riêng và bể khoáng nóng.",
    propertyTitle: "HotelAI Hạ Long Heritage Sanctuary",
    landmark: "Đảo Tuần Châu · Vịnh Hạ Long",
    coordinates: "20°55'41\"N · 107°03'12\"E",
    climate: "24°C · Sương vịnh kỳ ảo",
    signatureExperience: "Onsen khoáng nóng hướng vịnh & Thủy phi cơ riêng",
    gradient:
      "linear-gradient(160deg, rgba(12, 42, 36, 0.22) 0%, rgba(6, 26, 23, 0.62) 48%, rgba(3, 18, 16, 0.94) 100%), url(/images/hotelai-coastal-hero-v2.png)",
    matchKeywords: ["HALONG_SUITE", "PANORAMA", "HA_LONG"],
  },
  {
    code: "SAPA",
    name: "Sapa",
    province: "Lào Cai",
    region: "BAC",
    regionLabel: "Miền Bắc",
    styleTag: "Săn mây & Núi cao",
    vibe: "Khu nghỉ dưỡng trên mây phóng tầm mắt ra đỉnh Fansipan hùng vĩ và thung lũng Mường Hoa mùa lúa chín.",
    propertyTitle: "HotelAI Sapa Cloud Sanctuary",
    landmark: "Thung lũng Mường Hoa · Sapa",
    coordinates: "22°19'58\"N · 103°50'35\"E",
    climate: "16°C · Biển mây bảng lảng",
    signatureExperience: "Bể bơi nước ấm vô cực giữa tầng mây 1.600m",
    gradient:
      "linear-gradient(160deg, rgba(36, 42, 28, 0.25) 0%, rgba(12, 24, 20, 0.65) 48%, rgba(5, 16, 14, 0.95) 100%), url(/images/hotelai-room-atlas-v1.png)",
    matchKeywords: ["STANDARD", "MOUNTAIN", "SAPA"],
  },
  {
    code: "HA_NOI",
    name: "Hà Nội",
    province: "Hoàn Kiếm",
    region: "BAC",
    regionLabel: "Miền Bắc",
    styleTag: "Di sản & Đô thị",
    vibe: "Khách sạn boutique 5 sao mang phong vị Đông Dương thanh lịch ngay trái tim khu phố Pháp cổ kính.",
    propertyTitle: "HotelAI Hanoi Opera Palace",
    landmark: "Nhà hát Lớn · Hoàn Kiếm",
    coordinates: "21°01'28\"N · 105°51'26\"E",
    climate: "23°C · Tiết trời thanh lịch",
    signatureExperience: "Trà chiều kiểu Pháp & Hầm rượu vang cổ điển",
    gradient:
      "linear-gradient(160deg, rgba(48, 36, 22, 0.28) 0%, rgba(16, 22, 20, 0.68) 48%, rgba(6, 15, 13, 0.95) 100%), url(/images/hotelai-room-atlas-v1.png)",
    matchKeywords: ["HANOI_IMPERIAL", "OPERA", "HA_NOI"],
  },
  {
    code: "HOI_AN",
    name: "Hội An",
    province: "Quảng Nam",
    region: "TRUNG",
    regionLabel: "Miền Trung",
    styleTag: "Di sản & Đô thị",
    vibe: "Giao hòa nét duyên dáng di sản thương cảng cổ cùng biệt thự hồ bơi ẩn mình bên dòng Thu Bồn.",
    propertyTitle: "HotelAI Hội An Riverside Heritage",
    landmark: "Phố cổ & Biển An Bàng",
    coordinates: "15°52'46\"N · 108°20'06\"E",
    climate: "27°C · Nắng mật dịu dàng",
    signatureExperience: "Thuyền gỗ ngắm hoàng hôn & Trị liệu thảo mộc",
    gradient:
      "linear-gradient(160deg, rgba(54, 40, 18, 0.26) 0%, rgba(14, 25, 22, 0.65) 48%, rgba(5, 16, 14, 0.94) 100%), url(/images/hotelai-coastal-hero-v2.png)",
    matchKeywords: ["HOIAN_VILLA", "HERITAGE", "HOI_AN"],
  },
  {
    code: "NHA_TRANG",
    name: "Nha Trang",
    province: "Khánh Hòa",
    region: "TRUNG",
    regionLabel: "Miền Trung",
    styleTag: "Biển đảo",
    vibe: "Không gian nghỉ dưỡng ven vịnh san hô yên bình kèm tổ hợp trị liệu khoáng nóng chuẩn 5 sao quốc tế.",
    propertyTitle: "HotelAI Nha Trang Cam Ranh Bay",
    landmark: "Bãi Dài · Vịnh Cam Ranh",
    coordinates: "12°04'15\"N · 109°11'48\"E",
    climate: "28°C · Biển xanh nắng ấm",
    signatureExperience: "Lặn ngắm rạn san hô riêng & Spa thủy trị liệu",
    gradient:
      "linear-gradient(160deg, rgba(8, 56, 48, 0.24) 0%, rgba(5, 29, 25, 0.64) 48%, rgba(3, 18, 15, 0.94) 100%), url(/images/hotelai-room-atlas-v1.png)",
    matchKeywords: ["TWIN", "CAM_RANH", "NHA_TRANG"],
  },
  {
    code: "DA_LAT",
    name: "Đà Lạt",
    province: "Lâm Đồng",
    region: "TRUNG",
    regionLabel: "Miền Trung",
    styleTag: "Săn mây & Núi cao",
    vibe: "Dinh thự kiến trúc Pháp cổ điển ẩn mình giữa rừng thông nguyên sinh và mặt hồ Tuyền Lâm tĩnh lặng.",
    propertyTitle: "HotelAI Đà Lạt Pine Valley",
    landmark: "Hồ Tuyền Lâm · Đà Lạt",
    coordinates: "11°54'12\"N · 108°25'52\"E",
    climate: "18°C · Sương thông se lạnh",
    signatureExperience: "Bữa tối bên lò sưởi gỗ thông & Vườn hồng cổ",
    gradient:
      "linear-gradient(160deg, rgba(28, 48, 34, 0.28) 0%, rgba(9, 27, 20, 0.66) 48%, rgba(4, 16, 13, 0.95) 100%), url(/images/hotelai-room-atlas-v1.png)",
    matchKeywords: ["SUPERIOR", "PINE", "DA_LAT"],
  },
  {
    code: "HCM",
    name: "TP. Hồ Chí Minh",
    province: "Quận 1",
    region: "NAM",
    regionLabel: "Miền Nam",
    styleTag: "Di sản & Đô thị",
    vibe: "Biểu tượng xa hoa ven sông Sài Gòn với Sky Bar tầng thượng, bể bơi chân mây và quản gia doanh nhân.",
    propertyTitle: "HotelAI Saigon Grand Landmark",
    landmark: "Bến Bạch Đằng · Quận 1",
    coordinates: "10°46'34\"N · 106°42'22\"E",
    climate: "29°C · Đô thị phồn hoa",
    signatureExperience: "Executive Sky Lounge & Đưa đón Limousine riêng",
    gradient:
      "linear-gradient(160deg, rgba(18, 38, 48, 0.28) 0%, rgba(8, 22, 26, 0.68) 48%, rgba(4, 15, 18, 0.95) 100%), url(/images/hotelai-room-atlas-v1.png)",
    matchKeywords: ["EXECUTIVE", "SAIGON", "HCM"],
  },
  {
    code: "VUNG_TAU",
    name: "Vũng Tàu",
    province: "Bà Rịa - Vũng Tàu",
    region: "NAM",
    regionLabel: "Miền Nam",
    styleTag: "Biển đảo",
    vibe: "Ốc đảo nghỉ dưỡng ven biển Hồ Tràm nguyên sơ với sân golf chuẩn quốc tế và hồ bơi khoáng mặn.",
    propertyTitle: "HotelAI Vũng Tàu Hồ Tràm Sanctuary",
    landmark: "Bờ biển Hồ Tràm · Vũng Tàu",
    coordinates: "10°28'19\"N · 107°25'40\"E",
    climate: "28°C · Gió biển dịu êm",
    signatureExperience: "Biệt thự vườn biển & Sân Golf ven đại dương",
    gradient:
      "linear-gradient(160deg, rgba(12, 52, 46, 0.24) 0%, rgba(5, 26, 23, 0.64) 48%, rgba(3, 17, 15, 0.94) 100%), url(/images/hotelai-coastal-hero-v2.png)",
    matchKeywords: ["VUNGTAU_OCEAN", "HO_TRAM", "VUNG_TAU"],
  },
];

function resolveDestinationForRoom(
  option: RoomOption,
  index: number,
): VietnamDestination {
  if (option.destinationCode) {
    const exact = VIETNAM_DESTINATIONS.find(
      (d) => d.code.toUpperCase() === option.destinationCode?.toUpperCase(),
    );
    if (exact) {
      return {
        ...exact,
        name: option.destinationName || exact.name,
        province: option.province || exact.province,
        propertyTitle: option.propertyName || exact.propertyTitle,
        landmark: option.landmark || exact.landmark,
      };
    }
  }
  const upper = `${option.code} ${option.name} ${option.description}`.toUpperCase();
  const matched = VIETNAM_DESTINATIONS.find((dest) =>
    dest.matchKeywords.some((kw) => upper.includes(kw)),
  );
  return matched ?? VIETNAM_DESTINATIONS[index % VIETNAM_DESTINATIONS.length];
}

function iso(days: number) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}
function money(value: number) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
  }).format(value);
}

function mapAvailability(result: AvailabilityResponse): RoomOption[] {
  return result.content
    .map((option) => ({
      roomTypeId: option.roomTypeId,
      code: option.code,
      name: option.name,
      description: option.description,
      maxAdults: option.maxAdults,
      maxChildren: option.maxChildren,
      availableRooms: option.availableRooms,
      roomSubtotal: Number(option.price.subtotal ?? 0),
      taxAmount: Number(option.price.tax ?? 0),
      serviceFee: Number(option.price.serviceFee ?? 0),
      totalAmount: Number(option.price.total ?? 0),
      imageUrl: option.images.find(
        (image) =>
          image.active &&
          !image.url.includes(".invalid/") &&
          !image.url.includes("pixabay.com/vi/images/download/"),
      )?.url,
      amenities: option.amenities.map((amenity) => amenity.name),
      cancellationPolicy: viGuestText(option.policySummary),
      destinationCode: option.destinationCode,
      destinationName: option.destinationName,
      province: option.province,
      region: option.region,
      regionLabel: option.regionLabel,
      styleTag: option.styleTag,
      propertyName: option.propertyName,
      landmark: option.landmark,
    }))
    .filter(
      (option) =>
        isPresentableRoomType(option.name, option.code) &&
        option.availableRooms > 0 &&
        option.totalAmount > 0,
    );
}

export default function HomePage() {
  const { user } = useAuth();
  const router = useRouter();
  const [criteria, setCriteria] = useState({
    hotelId: "1",
    checkIn: iso(1),
    checkOut: iso(2),
    adults: "2",
    children: "0",
    rooms: "1",
  });
  const [options, setOptions] = useState<RoomOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  useFlashNotifications(error);
  const [aiLoading, setAiLoading] = useState(false);
  const [services, setServices] = useState<HotelServiceCatalogItem[]>(
    CURATED_LUXURY_SERVICES,
  );
  const [roomTypes, setRoomTypes] = useState<InventoryRoomType[]>([]);
  const [amenities, setAmenities] = useState<InventoryAmenity[]>([]);
  const [catalogDestinations, setCatalogDestinations] = useState<
    DestinationCatalogItem[]
  >([]);
  const [filters, setFilters] = useState({
    roomTypeId: "",
    minPrice: "",
    maxPrice: "",
    amenityIds: [] as string[],
  });
  const [selected, setSelected] = useState<RoomOption>();
  const [bookingForm, setBookingForm] = useState({
    promotionCode: "",
    specialRequests: "",
    serviceId: "",
    serviceQuantity: "1",
    documentType: "CCCD",
    documentNumber: "",
    nationality: "VN",
  });
  const [activeSanctuaryTab, setActiveSanctuaryTab] = useState(0);
  const [selectedDestination, setSelectedDestination] = useState<string>("ALL");
  const [selectedRegion, setSelectedRegion] = useState<
    "ALL" | "BAC" | "TRUNG" | "NAM"
  >("ALL");

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const requestedRoomTypeId = params.get("roomTypeId") ?? "";
    const requestedDestination = params.get("destination")?.toUpperCase() ?? "";
    const initialDest =
      requestedDestination &&
      VIETNAM_DESTINATIONS.some((d) => d.code === requestedDestination)
        ? requestedDestination
        : "ALL";
    const timer = window.setTimeout(() => {
      if (requestedRoomTypeId) {
        setFilters((current) => ({
          ...current,
          roomTypeId: requestedRoomTypeId,
        }));
      }
      if (initialDest !== "ALL") {
        setSelectedDestination(initialDest);
      }
      void search(undefined, requestedRoomTypeId, initialDest);
    }, 0);
    void api
      .getPublic<HotelServiceCatalogItem[]>("/hotel-services")
      .then((catalog) => {
        const presentable = catalog.filter(
          (item) =>
            isPresentableRoomType(item.name, item.code) &&
            !/^e2e/i.test(item.name.trim()) &&
            !/^e2e/i.test(item.code.trim()),
        );
        const merged = [
          ...presentable,
          ...CURATED_LUXURY_SERVICES.filter(
            (fallback) => !presentable.some((item) => item.code === fallback.code),
          ),
        ];
        setServices(merged);
      })
      .catch(() => undefined);
    void api
      .getPublic<DestinationCatalogItem[]>("/availability/destinations")
      .then(setCatalogDestinations)
      .catch(() => undefined);
    void Promise.all([
      api.getPublic<InventoryRoomType[]>("/catalog/room-types"),
      api.getPublic<InventoryAmenity[]>("/catalog/amenities"),
    ])
      .then(([typeData, amenityData]) => {
        setRoomTypes(
          typeData.filter((type) =>
            isPresentableRoomType(type.name, type.code),
          ),
        );
        setAmenities(amenityData);
      })
      .catch(() => undefined);
    return () => window.clearTimeout(timer);
    // Chỉ tìm một lần với tiêu chí mặc định khi trang được mở.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function search(
    event?: FormEvent,
    requestedRoomTypeId?: string,
    destinationOverride?: string,
  ) {
    event?.preventDefault();
    setLoading(true);
    setError("");
    const activeDest = destinationOverride ?? selectedDestination;
    const query = new URLSearchParams({
      checkIn: criteria.checkIn,
      checkOut: criteria.checkOut,
      adults: criteria.adults,
      children: criteria.children,
      rooms: criteria.rooms,
      ...(filters.minPrice && { minPrice: filters.minPrice }),
      ...(filters.maxPrice && { maxPrice: filters.maxPrice }),
      ...(activeDest &&
        activeDest !== "ALL" && { destinationCode: activeDest }),
    });
    filters.amenityIds.forEach((id) => query.append("amenityIds", String(id)));
    try {
      const result = await api.getPublic<AvailabilityResponse>(
        `/availability/search?${query}`,
      );
      const mapped = mapAvailability(result);
      const roomTypeId = requestedRoomTypeId ?? filters.roomTypeId;
      setOptions(
        roomTypeId
          ? mapped.filter((option) => String(option.roomTypeId) === roomTypeId)
          : mapped,
      );
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  }

  function beginBooking(option: RoomOption) {
    if (!user) {
      router.push("/login");
      return;
    }
    setSelected(option);
  }

  async function book(event: FormEvent) {
    event.preventDefault();
    if (!user || !selected) return;
    setError("");
    try {
      const selectedService = services.find(
        (service) => service.id === bookingForm.serviceId,
      );
      const serviceRequest = selectedService
        ? `Dịch vụ đặt trước: ${selectedService.name} x ${bookingForm.serviceQuantity}`
        : "";
      const specialRequests = [
        bookingForm.specialRequests.trim(),
        serviceRequest,
      ]
        .filter(Boolean)
        .join("\n");
      const booking = await api.post<ReservationCreated>(
        "/bookings",
        {
          roomTypeId: selected.roomTypeId,
          checkIn: criteria.checkIn,
          checkOut: criteria.checkOut,
          adults: Number(criteria.adults),
          children: Number(criteria.children),
          roomCount: Number(criteria.rooms),
          contactName: user.fullName,
          contactEmail: user.email,
          contactPhone: user.phone ?? "",
          specialRequests,
          promotionCode: bookingForm.promotionCode || undefined,
          guests: [
            {
              fullName: user.fullName,
              documentType: bookingForm.documentType,
              documentNumber: bookingForm.documentNumber,
              nationality: bookingForm.nationality,
              primary: true,
            },
          ],
        },
        {
          "Idempotency-Key": crypto.randomUUID(),
        },
      );
      if (selectedService && !selectedService.id.startsWith("curated-")) {
        // Keep the request as a first-class catalog order. The legacy text is
        // retained above for backwards-compatible reservation receipts.
        await api
          .post(
            "/service-orders",
            {
              reservationId: booking.id,
              customerId: String(user.id),
              serviceId: selectedService.id,
              quantity: Number(bookingForm.serviceQuantity),
              scheduledAt: `${criteria.checkIn}T05:00:00.000Z`,
              note: "Đặt trước cùng booking",
            },
            { "Idempotency-Key": crypto.randomUUID() },
          )
          .catch(() => undefined);
      }
      router.push(`/bookings?created=${booking.reference}`);
    } catch (reason) {
      setError((reason as Error).message);
    }
  }

  async function recommend() {
    setAiLoading(true);
    setError("");
    try {
      const availabilityQuery = new URLSearchParams({
        checkIn: criteria.checkIn,
        checkOut: criteria.checkOut,
        adults: criteria.adults,
        children: criteria.children,
        rooms: criteria.rooms,
        ...(selectedDestination !== "ALL" && {
          destinationCode: selectedDestination,
        }),
      });
      const [response, availability] = await Promise.all([
        api.postPublic<{ items: RoomOption[] }>("/ai/recommendations", {
          hotel_id: Number(criteria.hotelId),
          check_in: criteria.checkIn,
          check_out: criteria.checkOut,
          adults: Number(criteria.adults),
          children: Number(criteria.children),
          rooms: Number(criteria.rooms),
          preferred_amenities: ["WiFi", "Bữa sáng"],
          ...(selectedDestination !== "ALL" && {
            destination_code: selectedDestination,
          }),
        }),
        api.getPublic<AvailabilityResponse>(
          `/availability/search?${availabilityQuery}`,
        ),
      ]);
      const recommendationByCode = new Map(
        response.items.map((item) => [item.code.toUpperCase(), item]),
      );
      const ranked = mapAvailability(availability)
        .map((option) => {
          const recommendation = recommendationByCode.get(
            option.code.toUpperCase(),
          );
          return recommendation
            ? {
                ...option,
                recommendationScore: recommendation.recommendationScore,
                reasons: recommendation.reasons,
              }
            : option;
        })
        .sort(
          (left, right) =>
            (right.recommendationScore ?? -1) -
            (left.recommendationScore ?? -1),
        );
      setOptions(ranked);
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setAiLoading(false);
    }
  }

  const activeDestinationObj = VIETNAM_DESTINATIONS.find(
    (d) => d.code === selectedDestination,
  );

  const filteredByDestination =
    selectedDestination === "ALL"
      ? options
      : options.filter((option, index) => {
          if (option.destinationCode) {
            return (
              option.destinationCode.toUpperCase() ===
              selectedDestination.toUpperCase()
            );
          }
          return (
            resolveDestinationForRoom(option, index).code ===
            selectedDestination
          );
        });

  const visibleDestinations =
    selectedRegion === "ALL"
      ? VIETNAM_DESTINATIONS
      : VIETNAM_DESTINATIONS.filter((d) => d.region === selectedRegion);

  function broadcastDestinationChange(code: string) {
    setSelectedDestination(code);
    const destObj = VIETNAM_DESTINATIONS.find((d) => d.code === code);
    if (typeof window !== "undefined") {
      window.dispatchEvent(
        new CustomEvent("hotelai:destination-change", {
          detail: {
            code,
            name: destObj?.name ?? null,
            propertyTitle: destObj?.propertyTitle ?? null,
          },
        }),
      );
    }
    void search(undefined, undefined, code);
  }

  function selectDestinationAndScroll(code: string) {
    broadcastDestinationChange(code);
    const suitesEl = document.getElementById("suites");
    suitesEl?.scrollIntoView({ behavior: "smooth" });
  }

  const sanctuaryHighlights = [
    {
      kicker: "KIẾN TRÚC & KHÔNG GIAN",
      title: "Biệt thự ven biển & Hồ bơi vô cực",
      description:
        "Tầm nhìn panorama hướng vịnh ngọc bích với thiết kế mở đón ánh sáng tự nhiên, hồ bơi tràn bờ nước ấm và khu vườn nhiệt đới biệt lập.",
      metric: "180°",
      metricLabel: "tầm nhìn hướng biển",
      detail: "Mở cửa 06:00 – 22:00 · Phục vụ khăn ấm & trà thảo mộc tại hồ",
    },
    {
      kicker: "ẨM THỰC THƯỢNG HẠNG",
      title: "Nhà hàng Aurelia Fine Dining & Sky Bar",
      description:
        "Thực đơn theo mùa kết hợp nguyên liệu bản địa tuyển chọn và kỹ nghệ ẩm thực đương đại, đi kèm hầm rượu vang hơn 180 nhãn danh tiếng.",
      metric: "2 Sao",
      metricLabel: "tiêu chuẩn bếp trưởng",
      detail: "Bữa sáng À-la-carte · Tiệc tối hoàng hôn trên tầng thượng",
    },
    {
      kicker: "CHỮA LÀNH & THƯ GIÃN",
      title: "Serenity Holistic Spa & Onsen Khoáng Nóng",
      description:
        "Liệu trình trị liệu cá nhân hóa kết hợp đá nóng bazan, tinh dầu thảo dược bản địa và bể sục thủy trị liệu giúp tái tạo năng lượng sâu.",
      metric: "90 phút",
      metricLabel: "liệu trình đặc trưng",
      detail: "Đặt lịch trực tiếp cùng phòng · Chuyên gia trị liệu riêng",
    },
  ];

  const guestTestimonials = [
    {
      quote:
        "Trợ lý AI gợi ý đúng căn Suite tầng cao hướng biển yên tĩnh cho kỷ niệm ngày cưới của chúng tôi. Từ lúc nhận phòng đến khi trả phòng đều mượt mà tuyệt đối.",
      author: "Minh Anh & Hoàng Nam",
      stayType: "Oceanfront Grand Suite · 3 đêm",
      rating: "5.0",
    },
    {
      quote:
        "Mức giá hiển thị minh bạch ngay từ đầu, không có phụ phí ẩn. Dịch vụ đưa đón sân bay và spa đặt trước được chuẩn bị sẵn ngay khi chúng tôi vừa đến sảnh.",
      author: "Trần Bảo Khánh",
      stayType: "Executive Retreat · Kỳ nghỉ công tác",
      rating: "5.0",
    },
    {
      quote:
        "Không gian sang trọng, tinh tế và yên bình. Hệ thống giữ phòng trực tiếp giúp gia đình tôi hoàn toàn an tâm dù đặt sát kỳ nghỉ lễ.",
      author: "Lê Thanh Thảo",
      stayType: "Family Connecting Residence · 4 đêm",
      rating: "4.9",
    },
  ];

  function applyQuickPreset(preset: {
    nights: number;
    adults: string;
    children: string;
    rooms: string;
  }) {
    const nextCheckIn = iso(1);
    const nextCheckOut = iso(1 + preset.nights);
    setCriteria((prev) => ({
      ...prev,
      checkIn: nextCheckIn,
      checkOut: nextCheckOut,
      adults: preset.adults,
      children: preset.children,
      rooms: preset.rooms,
    }));
  }

  function preselectServiceAndScroll(serviceId: string) {
    setBookingForm((prev) => ({ ...prev, serviceId }));
    const searchEl = document.getElementById("search");
    searchEl?.scrollIntoView({ behavior: "smooth" });
  }

  const totalAvailableRooms = options.reduce(
    (total, option) => total + option.availableRooms,
    0,
  );
  const lowestPrice = options.length
    ? Math.min(...options.map((option) => option.totalAmount))
    : null;

  const stayNights = Math.max(
    1,
    Math.round(
      (new Date(criteria.checkOut).getTime() -
        new Date(criteria.checkIn).getTime()) /
        (1000 * 60 * 60 * 24),
    ) || 1,
  );

  const heroSanctuarySpotlights = VIETNAM_DESTINATIONS.slice(0, 4);
  const activeHeroSpotlight =
    activeDestinationObj ?? heroSanctuarySpotlights[0];

  const dayRhythmMoments = [
    {
      time: "06:30",
      title: "Bình minh & Trà sen thượng hạng",
      caption: "Thiền thở bên hồ bơi vô cực & thưởng trà cổ thụ tại hiên biệt thự",
      icon: "sun" as const,
    },
    {
      time: "14:00",
      title: "Khoáng nóng Onsen & Đá nóng Bazan",
      caption: "Liệu trình thủy trị liệu 90 phút phục hồi sâu tại Serenity Spa",
      icon: "waves" as const,
    },
    {
      time: "18:00",
      title: "Champagne Hoàng hôn trên Vịnh Ngọc",
      caption: "Canapé thủ công & nhạc Jazz sống tại Aurelia Sky Lounge",
      icon: "wine" as const,
    },
    {
      time: "21:30",
      title: "Nghi thức Chúc ngủ ngon (Turndown)",
      caption: "Tinh dầu gỗ tuyết tùng, gối lụa tơ tằm & quản gia AI trực 24/7",
      icon: "moon" as const,
    },
  ];

  const preselectedServiceObj = services.find(
    (s) => s.id === bookingForm.serviceId,
  );

  return (
    <>
      <section className="hero">
        <div className="hero-ambient-ornament" aria-hidden="true" />
        <div className="hero-copy">
          <div className="hero-top-telemetry" aria-label="Thông số điểm đến và danh hiệu">
            <span className="telemetry-item">
              <AppIcon name="diamond" />
              <span>MICHELIN TWO KEYS · FORBES 5★</span>
            </span>
            <span className="telemetry-divider" aria-hidden="true" />
            <span className="telemetry-item telemetry-coords">
              <AppIcon name="compass" />
              <span>
                {activeHeroSpotlight.name.toUpperCase()} ·{" "}
                {activeHeroSpotlight.coordinates}
              </span>
            </span>
            <span className="telemetry-divider" aria-hidden="true" />
            <span className="telemetry-item">
              <AppIcon name="sun" />
              <span>{activeHeroSpotlight.climate}</span>
            </span>
          </div>

          <div
            className="hero-luxury-badge"
            aria-label="Danh hiệu khu nghỉ dưỡng 5 sao"
          >
            <span className="hero-stars" aria-hidden="true">
              <AppIcon name="star" />
              <AppIcon name="star" />
              <AppIcon name="star" />
              <AppIcon name="star" />
              <AppIcon name="star" />
            </span>
            <p className="eyebrow">
              BỘ SƯU TẬP KHU NGHỈ DƯỠNG DI SẢN &amp; TRÍ TUỆ NHÂN TẠO ĐỘC BẢN
            </p>
          </div>

          <h1>
            Tuyệt tác lưu trú.
            <br />
            <em>Độc bản từng khoảnh khắc.</em>
          </h1>

          <p>
            Đắm mình trong không gian nghỉ dưỡng 5 sao giao hòa giữa kiến trúc
            Đông Dương đương đại và công nghệ Quản gia AI cá nhân hóa. Kiểm tra
            tồn kho phòng thực tế, khóa phòng tức thì trong 15 phút và tận hưởng
            đặc quyền thượng lưu xuyên suốt 10 điểm đến biểu tượng Việt Nam.
          </p>

          <div className="hero-cta-group">
            <a className="hero-primary-cta" href="#search">
              <AppIcon name="crown" />
              <span>Khởi tạo kỳ nghỉ thượng lưu</span>
              <AppIcon name="arrow" />
            </a>
            <Link className="hero-secondary-cta" href="/rooms">
              <AppIcon name="compass" />
              <span>Bộ sưu tập Suite &amp; Villa</span>
            </Link>
          </div>

          <div
            className="hero-sanctuary-switcher"
            role="group"
            aria-label="Điểm đến tiêu biểu"
          >
            <span className="switcher-label">ĐIỂM ĐẾN TIÊU BIỂU:</span>
            <div className="switcher-pills">
              <button
                type="button"
                className={`hero-destination-pill ${selectedDestination === "ALL" ? "active" : ""}`}
                onClick={() => broadcastDestinationChange("ALL")}
              >
                <span className="roman-idx">ALL</span>
                <span>Toàn bộ 10 Tuyệt tác</span>
              </button>
              {heroSanctuarySpotlights.map((spot, idx) => (
                <button
                  key={spot.code}
                  type="button"
                  className={`hero-destination-pill ${selectedDestination === spot.code ? "active" : ""}`}
                  onClick={() => broadcastDestinationChange(spot.code)}
                >
                  <span className="roman-idx">
                    {["I", "II", "III", "IV"][idx]}
                  </span>
                  <span>{spot.name}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="hero-benefits" aria-label="Lợi ích nổi bật">
            <span>
              <i className="benefit-icon">
                <AppIcon name="check" />
              </i>
              Biểu giá cuối minh bạch trọn gói
            </span>
            <span>
              <i className="benefit-icon">
                <AppIcon name="check" />
              </i>
              Khóa tồn kho thực tế tức thì
            </span>
            <span>
              <i className="benefit-icon">
                <AppIcon name="check" />
              </i>
              Private Butler &amp; Quản gia AI 24/7
            </span>
          </div>
        </div>

        <aside className="hero-note" aria-label="Cam kết dịch vụ">
          <div className="hero-note-top">
            <span className="hero-note-icon" aria-hidden="true">
              <AppIcon name="crown" />
            </span>
            <span className="hero-live-pill">
              <i /> TRỰC TUYẾN THỜI GIAN THỰC
            </span>
          </div>
          <small>HỒ SƠ ĐẶC QUYỀN · PRIVATE CONCIERGE</small>
          <strong>Tuyển chọn Suite hoàn mỹ cho riêng bạn</strong>
          <p>
            Hệ thống AI đối chiếu tức thời kiến trúc phòng, tầm nhìn, sức chứa
            và ngân sách để đề xuất không gian nghỉ dưỡng xứng tầm nhất.
          </p>
          <div className="hero-note-metric">
            <b>{totalAvailableRooms || "—"}</b>
            <span>phòng &amp; biệt thự sẵn sàng đón khách</span>
          </div>
          <div className="hero-note-metric">
            <b>{options.length || roomTypes.length || "—"}</b>
            <span>tuyệt tác hạng phòng 5 sao</span>
          </div>
          <div className="hero-note-spotlight">
            <span className="spotlight-kicker">
              <AppIcon name="map-pin" /> Đang tâm điểm:
            </span>
            <strong>{activeHeroSpotlight.propertyTitle}</strong>
            <small>{activeHeroSpotlight.signatureExperience}</small>
          </div>
          <div className="hero-note-footer">
            <span>
              <AppIcon name="tag" />
              {lowestPrice
                ? `Chỉ từ ${money(lowestPrice)} / kỳ nghỉ`
                : "Đặc quyền giá đặt trực tiếp tốt nhất"}
            </span>
            <a href="#suites" className="hero-note-link">
              Xem ngay <AppIcon name="arrow" />
            </a>
          </div>
        </aside>
      </section>

      <section className="search-shell" id="search">
        <form className="search-form" onSubmit={search}>
          <div className="search-intro">
            <div className="search-intro-title">
              <div className="search-kicker-row">
                <span className="search-atelier-badge">
                  <AppIcon name="diamond" /> ATELIER ĐẶT PHÒNG TRỰC TUYẾN
                </span>
                <span className="search-nights-pill">
                  <AppIcon name="moon" /> {stayNights} đêm lưu trú
                </span>
                {preselectedServiceObj && (
                  <span className="search-service-pill">
                    <AppIcon name="sparkles" /> Đính kèm:{" "}
                    {preselectedServiceObj.name}
                  </span>
                )}
              </div>
              <strong>
                Thiết lập lịch trình lưu trú hoặc chọn kịch bản nghỉ dưỡng được
                tuyển chọn bởi Quản gia
              </strong>
            </div>
            <div
              className="search-presets"
              role="group"
              aria-label="Gợi ý nhanh lịch trình"
            >
              <button
                type="button"
                className="preset-chip"
                onClick={() =>
                  applyQuickPreset({
                    nights: 1,
                    adults: "2",
                    children: "0",
                    rooms: "1",
                  })
                }
              >
                <AppIcon name="sun" /> Cặp đôi · 1 đêm
              </button>
              <button
                type="button"
                className="preset-chip"
                onClick={() =>
                  applyQuickPreset({
                    nights: 2,
                    adults: "2",
                    children: "0",
                    rooms: "1",
                  })
                }
              >
                <AppIcon name="sparkles" /> Cuối tuần · 2 đêm
              </button>
              <button
                type="button"
                className="preset-chip"
                onClick={() =>
                  applyQuickPreset({
                    nights: 3,
                    adults: "2",
                    children: "2",
                    rooms: "1",
                  })
                }
              >
                <AppIcon name="users" /> Gia đình · 3 đêm
              </button>
              <button
                type="button"
                className="preset-chip"
                onClick={() =>
                  applyQuickPreset({
                    nights: 4,
                    adults: "4",
                    children: "2",
                    rooms: "2",
                  })
                }
              >
                <AppIcon name="crown" /> Biệt thự VIP · 4 đêm
              </button>
            </div>
          </div>

          <div className="search-fields search-fields-with-destination">
            <label className="destination-field-label">
              <span className="field-label-head">
                <AppIcon name="map-pin" /> Điểm đến / Khu vực
              </span>
              <select
                value={selectedDestination}
                onChange={(e) => broadcastDestinationChange(e.target.value)}
                aria-label="Điểm đến du lịch Việt Nam"
              >
                <option value="ALL">Toàn quốc · Tất cả 10 điểm đến</option>
                <optgroup label="Miền Bắc">
                  {VIETNAM_DESTINATIONS.filter((d) => d.region === "BAC").map(
                    (dest) => (
                      <option key={dest.code} value={dest.code}>
                        {dest.name} · {dest.province}
                      </option>
                    ),
                  )}
                </optgroup>
                <optgroup label="Miền Trung">
                  {VIETNAM_DESTINATIONS.filter((d) => d.region === "TRUNG").map(
                    (dest) => (
                      <option key={dest.code} value={dest.code}>
                        {dest.name} · {dest.province}
                      </option>
                    ),
                  )}
                </optgroup>
                <optgroup label="Miền Nam">
                  {VIETNAM_DESTINATIONS.filter((d) => d.region === "NAM").map(
                    (dest) => (
                      <option key={dest.code} value={dest.code}>
                        {dest.name} · {dest.province}
                      </option>
                    ),
                  )}
                </optgroup>
              </select>
            </label>
            <label>
              <span className="field-label-head">
                <AppIcon name="calendar" /> Nhận phòng
              </span>
              <input
                type="date"
                value={criteria.checkIn}
                min={iso(0)}
                onChange={(e) =>
                  setCriteria({ ...criteria, checkIn: e.target.value })
                }
              />
            </label>
            <label>
              <span className="field-label-head">
                <AppIcon name="calendar" /> Trả phòng
              </span>
              <input
                type="date"
                value={criteria.checkOut}
                min={criteria.checkIn}
                onChange={(e) =>
                  setCriteria({ ...criteria, checkOut: e.target.value })
                }
              />
            </label>
            <label>
              <span className="field-label-head">
                <AppIcon name="user" /> Người lớn
              </span>
              <input
                type="number"
                min="1"
                value={criteria.adults}
                onChange={(e) =>
                  setCriteria({ ...criteria, adults: e.target.value })
                }
              />
            </label>
            <label>
              <span className="field-label-head">
                <AppIcon name="users" /> Trẻ em
              </span>
              <input
                type="number"
                min="0"
                value={criteria.children}
                onChange={(e) =>
                  setCriteria({ ...criteria, children: e.target.value })
                }
              />
            </label>
            <label>
              <span className="field-label-head">
                <AppIcon name="bed" /> Số phòng
              </span>
              <input
                type="number"
                min="1"
                value={criteria.rooms}
                onChange={(e) =>
                  setCriteria({ ...criteria, rooms: e.target.value })
                }
              />
            </label>
            <button
              className="primary search-button"
              disabled={loading}
              aria-label="Tìm phòng trống"
            >
              {loading ? (
                <>
                  <span className="spinner" /> Đang kiểm tra
                </>
              ) : (
                <>
                  <AppIcon name="search" />
                  <span>Tìm phòng phù hợp</span>
                </>
              )}
            </button>
          </div>
        </form>
        <details className="search-advanced">
          <summary>
            <AppIcon name="sliders" />
            <b>Bộ lọc Đặc quyền &amp; Cá nhân hóa</b>
            <span>
              Lọc theo hạng Suite, ngân sách kỳ nghỉ và tiện nghi chuẩn 5 sao
            </span>
          </summary>
          <div>
            <label>
              Loại phòng
              <select
                value={filters.roomTypeId}
                onChange={(event) =>
                  setFilters({ ...filters, roomTypeId: event.target.value })
                }
              >
                <option value="">Tất cả hạng phòng</option>
                {roomTypes.map((type) => (
                  <option key={type.id} value={type.id}>
                    {type.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Giá thấp nhất
              <input
                type="number"
                min="0"
                placeholder="VNĐ"
                value={filters.minPrice}
                onChange={(event) =>
                  setFilters({ ...filters, minPrice: event.target.value })
                }
              />
            </label>
            <label>
              Giá cao nhất
              <input
                type="number"
                min="0"
                placeholder="VNĐ"
                value={filters.maxPrice}
                onChange={(event) =>
                  setFilters({ ...filters, maxPrice: event.target.value })
                }
              />
            </label>
            <fieldset>
              <legend>Tiện nghi đặc quyền</legend>
              {amenities.map((amenity) => (
                <label key={amenity.id}>
                  <input
                    type="checkbox"
                    checked={filters.amenityIds.includes(amenity.id)}
                    onChange={(event) =>
                      setFilters({
                        ...filters,
                        amenityIds: event.target.checked
                          ? [...filters.amenityIds, amenity.id]
                          : filters.amenityIds.filter(
                              (id) => id !== amenity.id,
                            ),
                      })
                    }
                  />
                  {amenity.name}
                </label>
              ))}
            </fieldset>
          </div>
        </details>
      </section>

      <section className="guest-trust-strip" aria-label="Cam kết của HotelAI">
        <article>
          <span className="trust-medallion">
            <AppIcon name="shield" />
          </span>
          <div>
            <small className="trust-roman">BẢO CHỨNG 01</small>
            <strong>Giữ phòng an toàn tức thì</strong>
            <small>Tồn kho thực tế được khóa riêng trong 15 phút</small>
          </div>
        </article>
        <article>
          <span className="trust-medallion">
            <AppIcon name="wallet" />
          </span>
          <div>
            <small className="trust-roman">BẢO CHỨNG 02</small>
            <strong>Giá cuối minh bạch tuyệt đối</strong>
            <small>Hiển thị trọn gói thuế &amp; phí dịch vụ trước xác nhận</small>
          </div>
        </article>
        <article>
          <span className="trust-medallion">
            <AppIcon name="sparkles" />
          </span>
          <div>
            <small className="trust-roman">BẢO CHỨNG 03</small>
            <strong>Quản gia AI &amp; Concierge 24/7</strong>
            <small>Cá nhân hóa từng hạng phòng, gối ngủ &amp; ẩm thực</small>
          </div>
        </article>
        <article>
          <span className="trust-medallion">
            <AppIcon name="crown" />
          </span>
          <div>
            <small className="trust-roman">BẢO CHỨNG 04</small>
            <strong>Đặc quyền nghỉ dưỡng 5 sao</strong>
            <small>Check-in sảnh VIP, trà chiều &amp; nâng hạng ưu tiên</small>
          </div>
        </article>
      </section>

      <section
        className="vietnam-destinations-section"
        aria-label="Điểm đến du lịch Việt Nam"
      >
        <div className="destinations-header">
          <div>
            <span>BỘ SƯU TẬP ĐIỂM ĐẾN ĐỘC BẢN · HOTELAI GRAND COLLECTION</span>
            <h2>Khám phá 10 điểm đến biểu tượng trải dài 3 miền</h2>
          </div>
          <div
            className="region-filter-tabs"
            role="tablist"
            aria-label="Lọc theo vùng miền"
          >
            {[
              { key: "ALL", label: "Toàn quốc (10)" },
              { key: "BAC", label: "Miền Bắc (3)" },
              { key: "TRUNG", label: "Miền Trung (4)" },
              { key: "NAM", label: "Miền Nam (3)" },
            ].map((tab) => (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={selectedRegion === tab.key}
                className={`region-tab-btn ${selectedRegion === tab.key ? "active" : ""}`}
                onClick={() =>
                  setSelectedRegion(tab.key as "ALL" | "BAC" | "TRUNG" | "NAM")
                }
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
        <div className="destinations-grid destinations-bento-grid">
          {visibleDestinations.map((dest, idx) => {
            const liveDest = catalogDestinations.find(
              (c) => c.code.toUpperCase() === dest.code.toUpperCase(),
            );
            const matchingRooms = options.filter(
              (opt, optIdx) =>
                (opt.destinationCode
                  ? opt.destinationCode.toUpperCase()
                  : resolveDestinationForRoom(opt, optIdx).code) === dest.code,
            );
            const destAvailableCount =
              liveDest?.availableRooms ??
              matchingRooms.reduce((sum, item) => sum + item.availableRooms, 0);
            const destLowestPrice =
              liveDest?.minNightlyPrice ??
              (matchingRooms.length
                ? Math.min(...matchingRooms.map((item) => item.totalAmount))
                : null);
            const isSelected = selectedDestination === dest.code;

            return (
              <article
                key={dest.code}
                className={`destination-card ${dest.featured && selectedRegion === "ALL" ? "destination-card-featured" : ""} ${isSelected ? "active" : ""}`}
                style={{ backgroundImage: dest.gradient }}
                onClick={() =>
                  selectDestinationAndScroll(isSelected ? "ALL" : dest.code)
                }
              >
                <div className="destination-card-top">
                  <div className="destination-pill-cluster">
                    <span className="destination-index-pill">
                      {String(idx + 1).padStart(2, "0")}
                    </span>
                    <span className="destination-region-pill">
                      <AppIcon name="map-pin" /> {dest.regionLabel} ·{" "}
                      {dest.province}
                    </span>
                    <span className="destination-style-pill">
                      <AppIcon name="compass" />{" "}
                      {liveDest?.styleTag ?? dest.styleTag}
                    </span>
                  </div>
                  <span className="destination-count-pill">
                    {destAvailableCount > 0
                      ? `${destAvailableCount} phòng trống`
                      : "Đang mở đặt phòng"}
                  </span>
                </div>

                <div className="destination-card-body">
                  <div className="destination-telemetry-row">
                    <span>
                      <AppIcon name="globe" /> {dest.coordinates}
                    </span>
                    <span>
                      <AppIcon name="sun" /> {dest.climate}
                    </span>
                  </div>
                  <small>{liveDest?.propertyName ?? dest.propertyTitle}</small>
                  <h3>{dest.name}</h3>
                  <p>{liveDest?.description ?? dest.vibe}</p>
                  <div className="destination-signature-tag">
                    <AppIcon name="diamond" />
                    <span>{dest.signatureExperience}</span>
                  </div>
                  <div className="destination-card-foot">
                    <span>
                      {destLowestPrice
                        ? `Từ ${money(Number(destLowestPrice))} / đêm`
                        : liveDest?.landmark ?? dest.landmark}
                    </span>
                    <button
                      type="button"
                      className="destination-explore-btn"
                      onClick={(e) => {
                        e.stopPropagation();
                        selectDestinationAndScroll(
                          isSelected ? "ALL" : dest.code,
                        );
                      }}
                    >
                      <span>
                        {isSelected
                          ? "Đang chọn · Bỏ lọc"
                          : "Xem phòng khu vực"}
                      </span>
                      <AppIcon name="arrow" />
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </section>

      <section className="content-section" id="suites">
        <div className="section-heading">
          <div>
            <span>
              {activeDestinationObj
                ? `TUYỆT TÁC LƯU TRÚ TẠI: ${activeDestinationObj.name.toUpperCase()} · ${activeDestinationObj.province.toUpperCase()}`
                : "BỘ SƯU TẬP PHÒNG, SUITE & BIỆT THỰ TOÀN QUỐC"}
            </span>
            <h2>Kết quả theo thời gian thực</h2>
          </div>
          <div className="section-heading-actions">
            {activeDestinationObj && (
              <button
                type="button"
                className="clear-destination-btn"
                onClick={() => broadcastDestinationChange("ALL")}
              >
                <span>Đang lọc: {activeDestinationObj.name}</span>
                <AppIcon name="x" />
              </button>
            )}
            <p className="results-counter-pill">
              <AppIcon name="clock" />
              <span>
                {loading
                  ? "Đang kiểm tra tồn kho trực tiếp…"
                  : filteredByDestination.length
                    ? `${filteredByDestination.length} hạng phòng thượng hạng · ${filteredByDestination.reduce((s, o) => s + o.availableRooms, 0)} phòng sẵn sàng`
                    : "Chọn ngày để bắt đầu"}
              </span>
            </p>
          </div>
        </div>

        <div className="recommend-bar">
          <div className="recommend-bar-copy">
            <span className="recommend-icon-badge" aria-hidden="true">
              <AppIcon name="sparkles" />
            </span>
            <div>
              <small className="recommend-kicker">
                AI BESPOKE CONCIERGE · XẾP HẠNG ĐA TIÊU CHÍ
              </small>
              <strong>
                {activeDestinationObj
                  ? `Bạn muốn Quản gia AI tuyển chọn Suite hoàn hảo nhất tại ${activeDestinationObj.name}?`
                  : "Bạn muốn tìm căn Suite hoàn mỹ nhất chỉ trong một chạm?"}
              </strong>
              <span>
                {" "}
                Thuật toán AI đối chiếu điểm đến, ngân sách, sức chứa, tiện nghi
                đặc quyền và chỉ số hài lòng thực tế của khách lưu trú.
              </span>
            </div>
          </div>
          <button
            className="secondary recommend-trigger"
            onClick={() => void recommend()}
            disabled={aiLoading}
            aria-label="Gợi ý bằng AI"
          >
            <AppIcon name="sparkles" />
            <span>
              {aiLoading ? "Đang phân tích…" : "Nhờ Quản gia AI gợi ý"}
            </span>
          </button>
        </div>

        <div className="room-grid" aria-live="polite">
          {loading &&
            Array.from({ length: 3 }).map((_, index) => (
              <div className="room-skeleton" key={index} />
            ))}
          {filteredByDestination.map((option, index) => {
            const primaryDestination = resolveDestinationForRoom(option, index);
            const propertyBadgeText =
              option.propertyName || primaryDestination.propertyTitle;
            const landmarkBadgeText =
              option.landmark || primaryDestination.landmark;

            return (
              <article className="room-card" key={option.roomTypeId}>
                <div
                  className="room-visual"
                  style={{
                    backgroundImage: option.imageUrl
                      ? `linear-gradient(155deg, rgba(4, 21, 18, 0.22) 0%, rgba(4, 24, 20, 0.45) 52%, rgba(3, 18, 15, 0.88) 100%), url(${option.imageUrl}), url(/images/hotelai-room-atlas-v1.png)`
                      : `linear-gradient(155deg, rgba(4, 21, 18, 0.22) 0%, rgba(4, 24, 20, 0.45) 52%, rgba(3, 18, 15, 0.84) 100%), url(/images/hotelai-room-atlas-v1.png)`,
                    backgroundSize: option.imageUrl
                      ? "cover, cover, 300% 100%"
                      : "cover, 300% 100%",
                    backgroundPosition: option.imageUrl
                      ? `center, center, ${(index % 3) * 50}% center`
                      : `center, ${(index % 3) * 50}% center`,
                  }}
                >
                  <div className="room-visual-top">
                    <span className="room-code-badge">
                      <AppIcon name="diamond" /> {option.code || option.name}
                    </span>
                    <small className="room-capacity-pill">
                      <AppIcon name="users" /> {option.maxAdults} người lớn
                      {option.maxChildren > 0
                        ? ` · ${option.maxChildren} trẻ em`
                        : ""}
                    </small>
                  </div>
                  <div className="room-visual-bottom">
                    <span className="room-location-badge">
                      <AppIcon name="map-pin" />
                      HotelAI{" "}
                      {option.destinationName || primaryDestination.name} ·{" "}
                      {landmarkBadgeText}
                    </span>
                    <strong className="room-availability-pill">
                      <i /> Còn {option.availableRooms} phòng trống
                    </strong>
                  </div>
                </div>

                <div className="room-copy">
                  <div className="room-copy-badges">
                    <span className="room-property-tag">
                      <AppIcon name="crown" /> {propertyBadgeText}
                    </span>
                    {(option.styleTag || primaryDestination.styleTag) && (
                      <span className="room-style-tag">
                        {option.styleTag || primaryDestination.styleTag}
                      </span>
                    )}
                    {option.recommendationScore !== undefined && (
                      <span className="ai-score">
                        <AppIcon name="sparkles" />
                        <span>
                          Phù hợp {Math.round(option.recommendationScore * 100)}
                          %
                        </span>
                        <span className="sr-only">
                          AI phù hợp{" "}
                          {Math.round(option.recommendationScore * 100)}%
                        </span>
                      </span>
                    )}
                  </div>

                  <h3>{option.name}</h3>
                  <p>{option.description}</p>

                  <div className="room-luxury-highlights">
                    <span>
                      <AppIcon name="shield" /> Giữ phòng 15 phút
                    </span>
                    <span>
                      <AppIcon name="sparkles" /> Quản gia 24/7
                    </span>
                    <span>
                      <AppIcon name="sun" /> Trà chào mừng VIP
                    </span>
                  </div>

                  <ul>
                    {option.amenities.map((item) => (
                      <li key={item}>
                        <AppIcon name="check" />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                  <small>{option.cancellationPolicy}</small>
                  {option.reasons && (
                    <p className="ai-reasons">
                      <AppIcon name="sparkles" />{" "}
                      <span>{option.reasons.map(viAiReason).join(" · ")}</span>
                    </p>
                  )}
                </div>

                <div className="room-price">
                  <div>
                    <span>
                      Tổng chi phí kỳ nghỉ ({stayNights} đêm · trọn gói)
                    </span>
                    <strong>{money(option.totalAmount)}</strong>
                    <small>
                      Đã bao gồm thuế {money(option.taxAmount)} &amp; phí phục
                      vụ {money(option.serviceFee)}
                    </small>
                  </div>
                  <button
                    className="primary"
                    onClick={() => beginBooking(option)}
                  >
                    <span>Chọn phòng này</span>
                    <AppIcon name="arrow" />
                  </button>
                </div>
              </article>
            );
          })}
          {!loading && !filteredByDestination.length && !error && (
            <div className="empty-results">
              <span className="empty-results-icon" aria-hidden="true">
                <AppIcon name="search" />
              </span>
              <h3>Chưa tìm thấy hạng phòng phù hợp tại khu vực này</h3>
              <p>
                Hãy thử chuyển sang &ldquo;Toàn quốc · Tất cả 10 điểm
                đến&rdquo;, thay đổi ngày lưu trú hoặc mở rộng khoảng ngân sách.
              </p>
              {selectedDestination !== "ALL" && (
                <button
                  type="button"
                  className="secondary"
                  style={{ marginTop: "14px" }}
                  onClick={() => broadcastDestinationChange("ALL")}
                >
                  Xem tất cả 10 điểm đến Việt Nam
                </button>
              )}
            </div>
          )}
        </div>
      </section>

      <section className="experience-section">
        <div className="experience-copy">
          <span>NGHỆ THUẬT SỐNG THƯỢNG LƯU · HAUTE HOSPITALITY</span>
          <h2>Một khu nghỉ dưỡng, vạn khoảnh khắc được chế tác riêng.</h2>
          <p>
            Từ khoảnh khắc xe Limousine đón bạn tại sân bay đến nghi thức chúc
            ngủ ngon mỗi tối, lễ tân VIP, buồng phòng và Quản gia AI kết nối
            liền mạch trên cùng một nền tảng thời gian thực.
          </p>

          <div
            className="sanctuary-tabs"
            role="tablist"
            aria-label="Không gian đặc trưng"
          >
            {sanctuaryHighlights.map((item, idx) => (
              <button
                key={item.title}
                type="button"
                role="tab"
                aria-selected={activeSanctuaryTab === idx}
                className={`sanctuary-tab ${activeSanctuaryTab === idx ? "active" : ""}`}
                onClick={() => setActiveSanctuaryTab(idx)}
              >
                <small>
                  0{idx + 1} · {item.kicker}
                </small>
                <strong>{item.title}</strong>
              </button>
            ))}
          </div>

          <div className="sanctuary-active-card" role="tabpanel">
            <p>{sanctuaryHighlights[activeSanctuaryTab].description}</p>
            <div className="sanctuary-active-meta">
              <span>
                <AppIcon name="clock" />
                {sanctuaryHighlights[activeSanctuaryTab].detail}
              </span>
              <b>
                {sanctuaryHighlights[activeSanctuaryTab].metric}{" "}
                <small>
                  {sanctuaryHighlights[activeSanctuaryTab].metricLabel}
                </small>
              </b>
            </div>
          </div>

          <div className="experience-stats">
            <div>
              <b>24/7</b>
              <small>Private Butler &amp; Quản gia AI</small>
            </div>
            <div>
              <b>{roomTypes.length || "10+"}</b>
              <small>tuyệt tác Suite &amp; Biệt thự</small>
            </div>
            <div>
              <b>4.96 ★</b>
              <small>chỉ số hài lòng tuyệt đối</small>
            </div>
          </div>

          <Link className="secondary" href="/rooms">
            Khám phá toàn bộ Bộ sưu tập Suite <AppIcon name="arrow" />
          </Link>
        </div>

        <div
          className="experience-visual"
          aria-label="Không gian nghỉ dưỡng HotelAI"
        >
          <div className="experience-location-tag">
            <AppIcon name="diamond" />
            <span>
              {activeHeroSpotlight.propertyTitle} ·{" "}
              {activeHeroSpotlight.landmark}
            </span>
          </div>

          <div
            className="sanctuary-day-timeline"
            aria-label="Nhịp điệu một ngày tại khu nghỉ dưỡng"
          >
            <span className="timeline-kicker">
              NHỊP ĐIỆU MỘT NGÀY TẠI SANCTUARY
            </span>
            <div className="timeline-list">
              {dayRhythmMoments.map((moment) => (
                <div className="timeline-item" key={moment.time}>
                  <span className="timeline-time">
                    <AppIcon name={moment.icon} /> {moment.time}
                  </span>
                  <div>
                    <strong>{moment.title}</strong>
                    <small>{moment.caption}</small>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="experience-badge">
            <AppIcon name="sparkles" />
            <span>
              <b>Quản gia AI đồng hành</b>
              <small>Gợi ý cá nhân hóa theo gu thẩm mỹ &amp; lịch trình</small>
            </span>
          </div>
        </div>
      </section>

      <section className="guest-services-section">
        <header>
          <div>
            <span>ĐẶC QUYỀN HỘI VIÊN &amp; DỊCH VỤ TIỀN LƯU TRÚ</span>
            <h2>Tiện nghi đúng lúc, chăm sóc đúng tâm ý</h2>
          </div>
          <p>
            Đặt trước các nghi thức đón tiếp, ẩm thực và trị liệu spa cùng phòng
            lưu trú để đội ngũ Quản gia chuẩn bị hoàn hảo trước giờ bạn đến.
          </p>
        </header>

        <div className="services-pillars-grid">
          <article>
            <div className="pillar-top">
              <span>
                <AppIcon name="crown" />
              </span>
              <small className="pillar-num">ĐẶC QUYỀN I</small>
            </div>
            <b>Đón tiếp Sảnh VIP &amp; Private Check-in</b>
            <p>
              Nhận phòng riêng tư tại sảnh chờ thượng hạng hoặc ngay tại biệt
              thự, kèm khăn lạnh thảo mộc, trà sen và hỗ trợ nâng hạng linh
              hoạt.
            </p>
          </article>
          <article>
            <div className="pillar-top">
              <span>
                <AppIcon name="sparkles" />
              </span>
              <small className="pillar-num">ĐẶC QUYỀN II</small>
            </div>
            <b>Không gian Ngủ Đo ni Đóng giày</b>
            <p>
              Thực đơn 5 loại gối lông vũ &amp; lụa tơ tằm, tinh dầu khuếch tán
              theo sở thích và nhiệt độ phòng được chuẩn bị sẵn trước khi bạn mở
              cửa.
            </p>
          </article>
          <article>
            <div className="pillar-top">
              <span>
                <AppIcon name="wine" />
              </span>
              <small className="pillar-num">ĐẶC QUYỀN III</small>
            </div>
            <b>Ẩm thực &amp; Kỷ niệm Độc bản</b>
            <p>
              Từ bữa tối nến bên bờ biển, rượu Champagne kỷ niệm ngày cưới đến
              thực đơn dinh dưỡng riêng cho trẻ nhỏ đều được bếp trưởng chăm
              chút.
            </p>
          </article>
        </div>

        {services.length > 0 && (
          <div
            className="curated-addons-strip"
            aria-label="Dịch vụ đặt trước nổi bật"
          >
            <div className="curated-addons-head">
              <div>
                <span>BESPOKE PRE-ARRIVAL ATELIER</span>
                <h3>Dịch vụ thượng hạng đính kèm kỳ nghỉ</h3>
              </div>
              <small>
                Chạm để đính kèm dịch vụ vào biểu mẫu đặt phòng của bạn
              </small>
            </div>
            <div className="curated-addons-list">
              {services.slice(0, 4).map((service, idx) => {
                const isPreselected = bookingForm.serviceId === service.id;
                return (
                  <article
                    className={`addon-card ${isPreselected ? "addon-card-selected" : ""}`}
                    key={service.id}
                  >
                    <div>
                      <div className="addon-card-top">
                        <span className="addon-roman">
                          {["I", "II", "III", "IV"][idx]}
                        </span>
                        <span className="addon-code">{service.code}</span>
                      </div>
                      <h4>{service.name}</h4>
                      <p>
                        {money(service.currentPrice.unitPrice)} /{" "}
                        {viLabel(service.unit, service.unit)}
                      </p>
                    </div>
                    <button
                      type="button"
                      className={`secondary addon-select-btn ${isPreselected ? "active" : ""}`}
                      onClick={() => preselectServiceAndScroll(service.id)}
                    >
                      <span>
                        {isPreselected
                          ? "Đã chọn đính kèm"
                          : "Chọn dịch vụ này"}
                      </span>
                      <AppIcon name={isPreselected ? "check" : "arrow"} />
                    </button>
                  </article>
                );
              })}
            </div>
          </div>
        )}
      </section>

      <section
        className="luxury-testimonials"
        aria-label="Đánh giá từ khách lưu trú"
      >
        <header className="testimonials-header">
          <div>
            <span>NHẬT KÝ LƯU TRÚ &amp; ẤN TƯỢNG THƯỢNG KHÁCH</span>
            <h2>Được tin chọn bởi những tâm hồn yêu sự hoàn mỹ</h2>
          </div>
          <div className="testimonials-score-card">
            <div className="score-stars" aria-hidden="true">
              <AppIcon name="star" />
              <AppIcon name="star" />
              <AppIcon name="star" />
              <AppIcon name="star" />
              <AppIcon name="star" />
            </div>
            <strong>4.96 / 5.0</strong>
            <small>Từ hơn 1.280 kỳ nghỉ 5 sao đã xác thực</small>
          </div>
        </header>
        <div className="testimonials-grid">
          {guestTestimonials.map((item) => (
            <article className="testimonial-card" key={item.author}>
              <div className="testimonial-rating">
                <span className="testimonial-stars" aria-hidden="true">
                  <AppIcon name="star" />
                  <AppIcon name="star" />
                  <AppIcon name="star" />
                  <AppIcon name="star" />
                  <AppIcon name="star" />
                </span>
                <b>{item.rating} ★</b>
              </div>
              <blockquote>“{item.quote}”</blockquote>
              <footer>
                <div className="testimonial-author-row">
                  <div>
                    <strong>{item.author}</strong>
                    <small>{item.stayType}</small>
                  </div>
                  <span className="verified-stay-pill">
                    <AppIcon name="check" /> Đã xác thực
                  </span>
                </div>
              </footer>
            </article>
          ))}
        </div>
      </section>

      <section className="public-cta">
        <div className="public-cta-copy">
          <span>KHỞI ĐẦU HÀNH TRÌNH ĐÁNG NHỚ</span>
          <h2>Chọn ngày, xem giá thật và giữ phòng chỉ trong vài phút.</h2>
          <p className="public-cta-sub">
            Đặt trực tiếp trên hệ thống chính thức của HotelAI Grand Collection
            để hưởng quyền lợi nhận phòng ưu tiên, trà chiều thượng hạng và sự
            chăm sóc từ Quản gia AI 24/7.
          </p>
        </div>
        <div className="public-cta-actions">
          <a className="cta-button" href="#search">
            <AppIcon name="crown" />
            <span>Tìm phòng ngay</span>
            <AppIcon name="arrow" />
          </a>
          <Link className="cta-secondary-button" href="/rooms">
            <span>Xem danh mục Suite</span>
          </Link>
        </div>
      </section>

      {selected && user && (
        <div
          className="modal-backdrop"
          role="presentation"
          onMouseDown={() => setSelected(undefined)}
        >
          <form
            className="booking-modal"
            onSubmit={book}
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="modal-heading">
              <div>
                <span>HOÀN TẤT ĐẶT PHÒNG ĐẶC QUYỀN</span>
                <h2>{selected.name}</h2>
                <p className="modal-location-badge">
                  <AppIcon name="map-pin" />
                  <span>
                    {selected.propertyName ||
                      resolveDestinationForRoom(selected, 0).propertyTitle}{" "}
                    ·{" "}
                    {selected.landmark ||
                      resolveDestinationForRoom(selected, 0).landmark}
                  </span>
                </p>
              </div>
              <button
                type="button"
                aria-label="Đóng"
                onClick={() => setSelected(undefined)}
              >
                <AppIcon name="x" />
              </button>
            </div>
            <div className="booking-summary">
              <span>
                {criteria.checkIn} → {criteria.checkOut} ({stayNights} đêm)
              </span>
              <span>
                {criteria.adults} người lớn · {criteria.rooms} phòng
              </span>
              <strong>{money(selected.totalAmount)}</strong>
            </div>
            <label>
              Khách liên hệ
              <input value={`${user.fullName} · ${user.email}`} disabled />
            </label>
            <div className="form-row">
              <label>
                Loại giấy tờ
                <select
                  value={bookingForm.documentType}
                  onChange={(event) =>
                    setBookingForm({
                      ...bookingForm,
                      documentType: event.target.value,
                    })
                  }
                >
                  {["CCCD", "PASSPORT", "CMND"].map((value) => (
                    <option key={value} value={value}>
                      {viDocumentType(value)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Số giấy tờ
                <input
                  value={bookingForm.documentNumber}
                  onChange={(event) =>
                    setBookingForm({
                      ...bookingForm,
                      documentNumber: event.target.value,
                    })
                  }
                />
              </label>
              <label>
                Quốc tịch
                <select
                  value={bookingForm.nationality}
                  onChange={(event) =>
                    setBookingForm({
                      ...bookingForm,
                      nationality: event.target.value,
                    })
                  }
                >
                  <option value="VN">Việt Nam</option>
                  <option value="SG">Singapore</option>
                  <option value="TH">Thái Lan</option>
                  <option value="KR">Hàn Quốc</option>
                  <option value="JP">Nhật Bản</option>
                  <option value="CN">Trung Quốc</option>
                  <option value="US">Hoa Kỳ</option>
                  <option value="GB">Vương quốc Anh</option>
                  <option value="FR">Pháp</option>
                  <option value="DE">Đức</option>
                  <option value="AU">Úc</option>
                  <option value="CA">Canada</option>
                </select>
              </label>
            </div>
            <label>
              Mã khuyến mãi
              <input
                placeholder="Nhập mã nếu bạn có"
                value={bookingForm.promotionCode}
                onChange={(event) =>
                  setBookingForm({
                    ...bookingForm,
                    promotionCode: event.target.value.toUpperCase(),
                  })
                }
              />
            </label>
            <div className="form-row">
              <label>
                Dịch vụ đặt trước
                <select
                  value={bookingForm.serviceId}
                  onChange={(event) =>
                    setBookingForm({
                      ...bookingForm,
                      serviceId: event.target.value,
                    })
                  }
                >
                  <option value="">Không chọn</option>
                  {services.map((service) => (
                    <option key={service.id} value={service.id}>
                      {service.name} · {money(service.currentPrice.unitPrice)}/
                      {viLabel(service.unit, service.unit)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Số lượng
                <input
                  type="number"
                  min="1"
                  value={bookingForm.serviceQuantity}
                  onChange={(event) =>
                    setBookingForm({
                      ...bookingForm,
                      serviceQuantity: event.target.value,
                    })
                  }
                />
              </label>
            </div>
            <label>
              Yêu cầu đặc biệt
              <textarea
                placeholder="Giường phụ, tầng cao…"
                value={bookingForm.specialRequests}
                onChange={(event) =>
                  setBookingForm({
                    ...bookingForm,
                    specialRequests: event.target.value,
                  })
                }
              />
            </label>
            <p className="muted">
              Chúng tôi sẽ kiểm tra lại số phòng trống và giữ lựa chọn này cho
              bạn trong 15 phút.
            </p>
            <button className="primary">Xác nhận giữ phòng</button>
          </form>
        </div>
      )}
    </>
  );
}
