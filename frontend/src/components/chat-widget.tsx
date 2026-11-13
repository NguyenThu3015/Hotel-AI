"use client";

import { api } from "@/lib/api";
import { friendlyError } from "@/lib/vi";
import { FormEvent, useEffect, useState } from "react";
import { AppIcon } from "@/components/app-icon";

interface Message {
  role: "user" | "assistant";
  content: string;
  sources?: string[];
  tools?: string[];
  degraded?: boolean;
}

interface DestinationContext {
  code: string;
  name: string | null;
  propertyTitle: string | null;
}

export function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [sessionId, setSessionId] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [destination, setDestination] = useState<DestinationContext>({
    code: "ALL",
    name: null,
    propertyTitle: null,
  });
  const [messages, setMessages] = useState<Message[]>([
    {
      role: "assistant",
      content:
        "Xin chào! Tôi là trợ lý Chuỗi Khu Nghỉ Dưỡng & Khách Sạn HotelAI Việt Nam. Bạn có thể hỏi tôi về phòng trống theo điểm đến (Phú Quốc, Đà Nẵng, Hạ Long, Sapa, Hội An, Nha Trang, Đà Lạt, Hà Nội, TP.HCM, Vũng Tàu), mức giá, tiện nghi hoặc chính sách.",
    },
  ]);

  useEffect(() => {
    function handleDestinationChange(event: Event) {
      const custom = event as CustomEvent<DestinationContext>;
      if (custom.detail) {
        setDestination(custom.detail);
      }
    }
    window.addEventListener(
      "hotelai:destination-change",
      handleDestinationChange,
    );
    return () =>
      window.removeEventListener(
        "hotelai:destination-change",
        handleDestinationChange,
      );
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const message = text.trim();
    if (!message) return;
    setText("");
    setLoading(true);
    setMessages((items) => [...items, { role: "user", content: message }]);
    try {
      const response = await api.post<{
        session_id: string;
        answer: string;
        sources: Array<{ title: string }>;
        tools_used: string[];
        provider: string;
        degraded: boolean;
      }>("/ai/chat", {
        message,
        sessionId,
        ...(destination.code &&
          destination.code !== "ALL" && {
            destination_code: destination.code,
          }),
      });
      setSessionId(response.session_id);
      setMessages((items) => [
        ...items,
        {
          role: "assistant",
          content: response.answer,
          sources: response.sources.map((source) => source.title),
          tools: response.tools_used,
          degraded: response.degraded,
        },
      ]);
    } catch (error) {
      setMessages((items) => [
        ...items,
        {
          role: "assistant",
          content: friendlyError(
            error,
            "Tôi chưa thể trả lời ngay lúc này. Bạn vui lòng thử lại sau một chút nhé.",
          ),
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  const quickSuggestions = destination.name
    ? [
        `Tìm phòng tại ${destination.name} cho 2 người`,
        "Chính sách hủy phòng?",
        "Giờ nhận phòng là mấy giờ?",
      ]
    : [
        "Chính sách hủy phòng?",
        "Tìm phòng tại Phú Quốc cho 2 người",
        "Giờ nhận phòng là mấy giờ?",
      ];

  return (
    <div className={`chat ${open ? "open" : ""}`}>
      {open && (
        <section className="chat-panel">
          <div className="chat-title">
            <div>
              <span className="assistant-avatar">
                <AppIcon name="sparkles" />
              </span>
              <span>
                <b>Trợ lý HotelAI</b>
                <small>
                  <i />{" "}
                  {destination.name
                    ? `Đang tư vấn: ${destination.name}`
                    : "Sẵn sàng hỗ trợ 10 điểm đến"}
                </small>
              </span>
            </div>
            <button aria-label="Đóng trợ lý" onClick={() => setOpen(false)}>
              <AppIcon name="x" />
            </button>
          </div>
          <div className="chat-messages">
            {destination.name && (
              <div className="chat-destination-pill">
                <AppIcon name="map-pin" />
                <span>
                  Ưu tiên đề xuất tại{" "}
                  <b>{destination.propertyTitle || `HotelAI ${destination.name}`}</b>
                </span>
              </div>
            )}
            {messages.map((message, index) => (
              <article key={index} className={message.role}>
                <p>{message.content}</p>
                {!!message.tools?.length && (
                  <small>✓ Đã đối chiếu với dữ liệu khách sạn</small>
                )}
                {!!message.sources?.length && (
                  <small>Nguồn: {message.sources.join(" · ")}</small>
                )}
                {message.degraded && (
                  <small>
                    Trợ lý đang dùng chế độ hỗ trợ cơ bản; một số gợi ý có thể
                    chưa được cá nhân hóa.
                  </small>
                )}
              </article>
            ))}
            {loading && (
              <article className="assistant typing">
                <i />
                <i />
                <i />
              </article>
            )}
          </div>
          {messages.length === 1 && (
            <div className="chat-suggestions">
              {quickSuggestions.map((suggestion) => (
                <button key={suggestion} onClick={() => setText(suggestion)}>
                  {suggestion}
                </button>
              ))}
            </div>
          )}
          <form onSubmit={submit}>
            <input
              value={text}
              onChange={(event) => setText(event.target.value)}
              placeholder={
                destination.name
                  ? `Hỏi về phòng tại ${destination.name}...`
                  : "Tôi cần phòng cho 2 người..."
              }
              aria-label="Nội dung cần hỏi"
            />
            <button
              disabled={loading || !text.trim()}
              aria-label="Gửi câu hỏi"
            >
              Gửi
            </button>
          </form>
        </section>
      )}
      {!open && (
        <button
          className="chat-launcher"
          onClick={() => setOpen(true)}
          aria-label="Hỏi trợ lý AI"
        >
          <span>
            <AppIcon name="sparkles" />
          </span>
          <b>Hỏi trợ lý</b>
          <small>
            {destination.name ? `HotelAI ${destination.name}` : "Hỗ trợ 24/7"}
          </small>
        </button>
      )}
    </div>
  );
}
