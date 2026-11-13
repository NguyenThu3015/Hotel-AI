"use client";

import { api } from "@/lib/api";
import { isPresentableRoomType, viGuestText } from "@/lib/vi";
import { AppIcon } from "@/components/app-icon";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

interface Amenity {
  id: string;
  name: string;
}

interface RoomImage {
  url: string;
  active: boolean;
}

interface CatalogRoomType {
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
  amenities: Amenity[];
  images: RoomImage[];
  destinationCode?: string;
  destinationName?: string;
  province?: string;
  region?: string;
  regionLabel?: string;
  styleTag?: string;
  propertyName?: string;
  landmark?: string;
}

interface AvailabilityItem {
  roomTypeId: string;
  availableRooms: number;
  price: {
    pricingStatus: string;
    total: number | null;
  };
}

interface AvailabilityResponse {
  content: AvailabilityItem[];
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
    maximumFractionDigits: 0,
  }).format(value);
}

export default function RoomsPage() {
  const [roomTypes, setRoomTypes] = useState<CatalogRoomType[]>([]);
  const [availability, setAvailability] = useState<AvailabilityItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const query = new URLSearchParams({
      checkIn: iso(1),
      checkOut: iso(2),
      adults: "1",
      children: "0",
      rooms: "1",
    });
    void Promise.all([
      api.getPublic<CatalogRoomType[]>("/catalog/room-types"),
      api.getPublic<AvailabilityResponse>(`/availability/search?${query}`),
    ])
      .then(([catalog, current]) => {
        setRoomTypes(
          catalog.filter(
            (type) =>
              type.published &&
              type.active &&
              isPresentableRoomType(type.name, type.code),
          ),
        );
        setAvailability(current.content);
      })
      .finally(() => setLoading(false));
  }, []);

  const availabilityByType = useMemo(
    () => new Map(availability.map((item) => [item.roomTypeId, item])),
    [availability],
  );

  return (
    <div className="catalog-page">
      <section className="catalog-hero">
        <div>
          <span>KHÔNG GIAN NGHỈ DƯỠNG</span>
          <h1>Khám phá mọi hạng phòng</h1>
          <p>
            Toàn bộ hạng phòng đang được HotelAI công khai, kèm sức chứa, tiện
            nghi và tình trạng mở bán gần nhất.
          </p>
        </div>
        <aside>
          <b>{roomTypes.length || "—"}</b>
          <span>hạng phòng đang kinh doanh</span>
          <small>Giá và tồn được kiểm tra cho đêm kế tiếp</small>
        </aside>
      </section>

      <section className="catalog-content">
        <div className="catalog-toolbar">
          <div>
            <span>DANH MỤC PHÒNG</span>
            <h2>Chọn không gian hợp với bạn</h2>
          </div>
          <Link className="primary" href="/#search">
            Kiểm tra ngày lưu trú
          </Link>
        </div>

        <div className="catalog-grid" aria-live="polite">
          {loading &&
            Array.from({ length: 6 }).map((_, index) => (
              <div className="catalog-skeleton" key={index} />
            ))}
          {!loading &&
            roomTypes.map((type, index) => {
              const current = availabilityByType.get(type.id);
              const sellable = Boolean(
                current &&
                current.availableRooms > 0 &&
                current.price.pricingStatus === "AVAILABLE" &&
                current.price.total,
              );
              const image = type.images.find(
                (item) =>
                  item.active &&
                  !item.url.includes(".invalid/") &&
                  !item.url.includes("pixabay.com/vi/images/download/"),
              );

              return (
                <article className="catalog-card" key={type.id}>
                  <div
                    className={`catalog-image tone-${(index % 3) + 1}`}
                    style={{
                      backgroundImage: image
                        ? `linear-gradient(145deg, rgba(4,36,31,.08), rgba(4,36,31,.7)), url(${image.url}), url(/images/hotelai-room-atlas-v1.png)`
                        : `linear-gradient(145deg, rgba(4,36,31,.08), rgba(4,36,31,.62)), url(/images/hotelai-room-atlas-v1.png)`,
                      backgroundSize: image
                        ? "cover, cover, 300% 100%"
                        : "cover, 300% 100%",
                      backgroundPosition: image
                        ? `center, center, ${(index % 3) * 50}% center`
                        : `center, ${(index % 3) * 50}% center`,
                    }}
                  >
                    <span>{type.code.replaceAll("_", " ")}</span>
                    <b className={sellable ? "available" : "paused"}>
                      {sellable
                        ? `Còn ${current?.availableRooms} phòng`
                        : "Đang cập nhật lịch bán"}
                    </b>
                  </div>
                  <div className="catalog-copy">
                    <div className="room-copy-badges">
                      <span className="room-property-tag">
                        <AppIcon name="map-pin" />{" "}
                        {type.propertyName || "HotelAI Phú Quốc Emerald Bay"} ·{" "}
                        {type.landmark || "Bãi Kem"}
                      </span>
                      {type.styleTag && (
                        <span className="room-style-tag">{type.styleTag}</span>
                      )}
                    </div>
                    <h2>{type.name}</h2>
                    <p>{type.description}</p>
                    <div className="catalog-facts">
                      <span>Tối đa {type.maxOccupancy} khách</span>
                      {type.sizeSqm ? <span>{type.sizeSqm} m²</span> : null}
                      {type.bedDescription ? (
                        <span>{type.bedDescription}</span>
                      ) : null}
                    </div>
                    <ul>
                      {type.amenities.slice(0, 8).map((amenity) => (
                        <li key={amenity.id}>{amenity.name}</li>
                      ))}
                    </ul>
                    {type.policySummary ? (
                      <small>{viGuestText(type.policySummary)}</small>
                    ) : null}
                  </div>
                  <footer>
                    <div>
                      <span>
                        {sellable ? "Giá tham khảo từ" : "Tình trạng"}
                      </span>
                      <strong>
                        {sellable
                          ? money(Number(current?.price.total))
                          : "Liên hệ lễ tân"}
                      </strong>
                      <small>cho 1 đêm · giá thực tế theo ngày chọn</small>
                    </div>
                    <Link
                      className={sellable ? "primary" : "secondary"}
                      href={`/?roomTypeId=${encodeURIComponent(type.id)}${type.destinationCode ? `&destination=${encodeURIComponent(type.destinationCode)}` : ""}#search`}
                    >
                      {sellable ? "Chọn ngày đặt phòng" : "Xem ngày khác"}
                    </Link>
                  </footer>
                </article>
              );
            })}
        </div>
      </section>
    </div>
  );
}
