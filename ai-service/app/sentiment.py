TOPICS = {
    "vệ sinh": {"sạch", "bẩn", "vệ sinh", "mùi"},
    "nhân viên": {"nhân viên", "lễ tân", "phục vụ", "thái độ"},
    "đồ ăn": {"đồ ăn", "bữa sáng", "nhà hàng", "món"},
    "tiếng ồn": {"ồn", "tiếng ồn", "yên tĩnh"},
    "vị trí": {"vị trí", "trung tâm", "đi lại"},
    "giá": {"giá", "đắt", "rẻ", "chi phí"},
    "tiện nghi": {"wifi", "hồ bơi", "điều hòa", "tiện nghi"},
}
POSITIVE = {"tốt", "tuyệt", "sạch", "thân thiện", "hài lòng", "đẹp", "ngon", "nhanh"}
NEGATIVE = {"tệ", "bẩn", "ồn", "chậm", "thất vọng", "đắt", "hỏng", "khó chịu"}


def analyze(text: str) -> dict:
    lowered = text.lower()
    positive = sum(keyword in lowered for keyword in POSITIVE)
    negative = sum(keyword in lowered for keyword in NEGATIVE)
    for phrase in ("không sạch", "không tốt", "không thân thiện", "chưa hài lòng"):
        if phrase in lowered:
            negative += 1
            positive = max(0, positive - 1)
    delta = positive - negative
    sentiment = "POSITIVE" if delta > 0 else "NEGATIVE" if delta < 0 else "NEUTRAL"
    score = max(-1.0, min(1.0, delta / max(positive + negative, 1)))
    topics = [topic for topic, keywords in TOPICS.items() if any(keyword in lowered for keyword in keywords)]
    return {"sentiment": sentiment, "score": round(score, 4), "topics": topics}
